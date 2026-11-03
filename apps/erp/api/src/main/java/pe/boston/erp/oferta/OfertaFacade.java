package pe.boston.erp.oferta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.boston.erp.plataforma.AccesoAcademico;

@Service
public class OfertaFacade {
  private final JdbcTemplate db;
  public OfertaFacade(JdbcTemplate db) { this.db = db; }

  public List<Map<String,Object>> asignaturasGrado(UUID grado, UUID periodo, Jwt actor) {
    AccesoAcademico.lectura(actor);
    return db.queryForList("""
      SELECT ag.id,ag.grado_id,ag.periodo_id,ag.orden,ag.activa,a.id AS asignatura_id,a.codigo,a.nombre,a.activa AS catalogo_activa
      FROM asignaturas_grado ag JOIN asignaturas a ON a.id=ag.asignatura_id
      WHERE ag.grado_id=? AND (ag.periodo_id=? OR ag.periodo_id IS NULL) ORDER BY ag.orden,a.nombre
      """, grado, periodo);
  }
}
