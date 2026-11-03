package pe.boston.erp.personas;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import pe.boston.erp.plataforma.AccesoAcademico;

/** Consultas públicas de personas; las escrituras legadas se migrarán por caso de uso. */
@Service
public class PersonasFacade {
  private final JdbcTemplate db;

  public PersonasFacade(JdbcTemplate db) { this.db = db; }

  public List<Map<String,Object>> estudiantes(String buscar, Jwt actor) {
    AccesoAcademico.lectura(actor);
    String q = "%" + (buscar == null ? "" : buscar.trim()) + "%";
    return db.queryForList("""
      SELECT e.id,e.codigo,e.estado,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.fecha_nacimiento,p.nacionalidad,
        e.actualizado_en FROM estudiantes e JOIN personas p ON p.id=e.persona_id
      WHERE e.codigo IS NOT NULL AND (e.codigo ILIKE ? OR p.nombres ILIKE ? OR p.apellidos ILIKE ? OR p.documento ILIKE ?)
      ORDER BY p.apellidos,p.nombres LIMIT 200
      """, q,q,q,q);
  }

}
