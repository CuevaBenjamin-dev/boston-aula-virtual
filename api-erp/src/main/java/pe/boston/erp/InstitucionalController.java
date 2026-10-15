package pe.boston.erp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/erp") public class InstitucionalController {
  private final Institucional servicio;
  public InstitucionalController(Institucional servicio) { this.servicio=servicio; }
  public record Estudiante(@NotBlank String nombres,@NotBlank String apellidos,@NotBlank String codigo) {}
  public record Matricula(UUID estudianteId,UUID seccionId) {}
  public record CambioSeccion(UUID seccionId) {}
  public record Asignacion(UUID docenteId) {}
  public record Estado(@NotBlank String estado) {}
  public record Seccion(UUID gradoId,UUID periodoId,@NotBlank String nombre) {}
  public record Oferta(UUID seccionId,UUID asignaturaId) {}
  public record Docente(@NotBlank String nombres,@NotBlank String apellidos) {}
  public record Apoderado(@NotBlank String nombres,@NotBlank String apellidos,String documento) {}
  public record Vinculo(UUID apoderadoId,@NotBlank String vinculo) {}

  @PostMapping("/estudiantes") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> estudiante(@Valid @RequestBody Estudiante datos,@AuthenticationPrincipal Jwt actor) {
    return Map.of("id",servicio.nuevoEstudiante(datos.nombres(),datos.apellidos(),datos.codigo(),actor));
  }
  @PostMapping("/matriculas") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> matricula(@RequestBody Matricula datos,@AuthenticationPrincipal Jwt actor) {
    return Map.of("id",servicio.matricular(datos.estudianteId(),datos.seccionId(),actor));
  }
  @PutMapping("/estudiantes/{id}/seccion") public void seccion(@PathVariable UUID id,@RequestBody CambioSeccion datos,@AuthenticationPrincipal Jwt actor) { servicio.cambiarSeccion(id,datos.seccionId(),actor); }
  @PostMapping("/estudiantes/{id}/retiro") public void retiro(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.retirar(id,actor); }
  @PutMapping("/estudiantes/{id}/estado") public void estado(@PathVariable UUID id,@RequestBody Estado datos,@AuthenticationPrincipal Jwt actor) { servicio.estadoAcademico(id,datos.estado(),actor); }
  @PutMapping("/ofertas/{id}/docente") public void docente(@PathVariable UUID id,@RequestBody Asignacion datos,@AuthenticationPrincipal Jwt actor) { servicio.asignarDocente(id,datos.docenteId(),actor); }
  @GetMapping("/ofertas") public List<Map<String,Object>> ofertas(@AuthenticationPrincipal Jwt actor) { Institucional.admin(actor); return servicio.ofertas(); }
  @GetMapping("/catalogo") public Map<String,Object> catalogo(@AuthenticationPrincipal Jwt actor) { return servicio.catalogo(actor); }
  @PostMapping("/secciones") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> seccion(@Valid @RequestBody Seccion d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.nuevaSeccion(d.gradoId(),d.periodoId(),d.nombre(),actor)); }
  @PostMapping("/ofertas") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> oferta(@RequestBody Oferta d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.nuevaOferta(d.seccionId(),d.asignaturaId(),actor)); }
  @PostMapping("/docentes") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> docente(@Valid @RequestBody Docente d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.nuevoDocente(d.nombres(),d.apellidos(),actor)); }
  @PostMapping("/apoderados") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> apoderado(@Valid @RequestBody Apoderado d,@AuthenticationPrincipal Jwt actor) { return Map.of("id",servicio.nuevoApoderado(d.nombres(),d.apellidos(),d.documento(),actor)); }
  @PostMapping("/estudiantes/{id}/apoderados") @ResponseStatus(HttpStatus.CREATED) public void vincular(@PathVariable UUID id,@Valid @RequestBody Vinculo d,@AuthenticationPrincipal Jwt actor) { servicio.vincularApoderado(id,d.apoderadoId(),d.vinculo(),actor); }
  @GetMapping("/estudiantes/{id}/apoderados") public List<Map<String,Object>> apoderados(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.apoderados(id,actor); }
  @GetMapping("/eventos") public List<Map<String,Object>> eventos(@RequestParam(defaultValue="0") long despues,@RequestParam(defaultValue="100") int limite) { return servicio.eventos(despues,limite); }
}
