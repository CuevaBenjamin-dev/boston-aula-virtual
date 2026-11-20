package pe.boston.aula.integraciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProyeccionesControllerTest {
  @Test void repetirFotoNoDesactivaInscripcionesNiDocentes() throws Exception {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UUID curso=UUID.randomUUID(), seccion=UUID.randomUUID(), asignatura=UUID.randomUUID();
    UUID estudiante=UUID.randomUUID(), docente=UUID.randomUUID();
    when(db.query(startsWith("SELECT seccion_erp_id"),any(RowMapper.class),eq(curso))).thenAnswer(inv -> {
      RowMapper<?> mapper=inv.getArgument(1);
      ResultSet rs=mock(ResultSet.class);
      when(rs.getObject(1,UUID.class)).thenReturn(seccion);
      return List.of(mapper.mapRow(rs,0));
    });
    when(db.queryForList(startsWith("SELECT estudiante_erp_id"),eq(UUID.class),eq(curso)))
      .thenReturn(List.of(estudiante));
    when(db.queryForList(startsWith("SELECT docente_erp_id"),eq(UUID.class),eq(curso)))
      .thenReturn(List.of(docente));
    var entrada=new ProyeccionesController.Curso(2,curso,seccion,asignatura,2027,"PRIMARIA",3,"B",
      "Matemática","PR-03-MAT",List.of(estudiante),List.of(),List.of(new ProyeccionesController.Docente(docente,"Docente Prueba")));
    var servicio=new ProyeccionesController(db);
    assertEquals("CONFIRMADA",servicio.proyectar(curso,entrada).estado());
    assertEquals("CONFIRMADA",servicio.proyectar(curso,entrada).estado());
    verify(db,never()).update(startsWith("UPDATE inscripciones_curso"),any(Object[].class));
    verify(db,never()).update(startsWith("UPDATE docentes_curso"),any(Object[].class));
  }
  @Test void finalizadaConservaInscripcionSinActivarlaYRetiradaNoEsHistorica() {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UUID curso=UUID.randomUUID(),seccion=UUID.randomUUID(),asignatura=UUID.randomUUID();
    UUID finalizada=UUID.randomUUID(),retirada=UUID.randomUUID();
    when(db.queryForList(startsWith("SELECT estudiante_erp_id"),eq(UUID.class),eq(curso)))
      .thenReturn(List.of(finalizada,retirada));
    var entrada=new ProyeccionesController.Curso(2,curso,seccion,asignatura,2025,"PRIMARIA",3,"B",
      "Matemática","PR-03-MAT",List.of(),List.of(finalizada),List.of());
    var servicio=new ProyeccionesController(db);
    assertEquals("CONFIRMADA",servicio.proyectar(curso,entrada).estado());
    verify(db).update(contains("estado_matricula='FINALIZADA'"),eq(curso),eq(finalizada));
    verify(db).update(startsWith("UPDATE inscripciones_curso SET activo=FALSE,estado_matricula='RETIRADA'"),eq(curso),eq(retirada));
  }
}
