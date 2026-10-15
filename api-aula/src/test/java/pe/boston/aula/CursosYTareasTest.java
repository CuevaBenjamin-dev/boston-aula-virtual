package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

class CursosYTareasTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final UsuarioActual usuario=mock(UsuarioActual.class);
  private final CursosYTareas tareas=new CursosYTareas(db,usuario);
  private final Authentication actor=mock(Authentication.class);
  private final UUID tarea=UUID.randomUUID(),curso=UUID.randomUUID(),estudiante=UUID.randomUUID();

  private Map<String,Object> tarea(String estado) {
    var t=new HashMap<String,Object>();t.put("curso_id",curso);t.put("estado",estado);
    t.put("escala_maxima",new BigDecimal("20.00"));t.put("fecha_limite",Timestamp.from(Instant.now().plusSeconds(3600)));
    when(db.queryForMap("SELECT * FROM tareas WHERE id=?",tarea)).thenReturn(t);
    return t;
  }
  @Test void tareaCerradaNoRecibeMasEntregas() {
    tarea("CERRADA");when(usuario.tipo(actor)).thenReturn("ESTUDIANTE");
    assertThrows(IllegalArgumentException.class,()->tareas.entregar(tarea,"respuesta",actor));
  }
  @Test void notaFueraDeEscalaSeRechaza() {
    tarea("PUBLICADA");when(usuario.tipo(actor)).thenReturn("DOCENTE");
    assertThrows(IllegalArgumentException.class,()->tareas.calificar(tarea,estudiante,new BigDecimal("21"),"",0,actor));
  }
  @Test void versionAnteriorNoSobrescribeUnaNota() {
    tarea("PUBLICADA");when(usuario.tipo(actor)).thenReturn("DOCENTE");
    when(db.queryForObject(any(String.class),eq(Integer.class),eq(tarea),eq(estudiante))).thenReturn(1);
    when(db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? FOR UPDATE",tarea,estudiante))
      .thenReturn(java.util.List.of(Map.of("id",UUID.randomUUID(),"valor",new BigDecimal("17"),"version",2)));
    assertThrows(IllegalStateException.class,()->tareas.calificar(tarea,estudiante,new BigDecimal("18"),"corrección",1,actor));
  }
}
