package pe.boston.erp;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Consultas de tablero y catálogos legadas, pendientes de separar por caso de uso. */
@RestController @RequestMapping("/api/erp/v1")
public class ErpV1Controller {
  private final ErpV1Service servicio;
  public ErpV1Controller(ErpV1Service servicio) { this.servicio=servicio; }
  @GetMapping("/resumen") public List<Map<String,Object>> resumen(@AuthenticationPrincipal Jwt actor) { return servicio.resumen(actor); }
  @GetMapping("/catalogos") public Map<String,Object> catalogos(@AuthenticationPrincipal Jwt actor) { return servicio.catalogos(actor); }
}
