package pe.boston.erp.asignaciones.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.boston.erp.asignaciones.AsignacionesParaAsistencia;

@Component
class AsignacionesAsistenciaConsulta implements AsignacionesParaAsistencia {
  private final JdbcTemplate db;
  AsignacionesAsistenciaConsulta(JdbcTemplate db) { this.db=db; }
  @Override public List<UUID> docentes(UUID seccion,LocalDate fecha) {
    return db.query("""
      SELECT DISTINCT a.docente_id FROM asi_asignaciones a WHERE a.seccion_id=?
        AND a.estado='ACTIVA' AND a.desde<=? AND (a.hasta IS NULL OR a.hasta>=?)
        AND EXISTS(SELECT 1 FROM per_condiciones c WHERE c.persona_id=a.docente_id
          AND c.tipo='DOCENTE' AND c.periodo @> now())
      """,(r,n)->r.getObject(1,UUID.class),seccion,fecha,fecha);
  }
}
