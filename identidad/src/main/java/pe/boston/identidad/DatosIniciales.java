package pe.boston.identidad;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DatosIniciales {
  @Bean CommandLineRunner cuentasDemo(JdbcTemplate jdbc, PasswordEncoder encoder, @Value("${BOSTON_SEED_PASSWORD}") String clave) {
    return args -> {
      String hash = encoder.encode(clave);
      crear(jdbc, "estudiante.demo", "ESTUDIANTE", "00000000-0000-0000-0000-000000000101", false, false, hash);
      crear(jdbc, "docente.demo", "DOCENTE", "00000000-0000-0000-0000-000000000201", false, false, hash);
      crear(jdbc, "coordinacion.demo", "COORDINACION", null, false, false, hash);
      crear(jdbc, "admin.demo", "ADMINISTRADOR", null, true, true, hash);
    };
  }
  private void crear(JdbcTemplate jdbc, String usuario, String tipo, String referencia, boolean developer, boolean tesoreria, String hash) {
    jdbc.update("INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,developer,tesoreria) VALUES (?,?,?,?,?::uuid,?,?) ON CONFLICT (usuario) DO NOTHING",
      UUID.randomUUID(), usuario, hash, tipo, referencia, developer, tesoreria);
  }
}
