package pe.boston.erp;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.personas.PersonasFacade;

@RestController @RequestMapping("/api/erp/v1")
public class PersonasController {
  private final ErpV1Service servicio;
  private final PersonasFacade personas;
  public PersonasController(ErpV1Service servicio, PersonasFacade personas) { this.servicio=servicio; this.personas=personas; }
  public record Id(UUID id) {}

  @GetMapping("/estudiantes") public List<Map<String,Object>> estudiantes(@RequestParam(defaultValue="") String buscar,@AuthenticationPrincipal Jwt actor) { return personas.estudiantes(buscar,actor); }
  @GetMapping("/estudiantes/candidatos") public List<Map<String,Object>> candidatosMatricula(@RequestParam(defaultValue="") String buscar,@AuthenticationPrincipal Jwt actor) { return servicio.candidatosMatricula(buscar,actor); }
  @GetMapping("/estudiantes/verificar") public Map<String,Object> verificarEstudiante(@RequestParam String tipoDocumento,@RequestParam String documento,@AuthenticationPrincipal Jwt actor) { return servicio.verificarEstudiante(tipoDocumento,documento,actor); }
  @GetMapping("/estudiantes/{id}/verificar") public Map<String,Object> verificarEstudiantePorId(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.verificarEstudiante(id,actor); }
  @GetMapping("/estudiantes/{id}") public Map<String,Object> estudiante(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.estudiante(id,actor); }
  @PostMapping("/estudiantes") @ResponseStatus(HttpStatus.CREATED) public Id crearEstudiante(@RequestBody ErpV1Service.EstudianteDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearEstudiante(d,actor)); }
  @PutMapping("/estudiantes/{id}") public void editarEstudiante(@PathVariable UUID id,@RequestBody ErpV1Service.EstudianteDato d,@AuthenticationPrincipal Jwt actor) { servicio.editarEstudiante(id,d,actor); }
  @GetMapping("/personas") public List<Map<String,Object>> personas(@RequestParam String tipo,@RequestParam String documento,@AuthenticationPrincipal Jwt actor) { return servicio.personas(tipo,documento,actor); }
  @PostMapping("/personas") @ResponseStatus(HttpStatus.CREATED) public Id crearPersona(@RequestBody ErpV1Service.PersonaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearPersona(d,actor)); }
  @GetMapping("/estudiantes/{id}/responsables") public List<Map<String,Object>> apoderados(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.apoderados(id,actor); }
  @PostMapping("/estudiantes/{id}/responsables") public void vincular(@PathVariable UUID id,@RequestBody ErpV1Service.VinculoDato d,@AuthenticationPrincipal Jwt actor) { servicio.vincular(id,d,actor); }
}
