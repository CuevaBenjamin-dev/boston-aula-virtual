package pe.boston.aula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service class ConsultaAcademica {
  private static final String CURSOS_CON_DOCENTES="SELECT c.*,(SELECT string_agg(dc.nombre_completo, ', ' ORDER BY dc.nombre_completo) FROM docentes_curso dc WHERE dc.curso_id=c.id AND dc.activo AND dc.nombre_completo IS NOT NULL) AS docentes FROM cursos c ";
  private static final String INDICADORES="""
    LEFT JOIN LATERAL (
      SELECT count(*) AS total_estudiantes,
             count(e.id) AS entregados,
             count(*) FILTER (WHERE e.estado='TARDIA') AS tardias,
             count(*) FILTER (WHERE e.id IS NOT NULL AND (n.id IS NULL OR n.estado='BORRADOR')) AS pendientes_revision
      FROM inscripciones_curso i
      LEFT JOIN LATERAL (
        SELECT id,estado,version FROM entregas WHERE tarea_id=t.id AND estudiante_erp_id=i.estudiante_erp_id
        ORDER BY version DESC LIMIT 1
      ) e ON TRUE
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=t.id AND n.estudiante_erp_id=i.estudiante_erp_id AND n.entrega_version=e.version
      WHERE i.curso_id=t.curso_id AND i.activo
    ) k ON TRUE
    """;
  private final JdbcTemplate db;
  private final UsuarioActual usuario;
  ConsultaAcademica(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  public List<Map<String,Object>> cursos(Authentication a) {
    return switch(usuario.tipo(a)) {
      case "ESTUDIANTE" -> db.queryForList(CURSOS_CON_DOCENTES.replace("SELECT c.*", "SELECT c.*,i.estado_matricula")+"JOIN inscripciones_curso i ON i.curso_id=c.id WHERE i.estudiante_erp_id=? AND ((i.activo AND i.estado_matricula='VIGENTE') OR i.estado_matricula='FINALIZADA') ORDER BY c.periodo DESC,c.asignatura",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(CURSOS_CON_DOCENTES+"JOIN docentes_curso d ON d.curso_id=c.id WHERE d.docente_erp_id=? AND d.activo=TRUE AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) ORDER BY c.asignatura",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(CURSOS_CON_DOCENTES+"JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) ORDER BY c.asignatura",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin cursos académicos");
    };
  }
  public List<Map<String,Object>> tareas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,EXISTS(SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?) AS presentada,
        (SELECT e.estado FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=? ORDER BY e.version DESC LIMIT 1) AS ultima_entrega_estado,
        (SELECT max(e.version) FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?) AS ultima_entrega_version,
        EXISTS(SELECT 1 FROM calificaciones_tarea n WHERE n.tarea_id=t.id AND n.estudiante_erp_id=?
          AND n.estado='PUBLICADA' AND n.entrega_version=(SELECT max(e.version) FROM entregas e
            WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?)) AS evaluacion_publicada
      FROM tareas t WHERE t.curso_id=? AND t.estado<>'FALTA_COMPLETAR'
        AND (NOT EXISTS (SELECT 1 FROM tareas siguiente WHERE siguiente.tarea_anterior_id=t.id)
          OR EXISTS (SELECT 1 FROM inscripciones_curso i WHERE i.curso_id=t.curso_id
            AND i.estudiante_erp_id=? AND i.estado_matricula='FINALIZADA'))
      ORDER BY t.publicado_en DESC NULLS LAST,t.fecha_limite
      """,usuario.referencia(a),usuario.referencia(a),usuario.referencia(a),usuario.referencia(a),usuario.referencia(a),curso,usuario.referencia(a));
    if("DOCENTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,k.total_estudiantes,k.entregados,k.tardias,
        (k.total_estudiantes-k.entregados) AS pendientes,k.pendientes_revision,
        EXISTS(SELECT 1 FROM tareas sucesora WHERE sucesora.tarea_anterior_id=t.id) AS es_historica
      FROM tareas t
      """+INDICADORES+" WHERE t.curso_id=? ORDER BY t.creado_en DESC",curso);
    return db.queryForList("SELECT * FROM tareas WHERE curso_id=? AND estado<>'FALTA_COMPLETAR' ORDER BY fecha_limite",curso);
  }
  public Map<String,Object> tarea(UUID id,Authentication a) {
    var t=new HashMap<>(db.queryForMap("SELECT * FROM tareas WHERE id=?",id)); UUID curso=(UUID)t.get("curso_id");
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a)) && "FALTA_COMPLETAR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    t.put("tarea_sucesora_id",db.queryForList("SELECT id FROM tareas WHERE tarea_anterior_id=?",UUID.class,id).stream().findFirst().orElse(null));
    if("DOCENTE".equals(usuario.tipo(a))) { var contadores=db.queryForList("""
      SELECT k.total_estudiantes,k.entregados,k.tardias,
             (k.total_estudiantes-k.entregados) AS pendientes,k.pendientes_revision
      FROM tareas t
      """+INDICADORES+" WHERE t.id=?",id); if(!contadores.isEmpty()) t.putAll(contadores.getFirst()); }
    return t;
  }
}
