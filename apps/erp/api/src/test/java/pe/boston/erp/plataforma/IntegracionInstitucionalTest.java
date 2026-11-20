package pe.boston.erp.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class IntegracionInstitucionalTest {
  @Test void docenteActivoSinAsignaturasGeneraUnaClaveEstableParaIdentidad() throws Exception {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UUID persona=UUID.randomUUID();
    when(db.query(startsWith("SELECT DISTINCT persona_id FROM per_condiciones"),any(RowMapper.class)))
      .thenReturn(List.of(persona));
    when(db.query(startsWith("SELECT p.id,p.nombres"),any(RowMapper.class),eq(persona)))
      .thenAnswer(inv -> {
        RowMapper<?> mapper=inv.getArgument(1);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getObject(1,UUID.class)).thenReturn(persona);
        when(rs.getString(2)).thenReturn("Ana");
        when(rs.getString(3)).thenReturn("Prueba");
        when(rs.getObject(4,LocalDate.class)).thenReturn(LocalDate.of(1990,1,1));
        when(rs.getString(5)).thenReturn("00000011");
        return List.of(mapper.mapRow(rs,0));
      });
    when(db.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(persona)))
      .thenReturn(true);
    var integracion=new IntegracionInstitucional(db,"http://localhost/token",
      "http://localhost/identidad","http://localhost/aula","secreto-prueba");
    integracion.reconciliar();
    integracion.reconciliar();
    ArgumentCaptor<String> claves=ArgumentCaptor.forClass(String.class);
    verify(db,times(2)).update(startsWith("INSERT INTO erp_solicitudes_externas"),any(UUID.class),
      claves.capture(),eq("IDENTIDAD"),eq("PERSONA"),eq(persona),eq("DOCENTE"),anyString());
    assertEquals(claves.getAllValues().get(0),claves.getAllValues().get(1));
    ArgumentCaptor<String> perfiles=ArgumentCaptor.forClass(String.class);
    verify(db,times(2)).update(startsWith("INSERT INTO erp_solicitudes_externas"),any(UUID.class),
      perfiles.capture(),eq("AULA"),eq("PERFIL"),eq(persona),eq("DOCENTE"),anyString());
    assertEquals(perfiles.getAllValues().get(0),perfiles.getAllValues().get(1));
  }
}
