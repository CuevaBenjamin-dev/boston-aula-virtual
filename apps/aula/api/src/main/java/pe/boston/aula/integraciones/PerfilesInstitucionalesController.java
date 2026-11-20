package pe.boston.aula.integraciones;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/aula/integracion/v1/perfiles")
public class PerfilesInstitucionalesController {
  public record Perfil(int version,UUID personaId,String tipo,String nombre) {}
  public record Resultado(UUID personaId,String estado) {}

  private final JdbcTemplate jdbc;
  public PerfilesInstitucionalesController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  @PutMapping("/{id}/{tipo}")
  public Resultado proyectar(@PathVariable UUID id,@PathVariable String tipo,@RequestBody Perfil perfil) {
    if (perfil==null || perfil.version()!=1 || !id.equals(perfil.personaId())
      || !tipo.equals(perfil.tipo()) || !("DOCENTE".equals(tipo) || "ESTUDIANTE".equals(tipo))
      || perfil.nombre()==null || perfil.nombre().isBlank() || perfil.nombre().length()>401)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Perfil institucional inválido");
    jdbc.update("""
      INSERT INTO perfiles_institucionales(persona_erp_id,tipo,nombre_completo) VALUES (?,?,?)
      ON CONFLICT(persona_erp_id,tipo) DO UPDATE SET
      nombre_completo=EXCLUDED.nombre_completo,actualizado_en=now()
      WHERE perfiles_institucionales.nombre_completo IS DISTINCT FROM EXCLUDED.nombre_completo
      """,id,tipo,perfil.nombre().trim());
    return new Resultado(id,"CONFIRMADA");
  }
}
