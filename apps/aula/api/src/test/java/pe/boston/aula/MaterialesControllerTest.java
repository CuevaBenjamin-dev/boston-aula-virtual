package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class MaterialesControllerTest {
  @Test void editarPeriodoSinDestinosNoDesvinculaSeccionesCompartidas() {
    var db=mock(JdbcTemplate.class);
    var usuario=mock(UsuarioActual.class);
    var actor=mock(Authentication.class);
    var oidc=mock(OidcUser.class);
    var material=UUID.randomUUID();
    var destinos=List.of(UUID.randomUUID(),UUID.randomUUID());
    when(db.queryForList("SELECT curso_id FROM destinos_material WHERE material_id=?",UUID.class,material)).thenReturn(destinos);
    when(db.queryForObject("SELECT estado FROM materiales_curso WHERE id=?",String.class,material)).thenReturn("BORRADOR");
    when(db.queryForMap("SELECT * FROM materiales_curso WHERE id=?",material)).thenReturn(Map.of("id",material));
    when(db.queryForList("SELECT curso_id FROM destinos_material WHERE material_id=? ORDER BY curso_id",UUID.class,material)).thenReturn(destinos);
    when(usuario.tipo(actor)).thenReturn("DOCENTE");
    when(usuario.oidc(actor)).thenReturn(oidc);
    when(oidc.getSubject()).thenReturn("docente.demo");

    var resultado=new MaterialesController(db,usuario).editar(material,new MaterialesController.Datos("Guía","",null,null,10,2),actor);

    assertEquals(destinos,resultado.get("curso_ids"));
    verify(db,never()).update(startsWith("DELETE FROM destinos_material"),any(UUID.class));
  }
}
