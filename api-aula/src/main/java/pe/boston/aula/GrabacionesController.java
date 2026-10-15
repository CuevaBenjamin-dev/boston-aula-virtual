package pe.boston.aula;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/grabaciones") public class GrabacionesController {
  private final GrabacionesYoutube youtube; private final Grabaciones historicas;
  public GrabacionesController(GrabacionesYoutube youtube,Grabaciones historicas) { this.youtube=youtube;this.historicas=historicas; }
  public record Registro(UUID claseId,int orden,String url,boolean publicar) {}
  public record Enlace(String url) {}
  public record Estado(boolean publicar) {}
  public record Eliminacion(String periodo,int cantidadConfirmada) {}
  @GetMapping public List<Map<String,Object>> listar(Authentication a) { return youtube.listar(a); }
  @PostMapping public Map<String,UUID> registrar(@RequestBody Registro d,Authentication a) { return Map.of("id",youtube.registrar(d.claseId(),d.orden(),d.url(),d.publicar(),a)); }
  @GetMapping("/clase/{clase}") public List<Map<String,Object>> clase(@PathVariable UUID clase,Authentication a) { return youtube.listarClase(clase,a); }
  @GetMapping("/administracion") public List<Map<String,Object>> administracion(Authentication a) { return youtube.administracion(a); }
  @PutMapping("/{id}") public void editar(@PathVariable UUID id,@RequestBody Enlace d,Authentication a) { youtube.editar(id,d.url(),a); }
  @PutMapping("/{id}/estado") public void estado(@PathVariable UUID id,@RequestBody Estado d,Authentication a) { youtube.estado(id,d.publicar(),a); }
  // Solo inventario y borrado técnico de los MP4 históricos. La API ya no emite URLs ni permite nuevas cargas.
  @GetMapping("/eliminacion/vista-previa") public List<Map<String,Object>> vista(@RequestParam String periodo,Authentication a) { return historicas.vistaPrevia(periodo,a); }
  @PostMapping("/eliminacion") public Map<String,Object> eliminar(@RequestBody Eliminacion d,Authentication a) { return historicas.eliminarPeriodo(d.periodo(),d.cantidadConfirmada(),a); }
}
