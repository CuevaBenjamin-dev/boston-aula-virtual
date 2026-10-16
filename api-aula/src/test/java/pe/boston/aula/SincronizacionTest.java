package pe.boston.aula;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class SincronizacionTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final Sincronizacion sync=new Sincronizacion(db,mock(ErpCliente.class),mock(TransactionTemplate.class));
  private final ObjectMapper json=new ObjectMapper();

  @Test void eventoRepetidoOAnteriorNoRevierteLaProyeccion() throws Exception {
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1 FOR UPDATE",Long.class)).thenReturn(12L);
    sync.aplicar(json.readTree("{\"secuencia\":12,\"tipo\":\"OFERTA_ACTUALIZADA\"}"));
    sync.aplicar(json.readTree("{\"secuencia\":11,\"tipo\":\"OFERTA_ACTUALIZADA\"}"));
    verify(db,times(2)).queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1 FOR UPDATE",Long.class);
    verifyNoMoreInteractions(db);
  }

  @Test void ambitoInstitucionalSeProyectaUnaSolaVez() throws Exception {
    UUID cuenta=UUID.randomUUID(),seccion=UUID.randomUUID();
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1 FOR UPDATE",Long.class)).thenReturn(12L);
    var evento=json.readTree("{\"secuencia\":13,\"tipo\":\"AMBITO_COORDINACION\",\"datos\":{\"cuentaId\":\""+cuenta+"\",\"seccionId\":\""+seccion+"\",\"asignado\":true}}");
    sync.aplicar(evento);
    verify(db).update(startsWith("INSERT INTO coordinaciones_seccion"),eq(cuenta),eq(seccion));
    verify(db).update("UPDATE estado_sincronizacion SET ultima_secuencia=? WHERE id=1",13L);
  }

  @Test void proyectaNombreCompletoDelDocenteAsignado() throws Exception {
    UUID curso=UUID.randomUUID(),seccion=UUID.randomUUID(),docente=UUID.randomUUID();
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1 FOR UPDATE",Long.class)).thenReturn(0L);
    var evento=json.readTree("""
      {"secuencia":1,"tipo":"OFERTA_ACTUALIZADA","datos":{
        "cursoId":"%s","seccionId":"%s","asignatura":"Matemática","seccion":"A","grado":"Primero","periodo":"2026",
        "estudiantes":[],"docentes":[{"id":"%s","nombre":"Luis Demo Docente"}]}}
      """.formatted(curso,seccion,docente));
    sync.aplicar(evento);
    verify(db).update(startsWith("INSERT INTO docentes_curso"),eq(curso),eq(docente),eq("Luis Demo Docente"));
  }
}
