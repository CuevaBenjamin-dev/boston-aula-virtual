package pe.boston.erp.matriculas.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.ErpV1Service;

@RestController @RequestMapping("/api/erp/v1")
public class MatriculasController {
  private final ErpV1Service servicio;
  public MatriculasController(ErpV1Service servicio) { this.servicio=servicio; }
  public record Id(UUID id) {}
  public record Confirmacion(boolean confirmacionExplicita) {}
  public record PagoExterno(String medio,java.math.BigDecimal monto) {}

  @GetMapping("/matriculas") public List<Map<String,Object>> matriculas(@RequestParam(required=false) UUID periodo,@RequestParam(required=false) String estado,@AuthenticationPrincipal Jwt actor) { return servicio.matriculas(periodo,estado,actor); }
  @GetMapping("/matriculas/{id}") public Map<String,Object> matricula(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.matricula(id,actor); }
  @PostMapping("/matriculas") @ResponseStatus(HttpStatus.CREATED) public Id iniciar(@RequestBody ErpV1Service.MatriculaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.iniciarMatricula(d,actor)); }
  @PostMapping("/matriculas/nueva") @ResponseStatus(HttpStatus.CREATED) public Id iniciarNuevo(@RequestBody ErpV1Service.NuevaMatriculaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.iniciarNuevo(d,actor)); }
  @PutMapping("/matriculas/{id}") public void editar(@PathVariable UUID id,@RequestBody ErpV1Service.CambioMatricula d,@AuthenticationPrincipal Jwt actor) { servicio.editarMatricula(id,d,actor); }
  @PostMapping("/matriculas/{id}/reservar") public void reservar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.reservar(id,actor); }
  @PostMapping("/matriculas/{id}/cancelar") public void cancelar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.cancelar(id,actor); }
  @PostMapping("/matriculas/{id}/confirmar") public void confirmar(@PathVariable UUID id,@RequestBody Confirmacion d,@AuthenticationPrincipal Jwt actor) { servicio.confirmar(id,d.confirmacionExplicita(),actor); }
  @PostMapping("/matriculas/{id}/pago-externo") public void pagoExterno(@PathVariable UUID id,@RequestBody PagoExterno d,@AuthenticationPrincipal Jwt actor) { servicio.registrarPagoExterno(id,d.medio(),d.monto(),actor); }
}
