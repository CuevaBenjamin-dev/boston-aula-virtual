package pe.boston.aula;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/aula/cuentas") public class CuentasRemotasController {
  private final UsuarioActual usuario; private final String identidad;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  public CuentasRemotasController(UsuarioActual usuario,@Value("${BOSTON_IDENTITY_URL:http://identidad:9000}") String identidad) { this.usuario=usuario;this.identidad=identidad; }
  private String llamar(String ruta,String metodo,String cuerpo,OAuth2AuthorizedClient client) throws Exception {
    HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(identidad+"/api/identidad"+ruta)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+client.getAccessToken().getTokenValue());
    if("GET".equals(metodo))b.GET();else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(cuerpo));
    var res=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    if(res.statusCode()>=400)throw new ResponseStatusException(HttpStatus.valueOf(res.statusCode()),"Identidad rechazó la operación");
    return res.body();
  }
  @GetMapping public String listar(Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception { usuario.administrador(a);return llamar("/cuentas","GET","",client); }
  @PostMapping public String crear(@RequestBody String datos,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception { usuario.administrador(a);return llamar("/cuentas","POST",datos,client); }
  @PutMapping("/{id}/estado") public void estado(@PathVariable UUID id,@RequestBody String datos,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception { usuario.administrador(a);llamar("/cuentas/"+id+"/estado","PUT",datos,client); }
  @PostMapping("/{id}/recuperacion") public String recuperacion(@PathVariable UUID id,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception { usuario.administrador(a);return llamar("/cuentas/"+id+"/recuperacion","POST","{}",client); }
  String directorio(Authentication a,OAuth2AuthorizedClient client) throws Exception { usuario.oidc(a);return llamar("/directorio","GET","",client); }
  @PostMapping("/mi-clave") public void cambiarMiClave(@RequestBody String datos,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception { usuario.oidc(a);llamar("/mi-clave","POST",datos,client); }
}
