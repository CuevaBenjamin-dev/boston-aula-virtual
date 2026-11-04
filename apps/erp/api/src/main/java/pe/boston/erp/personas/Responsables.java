package pe.boston.erp.personas;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Contrato público de Personas para el trámite de matrícula y la API. */
public interface Responsables {
  List<Map<String,Object>> personas(String tipo, String documento, Jwt actor);
  UUID crearPersona(String nombres, String apellidos, String tipoDocumento, String documento,
      String telefono, String correo, Jwt actor);
  void vincular(UUID estudiante, UUID persona, String rol, String vinculo, boolean emergencia,
      String telefono, String correo, Jwt actor);
  List<Map<String,Object>> apoderados(UUID estudiante, Jwt actor);
}
