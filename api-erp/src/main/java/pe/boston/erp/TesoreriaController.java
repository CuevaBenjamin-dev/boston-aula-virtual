package pe.boston.erp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/erp/tesoreria") public class TesoreriaController {
  private final Tesoreria servicio;
  public TesoreriaController(Tesoreria servicio) { this.servicio=servicio; }
  public record Pago(UUID estudianteId,UUID obligacionId,BigDecimal importe,String referencia) {}
  public record Anulacion(String motivo) {}
  public record Concepto(String nombre) {}
  public record Obligacion(UUID estudianteId,UUID conceptoId,String descripcion,BigDecimal importe,LocalDate vencimiento) {}
  @PostMapping("/conceptos") public Map<String,UUID> concepto(@RequestBody Concepto d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.concepto(d.nombre(),actor)); }
  @PostMapping("/obligaciones") public Map<String,UUID> obligacion(@RequestBody Obligacion d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.obligacion(d.estudianteId(),d.conceptoId(),d.descripcion(),d.importe(),d.vencimiento(),actor)); }
  @GetMapping("/estudiantes/{id}") public Map<String,Object> estado(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.estado(id,actor); }
  @PostMapping("/pagos") public Map<String,UUID> pago(@RequestBody Pago datos,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.registrar(datos.estudianteId(),datos.obligacionId(),datos.importe(),datos.referencia(),actor)); }
  @PostMapping("/pagos/{id}/anular") public void anular(@PathVariable UUID id,@RequestBody Anulacion datos,@AuthenticationPrincipal Jwt actor) { servicio.anular(id,datos.motivo(),actor); }
}
