package pe.boston.aula;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service class CalificacionesTarea {
  private final JdbcTemplate db;
  private final UsuarioActual usuario;
  private final AvisosTarea avisos;
  private final ConsultaAcademica consulta;
  @Value("${boston.calificaciones.minimo:0}") private BigDecimal notaMinima=new BigDecimal("0");
  @Value("${boston.calificaciones.maximo:20}") private BigDecimal notaMaxima=new BigDecimal("20");
  CalificacionesTarea(JdbcTemplate db,UsuarioActual usuario,AvisosTarea avisos,ConsultaAcademica consulta) {
    this.db=db;this.usuario=usuario;this.avisos=avisos;this.consulta=consulta;
  }
  private Map<String,Object> contextoCalificacion(UUID tarea,Authentication a) {
    var t=consulta.tarea(tarea,a);
    var bloqueada=db.queryForMap("SELECT estado FROM tareas WHERE id=? FOR UPDATE",tarea);
    usuario.docente(a,(UUID)t.get("curso_id"));
    if("CANCELADA".equals(bloqueada.get("estado")) || "FALTA_COMPLETAR".equals(bloqueada.get("estado")) ||
       db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,tarea)>0)
      throw new IllegalStateException("Esta versión de la tarea no admite nuevas calificaciones");
    return t;
  }
  private int entregaCalificable(UUID tarea,UUID estudiante,Integer entregaEsperada) {
    int ultima=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,estudiante);
    if(ultima==0) throw new IllegalArgumentException("No existe entrega");
    if(entregaEsperada!=null && entregaEsperada!=ultima) throw new IllegalStateException("Llegó un intento más reciente; vuelve a abrir la entrega");
    return ultima;
  }
  @Transactional public Map<String,Object> guardarBorrador(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Integer entregaEsperada,Authentication a) {
    var t=contextoCalificacion(tarea,a);
    if(valor==null || valor.scale()>2 || valor.compareTo(notaMinima)<0 || valor.compareTo(notaMaxima)>0 || valor.compareTo((BigDecimal)t.get("escala_maxima"))>0)
      throw new IllegalArgumentException("La calificación debe estar entre 0 y 20, con hasta dos decimales");
    int intento=entregaCalificable(tarea,estudiante,entregaEsperada);
    var anteriores=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,intento);
    UUID id;
    if(anteriores.isEmpty()) {
      if(version!=null && version!=0) throw new IllegalStateException("Versión obsoleta");
      id=UUID.randomUUID();
      db.update("INSERT INTO calificaciones_tarea(id,tarea_id,estudiante_erp_id,valor,comentario,entrega_version,actualizado_por,estado) VALUES (?,?,?,?,?,?,?,'BORRADOR')",id,tarea,estudiante,valor,comentario,intento,usuario.cuenta(a));
    } else {
      var anterior=anteriores.getFirst(); id=(UUID)anterior.get("id");
      if(version==null || version!=((Number)anterior.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
      if("PUBLICADA".equals(anterior.get("estado")))
        db.update("UPDATE calificaciones_tarea SET valor_borrador=?,comentario_borrador=?,borrador_actualizado_en=now(),borrador_actualizado_por=?,version=version+1 WHERE id=?",valor,comentario,usuario.cuenta(a),id);
      else
        db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,actualizado_en=now(),actualizado_por=?,version=version+1 WHERE id=?",valor,comentario,usuario.cuenta(a),id);
    }
    auditar(a,"BORRADOR_CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  @Transactional public Map<String,Object> publicarCalificacion(UUID tarea,UUID estudiante,Integer entregaEsperada,Integer version,UUID operacionId,Authentication a) {
    var t=contextoCalificacion(tarea,a);
    if(operacionId==null) throw new IllegalArgumentException("Falta identificador de operación");
    var repetida=db.queryForList("SELECT c.* FROM historial_calificaciones h JOIN calificaciones_tarea c ON c.id=h.calificacion_id WHERE h.operacion_id=?",operacionId);
    if(!repetida.isEmpty()) {
      if(!tarea.equals(repetida.getFirst().get("tarea_id")) || !estudiante.equals(repetida.getFirst().get("estudiante_erp_id")))
        throw new IllegalStateException("Identificador de publicación utilizado en otra evaluación");
      return repetida.getFirst();
    }
    int intento=entregaCalificable(tarea,estudiante,entregaEsperada);
    var actuales=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,intento);
    if(actuales.isEmpty()) throw new IllegalStateException("Guarda primero el borrador de la calificación");
    var actual=actuales.getFirst(); UUID id=(UUID)actual.get("id");
    if(version==null || version!=((Number)actual.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
    boolean correccion="PUBLICADA".equals(actual.get("estado"));
    if(correccion && actual.get("valor_borrador")==null) return actual;
    BigDecimal nuevo=(BigDecimal)(correccion?actual.get("valor_borrador"):actual.get("valor"));
    BigDecimal anterior=correccion?(BigDecimal)actual.get("valor"):null;
    String comentarioNuevo=(String)(correccion?actual.get("comentario_borrador"):actual.get("comentario"));
    db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,estado='PUBLICADA',publicada_en=now(),publicada_por=?,valor_borrador=NULL,comentario_borrador=NULL,borrador_actualizado_en=NULL,borrador_actualizado_por=NULL,actualizado_en=now(),actualizado_por=?,version=version+1 WHERE id=?",nuevo,comentarioNuevo,usuario.cuenta(a),usuario.cuenta(a),id);
    db.update("INSERT INTO historial_calificaciones(id,calificacion_id,valor_anterior,valor_nuevo,comentario_anterior,comentario,actor_id,estado_anterior,estado_nuevo,operacion_id) VALUES (?,?,?,?,?,?,?,?,'PUBLICADA',?)",UUID.randomUUID(),id,anterior,nuevo,actual.get("comentario"),comentarioNuevo,usuario.cuenta(a),actual.get("estado"),operacionId);
    int nuevaVersion=((Number)actual.get("version")).intValue()+1;
    avisos.calificacion(tarea,(UUID)t.get("curso_id"),estudiante,(String)t.get("titulo"),id,nuevaVersion,correccion);
    auditar(a,correccion?"CORRECCION_CALIFICACION":"PUBLICACION_CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  public List<Map<String,Object>> notas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    String sql="SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=?";
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList(sql+" AND c.estudiante_erp_id=? AND c.estado='PUBLICADA' ORDER BY c.publicada_en DESC",curso,usuario.referencia(a)).stream().map(this::sinBorrador).toList();
    if("COORDINACION".equals(usuario.tipo(a))) return db.queryForList(sql+" AND c.estado='PUBLICADA' ORDER BY c.publicada_en DESC",curso).stream().map(this::sinBorrador).toList();
    return db.queryForList(sql+" ORDER BY c.actualizado_en DESC",curso);
  }
  public Map<String,Object> nota(UUID id,Authentication a) {
    var n=db.queryForMap("SELECT c.*,t.curso_id,t.titulo,t.instrucciones,t.fecha_limite,t.escala_maxima FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)n.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a)) && !usuario.referencia(a).equals(n.get("estudiante_erp_id"))) throw new AccessDeniedException("Nota ajena");
    if(!"DOCENTE".equals(usuario.tipo(a))) {
      if(!"PUBLICADA".equals(n.get("estado"))) throw new AccessDeniedException("Calificación sin publicar");
      return sinBorrador(n);
    }
    return n;
  }
  private Map<String,Object> sinBorrador(Map<String,Object> fila) {
    var visible=new HashMap<>(fila);
    visible.remove("valor_borrador"); visible.remove("comentario_borrador");
    visible.remove("borrador_actualizado_en"); visible.remove("borrador_actualizado_por");
    visible.remove("version");
    return visible;
  }
  public List<Map<String,Object>> historialNota(UUID id,Authentication a) {
    nota(id,a);
    return db.queryForList("SELECT id,valor_anterior,valor_nuevo,comentario_anterior,comentario,actor_id,instante,estado_anterior,estado_nuevo FROM historial_calificaciones WHERE calificacion_id=? ORDER BY instante,id",id);
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
