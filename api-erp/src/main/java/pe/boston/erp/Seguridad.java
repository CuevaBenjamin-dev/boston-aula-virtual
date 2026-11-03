package pe.boston.erp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration public class Seguridad {
  private static final Pattern ID_SESION = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  @Bean CookieSerializer cookieSesion() {
    var cookie = new DefaultCookieSerializer();
    cookie.setCookieName("ERP_SESSION");
    cookie.setSameSite("Lax");
    cookie.setCookieMaxAge(Integer.MAX_VALUE);
    return new CookieSerializer() {
      @Override public List<String> readCookieValues(HttpServletRequest request) {
        try { return cookie.readCookieValues(request).stream().filter(id -> ID_SESION.matcher(id).matches()).toList(); }
        catch (IllegalArgumentException e) { return List.of(); }
      }
      @Override public void writeCookieValue(CookieValue value) { cookie.writeCookieValue(value); }
    };
  }
  @Bean OAuth2AuthorizedClientService clientesAutorizados(JdbcTemplate jdbc, ClientRegistrationRepository registros) {
    return new JdbcOAuth2AuthorizedClientService(jdbc, registros);
  }
  @Bean JwtDecoder decodificador(@Value("${BOSTON_JWKS_URI}") String jwks, @Value("${BOSTON_ISSUER}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }
  @Bean OAuth2AuthorizedClientManager gestorClientes(ClientRegistrationRepository registros,
      OAuth2AuthorizedClientService clientes) {
    var gestor=new AuthorizedClientServiceOAuth2AuthorizedClientManager(registros,clientes);
    gestor.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build());
    return gestor;
  }
  @Bean @Order(1) SecurityFilterChain servicio(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/erp/**")
      .csrf(c -> c.disable())
      .authorizeHttpRequests(a -> a
        .requestMatchers("/api/erp/eventos/**").hasAuthority("SCOPE_erp.sync")
        .anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(withDefaults()));
    return http.build();
  }
  @Bean @Order(2) SecurityFilterChain web(HttpSecurity http,OAuth2AuthorizedClientService clientes) throws Exception {
    http.authorizeHttpRequests(a -> a
        .requestMatchers("/actuator/health","/error","/oauth2/**","/login/**","/api/erp-web/sesion").permitAll()
        .requestMatchers("/api/erp-web/**").authenticated()
        .anyRequest().denyAll())
      .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
        r -> r.getRequestURI().startsWith("/api/erp-web/")))
      .oauth2Login(o -> o.defaultSuccessUrl("http://127.0.0.1:8089/",true))
      .logout(l -> l.logoutUrl("/logout")
        .addLogoutHandler((request,response,auth) -> {
          if(auth!=null) clientes.removeAuthorizedClient("boston-erp",auth.getName());
        })
        .logoutSuccessHandler((request,response,auth) -> {
          String regreso="http://127.0.0.1:8089/";
          if(auth!=null && auth.getPrincipal() instanceof OidcUser usuario) {
            String token=URLEncoder.encode(usuario.getIdToken().getTokenValue(),StandardCharsets.UTF_8);
            String destino=URLEncoder.encode(regreso,StandardCharsets.UTF_8);
            response.sendRedirect("http://127.0.0.1:9000/connect/logout?id_token_hint="+token+"&post_logout_redirect_uri="+destino);
          } else response.sendRedirect(regreso);
        }))
      .csrf(c -> c.spa());
    return http.build();
  }
}
