package pe.boston.aula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
      .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=erp.read%20erp.sync")).build();
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
}
