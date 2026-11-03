package pe.boston.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class EstadoCuentaFiltroTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

  @Test @SuppressWarnings("unchecked") void tokenDeCuentaSuspendidaNoPuedeConsultarDirectorio() throws Exception {
    UUID cuenta=UUID.randomUUID();
    var jwt=Jwt.withTokenValue("local").header("alg","none").subject("estudiante.demo")
      .claim("cuenta_id",cuenta.toString()).claim("sesion_version",1).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    when(db.query(eq("SELECT activo,sesion_version FROM cuentas WHERE id=?"),any(RowMapper.class),eq(cuenta)))
      .thenReturn(List.of(new boolean[]{false,true}));
    var solicitud=new MockHttpServletRequest("GET","/api/identidad/directorio");
    var respuesta=new MockHttpServletResponse(); FilterChain siguiente=mock(FilterChain.class);
    var filtro=new EstadoCuentaFiltro(db);
    assertFalse(filtro.shouldNotFilter(solicitud));
    filtro.doFilter(solicitud,respuesta,siguiente);
    assertEquals(401,respuesta.getStatus());
    verify(siguiente,never()).doFilter(any(),any());
  }
}
