package pe.boston.aula;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class CursosYTareas {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final ErpCliente erp;
  public CursosYTareas(JdbcTemplate db,UsuarioActual usuario,ErpCliente erp) { this.db=db;this.usuario=usuario;this.erp=erp; }
  public List<Map<String,Object>> cursos(Authentication a) {
    return switch(usuario.tipo(a)) {
      case "ESTUDIANTE" -> db.queryForList("SELECT c.* FROM cursos c JOIN inscripciones_curso i ON i.curso_id=c.id WHERE i.estudiante_erp_id=? AND i.activo=TRUE ORDER BY c.asignatura",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList("SELECT c.* FROM cursos c JOIN docentes_curso d ON d.curso_id=c.id WHERE d.docente_erp_id=? AND d.activo=TRUE ORDER BY c.asignatura",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList("SELECT c.* FROM cursos c JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? ORDER BY c.asignatura",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin cursos académicos");
    };
  }
  public List<Map<String,Object>> tareas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,EXISTS(SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?) AS presentada
      FROM tareas t WHERE t.curso_id=? AND t.estado<>'FALTA_COMPLETAR'
      ORDER BY t.publicado_en DESC NULLS LAST,t.fecha_limite
      """,usuario.referencia(a),curso);
    if("DOCENTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,
        (SELECT count(DISTINCT e.estudiante_erp_id) FROM entregas e JOIN inscripciones_curso i ON i.curso_id=t.curso_id AND i.estudiante_erp_id=e.estudiante_erp_id AND i.activo WHERE e.tarea_id=t.id) AS entregados,
        (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=t.curso_id AND i.activo) AS total_estudiantes
      FROM tareas t WHERE t.curso_id=? ORDER BY t.creado_en DESC
      """,curso);
    return db.queryForList("SELECT * FROM tareas WHERE curso_id=? AND estado<>'FALTA_COMPLETAR' ORDER BY fecha_limite",curso);
  }
  public Map<String,Object> tarea(UUID id,Authentication a) {
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=?",id); UUID curso=(UUID)t.get("curso_id");
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a)) && "FALTA_COMPLETAR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    return t;
  }
  @Transactional public UUID crear(UUID curso,String titulo,String instrucciones,OffsetDateTime limite,Authentication a) {
    usuario.docente(a,curso); validar(titulo,instrucciones,limite);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,estado,escala_maxima,creado_por) VALUES (?,?,?,?,?,'FALTA_COMPLETAR',20,?)",id,curso,titulo.trim(),instrucciones.trim(),limite,usuario.cuenta(a));
    auditar(a,"TAREA_CREADA",id); return id;
  }
  @Transactional public void estado(UUID tarea,String estado,Authentication a) {
    if(!List.of("PUBLICADA","CERRADA").contains(estado)) throw new IllegalArgumentException("Estado inválido");
    var t=tarea(tarea,a); usuario.docente(a,(UUID)t.get("curso_id"));
    if(("PUBLICADA".equals(estado) && !List.of("FALTA_COMPLETAR","CERRADA").contains(t.get("estado"))) ||
       ("CERRADA".equals(estado) && !"PUBLICADA".equals(t.get("estado")))) throw new IllegalArgumentException("Transición de estado inválida");
    db.update("UPDATE tareas SET estado=?,publicado_en=CASE WHEN ?='PUBLICADA' THEN COALESCE(publicado_en,now()) ELSE publicado_en END,version=version+1 WHERE id=?",estado,estado,tarea); auditar(a,"TAREA_"+estado,tarea);
  }
  @Transactional public Map<String,Object> editar(UUID id,String titulo,String instrucciones,OffsetDateTime limite,int version,Authentication a) {
    var t=tarea(id,a); usuario.docente(a,(UUID)t.get("curso_id"));
    validar(titulo,instrucciones,limite);
    int actualizados=db.update("UPDATE tareas SET titulo=?,instrucciones=?,fecha_limite=?,version=version+1 WHERE id=? AND version=?",titulo.trim(),instrucciones.trim(),limite,id,version);
    if(actualizados!=1) throw new IllegalStateException("Versión obsoleta");
    auditar(a,"TAREA_EDITADA",id);return db.queryForMap("SELECT * FROM tareas WHERE id=?",id);
  }
  private void validar(String titulo,String instrucciones,OffsetDateTime limite) {
    if(titulo==null||titulo.isBlank()||titulo.length()>200||instrucciones==null||instrucciones.isBlank()||limite==null) throw new IllegalArgumentException("Título, instrucciones y fecha límite son obligatorios");
  }
  @Transactional public UUID entregar(UUID tarea,String respuesta,Authentication a) {
    usuario.estudiante(a); var t=tarea(tarea,a); UUID curso=(UUID)t.get("curso_id"); usuario.estudianteCurso(a,curso);
    if(!"PUBLICADA".equals(t.get("estado"))) throw new IllegalArgumentException("La tarea está cerrada");
    boolean tarde=OffsetDateTime.now().isAfter(((java.sql.Timestamp)t.get("fecha_limite")).toInstant().atOffset(java.time.ZoneOffset.UTC));
    if(tarde && !Boolean.TRUE.equals(t.get("acepta_tarde"))) throw new IllegalArgumentException("Plazo vencido");
    int version=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,usuario.referencia(a));
    if(version>0 && !Boolean.TRUE.equals(t.get("permite_reentrega"))) throw new IllegalArgumentException("Reentrega no permitida");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO entregas(id,tarea_id,estudiante_erp_id,version,respuesta,estado) VALUES (?,?,?,?,?,?)",id,tarea,usuario.referencia(a),version+1,respuesta,tarde?"TARDIA":"ENVIADA");
    auditar(a,"ENTREGA",id); return id;
  }
  public List<Map<String,Object>> entregas(UUID tarea,Authentication a) {
    var t=tarea(tarea,a);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("SELECT * FROM entregas WHERE tarea_id=? AND estudiante_erp_id=? ORDER BY version DESC",tarea,usuario.referencia(a));
    usuario.docente(a,(UUID)t.get("curso_id"));
    var filas=db.queryForList("SELECT * FROM entregas WHERE tarea_id=? ORDER BY entregado_en DESC",tarea);
    if(filas.isEmpty()) return filas;
    Map<String,String> nombres=new HashMap<>();
    try { for(var e:erp.get("/api/erp/cursos/"+t.get("curso_id")+"/estudiantes/resumen")) nombres.put(e.get("id").asText(),e.get("nombre").asText()); }
    catch(Exception e) { /* Las entregas siguen disponibles si ERP no responde. */ }
    for(var e:filas) e.put("estudiante",nombres.getOrDefault(e.get("estudiante_erp_id").toString(),"Estudiante no identificado"));
    return filas;
  }
  @Transactional public Map<String,Object> calificar(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Authentication a) {
    var t=tarea(tarea,a); usuario.docente(a,(UUID)t.get("curso_id"));
    if(valor==null || valor.signum()<0 || valor.compareTo((BigDecimal)t.get("escala_maxima"))>0) throw new IllegalArgumentException("Calificación fuera de escala");
    int ultimaEntrega=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,estudiante);
    if(ultimaEntrega==0) throw new IllegalArgumentException("No existe entrega");
    var anteriores=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? FOR UPDATE",tarea,estudiante);
    UUID id; BigDecimal antes=null;
    if(anteriores.isEmpty()) {
      if(version!=null && version!=0) throw new IllegalStateException("Versión obsoleta");
      id=UUID.randomUUID(); db.update("INSERT INTO calificaciones_tarea(id,tarea_id,estudiante_erp_id,valor,comentario,entrega_version,actualizado_por) VALUES (?,?,?,?,?,?,?)",id,tarea,estudiante,valor,comentario,ultimaEntrega,usuario.cuenta(a));
    } else {
      var anterior=anteriores.getFirst(); id=(UUID)anterior.get("id"); antes=(BigDecimal)anterior.get("valor");
      if(version==null || version!=((Number)anterior.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
      db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,entrega_version=?,version=version+1,actualizado_en=now(),actualizado_por=? WHERE id=?",valor,comentario,ultimaEntrega,usuario.cuenta(a),id);
    }
    db.update("INSERT INTO historial_calificaciones(id,calificacion_id,valor_anterior,valor_nuevo,comentario,actor_id) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,antes,valor,comentario,usuario.cuenta(a));
    auditar(a,"CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  public List<Map<String,Object>> notas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? AND c.estudiante_erp_id=? ORDER BY c.actualizado_en DESC",curso,usuario.referencia(a));
    return db.queryForList("SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? ORDER BY c.actualizado_en DESC",curso);
  }
  public Map<String,Object> nota(UUID id,Authentication a) {
    var n=db.queryForMap("SELECT c.*,t.curso_id,t.titulo,t.instrucciones,t.fecha_limite,t.escala_maxima FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)n.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&!usuario.referencia(a).equals(n.get("estudiante_erp_id"))) throw new AccessDeniedException("Nota ajena");
    return n;
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
