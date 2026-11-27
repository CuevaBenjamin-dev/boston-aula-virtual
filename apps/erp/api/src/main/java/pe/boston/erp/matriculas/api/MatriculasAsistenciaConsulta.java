package pe.boston.erp.matriculas.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.boston.erp.matriculas.MatriculasParaAsistencia;

@Component
class MatriculasAsistenciaConsulta implements MatriculasParaAsistencia {
  private final JdbcTemplate db;
  MatriculasAsistenciaConsulta(JdbcTemplate db) { this.db=db; }

  @Override public List<Miembro> padron(UUID seccionId,LocalDate fecha) {
    return db.query("""
      SELECT t.matricula_id,t.persona_id,t.seccion_id,p.nombres||' '||p.apellidos,m.estado
      FROM mat_tramos_asistencia t JOIN mat_matriculas m ON m.id=t.matricula_id
      JOIN per_personas p ON p.id=t.persona_id
      WHERE t.seccion_id=? AND t.desde<=? AND (t.hasta IS NULL OR t.hasta>=?)
      ORDER BY p.apellidos,p.nombres,t.persona_id
      """,(r,n)->new Miembro(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
        r.getObject(3,UUID.class),r.getString(4),r.getString(5)),
      seccionId,fecha,fecha);
  }

  @Override public List<Miembro> trayectoria(UUID personaId,int anio) {
    return db.query("""
      SELECT DISTINCT m.id,m.persona_id,m.seccion_id,p.nombres||' '||p.apellidos,m.estado
      FROM mat_matriculas m JOIN per_personas p ON p.id=m.persona_id
      WHERE m.persona_id=? AND m.anio=? AND m.estado<>'BORRADOR'
      ORDER BY m.id,m.persona_id,m.seccion_id,p.nombres||' '||p.apellidos,m.estado
      """,(r,n)->new Miembro(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
        r.getObject(3,UUID.class),r.getString(4),r.getString(5)),personaId,anio);
  }

  @Override public boolean puedeSolicitar(UUID personaId,UUID matriculaId) {
    return Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM mat_matriculas m WHERE m.id=? AND m.persona_id=?
        AND m.estado='VIGENTE')
      """,Boolean.class,matriculaId,personaId));
  }
}
