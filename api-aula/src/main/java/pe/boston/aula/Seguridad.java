package pe.boston.aula;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration public class Seguridad {
  @Bean OAuth2AuthorizedClientService clientesAutorizados(JdbcTemplate jdbc, ClientRegistrationRepository registros) {
    return new JdbcOAuth2AuthorizedClientService(jdbc, registros);
  }
  @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<EstadoCuentaFiltro> registroEstado(EstadoCuentaFiltro filtro) {
    var registro=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filtro);
    registro.setEnabled(false); return registro;
  }
  @Bean SecurityFilterChain cadena(HttpSecurity http, EstadoCuentaFiltro estado, OAuth2AuthorizedClientService clientesAutorizados,
      @Value("${BOSTON_ISSUER:http://localhost:9000}") String issuer,
      @Value("${BOSTON_PUBLIC_URL:http://localhost:8088}") String publico) throws Exception {
    http.authorizeHttpRequests(a -> a
      .requestMatchers("/actuator/health", "/error", "/oauth2/**", "/login/**").permitAll()
      .requestMatchers("/api/**").authenticated()
      .anyRequest().denyAll())
      .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), request -> request.getRequestURI().startsWith("/api/")))
      .oauth2Login(o -> o.defaultSuccessUrl("http://localhost:8088/", true).failureHandler((request,response,error) -> {
        LoggerFactory.getLogger(Seguridad.class).error("Fallo OIDC del BFF",error);
        response.sendRedirect("/login?error");
      }))
      .logout(l -> l.logoutUrl("/logout")
        .addLogoutHandler((request,response,auth) -> {
          if(auth!=null) clientesAutorizados.removeAuthorizedClient("boston",auth.getName());
        })
        .logoutSuccessHandler((request,response,auth) -> {
        if(auth!=null && auth.getPrincipal() instanceof OidcUser u) {
          String idToken=URLEncoder.encode(u.getIdToken().getTokenValue(),StandardCharsets.UTF_8);
          String regreso=URLEncoder.encode(publico+"/",StandardCharsets.UTF_8);
          response.sendRedirect(issuer+"/connect/logout?id_token_hint="+idToken+"&post_logout_redirect_uri="+regreso);
        } else response.sendRedirect(publico+"/");
      }))
      .csrf(c -> c.spa())
      .addFilterAfter(estado, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter.class);
    return http.build();
  }
}
