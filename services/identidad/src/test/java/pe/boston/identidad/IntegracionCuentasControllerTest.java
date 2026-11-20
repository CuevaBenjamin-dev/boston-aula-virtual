package pe.boston.identidad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class IntegracionCuentasControllerTest {
  JdbcTemplate db=mock(JdbcTemplate.class);
  PasswordEncoder claves=mock(PasswordEncoder.class);
  IntegracionCuentasController servicio=new IntegracionCuentasController(db,claves);
  UUID persona=UUID.randomUUID();

  @Test void reutilizaCuentaYConservaClavePersonal() throws Exception {
    UUID cuenta=UUID.randomUUID();
    when(db.query(startsWith("SELECT id,usuario,activo"),any(RowMapper.class),eq(persona),eq("DOCENTE")))
      .thenAnswer(inv -> {
        RowMapper<?> mapper=inv.getArgument(1);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getObject(1,UUID.class)).thenReturn(cuenta);
        when(rs.getString(2)).thenReturn("00000011");
        when(rs.getBoolean(3)).thenReturn(true);
        return List.of(mapper.mapRow(rs,0));
      });
    when(db.queryForObject(startsWith("SELECT count(*)"),eq(Long.class),eq("00000011"),eq(persona),eq("DOCENTE")))
      .thenReturn(0L);
    var resultado=servicio.proyectar(persona,"DOCENTE",
      new IntegracionCuentasController.Cuenta(persona,"DOCENTE","00000011",true));
    assertEquals(cuenta,resultado.cuentaId());
    assertEquals("REUTILIZADA",resultado.estado());
    verify(claves,never()).encode(anyString());
  }

  @Test void noCreaCuentaPorEstadoInactivoSinCuentaPrevia() {
    when(db.query(startsWith("SELECT id,usuario,activo"),any(RowMapper.class),eq(persona),eq("ESTUDIANTE")))
      .thenReturn(List.of());
    var resultado=servicio.proyectar(persona,"ESTUDIANTE",
      new IntegracionCuentasController.Cuenta(persona,"ESTUDIANTE","00000011",false));
    assertEquals("SIN_CUENTA",resultado.estado());
    verify(claves,never()).encode(anyString());
  }

  @Test void rechazaIdentificadorDeOtroUUID() {
    assertThrows(ResponseStatusException.class,()->servicio.proyectar(persona,"DOCENTE",
      new IntegracionCuentasController.Cuenta(UUID.randomUUID(),"DOCENTE","00000011",true)));
  }

  @Test void correccionDocumentalActualizaUsuarioSinReemplazarClave() throws Exception {
    UUID cuenta=UUID.randomUUID();
    when(db.query(startsWith("SELECT id,usuario,activo"),any(RowMapper.class),eq(persona),eq("DOCENTE")))
      .thenAnswer(inv -> {
        RowMapper<?> mapper=inv.getArgument(1);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getObject(1,UUID.class)).thenReturn(cuenta);
        when(rs.getString(2)).thenReturn("00000011");
        when(rs.getBoolean(3)).thenReturn(true);
        return List.of(mapper.mapRow(rs,0));
      });
    when(db.queryForObject(startsWith("SELECT count(*)"),eq(Long.class),eq("00000012"),eq(persona),eq("DOCENTE")))
      .thenReturn(0L);
    assertEquals("REUTILIZADA",servicio.proyectar(persona,"DOCENTE",
      new IntegracionCuentasController.Cuenta(persona,"DOCENTE","00000012",true)).estado());
    verify(db).update(startsWith("UPDATE cuentas SET usuario="),eq("00000012"),eq(true),eq(cuenta));
    verify(claves,never()).encode(anyString());
  }

  @Test void colisionDocumentalBloqueaSinModificarCuenta() throws Exception {
    when(db.query(startsWith("SELECT id,usuario,activo"),any(RowMapper.class),eq(persona),eq("DOCENTE")))
      .thenReturn(List.of());
    when(db.queryForObject(startsWith("SELECT count(*)"),eq(Long.class),eq("00000012"),eq(persona),eq("DOCENTE")))
      .thenReturn(1L);
    assertThrows(ResponseStatusException.class,()->servicio.proyectar(persona,"DOCENTE",
      new IntegracionCuentasController.Cuenta(persona,"DOCENTE","00000012",true)));
    verify(db,never()).update(anyString(),any(Object[].class));
    verify(claves,never()).encode(anyString());
  }
}
