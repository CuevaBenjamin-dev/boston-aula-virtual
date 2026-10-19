package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class CursosYTareasTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final UsuarioActual usuario=mock(UsuarioActual.class);
  private final AvisosTarea avisos=mock(AvisosTarea.class);
  private final CursosYTareas tareas=new CursosYTareas(db,usuario,mock(ErpCliente.class),avisos);
  private final Authentication actor=mock(Authentication.class);
  private final UUID tarea=UUID.randomUUID(),curso=UUID.randomUUID(),estudiante=UUID.randomUUID();

  private Map<String,Object> tarea(String estado) {
    var t=new HashMap<String,Object>();t.put("curso_id",curso);t.put("estado",estado);
    t.put("escala_maxima",new BigDecimal("20.00"));t.put("fecha_limite",Timestamp.from(Instant.now().plusSeconds(3600)));
    when(db.queryForMap("SELECT * FROM tareas WHERE id=?",tarea)).thenReturn(t);
    when(db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,tarea)).thenReturn(0L);
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
    when(db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,1))
      .thenReturn(java.util.List.of(Map.of("id",UUID.randomUUID(),"valor",new BigDecimal("17"),"version",2,"estado","PUBLICADA")));
    assertThrows(IllegalStateException.class,()->tareas.calificar(tarea,estudiante,new BigDecimal("18"),"corrección",1,actor));
  }
  @Test void calificacionNuevaAvisaAlEstudiante() {
    tarea("PUBLICADA").put("titulo","Fracciones");
    when(db.queryForObject(any(String.class),eq(Integer.class),eq(tarea),eq(estudiante))).thenReturn(1);
    var id=UUID.randomUUID();
    when(db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,1))
      .thenReturn(java.util.List.of(),java.util.List.of(Map.of("id",id,"valor",new BigDecimal("18"),"comentario","Buen trabajo","version",1,"estado","BORRADOR")));
    when(db.queryForMap(eq("SELECT * FROM calificaciones_tarea WHERE id=?"),any(UUID.class)))
      .thenReturn(Map.of("id",id,"version",1),Map.of("id",id,"version",2));
    var identidad=mock(OidcUser.class);
    when(usuario.oidc(actor)).thenReturn(identidad);
    when(identidad.getSubject()).thenReturn("docente.demo");
    tareas.calificar(tarea,estudiante,new BigDecimal("18"),"Buen trabajo",0,actor);
    verify(avisos).calificacion(eq(tarea),eq(curso),eq(estudiante),any(String.class),any(UUID.class),eq(2),eq(false));
  }
}
