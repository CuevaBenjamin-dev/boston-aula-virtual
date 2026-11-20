package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import pe.boston.aula.integraciones.ErpCliente;
import pe.boston.aula.integraciones.Sincronizacion;

class IntegracionesSpringTest {
  @Configuration
  @ComponentScan(basePackageClasses=ErpCliente.class)
  static class SoloIntegraciones {}

  @Test void springDescubreUnClienteYUnConsumidorConUnSoloScheduler() {
    new ApplicationContextRunner()
      .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
      .withBean(TransactionTemplate.class,()->mock(TransactionTemplate.class))
      .withPropertyValues("BOSTON_TOKEN_URI=http://localhost/token",
        "BOSTON_ERP_URL=http://localhost/erp","BOSTON_ERP_SECRET=clave-de-prueba")
      .withUserConfiguration(SoloIntegraciones.class)
      .run(contexto -> {
        assertNull(contexto.getStartupFailure());
        assertEquals(1,contexto.getBeansOfType(ErpCliente.class).size());
        assertEquals(1,contexto.getBeansOfType(Sincronizacion.class).size());
      });
    assertEquals(1,Arrays.stream(Sincronizacion.class.getDeclaredMethods())
      .filter(metodo -> metodo.isAnnotationPresent(Scheduled.class)).count());
  }
}
