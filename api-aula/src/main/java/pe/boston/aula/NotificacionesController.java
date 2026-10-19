package pe.boston.aula;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/aula/notificaciones") public class NotificacionesController {
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public NotificacionesController(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  @GetMapping public List<Map<String,Object>> listar(Authentication a) {
    String tipo=usuario.tipo(a);
    List<Map<String,Object>> filas=new ArrayList<>();
    if("ESTUDIANTE".equals(tipo)) filas.addAll(db.queryForList("""
      SELECT n.id,n.clase_id,n.tipo,n.mensaje,n.creada_en,n.leida_en,c.tema,c.inicio,
             curso.asignatura,curso.grado,curso.seccion
      FROM notificaciones_clase n JOIN clases_academicas c ON c.id=n.clase_id
      JOIN cursos curso ON curso.id=c.curso_id
      JOIN inscripciones_curso i ON i.curso_id=c.curso_id AND i.estudiante_erp_id=n.estudiante_erp_id AND i.activo
      WHERE n.estudiante_erp_id=? ORDER BY n.creada_en DESC LIMIT 100
      """,usuario.referencia(a)));
    String base="""
      SELECT h.id,h.clase_id,h.accion AS tipo,
             CASE h.accion WHEN 'CREADA' THEN 'Nueva clase: ' WHEN 'CANCELADA' THEN 'Clase cancelada: ' ELSE 'Clase actualizada: ' END || h.tema AS mensaje,
             h.registrado_en AS creada_en,v.leida_en,h.tema,h.inicio,
             curso.asignatura,curso.grado,curso.seccion
      FROM historial_clases h JOIN clases_academicas c ON c.id=h.clase_id
      JOIN cursos curso ON curso.id=c.curso_id
      LEFT JOIN notificaciones_vistas v ON v.historial_id=h.id AND v.cuenta_id=?
      WHERE h.accion IN ('CREADA','EDITADA','CANCELADA')
      """;
    if(!"ESTUDIANTE".equals(tipo)) filas.addAll(switch(tipo) {
      case "DOCENTE" -> db.queryForList(base+" AND EXISTS (SELECT 1 FROM docentes_curso d WHERE d.curso_id=c.curso_id AND d.docente_erp_id=? AND d.activo) ORDER BY h.registrado_en DESC LIMIT 100",usuario.cuenta(a),usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(base+" AND EXISTS (SELECT 1 FROM coordinaciones_seccion x WHERE x.seccion_erp_id=curso.seccion_erp_id AND x.cuenta_id=? AND x.confirmado_erp) ORDER BY h.registrado_en DESC LIMIT 100",usuario.cuenta(a),usuario.cuenta(a));
      case "ADMINISTRADOR" -> db.queryForList(base+" ORDER BY h.registrado_en DESC LIMIT 100",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Perfil no autorizado");
    });
    if("ESTUDIANTE".equals(tipo)||"DOCENTE".equals(tipo)) filas.addAll(db.queryForList("""
      SELECT n.id,NULL::uuid AS clase_id,n.tarea_id,n.tipo,n.mensaje,n.creada_en,n.actualizada_en,n.leida_en,
             n.nuevas_entregas,t.titulo AS tema,NULL::timestamptz AS inicio,
             CASE WHEN n.tipo='TRASLADADA' THEN FALSE
               WHEN n.destinatario_tipo='ESTUDIANTE' THEN EXISTS
                 (SELECT 1 FROM inscripciones_curso i JOIN cursos vigente ON vigente.id=i.curso_id
                  WHERE i.curso_id=t.curso_id AND i.estudiante_erp_id=n.destinatario_erp_id AND i.activo
                    AND (vigente.periodo_fin IS NULL OR vigente.periodo_fin>=CURRENT_DATE))
               ELSE EXISTS (SELECT 1 FROM docentes_curso d JOIN cursos vigente ON vigente.id=d.curso_id
                            WHERE d.curso_id=t.curso_id AND d.docente_erp_id=n.destinatario_erp_id AND d.activo
                              AND (vigente.periodo_fin IS NULL OR vigente.periodo_fin>=CURRENT_DATE))
             END AS puede_abrir,
             c.asignatura,c.grado,c.seccion
      FROM notificaciones_tarea n JOIN tareas t ON t.id=n.tarea_id JOIN cursos c ON c.id=COALESCE(n.curso_id,t.curso_id)
      WHERE n.destinatario_tipo=? AND n.destinatario_erp_id=?
      ORDER BY n.actualizada_en DESC LIMIT 100
      """,tipo,usuario.referencia(a)));
    filas.sort(Comparator.comparing((Map<String,Object> n)->
      (java.time.Instant)((java.sql.Timestamp)n.getOrDefault("actualizada_en",n.get("creada_en"))).toInstant()).reversed());
    return filas.stream().limit(100).toList();
  }
  @PostMapping("/{id}/leer") public void leer(@PathVariable UUID id,Authentication a) {
    String tipo=usuario.tipo(a);
    if(("ESTUDIANTE".equals(tipo)||"DOCENTE".equals(tipo)) && db.update("""
      UPDATE notificaciones_tarea SET leida_en=COALESCE(leida_en,now())
      WHERE id=? AND destinatario_tipo=? AND destinatario_erp_id=?
      """,id,tipo,usuario.referencia(a))>0) return;
    if("ESTUDIANTE".equals(usuario.tipo(a))) {
      if(db.update("""
        UPDATE notificaciones_clase n SET leida_en=COALESCE(leida_en,now())
        FROM clases_academicas c,inscripciones_curso i
        WHERE n.id=? AND n.estudiante_erp_id=? AND c.id=n.clase_id
          AND i.curso_id=c.curso_id AND i.estudiante_erp_id=n.estudiante_erp_id AND i.activo
        """,id,usuario.referencia(a))==0) throw new AccessDeniedException("Notificación ajena");
      return;
    }
    if(listar(a).stream().noneMatch(n->id.equals(n.get("id")))) throw new AccessDeniedException("Notificación ajena");
    db.update("INSERT INTO notificaciones_vistas(cuenta_id,historial_id) VALUES (?,?) ON CONFLICT DO NOTHING",usuario.cuenta(a),id);
  }
}
