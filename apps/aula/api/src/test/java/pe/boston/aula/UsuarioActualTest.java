package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class UsuarioActualTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final UsuarioActual usuario=new UsuarioActual(db);
  private final Authentication auth=mock(Authentication.class);
  private final OidcUser oidc=mock(OidcUser.class);
  private final UUID curso=UUID.randomUUID(),referencia=UUID.randomUUID();

  private void perfil(String tipo) {
    when(auth.getPrincipal()).thenReturn(oidc);
    when(oidc.getClaimAsString("tipo")).thenReturn(tipo);
    when(oidc.getClaimAsString("referencia_erp")).thenReturn(referencia.toString());
    when(oidc.getClaimAsString("cuenta_id")).thenReturn(referencia.toString());
    when(db.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(0L);
  }
  @Test void estudianteSinMatriculaVigenteNoLeeCursoAjeno() {
    perfil("ESTUDIANTE");
    assertThrows(AccessDeniedException.class,()->usuario.leerCurso(auth,curso));
  }
  @Test void docenteSinAsignacionVigenteNoModificaCursoAjeno() {
    perfil("DOCENTE");
    assertThrows(AccessDeniedException.class,()->usuario.docente(auth,curso));
  }
  @Test void coordinacionSinAmbitoConfirmadoNoSupervisaCurso() {
    perfil("COORDINACION");
    assertThrows(AccessDeniedException.class,()->usuario.leerCurso(auth,curso));
  }
  @Test void administradorSinPermisoDeveloperNoEntraPorUrlDirecta() {
    perfil("ADMINISTRADOR");
    assertThrows(AccessDeniedException.class,()->usuario.developerRequerido(auth));
  }
  @Test void administradorNoRecibePermisosParaCalificar() {
    perfil("ADMINISTRADOR");
    assertThrows(AccessDeniedException.class,()->usuario.docente(auth,curso));
  }
}
