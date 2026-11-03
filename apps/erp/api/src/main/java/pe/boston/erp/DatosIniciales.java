package pe.boston.erp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration public class DatosIniciales {
  @Bean CommandLineRunner eventoInicial(JdbcTemplate jdbc, Institucional institucional) {
    return args -> { if (jdbc.queryForObject("SELECT count(*) FROM eventos_pendientes",Long.class)==0L) institucional.publicarTodas(); };
  }
}

