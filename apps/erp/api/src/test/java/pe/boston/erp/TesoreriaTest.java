package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class TesoreriaTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final Tesoreria tesoreria=new Tesoreria(db);
  private final UUID estudiante=UUID.randomUUID(),obligacion=UUID.randomUUID();
  private final Jwt admin=new Jwt("token",Instant.now(),Instant.now().plusSeconds(300),Map.of("alg","RS256"),Map.of("sub","admin.demo","tipo","ADMINISTRADOR","tesoreria",true));

  @Test void administradorSinPermisoFinancieroNoRegistraPagos() {
    Jwt tecnico=new Jwt("token",Instant.now(),Instant.now().plusSeconds(300),Map.of("alg","RS256"),Map.of("sub","tecnico.demo","tipo","ADMINISTRADOR"));
    assertThrows(AccessDeniedException.class,()->tesoreria.registrar(estudiante,obligacion,new BigDecimal("10.00"),"LOCAL-TEST",tecnico));
  }

  @Test void noPermitePagoMayorAlSaldo() {
    when(db.queryForObject(anyString(),eq(BigDecimal.class),eq(obligacion),eq(estudiante))).thenReturn(new BigDecimal("100.00"));
    assertThrows(IllegalArgumentException.class,()->tesoreria.registrar(estudiante,obligacion,new BigDecimal("101.00"),"LOCAL-TEST",admin));
  }
  @Test void noPermiteCentimosOcultos() {
    assertThrows(IllegalArgumentException.class,()->tesoreria.registrar(estudiante,obligacion,new BigDecimal("1.001"),"LOCAL-TEST",admin));
  }
  @Test void noPermiteReferenciaVacia() {
    assertThrows(IllegalArgumentException.class,()->tesoreria.registrar(estudiante,obligacion,new BigDecimal("1.00")," ",admin));
  }
  @Test void cuentaAcademicaSoloConsultaSuEstudianteVinculado() {
    Jwt cuenta=new Jwt("token",Instant.now(),Instant.now().plusSeconds(300),Map.of("alg","RS256"),Map.of("sub","estudiante.demo","tipo","ESTUDIANTE","referencia_erp",estudiante.toString()));
    assertThrows(AccessDeniedException.class,()->tesoreria.estado(UUID.randomUUID(),cuenta));
    assertEquals(BigDecimal.ZERO,tesoreria.estado(estudiante,cuenta).get("saldoTotal"));
  }
}
