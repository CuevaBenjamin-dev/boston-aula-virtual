package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MigracionesPostgresTest {

  @Test
  void aplicaTodasLasMigracionesEnPostgresLimpio() throws Exception {
    try (var postgres = new PostgreSQLContainer("postgres:18.6-alpine")) {
      postgres.start();
      var flyway = Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load();

      assertEquals(11, flyway.migrate().migrationsExecuted);
      flyway.validate();

      try (var connection = DriverManager.getConnection(
          postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          var statement = connection.createStatement()) {
        try (var result = statement.executeQuery(
            "SELECT count(*) FROM flyway_schema_history WHERE success")) {
          assertTrue(result.next());
          assertEquals(11, result.getInt(1));
        }
        try (var result = statement.executeQuery(
            "SELECT to_regclass('public.borradores_matricula') IS NOT NULL, "
                + "to_regclass('public.asignaciones_docentes') IS NOT NULL, "
                + "to_regclass('public.referencias_documentales') IS NOT NULL")) {
          assertTrue(result.next());
          assertTrue(result.getBoolean(1));
          assertTrue(result.getBoolean(2));
          assertTrue(result.getBoolean(3));
        }
      }
    }
  }
}
