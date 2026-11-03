package pe.boston.identidad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SeguridadTest {
  @Test void clavesSeVerificanConArgon2id() {
    var encoder=new Seguridad().codificador();
    String hash=encoder.encode("clave-local-larga-123");
    assertTrue(hash.startsWith("$argon2id$"));
    assertTrue(encoder.matches("clave-local-larga-123",hash));
    assertFalse(encoder.matches("otra-clave-local-123",hash));
  }
}
