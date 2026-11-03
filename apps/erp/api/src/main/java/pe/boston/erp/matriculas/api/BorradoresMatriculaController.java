package pe.boston.erp.matriculas.api;

import pe.boston.erp.matriculas.BorradoresMatricula;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/erp/v1/matriculas/borradores")
public class BorradoresMatriculaController {
  private final BorradoresMatricula servicio;
  public BorradoresMatriculaController(BorradoresMatricula servicio) { this.servicio=servicio; }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Map<String,UUID> crear(@RequestBody BorradoresMatricula.Guardado datos,@AuthenticationPrincipal Jwt actor) {
    return Map.of("id",servicio.crear(datos,actor));
  }
  @GetMapping("/{id}")
  public Map<String,Object> ver(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.ver(id,actor); }
  @PutMapping("/{id}")
  public void guardar(@PathVariable UUID id,@RequestBody BorradoresMatricula.Guardado datos,@AuthenticationPrincipal Jwt actor) {
    servicio.guardar(id,datos,actor);
  }
  @PostMapping("/{id}/finalizar")
  public Map<String,UUID> finalizar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) {
    return Map.of("id",servicio.finalizar(id,actor));
  }
}
