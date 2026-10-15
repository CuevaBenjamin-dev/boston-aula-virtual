package pe.boston.aula;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/supervision") public class SupervisionController {
  private final JdbcTemplate db;private final UsuarioActual usuario;private final AlmacenamientoObjetos objetos;
  private final String erp,identidad;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  public SupervisionController(JdbcTemplate db,UsuarioActual usuario,AlmacenamientoObjetos objetos,
      @Value("${BOSTON_ERP_URL}") String erp,@Value("${BOSTON_IDENTITY_URL:http://identidad:9000}") String identidad) {
    this.db=db;this.usuario=usuario;this.objetos=objetos;this.erp=erp;this.identidad=identidad;
  }
  private void coordinacion(Authentication a) { if(!"COORDINACION".equals(usuario.tipo(a))) throw new AccessDeniedException("Solo coordinación"); }
  public record Ambito(UUID seccionId,boolean asignado) {}
  @GetMapping("/ambitos/{cuenta}") public List<Map<String,Object>> ambitos(@PathVariable UUID cuenta,Authentication a) { usuario.administrador(a);return db.queryForList("SELECT seccion_erp_id,asignado_en FROM coordinaciones_seccion WHERE cuenta_id=? ORDER BY asignado_en",cuenta); }
  @PutMapping("/ambitos/{cuenta}") public void ambito(@PathVariable UUID cuenta,@RequestBody Ambito d,Authentication a) {
    usuario.administrador(a);if(d.seccionId()==null)throw new IllegalArgumentException("Sección requerida");
    if(d.asignado()) db.update("INSERT INTO coordinaciones_seccion(cuenta_id,seccion_erp_id) VALUES (?,?) ON CONFLICT DO NOTHING",cuenta,d.seccionId());
    else db.update("DELETE FROM coordinaciones_seccion WHERE cuenta_id=? AND seccion_erp_id=?",cuenta,d.seccionId());
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id,detalle) VALUES (?,?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"AMBITO_COORDINACION",cuenta,d.seccionId()+"="+d.asignado());
  }
  @GetMapping("/coordinacion/resumen") public List<Map<String,Object>> resumen(Authentication a) {
    coordinacion(a);
    return db.queryForList("""
      SELECT c.id,c.asignatura,c.grado,c.seccion,
      (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo) AS estudiantes,
      (SELECT count(*) FROM docentes_curso d WHERE d.curso_id=c.id AND d.activo) AS docentes,
      (SELECT count(*) FROM tareas t WHERE t.curso_id=c.id AND t.estado<>'BORRADOR') AS tareas,
      (SELECT count(*) FROM entregas e JOIN tareas t ON t.id=e.tarea_id WHERE t.curso_id=c.id) AS entregas,
      (SELECT count(*) FROM calificaciones_tarea n JOIN tareas t ON t.id=n.tarea_id WHERE t.curso_id=c.id) AS calificaciones
      FROM cursos c JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? ORDER BY c.grado,c.seccion,c.asignatura
      """,usuario.cuenta(a));
  }
  @GetMapping("/coordinacion/docentes") public List<Map<String,Object>> docentes(Authentication a) {
    coordinacion(a);
    return db.queryForList("""
      SELECT d.docente_erp_id, count(*) AS cursos, string_agg(c.asignatura || ' ' || c.seccion, ', ' ORDER BY c.asignatura) AS asignaciones
      FROM docentes_curso d JOIN cursos c ON c.id=d.curso_id JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND d.activo GROUP BY d.docente_erp_id ORDER BY asignaciones
      """,usuario.cuenta(a));
  }
  @GetMapping("/administracion/clases") public List<Map<String,Object>> clases(Authentication a,@RequestParam(defaultValue="0") int pagina) {
    usuario.administrador(a);return db.queryForList("SELECT x.id,x.tema,x.inicio,x.fin,c.asignatura,c.grado,c.seccion,(SELECT count(*) FROM bloques_zoom b WHERE b.clase_id=x.id) AS bloques FROM clases_academicas x JOIN cursos c ON c.id=x.curso_id ORDER BY x.inicio DESC LIMIT 50 OFFSET ?",Math.max(0,pagina)*50);
  }
  @GetMapping("/administracion/grabaciones") public List<Map<String,Object>> grabaciones(Authentication a,@RequestParam(defaultValue="0") int pagina) {
    usuario.administrador(a);return db.queryForList("SELECT g.id,g.nombre,g.tamano,g.estado,g.publicado_en,c.periodo,c.asignatura,c.seccion FROM grabaciones g JOIN cursos c ON c.id=g.curso_id ORDER BY g.creado_en DESC LIMIT 50 OFFSET ?",Math.max(0,pagina)*50);
  }
  @GetMapping("/seguridad/auditoria") public List<Map<String,Object>> auditoria(Authentication a,@RequestParam(defaultValue="0") int pagina) {
    usuario.developerRequerido(a);return db.queryForList("SELECT actor,accion,entidad_id,detalle,instante FROM registros_auditoria ORDER BY instante DESC LIMIT 50 OFFSET ?",Math.max(0,pagina)*50);
  }
  @GetMapping("/sistema") public Map<String,Object> sistema(Authentication a) {
    usuario.developerRequerido(a);
    return Map.of("erp",comprobar(erp+"/actuator/health"),"identidad",comprobar(identidad+"/actuator/health"),"almacenamiento",objetos.disponible(),
      "sincronizacion",db.queryForMap("SELECT ultima_secuencia,ultimo_exito,ultimo_error FROM estado_sincronizacion WHERE id=1"),
      "cursos",db.queryForObject("SELECT count(*) FROM cursos",Long.class),"grabaciones",db.queryForObject("SELECT count(*) FROM grabaciones WHERE estado='PUBLICADA'",Long.class));
  }
  private boolean comprobar(String url) {
    try { return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200; }
    catch(Exception e) { return false; }
  }
}
