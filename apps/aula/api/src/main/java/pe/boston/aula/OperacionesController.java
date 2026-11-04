package pe.boston.aula;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula") public class OperacionesController {
  private final Clases clases; private final UsuarioActual usuario; private final JdbcTemplate db; private final ErpCliente erp; private final Sincronizacion sync; private final SupervisionController supervision; private final CuentasRemotasController identidad;
  public OperacionesController(Clases clases,UsuarioActual usuario,JdbcTemplate db,ErpCliente erp,Sincronizacion sync,SupervisionController supervision,CuentasRemotasController identidad) { this.clases=clases;this.usuario=usuario;this.db=db;this.erp=erp;this.sync=sync;this.supervision=supervision;this.identidad=identidad; }
  public record NuevaClase(String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,UUID operacionId,Boolean confirmarConflicto) {}
  public record LoteClases(List<UUID> cursoIds,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,UUID operacionId,Boolean confirmarConflicto) {}
  public record ProgramacionesClases(List<Clases.Programacion> programaciones,UUID operacionId,Boolean claseGrupal,Boolean confirmarConflicto) {}
  public record Enlace(String enlace,UUID operacionId) {}
  public record OperacionClase(UUID operacionId) {}
  public record Modulo(boolean habilitado) {}
  @GetMapping("/modulos") public Map<String,Boolean> modulos() { return Map.of("clasesYGrabaciones",clases.habilitado()); }
  @GetMapping("/sincronizacion/estado") public Map<String,Object> estadoSincronizacion(Authentication a) {
    usuario.oidc(a);
    var estado=db.queryForMap("SELECT ultimo_exito,ultimo_error FROM estado_sincronizacion WHERE id=1");
    var respuesta=new java.util.LinkedHashMap<String,Object>();
    respuesta.put("sincronizadoEn",estado.get("ultimo_exito"));
    respuesta.put("pendiente",estado.get("ultimo_error")!=null);
    return respuesta;
  }
  @PutMapping("/modulos/clases-y-grabaciones") public void modulo(@RequestBody Modulo datos,Authentication a) {
    usuario.administrador(a); db.update("UPDATE configuraciones_modulos SET habilitado=?,actualizado_en=now(),actualizado_por=? WHERE clave='clases_y_grabaciones'",datos.habilitado(),usuario.cuenta(a));
    db.update("INSERT INTO registros_auditoria(id,actor,accion,detalle) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"MODULO_CAMBIADO",String.valueOf(datos.habilitado()));
  }
  @GetMapping("/cursos/{curso}/clases") public List<Map<String,Object>> clases(@PathVariable UUID curso,Authentication a) { return clases.listar(curso,a); }
  @GetMapping("/clases") public List<Map<String,Object>> todasClases(Authentication a) { return clases.listarTodas(a); }
  @PostMapping("/cursos/{curso}/clases") public Map<String,UUID> clase(@PathVariable UUID curso,@RequestBody NuevaClase datos,Authentication a) { return Map.of("id",clases.crear(curso,datos.tema(),datos.inicio(),datos.fin(),datos.enlace(),datos.operacionId(),Boolean.TRUE.equals(datos.confirmarConflicto()),a)); }
  @PostMapping("/clases/lotes") public List<Map<String,Object>> clasesLote(@RequestBody LoteClases datos,Authentication a) { return clases.crearLote(datos.cursoIds(),datos.tema(),datos.inicio(),datos.fin(),datos.enlace(),datos.operacionId(),Boolean.TRUE.equals(datos.confirmarConflicto()),a); }
  @PostMapping("/clases/programaciones") public List<Map<String,Object>> clasesProgramadas(@RequestBody ProgramacionesClases datos,Authentication a) { return clases.crearProgramaciones(datos.programaciones(),datos.operacionId(),Boolean.TRUE.equals(datos.claseGrupal()),Boolean.TRUE.equals(datos.confirmarConflicto()),a); }
  @GetMapping("/clases/{clase}") public Map<String,Object> claseDetalle(@PathVariable UUID clase,Authentication a) { return clases.detalle(clase,a); }
  @PutMapping("/clases/{clase}") public void editarClase(@PathVariable UUID clase,@RequestBody NuevaClase datos,Authentication a) { clases.editar(clase,datos.tema(),datos.inicio(),datos.fin(),datos.enlace(),datos.operacionId(),Boolean.TRUE.equals(datos.confirmarConflicto()),a); }
  @PostMapping("/clases/{clase}/cancelar") public void cancelarClase(@PathVariable UUID clase,@RequestBody OperacionClase datos,Authentication a) { clases.cancelar(clase,datos.operacionId(),a); }
  @PostMapping("/clases/{clase}/finalizar") public void finalizarClase(@PathVariable UUID clase,@RequestBody OperacionClase datos,Authentication a) { clases.finalizar(clase,datos.operacionId(),a); }
  @PostMapping("/clases/{clase}/enlaces") public void enlace(@PathVariable UUID clase,@RequestBody Enlace datos,Authentication a) { clases.actualizar(clase,datos.enlace(),datos.operacionId(),a); }
  @GetMapping("/clases/{clase}/enlaces") public List<Map<String,Object>> enlaces(@PathVariable UUID clase,Authentication a) { return clases.historial(clase,a); }
  @GetMapping("/clases/{clase}/historial") public List<Map<String,Object>> historialClase(@PathVariable UUID clase,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception {
    var historial=clases.historialClase(clase,a);
    var docentes=new HashMap<String,String>();
    for(var fila:db.queryForList("SELECT docente_erp_id,nombre_completo FROM docentes_curso WHERE nombre_completo IS NOT NULL")) docentes.put(fila.get("docente_erp_id").toString(),fila.get("nombre_completo").toString());
    var responsables=new HashMap<String,String>();
    for(var cuenta:new com.fasterxml.jackson.databind.ObjectMapper().readTree(identidad.directorio(a,client))) {
      if("DOCENTE".equals(cuenta.path("tipo").asText())) {
        var nombre=docentes.get(cuenta.path("referencia_erp").asText());
        if(nombre!=null) responsables.put(cuenta.path("id").asText(),nombre);
      }
    }
    for(var fila:historial) {
      var cuenta=fila.remove("registrado_por");
      fila.put("responsable",cuenta==null?"No identificado":responsables.getOrDefault(cuenta.toString(),"No identificado"));
    }
    return historial;
  }
  @GetMapping("/pagos") public Object pagos(Authentication a) {
    usuario.estudiante(a);
    try { return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(erp.get("/api/erp/v1/tesoreria/estudiantes/"+usuario.referencia(a)),Object.class); }
    catch(Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ERP temporalmente no disponible"); }
  }
  @GetMapping("/integraciones") public Map<String,Object> integraciones(Authentication a) { return supervision.sistema(a); }
  @PostMapping("/integraciones/reintentar") public void reintentar(Authentication a) { usuario.developerRequerido(a); sync.ejecutar(); }
}
