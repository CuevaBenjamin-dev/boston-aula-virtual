package pe.boston.erp.plataforma;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SeguridadWeb {
  private static final Pattern ID_SESION = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  @Bean
  CookieSerializer cookieSesion(@Value("${BOSTON_ERP_COOKIE_MAX_AGE_SECONDS:43200}") int maxAge,
      @Value("${BOSTON_ERP_COOKIE_SECURE:false}") boolean secure) {
    var cookie = new DefaultCookieSerializer();
    cookie.setCookieName("ERP_SESSION");
    cookie.setSameSite("Lax");
    cookie.setCookieMaxAge(maxAge);
    cookie.setUseHttpOnlyCookie(true);
    cookie.setUseSecureCookie(secure);
    return new CookieSerializer() {
      @Override public List<String> readCookieValues(HttpServletRequest request) {
        try {
          return cookie.readCookieValues(request).stream()
            .filter(id -> ID_SESION.matcher(id).matches()).toList();
        } catch (IllegalArgumentException error) {
          return List.of();
        }
      }
      @Override public void writeCookieValue(CookieValue value) {
        cookie.writeCookieValue(value);
      }
    };
  }

  @Bean
  OAuth2AuthorizedClientService clientesAutorizados(JdbcTemplate jdbc, ClientRegistrationRepository registros) {
    return new JdbcOAuth2AuthorizedClientService(jdbc, registros);
  }

  @Bean
  SecurityFilterChain seguridad(HttpSecurity http, OAuth2AuthorizedClientService clientes,
      @Value("${BOSTON_ERP_SESSION_ABSOLUTE_SECONDS:86400}") long duracionAbsoluta) throws Exception {
    http.authorizeHttpRequests(reglas -> reglas
        .requestMatchers("/actuator/health", "/actuator/info", "/error", "/oauth2/**",
          "/login/**", "/api/erp/v1/sesion").permitAll()
        .requestMatchers("/api/erp/v1/personas/**", "/api/erp/v1/personas").authenticated()
        .requestMatchers("/api/erp/v1/openapi").access((autenticacion, contexto) -> {
          var actual=autenticacion.get();
          boolean autorizado=actual instanceof OAuth2AuthenticationToken token
            && token.getPrincipal() instanceof OidcUser usuario
            && ("ADMINISTRADOR".equals(usuario.getClaimAsString("tipo"))
              || "DIRECCION".equals(usuario.getClaimAsString("tipo")));
          return new AuthorizationDecision(autorizado);
        })
        .anyRequest().denyAll())
      .exceptionHandling(errores -> errores.defaultAuthenticationEntryPointFor(
        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
        solicitud -> solicitud.getRequestURI().startsWith("/api/erp/")))
      .oauth2Login(login -> login.defaultSuccessUrl("http://127.0.0.1:8089/", true))
      .logout(logout -> logout.logoutUrl("/logout")
        .addLogoutHandler((solicitud, respuesta, auth) -> {
          if (auth != null) clientes.removeAuthorizedClient("boston-erp", auth.getName());
        })
        .logoutSuccessHandler((solicitud, respuesta, auth) -> {
          String regreso = "http://127.0.0.1:8089/";
          if (auth != null && auth.getPrincipal() instanceof OidcUser usuario) {
            String token = URLEncoder.encode(usuario.getIdToken().getTokenValue(), StandardCharsets.UTF_8);
            String destino = URLEncoder.encode(regreso, StandardCharsets.UTF_8);
            respuesta.sendRedirect("http://127.0.0.1:9000/connect/logout?id_token_hint="
              + token + "&post_logout_redirect_uri=" + destino);
          } else {
            respuesta.sendRedirect(regreso);
          }
        }))
      .csrf(csrf -> csrf.spa())
      .addFilterBefore(new SesionAbsolutaFiltro(duracionAbsoluta), SecurityContextHolderFilter.class);
    return http.build();
  }
}
