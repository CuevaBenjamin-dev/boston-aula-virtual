package pe.boston.identidad;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class CuentasControllerTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final PasswordEncoder encoder=mock(PasswordEncoder.class);
  private final CuentasController cuentas=new CuentasController(db,encoder,mock(Restablecimiento.class));
  private final UUID id=UUID.randomUUID();
  private final Jwt actor=Jwt.withTokenValue("test").header("alg","none").subject("estudiante.demo").claim("cuenta_id",id.toString()).build();

  @Test void exigeClaveActualAntesDeModificar() {
    when(db.queryForMap("SELECT clave_hash,activo FROM cuentas WHERE id=? FOR UPDATE",id)).thenReturn(Map.of("clave_hash","hash-anterior","activo",true));
    when(encoder.matches("incorrecta","hash-anterior")).thenReturn(false);
    assertThrows(IllegalArgumentException.class,()->cuentas.cambiarMiClave(new CuentasController.CambioPropio("incorrecta","nueva-clave-segura"),actor));
    verify(encoder,never()).encode(anyString());
  }

  @Test void cambiaClaveYRevocaAutorizaciones() {
    when(db.queryForMap("SELECT clave_hash,activo FROM cuentas WHERE id=? FOR UPDATE",id)).thenReturn(Map.of("clave_hash","hash-anterior","activo",true));
    when(encoder.matches("actual","hash-anterior")).thenReturn(true);
    when(encoder.encode("nueva-clave-segura")).thenReturn("hash-nuevo");
    cuentas.cambiarMiClave(new CuentasController.CambioPropio("actual","nueva-clave-segura"),actor);
    verify(db).update("UPDATE cuentas SET clave_hash=?,intentos_fallidos=0,bloqueado_hasta=NULL,sesion_version=sesion_version+1 WHERE id=?","hash-nuevo",id);
    verify(db).update("DELETE FROM oauth2_authorization WHERE principal_name=(SELECT usuario FROM cuentas WHERE id=?)",id);
  }
}
