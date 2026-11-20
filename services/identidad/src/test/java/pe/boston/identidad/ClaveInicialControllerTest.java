package pe.boston.identidad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;

class ClaveInicialControllerTest {
  @Test void permiteCincoCaracteresEnFormularioYServidor() throws Exception {
    JdbcTemplate db=mock(JdbcTemplate.class);
    PasswordEncoder encoder=mock(PasswordEncoder.class);
    var controller=new ClaveInicialController(db,encoder);
    CsrfToken csrf=mock(CsrfToken.class);
    when(csrf.getParameterName()).thenReturn("_csrf");
    when(csrf.getToken()).thenReturn("token-prueba");
    assertTrue(controller.formulario(mock(Authentication.class),csrf).getBody().contains("minlength=\"5\""));

    UUID cuenta=UUID.randomUUID();
    Authentication actor=mock(Authentication.class);
    when(actor.getName()).thenReturn("estudiante.prueba");
    when(db.query(startsWith("SELECT id,clave_hash"),any(RowMapper.class),eq("estudiante.prueba")))
      .thenAnswer(inv -> {
        RowMapper<?> mapper=inv.getArgument(1);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getObject(1,UUID.class)).thenReturn(cuenta);
        when(rs.getString(2)).thenReturn("hash-inicial");
        when(rs.getBoolean(3)).thenReturn(true);
        return List.of(mapper.mapRow(rs,0));
      });
    when(encoder.matches("temporal","hash-inicial")).thenReturn(true);
    when(encoder.encode("abcde")).thenReturn("hash-nuevo");
    HttpServletRequest request=mock(HttpServletRequest.class);
    when(request.getSession()).thenReturn(mock(HttpSession.class));
    assertEquals(303,controller.cambiar("temporal","abcde",actor,request).getStatusCode().value());
    verify(db).update(startsWith("UPDATE cuentas SET clave_hash="),eq("hash-nuevo"),eq(cuenta));
    assertThrows(IllegalArgumentException.class,()->controller.cambiar("temporal","abcd",actor,request));
  }
}
