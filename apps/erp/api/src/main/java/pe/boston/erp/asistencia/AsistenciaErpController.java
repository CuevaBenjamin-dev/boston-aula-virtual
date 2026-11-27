package pe.boston.erp.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.boston.erp.plataforma.AccesoErp;

@RestController
@RequestMapping("/api/erp/v1/asistencia")
public class AsistenciaErpController {
  public record Publicar(int version,String clave,boolean confirmar,String motivo) {}
  public record Decision(boolean aprobar,String comentario,int version) {}
  public record NuevaJustificacion(String motivo,String clave) {}
  public record Complemento(String texto,int version) {}
  public record NuevaSalida(UUID personaId,String estado,String motivo,String clave) {}
  public record Hora(String valor) {}
  public record NuevoCalendario(
    Integer anio,
    LocalDate inicio,
    LocalDate fin,
    List<Integer> diasSemana
) {}
  private final AsistenciaService ast;
  public AsistenciaErpController(AsistenciaService ast) { this.ast=ast; }
  private AsistenciaService.Actor actor(Authentication a) {
    return new AsistenciaService.Actor(AccesoErp.actor(a),AccesoErp.coordinacion(a)?"COORDINACION":"DIRECCION",null);
  }
  @GetMapping("/calendario/{anio}") public ResponseEntity<?> calendario(@PathVariable int anio,Authentication a) {
    actor(a);
    var c=ast.calendario(anio);
    return c==null?ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null"):ResponseEntity.ok(c);
  }
  @PostMapping("/calendario")
public AsistenciaService.Calendario calendario(
    @RequestBody NuevoCalendario c,
    Authentication a) {

  if (c.anio() == null) {
    throw new IllegalArgumentException(
        "El año escolar es obligatorio");
  }

  if (c.diasSemana() != null &&
      c.diasSemana().stream().anyMatch(d -> d == null)) {
    throw new IllegalArgumentException(
        "Los días lectivos deben ser números del 1 al 7");
  }

  return ast.configurarCalendario(
      actor(a),
      new AsistenciaService.Calendario(
          c.anio(),
          c.inicio(),
          c.fin(),
          c.diasSemana(),
          null,
          null,
          0
      )
  );
}
  @GetMapping("/excepciones/{anio}") public List<Map<String,Object>> excepciones(@PathVariable int anio,Authentication a) {
    actor(a);return ast.excepciones(anio);
  }
  @PutMapping("/excepciones") public void excepcion(@RequestBody AsistenciaService.Excepcion e,Authentication a) {
    ast.excepcion(actor(a),e);
  }
  @PutMapping("/recordatorio/{anio}") public void hora(@PathVariable int anio,@RequestBody Hora h,Authentication a) {
    ast.configurarHora(actor(a),anio,h.valor());
  }
  @GetMapping("/control") public List<AsistenciaService.Resumen> control(@RequestParam LocalDate fecha,@RequestParam int anio,Authentication a) {
    actor(a);return ast.control(fecha,anio);
  }
  @GetMapping("/jornadas/{seccion}") public AsistenciaService.Jornada jornada(@PathVariable UUID seccion,@RequestParam LocalDate fecha,Authentication a) {
    actor(a);return ast.jornada(seccion,fecha,true);
  }
  @PutMapping("/jornadas/{seccion}/avance") public AsistenciaService.Jornada avance(@PathVariable UUID seccion,
      @RequestParam LocalDate fecha,@RequestBody AsistenciaService.Lote lote,Authentication a) {
    return ast.avance(actor(a),seccion,fecha,lote);
  }
  @PostMapping("/jornadas/{seccion}/publicar") public AsistenciaService.Jornada publicar(@PathVariable UUID seccion,
      @RequestParam LocalDate fecha,@RequestBody Publicar p,Authentication a) {
    return ast.publicar(actor(a),seccion,fecha,p.version(),p.clave(),p.confirmar(),p.motivo());
  }
  @GetMapping("/historial/{persona}") public List<AsistenciaService.Historial> historial(@PathVariable UUID persona,@RequestParam int anio,Authentication a) {
    actor(a);return ast.historial(persona,anio);
  }
  @GetMapping("/estadisticas/{persona}") public AsistenciaService.Estadisticas estadisticas(@PathVariable UUID persona,@RequestParam int anio,Authentication a) {
    actor(a);return ast.estadisticas(persona,anio);
  }
  @GetMapping("/pendientes/{anio}") public List<Map<String,Object>> pendientes(@PathVariable int anio,Authentication a) {
    actor(a);return ast.pendientes(anio);
  }
  @GetMapping("/justificaciones/conteo") public ResponseEntity<List<AsistenciaService.ConteoJustificaciones>> conteoJustificaciones(Authentication a) {
    if (!AccesoErp.coordinacion(a)) throw new org.springframework.security.access.AccessDeniedException("Solo Coordinación puede consultar su contador de trabajo");
    return ResponseEntity.ok().header("Cache-Control","no-store").body(ast.conteoJustificaciones());
  }
  @GetMapping("/justificaciones") public List<AsistenciaService.Justificacion> justificaciones(
      @RequestParam(required=false) Integer anio,@RequestParam(defaultValue="false") boolean pendientes,Authentication a) {
    actor(a);return ast.justificaciones(anio,pendientes);
  }
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PostMapping("/justificaciones/{id}/resolver") public void resolver(@PathVariable UUID id,@RequestBody Decision d,Authentication a) {
    ast.resolver(actor(a),id,d.aprobar(),d.comentario(),d.version());
  }
  @GetMapping("/justificaciones/{id}/adjuntos") public List<AsistenciaService.Adjunto> adjuntos(@PathVariable UUID id,Authentication a) {
    return ast.adjuntos(actor(a),id);
  }
  @GetMapping("/adjuntos/{id}") public ResponseEntity<byte[]> adjunto(@PathVariable UUID id,Authentication a) {
    return ResponseEntity.ok().header("Cache-Control","no-store").contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(ast.descargar(actor(a),id));
  }
  @GetMapping("/salidas") public List<AsistenciaService.Salida> salidas(@RequestParam(required=false) UUID personaId,Authentication a) {
    actor(a);return ast.salidas(personaId);
  }
  @PostMapping("/salidas") public AsistenciaService.Salida salida(@RequestBody NuevaSalida p,Authentication a) {
    return ast.salida(actor(a),p.personaId(),p.estado(),p.motivo(),p.clave());
  }
}
