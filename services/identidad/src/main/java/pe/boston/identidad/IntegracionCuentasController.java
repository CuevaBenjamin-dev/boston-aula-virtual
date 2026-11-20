package pe.boston.identidad;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Proyección de cuentas solicitada exclusivamente por el cliente técnico ERP. */
@RestController
@RequestMapping("/api/identidad/integracion/v1/cuentas")
public class IntegracionCuentasController {
  public record Cuenta(UUID personaId, String tipo, String usuario, boolean activa) {}
  public record Resultado(UUID cuentaId, String estado) {}
  private final JdbcTemplate jdbc;
  private final PasswordEncoder claves;
  public IntegracionCuentasController(JdbcTemplate jdbc, PasswordEncoder claves) {
    this.jdbc=jdbc; this.claves=claves;
  }

  @PutMapping("/{personaId}/{tipo}")
  @Transactional
  public Resultado proyectar(@PathVariable UUID personaId, @PathVariable String tipo, @RequestBody Cuenta entrada) {
    if (entrada==null || !personaId.equals(entrada.personaId()) || !tipo.equals(entrada.tipo())
        || !List.of("DOCENTE","ESTUDIANTE").contains(tipo))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cuenta institucional inválida");
    var existentes=jdbc.query("SELECT id,usuario,activo FROM cuentas WHERE referencia_erp=? AND tipo=? FOR UPDATE",
      (rs,n)->new Actual(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)),personaId,tipo);
    if (existentes.isEmpty() && !entrada.activa()) return new Resultado(null,"SIN_CUENTA");
    String usuario=entrada.usuario()==null?"":entrada.usuario().trim().toLowerCase();
    if (!usuario.matches("[a-z0-9._-]{4,120}"))
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Identificación de acceso no disponible");
    if (jdbc.queryForObject("SELECT count(*) FROM cuentas WHERE usuario=? AND (referencia_erp IS DISTINCT FROM ? OR tipo<>?)",
      Long.class,usuario,personaId,tipo)>0)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Identificador de acceso ya asignado a otra cuenta");
    if (existentes.isEmpty()) {
      UUID id=UUID.randomUUID();
      try {
        jdbc.update("INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,activo,cambio_clave_obligatorio) VALUES(?,?,?,?,?,TRUE,TRUE)",
          id,usuario,claves.encode(usuario),tipo,personaId);
      } catch (DuplicateKeyException error) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Cuenta o identificador en conflicto",error);
      }
      jdbc.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) VALUES(?,?,'CUENTA_INSTITUCIONAL_CREADA')",UUID.randomUUID(),id);
      return new Resultado(id,"CREADA");
    }
    Actual actual=existentes.getFirst();
    if (!actual.usuario().equals(usuario) || actual.activa()!=entrada.activa()) {
      jdbc.update("UPDATE cuentas SET usuario=?,activo=?,sesion_version=sesion_version+1 WHERE id=?",usuario,entrada.activa(),actual.id());
      jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name=?",actual.usuario());
      jdbc.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) VALUES(?,?,?)",UUID.randomUUID(),actual.id(),
        entrada.activa()?"CUENTA_INSTITUCIONAL_ACTUALIZADA":"CUENTA_INSTITUCIONAL_SUSPENDIDA");
    }
    return new Resultado(actual.id(),"REUTILIZADA");
  }
  private record Actual(UUID id,String usuario,boolean activa) {}
}
