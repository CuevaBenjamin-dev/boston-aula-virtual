package pe.boston.aula;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/grabaciones") public class GrabacionesController {
  private final Grabaciones servicio;
  public GrabacionesController(Grabaciones servicio) { this.servicio=servicio; }
  public record Inicio(UUID claseId,String nombre,int orden,long tamano) {}
  public record Completar(List<Map<String,Object>> partes) {}
  public record Eliminacion(String periodo,int cantidadConfirmada) {}
  @PostMapping public Map<String,Object> iniciar(@RequestBody Inicio datos,Authentication a) { return servicio.iniciar(datos.claseId(),datos.nombre(),datos.orden(),datos.tamano(),a); }
  @GetMapping("/{id}/partes/{numero}") public Map<String,String> parte(@PathVariable UUID id,@PathVariable int numero,Authentication a) { return Map.of("url",servicio.parte(id,numero,a).toString()); }
  @PostMapping("/{id}/completar") public void completar(@PathVariable UUID id,@RequestBody Completar datos,Authentication a) { servicio.completar(id,datos.partes(),a); }
  @GetMapping("/clase/{clase}") public List<Map<String,Object>> listar(@PathVariable UUID clase,Authentication a) { return servicio.listar(clase,a); }
  @GetMapping("/{id}/url") public Map<String,String> url(@PathVariable UUID id,Authentication a) { return Map.of("url",servicio.enlace(id,a).toString()); }
  @GetMapping("/{id}/abrir") public ResponseEntity<Void> abrir(@PathVariable UUID id,Authentication a) { return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,servicio.enlace(id,a).toString()).build(); }
  @GetMapping("/{id}/descargar") public ResponseEntity<Void> descargar(@PathVariable UUID id,Authentication a) { return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,servicio.descarga(id,a).toString()).build(); }
  @GetMapping("/eliminacion/vista-previa") public List<Map<String,Object>> vista(@RequestParam String periodo,Authentication a) { return servicio.vistaPrevia(periodo,a); }
  @PostMapping("/eliminacion") public Map<String,Object> eliminar(@RequestBody Eliminacion datos,Authentication a) { return servicio.eliminarPeriodo(datos.periodo(),datos.cantidadConfirmada(),a); }
}
