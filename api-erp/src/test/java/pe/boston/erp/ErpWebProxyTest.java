package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.csrf.CsrfToken;
import java.time.Instant;

class ErpWebProxyTest {
  private final OAuth2AuthorizedClientService clientes=mock(OAuth2AuthorizedClientService.class);
  private final OAuth2AuthorizedClientManager gestor=mock(OAuth2AuthorizedClientManager.class);
  private final JwtDecoder decodificador=mock(JwtDecoder.class);
  private final ErpWebProxy proxy=new ErpWebProxy(clientes,gestor,decodificador);
  private final OAuth2AuthenticationToken usuario=mock(OAuth2AuthenticationToken.class);
  private final CsrfToken csrf=mock(CsrfToken.class);

  private OAuth2AuthorizedClient cliente(String token) {
    var cliente=mock(OAuth2AuthorizedClient.class);
    when(cliente.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
      token,Instant.now().minusSeconds(120),Instant.now().plusSeconds(120)));
    return cliente;
  }

  @Test void renuevaElTokenAntesDeAbrirLaSesion() {
    when(usuario.getName()).thenReturn("direccion.demo");
    when(csrf.getToken()).thenReturn("csrf");
    var vencido=cliente("vencido");
    var renovado=cliente("renovado");
    when(clientes.loadAuthorizedClient("boston-erp","direccion.demo")).thenReturn(vencido);
    when(gestor.authorize(any())).thenReturn(renovado);
    when(decodificador.decode("renovado")).thenReturn(Jwt.withTokenValue("renovado")
      .header("alg","none").subject("direccion.demo").claim("tipo","DIRECCION").build());

    var sesion=proxy.sesion(usuario,csrf);

    assertTrue((Boolean)sesion.get("autenticado"));
    assertEquals("DIRECCION",sesion.get("tipo"));
    verify(decodificador).decode("renovado");
  }

  @Test void permiteVolverAIniciarSesionSiFallaLaRenovacion() {
    when(usuario.getName()).thenReturn("direccion.demo");
    when(csrf.getToken()).thenReturn("csrf");
    var vencido=cliente("vencido");
    when(clientes.loadAuthorizedClient("boston-erp","direccion.demo")).thenReturn(vencido);
    when(gestor.authorize(any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

    var sesion=proxy.sesion(usuario,csrf);

    assertFalse((Boolean)sesion.get("autenticado"));
    verify(clientes).removeAuthorizedClient("boston-erp","direccion.demo");
  }

  @Test void unTokenVencidoNoRompeLaPantallaDeAcceso() {
    when(usuario.getName()).thenReturn("direccion.demo");
    when(csrf.getToken()).thenReturn("csrf");
    var vencido=cliente("vencido");
    when(clientes.loadAuthorizedClient("boston-erp","direccion.demo")).thenReturn(vencido);
    when(gestor.authorize(any())).thenReturn(vencido);
    when(decodificador.decode("vencido")).thenThrow(new JwtException("Token vencido"));

    var sesion=proxy.sesion(usuario,csrf);

    assertFalse((Boolean)sesion.get("autenticado"));
    verify(clientes).removeAuthorizedClient("boston-erp","direccion.demo");
  }
}
