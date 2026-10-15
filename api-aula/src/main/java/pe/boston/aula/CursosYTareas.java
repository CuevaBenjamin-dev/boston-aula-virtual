package pe.boston.aula;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class CursosYTareas {
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public CursosYTareas(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
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
    return "ESTUDIANTE".equals(usuario.tipo(a)) ? db.queryForList("SELECT * FROM tareas WHERE curso_id=? AND estado<>'BORRADOR' ORDER BY fecha_limite",curso)
      : db.queryForList("SELECT * FROM tareas WHERE curso_id=? ORDER BY fecha_limite",curso);
  }
  public Map<String,Object> tarea(UUID id,Authentication a) {
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=?",id); UUID curso=(UUID)t.get("curso_id");
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a)) && "BORRADOR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    return t;
  }
  @Transactional public UUID crear(UUID curso,String titulo,String instrucciones,OffsetDateTime limite,boolean tarde,boolean reentrega,BigDecimal maxima,Authentication a) {
    usuario.docente(a,curso); if(titulo==null||titulo.isBlank()||limite==null||maxima==null||maxima.signum()<=0) throw new IllegalArgumentException("Datos de tarea inválidos");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,acepta_tarde,permite_reentrega,estado,escala_maxima,creado_por) VALUES (?,?,?,?,?,?,?,'BORRADOR',?,?)",id,curso,titulo,instrucciones,limite,tarde,reentrega,maxima,usuario.cuenta(a));
    auditar(a,"TAREA_CREADA",id); return id;
  }
  @Transactional public void estado(UUID tarea,String estado,Authentication a) {
    if(!List.of("PUBLICADA","CERRADA").contains(estado)) throw new IllegalArgumentException("Estado inválido");
    var t=tarea(tarea,a); usuario.docente(a,(UUID)t.get("curso_id"));
    if(("PUBLICADA".equals(estado) && !"BORRADOR".equals(t.get("estado"))) ||
       ("CERRADA".equals(estado) && !"PUBLICADA".equals(t.get("estado")))) throw new IllegalArgumentException("Transición de estado inválida");
    db.update("UPDATE tareas SET estado=?,version=version+1 WHERE id=?",estado,tarea); auditar(a,"TAREA_"+estado,tarea);
  }
  @Transactional public Map<String,Object> editar(UUID id,String titulo,String instrucciones,OffsetDateTime limite,boolean tarde,boolean reentrega,BigDecimal maxima,int version,Authentication a) {
    var t=tarea(id,a); usuario.docente(a,(UUID)t.get("curso_id"));
    if(!"BORRADOR".equals(t.get("estado"))) throw new IllegalArgumentException("Solo se edita un borrador");
    if(titulo==null||titulo.isBlank()||instrucciones==null||instrucciones.isBlank()||limite==null||maxima==null||maxima.signum()<=0) throw new IllegalArgumentException("Datos de tarea inválidos");
    int actualizados=db.update("UPDATE tareas SET titulo=?,instrucciones=?,fecha_limite=?,acepta_tarde=?,permite_reentrega=?,escala_maxima=?,version=version+1 WHERE id=? AND version=?",titulo,instrucciones,limite,tarde,reentrega,maxima,id,version);
    if(actualizados!=1) throw new IllegalStateException("Versión obsoleta");
    auditar(a,"TAREA_EDITADA",id);return db.queryForMap("SELECT * FROM tareas WHERE id=?",id);
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
    return db.queryForList("SELECT * FROM entregas WHERE tarea_id=? ORDER BY entregado_en DESC",tarea);
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
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("SELECT c.*,t.titulo,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? AND c.estudiante_erp_id=? ORDER BY t.fecha_limite",curso,usuario.referencia(a));
    return db.queryForList("SELECT c.*,t.titulo,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? ORDER BY t.fecha_limite",curso);
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
