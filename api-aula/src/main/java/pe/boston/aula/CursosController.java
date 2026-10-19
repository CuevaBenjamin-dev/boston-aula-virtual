package pe.boston.aula;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula") public class CursosController {
  private final CursosYTareas servicio;
  public CursosController(CursosYTareas servicio) { this.servicio=servicio; }
  public record Tarea(String titulo,String instrucciones,OffsetDateTime fechaLimite) {}
  public record Entrega(String respuesta) {}
  public record Nota(UUID estudianteId,BigDecimal valor,String comentario,Integer version) {}
  public record Edicion(String titulo,String instrucciones,OffsetDateTime fechaLimite,int version,UUID cursoId,Boolean confirmarReinicio,UUID operacionId,String tipoCambio) {}
  public record Lote(List<UUID> cursoIds,String titulo,String instrucciones,OffsetDateTime fechaLimite,UUID operacionId) {}
  @GetMapping("/cursos") public List<Map<String,Object>> cursos(Authentication a) { return servicio.cursos(a); }
  @GetMapping("/cursos/{curso}/tareas") public List<Map<String,Object>> tareas(@PathVariable UUID curso,Authentication a) { return servicio.tareas(curso,a); }
  @PostMapping("/cursos/{curso}/tareas") public Map<String,UUID> crear(@PathVariable UUID curso,@RequestBody Tarea datos,Authentication a) { return Map.of("id",servicio.crear(curso,datos.titulo(),datos.instrucciones(),datos.fechaLimite(),a)); }
  @PostMapping("/tareas/lotes") public Map<String,Object> crearLote(@RequestBody Lote d,Authentication a) { return servicio.crearLote(d.cursoIds(),d.titulo(),d.instrucciones(),d.fechaLimite(),d.operacionId(),a); }
  @GetMapping("/tareas/lotes/{lote}") public List<Map<String,Object>> lote(@PathVariable UUID lote,Authentication a) { return servicio.lote(lote,a); }
  @PostMapping("/tareas/lotes/{lote}/compartir-archivos") public List<Map<String,Object>> compartirArchivos(@PathVariable UUID lote,Authentication a) { return servicio.compartirArchivosLote(lote,a); }
  @PostMapping("/tareas/lotes/{lote}/publicar") public List<Map<String,Object>> publicarLote(@PathVariable UUID lote,Authentication a) { return servicio.publicarLote(lote,a); }
  @GetMapping("/tareas/{id}") public Map<String,Object> tarea(@PathVariable UUID id,Authentication a) { return servicio.tarea(id,a); }
  @PutMapping("/tareas/{id}") public Map<String,Object> editar(@PathVariable UUID id,@RequestBody Edicion d,Authentication a) { return servicio.editar(id,d.titulo(),d.instrucciones(),d.fechaLimite(),d.version(),d.cursoId(),Boolean.TRUE.equals(d.confirmarReinicio()),d.operacionId(),d.tipoCambio(),a); }
  @PostMapping("/tareas/{id}/publicar") public void publicar(@PathVariable UUID id,Authentication a) { servicio.estado(id,"PUBLICADA",a); }
  @PostMapping("/tareas/{id}/cerrar") public void cerrar(@PathVariable UUID id,Authentication a) { servicio.estado(id,"CERRADA",a); }
  @PostMapping("/tareas/{id}/cancelar") public void cancelar(@PathVariable UUID id,Authentication a) { servicio.estado(id,"CANCELADA",a); }
  @PostMapping("/tareas/{id}/entregas") public Map<String,UUID> entregar(@PathVariable UUID id,@RequestBody Entrega datos,Authentication a) { return Map.of("id",servicio.entregar(id,datos.respuesta(),a)); }
  @GetMapping("/tareas/{id}/entregas") public List<Map<String,Object>> entregas(@PathVariable UUID id,Authentication a) { return servicio.entregas(id,a); }
  @GetMapping("/tareas/{id}/seguimiento") public List<Map<String,Object>> seguimiento(@PathVariable UUID id,@RequestParam(defaultValue="TODOS") String grupo,Authentication a) { return servicio.seguimiento(id,grupo,a); }
  @PutMapping("/tareas/{id}/calificacion") public Map<String,Object> calificar(@PathVariable UUID id,@RequestBody Nota datos,Authentication a) { return servicio.calificar(id,datos.estudianteId(),datos.valor(),datos.comentario(),datos.version(),a); }
  @GetMapping("/cursos/{curso}/notas") public List<Map<String,Object>> notas(@PathVariable UUID curso,Authentication a) { return servicio.notas(curso,a); }
  @GetMapping("/notas/{id}") public Map<String,Object> nota(@PathVariable UUID id,Authentication a) { return servicio.nota(id,a); }
}
