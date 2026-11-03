package pe.boston.aula;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/supervision") public class SupervisionController {
  private final JdbcTemplate db;private final UsuarioActual usuario;private final AlmacenamientoObjetos objetos;private final ErpCliente erpCliente;private final Clases clasesServicio;private final CuentasRemotasController identidadServicio;
  private final String erp,identidad;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  public SupervisionController(JdbcTemplate db,UsuarioActual usuario,AlmacenamientoObjetos objetos,ErpCliente erpCliente,Clases clasesServicio,CuentasRemotasController identidadServicio,
      @Value("${BOSTON_ERP_URL}") String erp,@Value("${BOSTON_IDENTITY_URL:http://identidad:9000}") String identidad) {
    this.db=db;this.usuario=usuario;this.objetos=objetos;this.erpCliente=erpCliente;this.clasesServicio=clasesServicio;this.identidadServicio=identidadServicio;this.erp=erp;this.identidad=identidad;
  }
  private void coordinacion(Authentication a) { if(!"COORDINACION".equals(usuario.tipo(a))) throw new AccessDeniedException("Solo coordinación"); }
  public record Ambito(UUID seccionId,boolean asignado) {}
  @GetMapping("/ambitos/{cuenta}") public org.springframework.http.ResponseEntity<String> ambitos(@PathVariable UUID cuenta,Authentication a,
      @org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient("boston") org.springframework.security.oauth2.client.OAuth2AuthorizedClient client) throws Exception {
    usuario.administrador(a);
    var respuesta=http.send(HttpRequest.newBuilder(URI.create(erp+"/api/erp/coordinaciones/"+cuenta+"/ambitos"))
      .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+client.getAccessToken().getTokenValue()).GET().build(),HttpResponse.BodyHandlers.ofString());
    return org.springframework.http.ResponseEntity.status(respuesta.statusCode()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(respuesta.body());
  }
  @PutMapping("/ambitos/{cuenta}") public void ambito(@PathVariable UUID cuenta,@RequestBody Ambito d,Authentication a,
      @org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient("boston") org.springframework.security.oauth2.client.OAuth2AuthorizedClient client) throws Exception {
    usuario.administrador(a);if(d.seccionId()==null)throw new IllegalArgumentException("Sección requerida");
    String cuerpo=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(d);
    var respuesta=http.send(HttpRequest.newBuilder(URI.create(erp+"/api/erp/coordinaciones/"+cuenta+"/ambitos"))
      .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+client.getAccessToken().getTokenValue())
      .header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),HttpResponse.BodyHandlers.discarding());
    if(respuesta.statusCode()!=200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(respuesta.statusCode()),"ERP rechazó el ámbito");
  }
  @GetMapping("/coordinacion/resumen") public List<Map<String,Object>> resumen(Authentication a) {
    coordinacion(a);
    return db.queryForList("""
      SELECT c.id,c.asignatura,c.nivel,c.grado,c.seccion,c.periodo,c.seccion_erp_id,
      (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo) AS estudiantes,
      (SELECT count(*) FROM docentes_curso d WHERE d.curso_id=c.id AND d.activo) AS docentes,
      (SELECT count(*) FROM tareas t WHERE t.curso_id=c.id AND t.estado IN ('PUBLICADA','CERRADA')) AS tareas,
      (SELECT count(*) FROM entregas e JOIN tareas t ON t.id=e.tarea_id WHERE t.curso_id=c.id) AS entregas,
      (SELECT count(*) FROM calificaciones_tarea n JOIN tareas t ON t.id=n.tarea_id WHERE t.curso_id=c.id AND n.estado='PUBLICADA') AS calificaciones,
      (SELECT count(*) FROM tareas t WHERE t.curso_id=c.id AND t.estado='PUBLICADA' AND EXISTS
        (SELECT 1 FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo AND NOT EXISTS
          (SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=i.estudiante_erp_id))) AS actividades_pendientes,
      (SELECT count(*) FROM entregas e JOIN tareas t ON t.id=e.tarea_id
        JOIN inscripciones_curso i ON i.curso_id=c.id AND i.estudiante_erp_id=e.estudiante_erp_id AND i.activo
        WHERE t.curso_id=c.id AND NOT EXISTS (SELECT 1 FROM calificaciones_tarea n WHERE n.tarea_id=t.id
          AND n.estudiante_erp_id=e.estudiante_erp_id AND n.entrega_version=e.version AND n.estado='PUBLICADA')
          AND e.version=(SELECT max(otra.version) FROM entregas otra WHERE otra.tarea_id=t.id AND otra.estudiante_erp_id=e.estudiante_erp_id)) AS pendientes_calificacion
      FROM cursos c JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp ORDER BY c.grado,c.seccion,c.asignatura
      """,usuario.cuenta(a));
  }
  @GetMapping("/coordinacion/docentes") public List<Map<String,Object>> docentes(Authentication a,
      @org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient("boston") org.springframework.security.oauth2.client.OAuth2AuthorizedClient client) throws Exception {
    coordinacion(a);
    var filas=db.queryForList("""
      SELECT d.docente_erp_id,max(d.nombre_completo) AS docente,count(*) AS cursos,
        string_agg(c.asignatura || ' ' || c.seccion, ', ' ORDER BY c.asignatura) AS asignaciones,
        string_agg(c.id::text, ',') AS curso_ids
      FROM docentes_curso d JOIN cursos c ON c.id=d.curso_id JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND x.confirmado_erp AND d.activo GROUP BY d.docente_erp_id ORDER BY asignaciones
      """,usuario.cuenta(a));
    var cuentas=new com.fasterxml.jackson.databind.ObjectMapper().readTree(identidadServicio.directorio(a,client));
    Map<UUID,UUID> porDocente=new HashMap<>();
    for(var c:cuentas) if("DOCENTE".equals(c.path("tipo").asText()) && c.hasNonNull("referencia_erp"))
      porDocente.put(UUID.fromString(c.get("referencia_erp").asText()),UUID.fromString(c.get("id").asText()));
    for(var fila:filas) {
      UUID docente=(UUID)fila.get("docente_erp_id"),cuenta=porDocente.get(docente);
      fila.put("tareas_publicadas",cuenta==null?0:db.queryForObject("""
        SELECT count(*) FROM tareas t JOIN cursos c ON c.id=t.curso_id
        JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
        WHERE x.cuenta_id=? AND x.confirmado_erp AND t.creado_por=? AND t.estado IN ('PUBLICADA','CERRADA')
        """,Long.class,usuario.cuenta(a),cuenta));
    }
    return filas;
  }
  @GetMapping("/coordinacion/tareas") public List<Map<String,Object>> tareasCoordinacion(Authentication a) {
    coordinacion(a);
    return db.queryForList("""
      SELECT t.id,t.curso_id,t.titulo,t.estado,t.fecha_limite,t.publicado_en,
        c.asignatura,c.nivel,c.grado,c.seccion,c.periodo,
        (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo) AS estudiantes,
        (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo AND EXISTS
          (SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=i.estudiante_erp_id)) AS entregadas,
        (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo AND EXISTS
          (SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=i.estudiante_erp_id
            AND e.estado='TARDIA' AND e.version=(SELECT max(v.version) FROM entregas v WHERE v.tarea_id=t.id AND v.estudiante_erp_id=i.estudiante_erp_id))) AS tardias,
        (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND i.activo AND EXISTS
          (SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=i.estudiante_erp_id
            AND e.version=(SELECT max(v.version) FROM entregas v WHERE v.tarea_id=t.id AND v.estudiante_erp_id=i.estudiante_erp_id)
            AND NOT EXISTS (SELECT 1 FROM calificaciones_tarea n WHERE n.tarea_id=t.id AND n.estudiante_erp_id=i.estudiante_erp_id
              AND n.entrega_version=e.version AND n.estado='PUBLICADA'))) AS pendientes_calificacion
      FROM tareas t JOIN cursos c ON c.id=t.curso_id
      JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND x.confirmado_erp AND t.estado IN ('PUBLICADA','CERRADA')
      ORDER BY t.fecha_limite DESC LIMIT 300
      """,usuario.cuenta(a));
  }
  @GetMapping("/coordinacion/estudiantes") public List<Map<String,Object>> estudiantesCoordinacion(Authentication a) {
    coordinacion(a);
    var filas=db.queryForList("""
      SELECT i.estudiante_erp_id,i.curso_id,c.asignatura,c.nivel,c.grado,c.seccion,c.periodo,
        (SELECT count(*) FROM tareas t WHERE t.curso_id=c.id AND t.estado='PUBLICADA'
          AND NOT EXISTS (SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=i.estudiante_erp_id)) AS pendientes,
        (SELECT count(DISTINCT e.tarea_id) FROM entregas e JOIN tareas t ON t.id=e.tarea_id
          WHERE t.curso_id=c.id AND e.estudiante_erp_id=i.estudiante_erp_id AND t.estado IN ('PUBLICADA','CERRADA')) AS entregadas,
        (SELECT count(*) FROM entregas e JOIN tareas t ON t.id=e.tarea_id
          WHERE t.curso_id=c.id AND e.estudiante_erp_id=i.estudiante_erp_id AND e.estado='TARDIA'
            AND e.version=(SELECT max(v.version) FROM entregas v WHERE v.tarea_id=e.tarea_id AND v.estudiante_erp_id=e.estudiante_erp_id)) AS tardias,
        (SELECT count(*) FROM calificaciones_tarea n JOIN tareas t ON t.id=n.tarea_id
          WHERE t.curso_id=c.id AND n.estudiante_erp_id=i.estudiante_erp_id AND n.estado='PUBLICADA') AS notas_publicadas
      FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id
      JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND x.confirmado_erp AND i.activo
        AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)
      ORDER BY c.grado,c.seccion,c.asignatura,i.estudiante_erp_id LIMIT 500
      """,usuario.cuenta(a));
    Map<UUID,Map<String,String>> nombres=new HashMap<>();
    for(var fila:filas) {
      UUID curso=(UUID)fila.get("curso_id"),estudiante=(UUID)fila.get("estudiante_erp_id");
      if(!nombres.containsKey(curso)) {
        Map<String,String> porId=new HashMap<>();
        try { for(var e:erpCliente.get("/api/erp/cursos/"+curso+"/estudiantes/resumen")) porId.put(e.get("id").asText(),e.get("nombre").asText()); }
        catch(Exception ignored) { /* IDs y métricas siguen disponibles si ERP no responde. */ }
        nombres.put(curso,porId);
      }
      fila.put("estudiante",nombres.get(curso).getOrDefault(estudiante.toString(),"Estudiante "+estudiante));
      fila.put("multiples_pendientes",((Number)fila.get("pendientes")).longValue()>=2);
    }
    return filas;
  }
  @GetMapping("/coordinacion/clases") public List<Map<String,Object>> clasesCoordinacion(Authentication a) {
    coordinacion(a);
    if(!clasesServicio.habilitado()) return List.of();
    return db.queryForList("""
      SELECT cl.id,cl.curso_id,cl.tema,cl.inicio,cl.fin,cl.estado,
        c.asignatura,c.nivel,c.grado,c.seccion,c.periodo
      FROM clases_academicas cl JOIN cursos c ON c.id=cl.curso_id
      JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND x.confirmado_erp
      ORDER BY cl.inicio DESC LIMIT 300
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
