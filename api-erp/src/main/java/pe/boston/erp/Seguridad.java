package pe.boston.erp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration public class Seguridad {
 @Bean JwtDecoder decodificador(@Value("${BOSTON_JWKS_URI}") String jwks, @Value("${BOSTON_ISSUER}") String issuer) {
   NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
   decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
   return decoder;
 }
 @Bean SecurityFilterChain cadena(HttpSecurity http) throws Exception {
   http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
     .requestMatchers("/actuator/health").permitAll()
     .requestMatchers("/api/erp/eventos/**").hasAuthority("SCOPE_erp.sync")
     .anyRequest().authenticated()).oauth2ResourceServer(o -> o.jwt(withDefaults()));
   return http.build();
 }
}
