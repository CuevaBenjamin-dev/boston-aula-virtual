package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

class PerfilControllerTest {
  @Test void muestraNombreProyectadoEnLugarDelDocumentoDeAcceso() {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UsuarioActual usuario=mock(UsuarioActual.class);
    Authentication auth=mock(Authentication.class);
    UUID cuenta=UUID.randomUUID(),persona=UUID.randomUUID();
    when(usuario.perfil(auth)).thenReturn(Map.of("usuario","00000011","tipo","DOCENTE"));
    when(usuario.cuenta(auth)).thenReturn(cuenta);
    when(usuario.tipo(auth)).thenReturn("DOCENTE");
    when(usuario.referencia(auth)).thenReturn(persona);
    when(db.queryForList("SELECT actualizada_en FROM perfiles_aula WHERE cuenta_id=? AND foto IS NOT NULL",cuenta))
      .thenReturn(List.of());
    when(db.queryForList("SELECT nombre_completo FROM perfiles_institucionales WHERE persona_erp_id=? AND tipo=?",persona,"DOCENTE"))
      .thenReturn(List.of(Map.of("nombre_completo","Ana Prueba Lagos")));
    var perfil=new PerfilController(db,usuario).ver(auth);
    assertEquals("Ana Prueba Lagos",((Map<?,?>)perfil.get("datosInstitucionales")).get("nombre"));
    assertEquals("00000011",perfil.get("usuario"));
  }
}
