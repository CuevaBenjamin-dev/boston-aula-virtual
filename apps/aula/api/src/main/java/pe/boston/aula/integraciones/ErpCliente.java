package pe.boston.aula.integraciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component public class ErpCliente {
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper json=new ObjectMapper();
  private final String tokenUri,erpUrl,secret;
  private String tokenActual;
  private Instant vence=Instant.EPOCH;
  public ErpCliente(@Value("${BOSTON_TOKEN_URI}") String tokenUri,@Value("${BOSTON_ERP_URL}") String erpUrl,@Value("${BOSTON_ERP_SECRET}") String secret) {
    this.tokenUri=tokenUri; this.erpUrl=erpUrl; this.secret=secret;
  }
  public synchronized String token() throws Exception {
    if(tokenActual!=null && Instant.now().isBefore(vence)) return tokenActual;
    String basic=Base64.getEncoder().encodeToString(("aula-servicio:"+secret).getBytes(StandardCharsets.UTF_8));
    HttpRequest req=HttpRequest.newBuilder(URI.create(tokenUri)).timeout(Duration.ofSeconds(8)).header("Authorization","Basic "+basic)
      .header("Content-Type","application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=erp.read%20erp.sync%20erp.asistencia")).build();
    var res=http.send(req,HttpResponse.BodyHandlers.ofString());
    if(res.statusCode()!=200) throw new IllegalStateException("Token ERP: HTTP "+res.statusCode());
    var respuesta=json.readTree(res.body());
    tokenActual=respuesta.get("access_token").asText();
    vence=Instant.now().plusSeconds(Math.max(10,respuesta.path("expires_in").asLong(300)-30));
    return tokenActual;
  }
  public JsonNode get(String path) throws Exception {
    HttpRequest req=HttpRequest.newBuilder(URI.create(erpUrl+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token()).GET().build();
    var res=http.send(req,HttpResponse.BodyHandlers.ofString());
    if(res.statusCode()!=200) throw new IllegalStateException("ERP: HTTP "+res.statusCode());
    return json.readTree(res.body());
  }
  public byte[] asistencia(String metodo,String ruta,byte[] cuerpo,String tipo,String sub,String rol,String persona) throws Exception {
    try {
      HttpRequest.Builder pedido=HttpRequest.newBuilder(URI.create(erpUrl+"/api/erp/v1/integracion/asistencia"+ruta))
        .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+token())
        .header("X-Ast-Sub",sub).header("X-Ast-Rol",rol).header("X-Ast-Persona",persona);
      if(cuerpo!=null) pedido.header("Content-Type",tipo);
      pedido.method(metodo,cuerpo==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(cuerpo));
      var res=http.send(pedido.build(),HttpResponse.BodyHandlers.ofByteArray());
      if(res.statusCode()<200||res.statusCode()>=300) {
        String mensaje="ERP: HTTP "+res.statusCode();
        try { mensaje=json.readTree(res.body()).path("mensaje").asText(mensaje); } catch(Exception ignored) { }
        throw new ResponseStatusException(HttpStatus.valueOf(res.statusCode()),mensaje);
      }
      return res.body();
    } catch(IOException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"ERP o Identidad temporalmente inaccesible. Reintenta la operación.",e);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Comunicación con ERP interrumpida. Reintenta la operación.",e);
    }
  }
  public JsonNode avisos(String metodo,String ruta) throws Exception {
    byte[] respuesta=asistencia(metodo,ruta,null,null,"aula-servicio","SERVICIO","00000000-0000-0000-0000-000000000000");
    return respuesta.length==0?json.createObjectNode():json.readTree(respuesta);
  }
}
