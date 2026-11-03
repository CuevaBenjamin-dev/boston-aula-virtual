package pe.boston.erp.oferta.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.ErpV1Service;
import pe.boston.erp.oferta.OfertaFacade;

@RestController @RequestMapping("/api/erp/v1")
public class OfertaController {
  private final ErpV1Service servicio;
  private final OfertaFacade oferta;
  public OfertaController(ErpV1Service servicio, OfertaFacade oferta) { this.servicio=servicio; this.oferta=oferta; }
  public record Id(UUID id) {}
  public record EstadoSeccion(boolean habilitada,Integer capacidad) {}
  public record AsignaturaDato(String codigo,String nombre,boolean activa) {}
  public record RelacionDato(int orden,boolean activa) {}
  public record NombreDato(String nombre) {}
  public record Habilitacion(boolean habilitado) {}
  public record GradoDato(UUID nivelId,String nombre) {}
  public record OfertaDato(UUID seccionId,UUID asignaturaId) {}
  public record OfertaGradoDato(UUID periodoId,UUID gradoId,UUID asignaturaId) {}

  @PostMapping("/periodos") @ResponseStatus(HttpStatus.CREATED) public Id periodo(@RequestBody ErpV1Service.PeriodoDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearPeriodo(d,actor)); }
  @PutMapping("/periodos/{id}/estado") public void periodoEstado(@PathVariable UUID id,@RequestBody Habilitacion d,@AuthenticationPrincipal Jwt actor) { servicio.estadoPeriodo(id,d.habilitado(),actor); }
  @PostMapping("/niveles") @ResponseStatus(HttpStatus.CREATED) public Id nivel(@RequestBody NombreDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearNivel(d.nombre(),actor)); }
  @PutMapping("/niveles/{id}/estado") public void nivelEstado(@PathVariable UUID id,@RequestBody Habilitacion d,@AuthenticationPrincipal Jwt actor) { servicio.estadoNivel(id,d.habilitado(),actor); }
  @PostMapping("/grados") @ResponseStatus(HttpStatus.CREATED) public Id grado(@RequestBody GradoDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearGrado(d.nivelId(),d.nombre(),actor)); }
  @PutMapping("/grados/{id}/estado") public void gradoEstado(@PathVariable UUID id,@RequestBody Habilitacion d,@AuthenticationPrincipal Jwt actor) { servicio.estadoGrado(id,d.habilitado(),actor); }
  @PostMapping("/secciones") @ResponseStatus(HttpStatus.CREATED) public Id seccion(@RequestBody ErpV1Service.SeccionDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearSeccion(d,actor)); }
  @PutMapping("/secciones/{id}") public void seccionEstado(@PathVariable UUID id,@RequestBody EstadoSeccion d,@AuthenticationPrincipal Jwt actor) { servicio.estadoSeccion(id,d.habilitada(),d.capacidad(),actor); }
  @PostMapping("/ofertas") @ResponseStatus(HttpStatus.CREATED) public Id oferta(@RequestBody OfertaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearOferta(d.seccionId(),d.asignaturaId(),actor)); }
  @PostMapping("/ofertas/grado") @ResponseStatus(HttpStatus.CREATED) public Map<String,Integer> ofertaGrado(@RequestBody OfertaGradoDato d,@AuthenticationPrincipal Jwt actor) { return Map.of("secciones",servicio.crearOfertaGrado(d.gradoId(),d.periodoId(),d.asignaturaId(),actor)); }
  @GetMapping("/asignaturas/grado/{grado}") public List<Map<String,Object>> asignaturas(@PathVariable UUID grado,@RequestParam(required=false) UUID periodo,@AuthenticationPrincipal Jwt actor) { return oferta.asignaturasGrado(grado,periodo,actor); }
  @PostMapping("/asignaturas") @ResponseStatus(HttpStatus.CREATED) public Id asignatura(@RequestBody AsignaturaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearAsignatura(d.codigo(),d.nombre(),actor)); }
  @PutMapping("/asignaturas/{id}") public void asignaturaEditar(@PathVariable UUID id,@RequestBody AsignaturaDato d,@AuthenticationPrincipal Jwt actor) { servicio.editarAsignatura(id,d.codigo(),d.nombre(),d.activa(),actor); }
  @DeleteMapping("/asignaturas/{id}") public void asignaturaEliminar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.eliminarAsignatura(id,actor); }
  @PostMapping("/asignaturas/grados") @ResponseStatus(HttpStatus.CREATED) public Id asignaturaGrado(@RequestBody ErpV1Service.AsignaturaGradoDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.vincularAsignatura(d,actor)); }
  @PutMapping("/asignaturas/grados/{id}") public void relacion(@PathVariable UUID id,@RequestBody RelacionDato d,@AuthenticationPrincipal Jwt actor) { servicio.editarRelacion(id,d.orden(),d.activa(),actor); }
}
