package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import pe.boston.aula.integraciones.ErpCliente;
import pe.boston.aula.integraciones.Sincronizacion;

class EstadoSincronizacionTest {
  @Test void noPublicaErrorHistoricoCuandoLaIntegracionEsPorProyecciones() {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UsuarioActual usuario=mock(UsuarioActual.class);
    Sincronizacion sync=mock(Sincronizacion.class);
    Authentication auth=mock(Authentication.class);
    var controller=new OperacionesController(mock(Clases.class),usuario,db,mock(ErpCliente.class),sync,
      mock(SupervisionController.class),mock(CuentasRemotasController.class));
    var estado=controller.estadoSincronizacion(auth);
    assertEquals("PROYECCION_ERP",estado.get("modo"));
    assertEquals(false,estado.get("pendiente"));
    assertNull(estado.get("sincronizadoEn"));
    verify(db,never()).queryForMap("SELECT ultimo_exito,ultimo_error FROM estado_sincronizacion WHERE id=1");
  }
}
