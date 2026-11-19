package pe.boston.erp.plataforma;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Autorización técnica compartida. La condición de Persona no concede permiso ERP. */
public final class AccesoErp {
  private AccesoErp() {}

  public static String actor(Authentication auth) {
    if (!(auth instanceof OAuth2AuthenticationToken token)
        || !(token.getPrincipal() instanceof OidcUser oidc)) throw new SecurityException("Sesión ERP requerida");
    String rol = oidc.getClaimAsString("tipo");
    if (!"DIRECCION".equals(rol) && !"COORDINACION".equals(rol))
      throw new SecurityException("Rol ERP no autorizado");
    return oidc.getSubject();
  }

  public static boolean coordinacion(Authentication auth) {
    actor(auth);
    return "COORDINACION".equals(((OidcUser)((OAuth2AuthenticationToken)auth).getPrincipal()).getClaimAsString("tipo"));
  }
}
