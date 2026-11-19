package pe.boston.erp.plataforma;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Único contrato técnico para la sesión del nuevo ERP Web. */
@RestController
public class SesionController {
  public record Sesion(boolean autenticado, String usuario, String rol, String csrf) {}

  @GetMapping("/api/erp/v1/sesion")
  public Sesion sesion(Authentication autenticacion, CsrfToken csrf) {
    OidcUser usuario = autenticacion instanceof OAuth2AuthenticationToken token
      && token.getPrincipal() instanceof OidcUser oidc ? oidc : null;
    String rol = usuario == null ? null : usuario.getClaimAsString("tipo");
    boolean activa = "DIRECCION".equals(rol) || "COORDINACION".equals(rol);
    return new Sesion(activa, usuario == null ? null : autenticacion.getName(), rol, csrf.getToken());
  }
}
