package pe.boston.erp.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Ruta de servicio separada de sesiones ERP; el BFF Aula aporta actor de su sesión verificada. */
@RestController
@RequestMapping("/api/erp/v1/integracion/asistencia")
public class AsistenciaInternaController {
  private final AsistenciaService ast;
  public AsistenciaInternaController(AsistenciaService ast) { this.ast=ast; }
  private AsistenciaService.Actor actor(String sub,String rol,String persona) {
    AsistenciaService.requerido(sub,"Actor");
    if(!List.of("DOCENTE","ESTUDIANTE").contains(rol)) throw new SecurityException("Rol no permitido en Aula");
    UUID id;
    try { id=UUID.fromString(persona); } catch(Exception e) { throw new SecurityException("Identidad ERP inválida"); }
    return new AsistenciaService.Actor(sub,rol,id);
  }
  @GetMapping("/secciones") public List<AsistenciaService.Resumen> secciones(
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    ast.docenteActivo(actor(sub,rol,persona));
    return ast.control(ast.hoy(),ast.hoy().getYear());
  }
  @GetMapping("/jornadas/{seccion}") public AsistenciaService.Jornada jornada(@PathVariable UUID seccion,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    ast.docenteActivo(actor(sub,rol,persona));
    return ast.jornada(seccion,ast.hoy(),true);
  }
  @PutMapping("/jornadas/{seccion}/avance") public AsistenciaService.Jornada avance(@PathVariable UUID seccion,
      @RequestBody AsistenciaService.Lote lote,@RequestHeader("X-Ast-Sub") String sub,
      @RequestHeader("X-Ast-Rol") String rol,@RequestHeader("X-Ast-Persona") String persona) {
    return ast.avance(actor(sub,rol,persona),seccion,ast.hoy(),lote);
  }
  @PostMapping("/jornadas/{seccion}/publicar") public AsistenciaService.Jornada publicar(@PathVariable UUID seccion,
      @RequestBody AsistenciaErpController.Publicar p,@RequestHeader("X-Ast-Sub") String sub,
      @RequestHeader("X-Ast-Rol") String rol,@RequestHeader("X-Ast-Persona") String persona) {
    return ast.publicar(actor(sub,rol,persona),seccion,ast.hoy(),p.version(),p.clave(),p.confirmar(),null);
  }
  @GetMapping("/mi-asistencia") public List<AsistenciaService.Historial> miAsistencia(@RequestParam int anio,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    var a=actor(sub,rol,persona);AsistenciaService.rol(a,"ESTUDIANTE");
    return ast.historial(a.personaId(),anio).stream().filter(AsistenciaService.Historial::valido).toList();
  }
  @GetMapping("/mis-anios") public List<Integer> misAnios(@RequestHeader("X-Ast-Sub") String sub,
      @RequestHeader("X-Ast-Rol") String rol,@RequestHeader("X-Ast-Persona") String persona) {
    var a=actor(sub,rol,persona);AsistenciaService.rol(a,"ESTUDIANTE");
    return ast.anios(a.personaId());
  }
  @GetMapping("/mis-estadisticas") public AsistenciaService.Estadisticas estadisticas(@RequestParam int anio,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    var a=actor(sub,rol,persona);AsistenciaService.rol(a,"ESTUDIANTE");
    return ast.estadisticas(a.personaId(),anio);
  }
  @GetMapping("/mis-justificaciones") public List<AsistenciaService.Justificacion> misJustificaciones(
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    var a=actor(sub,rol,persona);AsistenciaService.rol(a,"ESTUDIANTE");
    return ast.misJustificaciones(a.personaId());
  }
  @PostMapping("/justificaciones/{registro}") public AsistenciaService.Justificacion solicitar(@PathVariable UUID registro,
      @RequestBody AsistenciaErpController.NuevaJustificacion p,@RequestHeader("X-Ast-Sub") String sub,
      @RequestHeader("X-Ast-Rol") String rol,@RequestHeader("X-Ast-Persona") String persona) {
    return ast.solicitar(actor(sub,rol,persona),registro,p.motivo(),p.clave());
  }
  @PostMapping("/justificaciones/{id}/complementar") public void complementar(@PathVariable UUID id,
      @RequestBody AsistenciaErpController.Complemento p,@RequestHeader("X-Ast-Sub") String sub,
      @RequestHeader("X-Ast-Rol") String rol,@RequestHeader("X-Ast-Persona") String persona) {
    ast.complementar(actor(sub,rol,persona),id,p.texto(),p.version());
  }
  @PostMapping(value="/justificaciones/{id}/adjuntos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public AsistenciaService.Adjunto adjuntar(@PathVariable UUID id,@RequestPart("archivo") org.springframework.web.multipart.MultipartFile archivo,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) throws java.io.IOException {
    return ast.adjuntar(actor(sub,rol,persona),id,archivo.getOriginalFilename(),archivo.getBytes());
  }
  @GetMapping("/justificaciones/{id}/adjuntos") public List<AsistenciaService.Adjunto> adjuntos(@PathVariable UUID id,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    return ast.adjuntos(actor(sub,rol,persona),id);
  }
  @GetMapping("/adjuntos/{id}") public ResponseEntity<byte[]> adjunto(@PathVariable UUID id,
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    return ResponseEntity.ok().header("Cache-Control","no-store").contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(ast.descargar(actor(sub,rol,persona),id));
  }
  @GetMapping("/mis-salidas") public List<AsistenciaService.Salida> misSalidas(
      @RequestHeader("X-Ast-Sub") String sub,@RequestHeader("X-Ast-Rol") String rol,
      @RequestHeader("X-Ast-Persona") String persona) {
    var a=actor(sub,rol,persona);AsistenciaService.rol(a,"ESTUDIANTE");
    return ast.salidas(a.personaId());
  }
  @GetMapping("/avisos") public List<Map<String,Object>> avisos() { return ast.avisosPendientes(); }
  @PostMapping("/avisos/{clave}/confirmar") public void confirmarAviso(@PathVariable String clave) { ast.confirmarAviso(clave); }
}
