package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ServiciosAcademicosExtraidosTest {
  @Test void planificacionConservaValidacionYConfirmacionDeHorarios() {
    var servicio=new PlanificacionClases(mock(JdbcTemplate.class));
    var inicio=OffsetDateTime.parse("2026-11-23T08:00:00-05:00");
    assertThrows(IllegalArgumentException.class,()->servicio.validar("Clase",inicio,inicio));
    assertThrows(IllegalStateException.class,()->servicio.exigirConfirmacion(List.of(),2,false));
    servicio.exigirConfirmacion(List.of(),2,true);
    var curso=UUID.randomUUID();
    assertEquals(servicio.huella(List.of(curso),"Clase",inicio,inicio.plusHours(1),"https://zoom.example"),
      servicio.huella(List.of(curso),"Clase",inicio,inicio.plusHours(1),"https://zoom.example"));
  }

  @Test void validacionDeEntregasConservaTipoYTamanio() {
    var servicio=new ValidacionArchivosAcademicos(mock(AlmacenamientoObjetos.class));
    assertEquals("txt",servicio.extensionEntrega("respuesta.txt","text/plain",10));
    assertThrows(IllegalArgumentException.class,()->servicio.extensionEntrega("respuesta.txt","application/pdf",10));
    assertThrows(IllegalArgumentException.class,()->servicio.extensionEntrega("respuesta.txt","text/plain",26L*1024*1024));
  }
}
