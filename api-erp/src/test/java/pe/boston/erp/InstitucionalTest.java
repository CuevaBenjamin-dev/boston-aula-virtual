package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;

class InstitucionalTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final Institucional servicio=new Institucional(db);
  private final Jwt admin=Jwt.withTokenValue("local").header("alg","none").subject("admin.demo").claim("tipo","ADMINISTRADOR").build();
  private final UUID estudiante=UUID.randomUUID(),seccion=UUID.randomUUID(),periodo=UUID.randomUUID();

  @SuppressWarnings("unchecked")
  private void estudianteActivo() {
    when(db.query(eq("SELECT estado FROM estudiantes WHERE id=? FOR UPDATE"),any(RowMapper.class),eq(estudiante))).thenReturn(List.of("ACTIVO"));
    when(db.queryForObject("SELECT periodo_id FROM secciones WHERE id=?",UUID.class,seccion)).thenReturn(periodo);
  }
  @Test void matriculaRepetidaConservaSuIdentificador() {
    estudianteActivo(); UUID matricula=UUID.randomUUID();
    when(db.queryForList("SELECT m.id,m.seccion_id,s.periodo_id FROM matriculas m JOIN secciones s ON s.id=m.seccion_id WHERE m.estudiante_id=? AND m.estado='ACTIVA' FOR UPDATE OF m",estudiante))
      .thenReturn(List.of(Map.of("id",matricula,"seccion_id",seccion,"periodo_id",periodo)));
    assertEquals(matricula,servicio.matricular(estudiante,seccion,admin));
  }
  @Test void cambioDeSeccionDuranteMatriculaDebeUsarSuOperacionPropia() {
    estudianteActivo();
    when(db.queryForList("SELECT m.id,m.seccion_id,s.periodo_id FROM matriculas m JOIN secciones s ON s.id=m.seccion_id WHERE m.estudiante_id=? AND m.estado='ACTIVA' FOR UPDATE OF m",estudiante))
      .thenReturn(List.of(Map.of("id",UUID.randomUUID(),"seccion_id",UUID.randomUUID(),"periodo_id",periodo)));
    assertThrows(IllegalArgumentException.class,()->servicio.matricular(estudiante,seccion,admin));
  }
}
