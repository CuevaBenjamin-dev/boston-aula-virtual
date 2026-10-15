package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class MensajesControllerTest {
  @Test void destinatarioAjenoNoPuedeAbrirMensaje() {
    var db=mock(JdbcTemplate.class);
    var usuario=mock(UsuarioActual.class);
    var actor=mock(Authentication.class);
    var mensaje=UUID.randomUUID();
    var cuenta=UUID.randomUUID();
    when(usuario.cuenta(actor)).thenReturn(cuenta);
    when(db.queryForMap("SELECT * FROM mensajes WHERE id=?",mensaje)).thenReturn(Map.of("emisor_cuenta",UUID.randomUUID()));
    when(db.queryForObject("SELECT count(*) FROM mensajes_destinatarios WHERE mensaje_id=? AND cuenta_id=?",Long.class,mensaje,cuenta)).thenReturn(0L);
    var controller=new MensajesController(db,usuario,mock(CuentasRemotasController.class));
    assertThrows(AccessDeniedException.class,()->controller.detalle(mensaje,actor));
  }
}
