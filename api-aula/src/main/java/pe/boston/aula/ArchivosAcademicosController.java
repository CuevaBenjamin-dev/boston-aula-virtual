package pe.boston.aula;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula") public class ArchivosAcademicosController {
  private final ArchivosAcademicos archivos;
  public ArchivosAcademicosController(ArchivosAcademicos archivos) { this.archivos=archivos; }
  public record Inicio(String nombre,String tipo,long tamano) {}
  public record Completar(List<Map<String,Object>> partes) {}
  @PostMapping("/tareas/{id}/archivos") public Map<String,Object> iniciarTarea(@PathVariable UUID id,@RequestBody Inicio d,Authentication a) { return archivos.iniciarTarea(id,d.nombre(),d.tipo(),d.tamano(),a); }
  @PostMapping("/entregas/{id}/archivos") public Map<String,Object> iniciarEntrega(@PathVariable UUID id,@RequestBody Inicio d,Authentication a) { return archivos.iniciarEntrega(id,d.nombre(),d.tipo(),d.tamano(),a); }
  @GetMapping("/tareas/{id}/archivos") public List<Map<String,Object>> tarea(@PathVariable UUID id,Authentication a) { return archivos.tarea(id,a); }
  @GetMapping("/entregas/{id}/archivos") public List<Map<String,Object>> entrega(@PathVariable UUID id,Authentication a) { return archivos.entrega(id,a); }
  @GetMapping("/archivos/{id}/partes/{numero}") public Map<String,URI> parte(@PathVariable UUID id,@PathVariable int numero,Authentication a) { return Map.of("url",archivos.parte(id,numero,a)); }
  @PostMapping("/archivos/{id}/completar") public void completar(@PathVariable UUID id,@RequestBody Completar d,Authentication a) { archivos.completar(id,d.partes(),a); }
  @GetMapping("/archivos/{id}/url") public Map<String,URI> enlace(@PathVariable UUID id,Authentication a) { return Map.of("url",archivos.enlace(id,a)); }
}
