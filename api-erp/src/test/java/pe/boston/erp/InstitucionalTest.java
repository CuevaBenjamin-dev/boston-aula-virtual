package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class InstitucionalTest {
  private final Institucional servicio=new Institucional(mock(JdbcTemplate.class));
  private final UUID estudiante=UUID.randomUUID(),seccion=UUID.randomUUID();
  private Jwt actor(String tipo) {
    return Jwt.withTokenValue("local").header("alg","none").subject("prueba.demo").claim("tipo",tipo).build();
  }
  @Test void noPermiteConfirmarSinAceptacionExplicita() {
    assertThrows(IllegalArgumentException.class,()->servicio.matricular(estudiante,seccion,false,actor("DIRECCION")));
  }
  @Test void administradorTecnicoNoConfirmaMatriculas() {
    assertThrows(AccessDeniedException.class,()->servicio.matricular(estudiante,seccion,true,actor("ADMINISTRADOR")));
  }
}
