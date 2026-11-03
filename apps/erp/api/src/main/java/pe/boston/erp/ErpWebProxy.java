package pe.boston.erp;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController @RequestMapping("/api/erp-web")
public class ErpWebProxy {
  private final OAuth2AuthorizedClientService clientes;
  private final OAuth2AuthorizedClientManager gestor;
  private final JwtDecoder decodificador;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  public ErpWebProxy(OAuth2AuthorizedClientService clientes,OAuth2AuthorizedClientManager gestor,JwtDecoder decodificador) {
    this.clientes=clientes;this.gestor=gestor;this.decodificador=decodificador;
  }
  private record Acceso(OAuth2AuthorizedClient cliente,Jwt jwt) {}
  private Acceso acceso(Authentication auth) {
    if(!(auth instanceof OAuth2AuthenticationToken oauth)) return null;
    String usuario=oauth.getName();
    var cliente=clientes.loadAuthorizedClient("boston-erp",usuario);
    if(cliente==null) return null;
    try {
      var actualizado=gestor.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("boston-erp").principal(oauth).build());
      if(actualizado==null) return null;
      return new Acceso(actualizado,decodificador.decode(actualizado.getAccessToken().getTokenValue()));
    } catch(OAuth2AuthorizationException | JwtException e) {
      clientes.removeAuthorizedClient("boston-erp",usuario);
      return null;
    }
  }
  @GetMapping("/sesion") public Map<String,Object> sesion(Authentication auth,CsrfToken csrf) {
    Acceso acceso=acceso(auth);
    if(acceso==null) return Map.of("autenticado",false,"csrf",csrf.getToken());
    Jwt jwt=acceso.jwt();
    String tipo=jwt.getClaimAsString("tipo");
    boolean autorizado="DIRECCION".equals(tipo)||"COORDINACION".equals(tipo);
    return Map.of("autenticado",true,"autorizado",autorizado,"tipo",tipo,"usuario",jwt.getSubject(),"csrf",csrf.getToken());
  }
  @RequestMapping(value="/v1/**")
  public ResponseEntity<byte[]> enviar(HttpServletRequest request,Authentication auth) throws IOException,InterruptedException {
    Acceso acceso=acceso(auth);
    if(acceso==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    String ruta=request.getRequestURI().substring("/api/erp-web/v1".length());
    if(ruta.contains("..")||ruta.contains("//")) return ResponseEntity.badRequest().build();
    String consulta=request.getQueryString();
    URI destino=URI.create("http://127.0.0.1:8080/api/erp/v1"+ruta+(consulta==null?"":"?"+consulta));
    byte[] cuerpo;
    String tipo=request.getContentType();
    if(request instanceof MultipartHttpServletRequest multipart) {
      var archivo=multipart.getFile("archivo");
      if(archivo==null) return ResponseEntity.badRequest().build();
      String frontera="boston-erp-"+java.util.UUID.randomUUID();
      var bytes=new ByteArrayOutputStream();
      bytes.write(("--"+frontera+"\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"archivo.xlsx\"\r\n"
        +"Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      bytes.write(archivo.getBytes());
      bytes.write(("\r\n--"+frontera+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      cuerpo=bytes.toByteArray();
      tipo="multipart/form-data; boundary="+frontera;
    } else cuerpo=request.getInputStream().readAllBytes();
    var builder=HttpRequest.newBuilder(destino).timeout(Duration.ofSeconds(30))
      .header("Authorization","Bearer "+acceso.cliente().getAccessToken().getTokenValue());
    if(tipo!=null) builder.header("Content-Type",tipo);
    HttpRequest externo=builder.method(request.getMethod(),HttpRequest.BodyPublishers.ofByteArray(cuerpo)).build();
    HttpResponse<byte[]> respuesta=http.send(externo,HttpResponse.BodyHandlers.ofByteArray());
    HttpHeaders cabeceras=new HttpHeaders();
    respuesta.headers().firstValue("Content-Type").ifPresent(v -> cabeceras.set("Content-Type",v));
    respuesta.headers().firstValue("Content-Disposition").ifPresent(v -> cabeceras.set("Content-Disposition",v));
    return new ResponseEntity<>(respuesta.body(),cabeceras,org.springframework.http.HttpStatusCode.valueOf(respuesta.statusCode()));
  }
}
