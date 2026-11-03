package pe.boston.erp.asignaciones.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.ErpV1Service;
import pe.boston.erp.DirectorioIdentidad;
import pe.boston.erp.asignaciones.AsignacionesFacade;

@RestController @RequestMapping("/api/erp/v1")
public class AsignacionesController {
  private final ErpV1Service servicio;
  private final DirectorioIdentidad identidad;
  private final AsignacionesFacade asignaciones;
  public AsignacionesController(ErpV1Service servicio, DirectorioIdentidad identidad, AsignacionesFacade asignaciones) { this.servicio=servicio; this.identidad=identidad; this.asignaciones=asignaciones; }
  public record Id(UUID id) {}
  public record AsignacionDato(UUID ofertaId,UUID docenteId) {}
  public record AsignacionesLoteDato(UUID docenteId,List<UUID> ofertasIds) {}
  public record CuentaDocenteDato(String usuario,String claveInicial) {}
  public record DocenteEdicion(ErpV1Service.DocenteDato docente,String estado) {}

  @GetMapping("/docentes") public List<Map<String,Object>> docentes(@RequestParam(defaultValue="") String buscar,@AuthenticationPrincipal Jwt actor) { return servicio.docentes(buscar,actor); }
  @GetMapping("/docentes/cuentas-identidad") public List<Map<String,Object>> cuentasDocentes(@AuthenticationPrincipal Jwt actor) { return identidad.docentes(actor); }
  @PostMapping("/docentes") @ResponseStatus(HttpStatus.CREATED) public Id docente(@RequestBody ErpV1Service.DocenteDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearDocente(d,actor)); }
  @PostMapping("/docentes/{id}/cuenta") @ResponseStatus(HttpStatus.CREATED) public Id cuentaDocente(@PathVariable UUID id,@RequestBody CuentaDocenteDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearCuentaDocente(id,d.usuario(),d.claveInicial(),actor)); }
  @PutMapping("/docentes/{id}") public void docenteEditar(@PathVariable UUID id,@RequestBody DocenteEdicion d,@AuthenticationPrincipal Jwt actor) { servicio.editarDocente(id,d.docente(),d.estado(),actor); }
  @GetMapping("/docentes/{id}/asignaciones") public List<Map<String,Object>> asignaciones(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return asignaciones.asignaciones(id,actor); }
  @PostMapping("/docentes/asignaciones") public void asignar(@RequestBody AsignacionDato d,@AuthenticationPrincipal Jwt actor) { servicio.asignarDocente(d.ofertaId(),d.docenteId(),actor); }
  @PostMapping("/docentes/asignaciones/lote") public void asignarLote(@RequestBody AsignacionesLoteDato d,@AuthenticationPrincipal Jwt actor) { servicio.asignarDocenteLote(d.docenteId(),d.ofertasIds(),actor); }
  @PostMapping("/docentes/asignaciones/{id}/retirar") public void retirar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.retirarDocente(id,actor); }
}
