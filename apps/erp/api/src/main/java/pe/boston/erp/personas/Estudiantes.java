package pe.boston.erp.personas;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Escritura pública de fichas de alumno, usada por matrícula y API. */
public interface Estudiantes {
  UUID crear(String nombres, String apellidos, LocalDate fechaNacimiento, String tipoDocumento,
      String documento, String nacionalidad, Jwt actor);
  void editar(UUID estudiante, String nombres, String apellidos, LocalDate fechaNacimiento,
      String tipoDocumento, String documento, String nacionalidad, Jwt actor);
}
