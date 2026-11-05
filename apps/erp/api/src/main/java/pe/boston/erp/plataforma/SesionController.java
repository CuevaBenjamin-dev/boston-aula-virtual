package pe.boston.erp.plataforma;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Único contrato técnico para la sesión del nuevo ERP Web. */
@RestController
public class SesionController {
  public record Sesion(boolean autenticado, String usuario, String csrf) {}

  @GetMapping("/api/erp/v1/sesion")
  public Sesion sesion(Authentication autenticacion, CsrfToken csrf) {
    boolean activa = autenticacion instanceof OAuth2AuthenticationToken;
    return new Sesion(activa, activa ? autenticacion.getName() : null, csrf.getToken());
  }
}
