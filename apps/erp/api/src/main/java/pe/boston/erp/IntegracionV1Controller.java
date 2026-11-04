package pe.boston.erp;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contratos V1 consumidos por Aula y por la administración institucional. */
@RestController
@RequestMapping("/api/erp/v1")
public class IntegracionV1Controller {
  private final Institucional servicio;
  public IntegracionV1Controller(Institucional servicio) { this.servicio = servicio; }

  public record Ambito(UUID seccionId, boolean asignado) {}

  @GetMapping("/docentes/resumen")
  public List<Map<String,Object>> docentesResumen(@AuthenticationPrincipal Jwt actor) {
    return servicio.docentesResumen(actor);
  }

  @GetMapping("/cursos/{curso}/estudiantes/resumen")
  public List<Map<String,Object>> estudiantesResumenCurso(@PathVariable UUID curso, @AuthenticationPrincipal Jwt actor) {
    return servicio.estudiantesResumenCurso(curso, actor);
  }

  @GetMapping("/perfiles/{tipo}/{id}")
  public Map<String,Object> perfil(@PathVariable String tipo, @PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
    return servicio.perfil(tipo, id, actor);
  }

  @GetMapping("/eventos")
  public List<Map<String,Object>> eventos(@RequestParam(defaultValue="0") long despues,
      @RequestParam(defaultValue="100") int limite) {
    return servicio.eventos(despues, limite);
  }

  @GetMapping("/coordinaciones/{cuenta}/ambitos")
  public List<Map<String,Object>> ambitos(@PathVariable UUID cuenta, @AuthenticationPrincipal Jwt actor) {
    return servicio.ambitos(cuenta, actor);
  }

  @PutMapping("/coordinaciones/{cuenta}/ambitos")
  public void cambiarAmbito(@PathVariable UUID cuenta, @RequestBody Ambito datos, @AuthenticationPrincipal Jwt actor) {
    servicio.cambiarAmbito(cuenta, datos.seccionId(), datos.asignado(), actor);
  }

  @PostMapping("/reconciliar")
  public void reconciliar(@AuthenticationPrincipal Jwt actor) {
    servicio.reconciliar(actor);
  }

  @GetMapping("/catalogo")
  public Map<String,Object> catalogo(@AuthenticationPrincipal Jwt actor) {
    return servicio.catalogo(actor);
  }

  @GetMapping("/ofertas")
  public List<Map<String,Object>> ofertas(@AuthenticationPrincipal Jwt actor) {
    Institucional.admin(actor);
    return servicio.ofertas();
  }
}
