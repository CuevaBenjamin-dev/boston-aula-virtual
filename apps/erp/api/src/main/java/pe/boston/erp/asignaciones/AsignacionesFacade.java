package pe.boston.erp.asignaciones;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.boston.erp.plataforma.AccesoAcademico;

@Service
public class AsignacionesFacade {
  private final JdbcTemplate db;
  public AsignacionesFacade(JdbcTemplate db) { this.db = db; }

  public List<Map<String,Object>> asignaciones(UUID docente, Jwt actor) {
    AccesoAcademico.lectura(actor);
    return db.queryForList("""
      SELECT ad.id,ad.oferta_id,ad.estado,ad.creado_en,ad.actualizado_en,a.codigo AS codigo_asignatura,a.nombre AS asignatura,
        g.nombre AS grado,s.nombre AS seccion,p.nombre AS periodo,n.nombre AS nivel
      FROM asignaciones_docentes ad JOIN ofertas_academicas o ON o.id=ad.oferta_id
      JOIN asignaturas a ON a.id=o.asignatura_id JOIN secciones s ON s.id=o.seccion_id
      JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id JOIN periodos_academicos p ON p.id=s.periodo_id
      WHERE ad.docente_id=? ORDER BY ad.creado_en DESC
      """, docente);
  }
}
