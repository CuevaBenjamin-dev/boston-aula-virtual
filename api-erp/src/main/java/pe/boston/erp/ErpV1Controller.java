package pe.boston.erp;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/erp/v1")
public class ErpV1Controller {
  private final ErpV1Service servicio;
  private final DirectorioIdentidad identidad;
  public ErpV1Controller(ErpV1Service servicio,DirectorioIdentidad identidad) { this.servicio=servicio;this.identidad=identidad; }
  public record Id(UUID id) {}
  public record Confirmacion(boolean confirmacionExplicita) {}
  public record PagoExterno(String medio,java.math.BigDecimal monto) {}
  public record EstadoSeccion(boolean habilitada,Integer capacidad) {}
  public record AsignaturaDato(String codigo,String nombre,boolean activa) {}
  public record RelacionDato(int orden,boolean activa) {}
  public record AsignacionDato(UUID ofertaId,UUID docenteId) {}
  public record AsignacionesLoteDato(UUID docenteId,List<UUID> ofertasIds) {}
  public record CuentaDocenteDato(String usuario,String claveInicial) {}
  public record NombreDato(String nombre) {}
  public record Habilitacion(boolean habilitado) {}
  public record GradoDato(UUID nivelId,String nombre) {}
  public record OfertaDato(UUID seccionId,UUID asignaturaId) {}
  public record OfertaGradoDato(UUID periodoId,UUID gradoId,UUID asignaturaId) {}
  public record DocenteEdicion(ErpV1Service.DocenteDato docente,String estado) {}

  @GetMapping("/resumen") public List<Map<String,Object>> resumen(@AuthenticationPrincipal Jwt actor) { return servicio.resumen(actor); }
  @GetMapping("/catalogos") public Map<String,Object> catalogos(@AuthenticationPrincipal Jwt actor) { return servicio.catalogos(actor); }

  @GetMapping("/estudiantes") public List<Map<String,Object>> estudiantes(@RequestParam(defaultValue="") String buscar,@AuthenticationPrincipal Jwt actor) { return servicio.estudiantes(buscar,actor); }
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

  @GetMapping("/matriculas") public List<Map<String,Object>> matriculas(@RequestParam(required=false) UUID periodo,@RequestParam(required=false) String estado,@AuthenticationPrincipal Jwt actor) { return servicio.matriculas(periodo,estado,actor); }
  @GetMapping("/matriculas/{id}") public Map<String,Object> matricula(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.matricula(id,actor); }
  @PostMapping("/matriculas") @ResponseStatus(HttpStatus.CREATED) public Id iniciar(@RequestBody ErpV1Service.MatriculaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.iniciarMatricula(d,actor)); }
  @PostMapping("/matriculas/nueva") @ResponseStatus(HttpStatus.CREATED) public Id iniciarNuevo(@RequestBody ErpV1Service.NuevaMatriculaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.iniciarNuevo(d,actor)); }
  @PutMapping("/matriculas/{id}") public void editar(@PathVariable UUID id,@RequestBody ErpV1Service.CambioMatricula d,@AuthenticationPrincipal Jwt actor) { servicio.editarMatricula(id,d,actor); }
  @PostMapping("/matriculas/{id}/reservar") public void reservar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.reservar(id,actor); }
  @PostMapping("/matriculas/{id}/cancelar") public void cancelar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.cancelar(id,actor); }
  @PostMapping("/matriculas/{id}/confirmar") public void confirmar(@PathVariable UUID id,@RequestBody Confirmacion d,@AuthenticationPrincipal Jwt actor) { servicio.confirmar(id,d.confirmacionExplicita(),actor); }
  @PostMapping("/matriculas/{id}/pago-externo") public void pagoExterno(@PathVariable UUID id,@RequestBody PagoExterno d,@AuthenticationPrincipal Jwt actor) { servicio.registrarPagoExterno(id,d.medio(),d.monto(),actor); }

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

  @GetMapping("/asignaturas/grado/{grado}") public List<Map<String,Object>> asignaturas(@PathVariable UUID grado,@RequestParam(required=false) UUID periodo,@AuthenticationPrincipal Jwt actor) { return servicio.asignaturasGrado(grado,periodo,actor); }
  @PostMapping("/asignaturas") @ResponseStatus(HttpStatus.CREATED) public Id asignatura(@RequestBody AsignaturaDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearAsignatura(d.codigo(),d.nombre(),actor)); }
  @PutMapping("/asignaturas/{id}") public void asignaturaEditar(@PathVariable UUID id,@RequestBody AsignaturaDato d,@AuthenticationPrincipal Jwt actor) { servicio.editarAsignatura(id,d.codigo(),d.nombre(),d.activa(),actor); }
  @DeleteMapping("/asignaturas/{id}") public void asignaturaEliminar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.eliminarAsignatura(id,actor); }
  @PostMapping("/asignaturas/grados") @ResponseStatus(HttpStatus.CREATED) public Id asignaturaGrado(@RequestBody ErpV1Service.AsignaturaGradoDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.vincularAsignatura(d,actor)); }
  @PutMapping("/asignaturas/grados/{id}") public void relacion(@PathVariable UUID id,@RequestBody RelacionDato d,@AuthenticationPrincipal Jwt actor) { servicio.editarRelacion(id,d.orden(),d.activa(),actor); }

  @GetMapping("/docentes") public List<Map<String,Object>> docentes(@RequestParam(defaultValue="") String buscar,@AuthenticationPrincipal Jwt actor) { return servicio.docentes(buscar,actor); }
  @GetMapping("/docentes/cuentas-identidad") public List<Map<String,Object>> cuentasDocentes(@AuthenticationPrincipal Jwt actor) { return identidad.docentes(actor); }
  @PostMapping("/docentes") @ResponseStatus(HttpStatus.CREATED) public Id docente(@RequestBody ErpV1Service.DocenteDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearDocente(d,actor)); }
  @PostMapping("/docentes/{id}/cuenta") @ResponseStatus(HttpStatus.CREATED) public Id cuentaDocente(@PathVariable UUID id,@RequestBody CuentaDocenteDato d,@AuthenticationPrincipal Jwt actor) { return new Id(servicio.crearCuentaDocente(id,d.usuario(),d.claveInicial(),actor)); }
  @PutMapping("/docentes/{id}") public void docenteEditar(@PathVariable UUID id,@RequestBody DocenteEdicion d,@AuthenticationPrincipal Jwt actor) { servicio.editarDocente(id,d.docente(),d.estado(),actor); }
  @GetMapping("/docentes/{id}/asignaciones") public List<Map<String,Object>> asignaciones(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { return servicio.asignaciones(id,actor); }
  @PostMapping("/docentes/asignaciones") public void asignar(@RequestBody AsignacionDato d,@AuthenticationPrincipal Jwt actor) { servicio.asignarDocente(d.ofertaId(),d.docenteId(),actor); }
  @PostMapping("/docentes/asignaciones/lote") public void asignarLote(@RequestBody AsignacionesLoteDato d,@AuthenticationPrincipal Jwt actor) { servicio.asignarDocenteLote(d.docenteId(),d.ofertasIds(),actor); }
  @PostMapping("/docentes/asignaciones/{id}/retirar") public void retirar(@PathVariable UUID id,@AuthenticationPrincipal Jwt actor) { servicio.retirarDocente(id,actor); }
}
