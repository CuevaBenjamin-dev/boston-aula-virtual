package pe.boston.aula;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;

/** Pasarela local de operaciones institucionales: conserva el token del administrador en el BFF. */
@RestController @RequestMapping("/api/aula/administracion-erp")
public class ErpAdministracionController {
  private final UsuarioActual usuario;
  private final String erp;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  public ErpAdministracionController(UsuarioActual usuario,@Value("${BOSTON_ERP_URL}") String erp) { this.usuario=usuario;this.erp=erp; }
  private ResponseEntity<String> enviar(String ruta,String metodo,String cuerpo,Authentication actor,OAuth2AuthorizedClient client) throws Exception {
    usuario.administrador(actor);
    HttpRequest.Builder solicitud=HttpRequest.newBuilder(URI.create(erp+"/api/erp"+ruta)).timeout(Duration.ofSeconds(8))
      .header("Authorization","Bearer "+client.getAccessToken().getTokenValue());
    if("GET".equals(metodo)) solicitud.GET();
    else solicitud.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(cuerpo==null?"{}":cuerpo));
    var respuesta=http.send(solicitud.build(),HttpResponse.BodyHandlers.ofString());
    return ResponseEntity.status(respuesta.statusCode()).contentType(MediaType.APPLICATION_JSON).body(respuesta.body());
  }
  @GetMapping("/ofertas") public ResponseEntity<String> ofertas(Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/ofertas","GET",null,a,c); }
  @GetMapping("/catalogo") public ResponseEntity<String> catalogo(Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/catalogo","GET",null,a,c); }
  @PostMapping("/reconciliar") public ResponseEntity<String> reconciliar(Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { usuario.developerRequerido(a);return enviar("/reconciliar","POST","{}",a,c); }
  @PostMapping("/secciones") public ResponseEntity<String> nuevaSeccion(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/secciones","POST",d,a,c); }
  @PostMapping("/ofertas") public ResponseEntity<String> nuevaOferta(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/ofertas","POST",d,a,c); }
  @PostMapping("/docentes") public ResponseEntity<String> nuevoDocente(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/docentes","POST",d,a,c); }
  @PostMapping("/apoderados") public ResponseEntity<String> nuevoApoderado(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/apoderados","POST",d,a,c); }
  @PostMapping("/estudiantes") public ResponseEntity<String> estudiante(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes","POST",d,a,c); }
  @PostMapping("/estudiantes/{id}/apoderados") public ResponseEntity<String> vincularApoderado(@PathVariable UUID id,@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes/"+id+"/apoderados","POST",d,a,c); }
  @GetMapping("/estudiantes/{id}/apoderados") public ResponseEntity<String> apoderados(@PathVariable UUID id,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes/"+id+"/apoderados","GET",null,a,c); }
  @PostMapping("/matriculas") public ResponseEntity<String> matricula(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/matriculas","POST",d,a,c); }
  @PutMapping("/estudiantes/{id}/seccion") public ResponseEntity<String> seccion(@PathVariable UUID id,@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes/"+id+"/seccion","PUT",d,a,c); }
  @PostMapping("/estudiantes/{id}/retiro") public ResponseEntity<String> retiro(@PathVariable UUID id,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes/"+id+"/retiro","POST",null,a,c); }
  @PutMapping("/estudiantes/{id}/estado") public ResponseEntity<String> estado(@PathVariable UUID id,@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/estudiantes/"+id+"/estado","PUT",d,a,c); }
  @PutMapping("/ofertas/{id}/docente") public ResponseEntity<String> docente(@PathVariable UUID id,@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/ofertas/"+id+"/docente","PUT",d,a,c); }
  @GetMapping("/tesoreria/estudiantes/{id}") public ResponseEntity<String> deuda(@PathVariable UUID id,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/tesoreria/estudiantes/"+id,"GET",null,a,c); }
  @PostMapping("/tesoreria/pagos") public ResponseEntity<String> pago(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/tesoreria/pagos","POST",d,a,c); }
  @PostMapping("/tesoreria/conceptos") public ResponseEntity<String> concepto(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/tesoreria/conceptos","POST",d,a,c); }
  @PostMapping("/tesoreria/obligaciones") public ResponseEntity<String> obligacion(@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/tesoreria/obligaciones","POST",d,a,c); }
  @PostMapping("/tesoreria/pagos/{id}/anular") public ResponseEntity<String> anular(@PathVariable UUID id,@RequestBody String d,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient c) throws Exception { return enviar("/tesoreria/pagos/"+id+"/anular","POST",d,a,c); }
}
