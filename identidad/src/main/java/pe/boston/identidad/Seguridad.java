package pe.boston.identidad;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class Seguridad {
  @Bean PasswordEncoder codificador() { return new Argon2PasswordEncoder(16, 32, 1, 19456, 2); }

  @Bean UserDetailsService usuarios(JdbcTemplate jdbc) {
    return nombre -> jdbc.query("SELECT usuario,clave_hash,tipo,activo,developer,bloqueado_hasta FROM cuentas WHERE usuario=?",
      (rs, n) -> {
        boolean habilitado = rs.getBoolean("activo") && (rs.getTimestamp("bloqueado_hasta") == null || rs.getTimestamp("bloqueado_hasta").toInstant().isBefore(Instant.now()));
        var roles = new java.util.ArrayList<SimpleGrantedAuthority>();
        roles.add(new SimpleGrantedAuthority("ROLE_" + rs.getString("tipo")));
        if (rs.getBoolean("developer")) roles.add(new SimpleGrantedAuthority("developer.acceder"));
        return new User(rs.getString("usuario"), rs.getString("clave_hash"), habilitado, true, true, true, roles);
      }, nombre).stream().findFirst().orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(nombre));
  }

  @Bean @Order(1) SecurityFilterChain protocolo(HttpSecurity http) throws Exception {
    var configurer = new OAuth2AuthorizationServerConfigurer();
    http.securityMatcher(configurer.getEndpointsMatcher())
      .with(configurer, c -> c.oidc(Customizer.withDefaults()))
      .authorizeHttpRequests(a -> a.anyRequest().authenticated())
      .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
      .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    return http.build();
  }

  @Bean @Order(2) SecurityFilterChain paginas(HttpSecurity http, JdbcTemplate jdbc) throws Exception {
    var jwtConverter=new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
      var tipo=jwt.getClaimAsString("tipo");
      var authorities=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
      if(tipo!=null) authorities.add(new SimpleGrantedAuthority("ROLE_"+tipo));
      Object scopes=jwt.getClaim("scope");
      if(scopes instanceof String texto) java.util.Arrays.stream(texto.split(" ")).filter(s -> !s.isBlank()).forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_"+s)));
      else if(scopes instanceof java.util.Collection<?> lista) lista.forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_"+s)));
      return authorities;
    });
    http.csrf(c -> c.ignoringRequestMatchers("/api/identidad/**"))
      .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/error", "/login", "/restablecer", "/.well-known/**", "/api/identidad/restablecer").permitAll()
      .requestMatchers("/api/identidad/estado/**").hasAuthority("SCOPE_erp.read")
      .requestMatchers("/api/identidad/**").hasRole("ADMINISTRADOR")
      .anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)))
      .formLogin(f -> f.loginPage("/login").permitAll().failureHandler((request, response, error) -> {
        String usuario = request.getParameter("username");
        if (usuario != null) {
          jdbc.update("UPDATE cuentas SET intentos_fallidos=intentos_fallidos+1, bloqueado_hasta=CASE WHEN intentos_fallidos >= 4 THEN now() + interval '15 minutes' ELSE bloqueado_hasta END WHERE usuario=?", usuario);
          jdbc.update("INSERT INTO auditoria_seguridad(id,accion,detalle) VALUES (?, 'LOGIN_FALLIDO', ?)", UUID.randomUUID(), usuario);
        }
        response.sendRedirect("/login?error");
      }).successHandler((request, response, authentication) -> {
        jdbc.update("UPDATE cuentas SET intentos_fallidos=0,bloqueado_hasta=NULL WHERE usuario=?", authentication.getName());
        jdbc.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) SELECT ?,id,'LOGIN' FROM cuentas WHERE usuario=?", UUID.randomUUID(), authentication.getName());
        new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler().onAuthenticationSuccess(request, response, authentication);
      }))
      .logout(l -> l.logoutSuccessUrl("/login?logout"));
    return http.build();
  }

  @Bean RegisteredClientRepository clientes(JdbcTemplate jdbc, PasswordEncoder encoder,
      @Value("${BOSTON_BFF_SECRET}") String bff, @Value("${BOSTON_ERP_SECRET}") String erp) {
    var repo = new JdbcRegisteredClientRepository(jdbc);
    if (repo.findByClientId("aula-bff") == null) repo.save(RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId("aula-bff").clientSecret(encoder.encode(bff)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
      .redirectUri("http://localhost:8088/login/oauth2/code/boston")
      .postLogoutRedirectUri("http://localhost:8088/")
      .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
      .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build()).build());
    if (repo.findByClientId("aula-servicio") == null) repo.save(RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId("aula-servicio").clientSecret(encoder.encode(erp)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("erp.read").scope("erp.sync").build());
    return repo;
  }

  @Bean OAuth2AuthorizationService autorizaciones(JdbcTemplate jdbc, RegisteredClientRepository clientes) {
    return new JdbcOAuth2AuthorizationService(jdbc, clientes);
  }
  @Bean OAuth2AuthorizationConsentService consentimientos(JdbcTemplate jdbc, RegisteredClientRepository clientes) {
    return new JdbcOAuth2AuthorizationConsentService(jdbc, clientes);
  }
  @Bean AuthorizationServerSettings configuracionServidor(@Value("${BOSTON_ISSUER}") String issuer) {
    return AuthorizationServerSettings.builder().issuer(issuer).build();
  }
  @Bean JWKSource<SecurityContext> jwk(JdbcTemplate jdbc) throws Exception {
    var claves = jdbc.query("SELECT id,privada,publica FROM claves_firma ORDER BY creado_en LIMIT 1", (rs, i) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)));
    String id, priv, pub;
    if (claves.isEmpty()) {
      KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA"); generador.initialize(3072);
      KeyPair kp = generador.generateKeyPair();
      id = UUID.randomUUID().toString();
      priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
      pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
      jdbc.update("INSERT INTO claves_firma(id,privada,publica) VALUES (?,?,?)", UUID.fromString(id), priv, pub);
    } else { id=claves.getFirst().get(0); priv=claves.getFirst().get(1); pub=claves.getFirst().get(2); }
    KeyFactory factory = KeyFactory.getInstance("RSA");
    var publica = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub)));
    var privada = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv)));
    RSAKey rsa = new RSAKey.Builder(publica).privateKey(privada).keyID(id).build();
    return (selector, context) -> selector.select(new JWKSet(rsa));
  }
  @Bean JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwk) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwk);
  }
  @Bean OAuth2TokenCustomizer<JwtEncodingContext> claims(JdbcTemplate jdbc) {
    return context -> {
      if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) return;
      String usuario = context.getPrincipal().getName();
      jdbc.query("SELECT id,tipo,referencia_erp,developer,tesoreria,activo,sesion_version FROM cuentas WHERE usuario=?", rs -> {
        if (!rs.getBoolean("activo")) throw new IllegalStateException("Cuenta suspendida");
        context.getClaims().claim("cuenta_id", rs.getString("id"));
        context.getClaims().claim("tipo", rs.getString("tipo"));
        if (rs.getString("referencia_erp") != null) context.getClaims().claim("referencia_erp", rs.getString("referencia_erp"));
        context.getClaims().claim("developer", rs.getBoolean("developer"));
        context.getClaims().claim("tesoreria", rs.getBoolean("tesoreria"));
        context.getClaims().claim("sesion_version", rs.getInt("sesion_version"));
      }, usuario);
    };
  }
}

