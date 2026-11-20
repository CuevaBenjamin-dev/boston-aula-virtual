package pe.boston.aula.integraciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PerfilesInstitucionalesControllerTest {
  @Test void proyectaNombreSinModificarCredenciales() {
    JdbcTemplate db=mock(JdbcTemplate.class);
    UUID persona=UUID.randomUUID();
    var controller=new PerfilesInstitucionalesController(db);
    var perfil=new PerfilesInstitucionalesController.Perfil(1,persona,"DOCENTE","Ana Prueba Lagos");
    assertEquals("CONFIRMADA",controller.proyectar(persona,"DOCENTE",perfil).estado());
    verify(db).update(startsWith("INSERT INTO perfiles_institucionales"),eq(persona),eq("DOCENTE"),eq("Ana Prueba Lagos"));
    verifyNoMoreInteractions(db);
  }

  @Test void rechazaIdentidadDistintaDeLaRuta() {
    JdbcTemplate db=mock(JdbcTemplate.class);
    var controller=new PerfilesInstitucionalesController(db);
    var perfil=new PerfilesInstitucionalesController.Perfil(1,UUID.randomUUID(),"ESTUDIANTE","Luis Ensayo Rojas");
    assertThrows(ResponseStatusException.class,()->controller.proyectar(UUID.randomUUID(),"ESTUDIANTE",perfil));
    verifyNoInteractions(db);
  }
}
