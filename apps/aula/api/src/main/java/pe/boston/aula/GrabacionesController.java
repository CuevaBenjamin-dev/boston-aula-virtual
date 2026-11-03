package pe.boston.aula;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/grabaciones") public class GrabacionesController {
  private final GrabacionesYoutube youtube; private final Grabaciones historicas;
  public GrabacionesController(GrabacionesYoutube youtube,Grabaciones historicas) { this.youtube=youtube;this.historicas=historicas; }
  public record Registro(UUID claseId,int orden,String titulo,String url,boolean publicar,UUID operacionId) {}
  public record Enlace(String titulo,Integer orden,String url,UUID operacionId) {}
  public record Estado(boolean publicar,UUID operacionId) {}
  public record Operacion(UUID operacionId) {}
  public record Eliminacion(String periodo,int cantidadConfirmada) {}
  @GetMapping public List<Map<String,Object>> listar(Authentication a) { return youtube.listar(a); }
  @PostMapping public Map<String,UUID> registrar(@RequestBody Registro d,Authentication a) { return Map.of("id",youtube.registrar(d.claseId(),d.orden(),d.titulo(),d.url(),d.publicar(),d.operacionId(),a)); }
  @GetMapping("/clase/{clase}") public List<Map<String,Object>> clase(@PathVariable UUID clase,Authentication a) { return youtube.listarClase(clase,a); }
  @GetMapping("/administracion") public List<Map<String,Object>> administracion(Authentication a) { return youtube.administracion(a); }
  @GetMapping("/{id}/historial") public List<Map<String,Object>> historial(@PathVariable UUID id,Authentication a) { return youtube.historial(id,a); }
  @PutMapping("/{id}") public void editar(@PathVariable UUID id,@RequestBody Enlace d,Authentication a) { youtube.editar(id,d.titulo(),d.orden(),d.url(),d.operacionId(),a); }
  @PutMapping("/{id}/estado") public void estado(@PathVariable UUID id,@RequestBody Estado d,Authentication a) { youtube.estado(id,d.publicar(),d.operacionId(),a); }
  @PostMapping("/{id}/retirar") public void retirar(@PathVariable UUID id,@RequestBody Operacion d,Authentication a) { youtube.retirar(id,d.operacionId(),a); }
  // Solo inventario y borrado técnico de los MP4 históricos. La API ya no emite URLs ni permite nuevas cargas.
  @GetMapping("/eliminacion/vista-previa") public List<Map<String,Object>> vista(@RequestParam String periodo,Authentication a) { return historicas.vistaPrevia(periodo,a); }
  @PostMapping("/eliminacion") public Map<String,Object> eliminar(@RequestBody Eliminacion d,Authentication a) { return historicas.eliminarPeriodo(d.periodo(),d.cantidadConfirmada(),a); }
}
