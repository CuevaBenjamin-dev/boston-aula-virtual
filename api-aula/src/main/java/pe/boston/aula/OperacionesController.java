package pe.boston.aula;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula") public class OperacionesController {
  private final Clases clases; private final UsuarioActual usuario; private final JdbcTemplate db; private final ErpCliente erp; private final Sincronizacion sync; private final SupervisionController supervision;
  public OperacionesController(Clases clases,UsuarioActual usuario,JdbcTemplate db,ErpCliente erp,Sincronizacion sync,SupervisionController supervision) { this.clases=clases;this.usuario=usuario;this.db=db;this.erp=erp;this.sync=sync;this.supervision=supervision; }
  public record NuevaClase(String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace) {}
  public record Enlace(String enlace) {}
  public record Modulo(boolean habilitado) {}
  @GetMapping("/modulos") public Map<String,Boolean> modulos() { return Map.of("clasesYGrabaciones",clases.habilitado()); }
  @PutMapping("/modulos/clases-y-grabaciones") public void modulo(@RequestBody Modulo datos,Authentication a) {
    usuario.administrador(a); db.update("UPDATE configuraciones_modulos SET habilitado=?,actualizado_en=now(),actualizado_por=? WHERE clave='clases_y_grabaciones'",datos.habilitado(),usuario.cuenta(a));
    db.update("INSERT INTO registros_auditoria(id,actor,accion,detalle) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"MODULO_CAMBIADO",String.valueOf(datos.habilitado()));
  }
  @GetMapping("/cursos/{curso}/clases") public List<Map<String,Object>> clases(@PathVariable UUID curso,Authentication a) { return clases.listar(curso,a); }
  @PostMapping("/cursos/{curso}/clases") public Map<String,UUID> clase(@PathVariable UUID curso,@RequestBody NuevaClase datos,Authentication a) { return Map.of("id",clases.crear(curso,datos.tema(),datos.inicio(),datos.fin(),datos.enlace(),a)); }
  @PostMapping("/clases/{clase}/enlaces") public void enlace(@PathVariable UUID clase,@RequestBody Enlace datos,Authentication a) { clases.actualizar(clase,datos.enlace(),a); }
  @GetMapping("/clases/{clase}/enlaces") public List<Map<String,Object>> enlaces(@PathVariable UUID clase,Authentication a) { return clases.historial(clase,a); }
  @GetMapping("/pagos") public Object pagos(Authentication a) {
    usuario.estudiante(a);
    try { return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(erp.get("/api/erp/tesoreria/estudiantes/"+usuario.referencia(a)),Object.class); }
    catch(Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ERP temporalmente no disponible"); }
  }
  @GetMapping("/integraciones") public Map<String,Object> integraciones(Authentication a) { return supervision.sistema(a); }
  @PostMapping("/integraciones/reintentar") public void reintentar(Authentication a) { usuario.developerRequerido(a); sync.ejecutar(); }
}
