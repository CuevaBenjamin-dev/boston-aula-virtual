package pe.boston.erp.plataforma;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class DirectorioIdentidad {
  private final URI url;
  private final URI urlDocentes;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper json=new ObjectMapper();
  public DirectorioIdentidad(@Value("${BOSTON_IDENTIDAD_URL:http://localhost:9000}") String base) {
    url=URI.create(base+"/api/identidad/directorio");
    urlDocentes=URI.create(base+"/api/identidad/docentes");
  }
  public UUID crearDocente(String usuario,String clave,UUID docente,Jwt actor) {
    AccesoAcademico.direccion(actor);
    try {
      String cuerpo=json.writeValueAsString(Map.of("usuario",usuario,"claveInicial",clave,"referenciaErp",docente));
      var solicitud=HttpRequest.newBuilder(urlDocentes).timeout(Duration.ofSeconds(10))
        .header("Authorization","Bearer "+actor.getTokenValue())
        .header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
      var respuesta=http.send(solicitud,HttpResponse.BodyHandlers.ofString());
      if(respuesta.statusCode()!=201) throw new IllegalArgumentException("No se pudo crear la cuenta docente. Verifica que el usuario sea único y la clave tenga al menos 12 caracteres.");
      Map<String,String> creado=json.readValue(respuesta.body(),new TypeReference<>(){});
      return UUID.fromString(creado.get("id"));
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Alta docente interrumpida",e);
    } catch(IllegalArgumentException e) { throw e; }
    catch(Exception e) { throw new IllegalStateException("No se pudo crear la cuenta docente",e); }
  }
  public List<Map<String,Object>> docentes(Jwt actor) {
    AccesoAcademico.lectura(actor);
    try {
      var solicitud=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5))
        .header("Authorization","Bearer "+actor.getTokenValue()).GET().build();
      var respuesta=http.send(solicitud,HttpResponse.BodyHandlers.ofString());
      if(respuesta.statusCode()!=200) throw new IllegalStateException("No se pudo consultar Boston Identidad");
      List<Map<String,Object>> cuentas=json.readValue(respuesta.body(),new TypeReference<>(){});
      return cuentas.stream().filter(c->"DOCENTE".equals(c.get("tipo"))).toList();
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Consulta de Identidad interrumpida",e);
    } catch(Exception e) { throw new IllegalStateException("No se pudo consultar Boston Identidad",e); }
  }
  public void verificarDocente(UUID cuenta,UUID docente,Jwt actor) {
    if(cuenta==null) return;
    boolean coincide=docentes(actor).stream().anyMatch(c->cuenta.toString().equals(String.valueOf(c.get("id")))
      && docente.toString().equals(String.valueOf(c.get("referencia_erp"))));
    if(!coincide) throw new IllegalArgumentException("La cuenta de Identidad no corresponde a este docente");
  }
}
