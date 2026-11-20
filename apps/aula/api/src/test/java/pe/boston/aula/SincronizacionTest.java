package pe.boston.aula;
import pe.boston.aula.integraciones.ErpCliente;
import pe.boston.aula.integraciones.Sincronizacion;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import java.util.function.Consumer;

class SincronizacionTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final ErpCliente erp=mock(ErpCliente.class);
  private final TransactionTemplate transaccion=mock(TransactionTemplate.class);
  private final Sincronizacion sync=new Sincronizacion(db,erp,transaccion);
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

  @Test void falloDelContratoHeredadoNoAvanzaSecuenciaYSePuedeReintentar() throws Exception {
    String consulta="/api/erp/v1/eventos?despues=7&limite=100";
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1",Long.class)).thenReturn(7L);
    when(erp.get(consulta)).thenThrow(new IllegalStateException("ERP: HTTP 404")).thenReturn(json.readTree("[]"));

    sync.ejecutar();
    verify(db).update(startsWith("UPDATE estado_sincronizacion SET ultimo_error="),contains("ERP: HTTP 404"));
    verify(db,never()).update(startsWith("UPDATE estado_sincronizacion SET ultimo_exito="));
    verify(db,never()).update(eq("UPDATE estado_sincronizacion SET ultima_secuencia=? WHERE id=1"),any(Object.class));

    sync.ejecutar();
    verify(erp,times(2)).get(consulta);
    verify(db).update("UPDATE estado_sincronizacion SET ultimo_exito=now(),ultimo_error=NULL WHERE id=1");
    verify(db,never()).update(eq("UPDATE estado_sincronizacion SET ultima_secuencia=? WHERE id=1"),any(Object.class));
  }

  @Test void consumidorAplicaEventoYRegistraExitoSoloTrasLaTransaccion() throws Exception {
    UUID cuenta=UUID.randomUUID(),seccion=UUID.randomUUID();
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1",Long.class)).thenReturn(0L);
    when(db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1 FOR UPDATE",Long.class)).thenReturn(0L);
    when(erp.get("/api/erp/v1/eventos?despues=0&limite=100")).thenReturn(json.readTree("""
      [{"secuencia":1,"tipo":"AMBITO_COORDINACION","datos":{"cuentaId":"%s","seccionId":"%s","asignado":true}}]
      """.formatted(cuenta,seccion)));
    doAnswer(invocacion -> {
      Consumer<TransactionStatus> accion=invocacion.getArgument(0);
      accion.accept(mock(TransactionStatus.class));
      return null;
    }).when(transaccion).executeWithoutResult(any());

    sync.ejecutar();
    verify(db).update("UPDATE estado_sincronizacion SET ultima_secuencia=? WHERE id=1",1L);
    verify(db).update("UPDATE estado_sincronizacion SET ultimo_exito=now(),ultimo_error=NULL WHERE id=1");
    verify(db,never()).update(startsWith("UPDATE estado_sincronizacion SET ultimo_error="),any(Object.class));
  }
}
