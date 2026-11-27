package pe.boston.aula.integraciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ErpClienteAsistenciaTest {
  @Test void erpInaccesibleNoConfirmaOperacion() throws Exception {
    var cliente=spy(new ErpCliente("http://127.0.0.1:1/token","http://127.0.0.1:1","prueba"));
    doReturn("token-de-prueba").when(cliente).token();

    var error=assertThrows(ResponseStatusException.class,()->cliente.asistencia(
      "POST","/jornadas/00000000-0000-0000-0000-000000000000/publicar",
      "{}".getBytes(),"application/json","docente","DOCENTE",
      "00000000-0000-0000-0000-000000000001"));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
  }
}
