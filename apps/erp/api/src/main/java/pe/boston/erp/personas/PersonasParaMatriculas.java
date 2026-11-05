package pe.boston.erp.personas;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/** Contrato interno para Matrículas; no representa una matrícula ni concede acceso por sí mismo. */
public interface PersonasParaMatriculas {
  record NuevaPersona(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
    String nacionalidad, String telefono, String correo, String direccion, String tipoDocumento,
    String paisEmisor, String numeroDocumento, String motivoProvisional,
    String sustentoTipo, String sustentoReferencia) {}
  record Autorizacion(String autorizador, String medio, Instant fecha, String referencia) {}

  UUID buscarPorDocumento(String tipo, String pais, String numero, Authentication actor);
  UUID registrarEstudiante(NuevaPersona persona, Authentication actor);
  boolean esEstudianteActivo(UUID persona, Authentication actor);
  List<UUID> apoderadosVigentes(UUID estudiante, Authentication actor);
  boolean tieneIdentificacionValida(UUID persona, Authentication actor);
  UUID vincularApoderado(UUID estudiante, UUID apoderado, boolean sinParentesco,
    Autorizacion autorizacion, Authentication actor);
}
