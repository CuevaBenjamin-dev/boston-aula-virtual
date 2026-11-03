package pe.boston.erp.plataforma;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Control de acceso compartido por los casos de uso académicos. */
public final class AccesoAcademico {
  private AccesoAcademico() {}

  public static void lectura(Jwt actor) {
    if (actor == null || !List.of("DIRECCION", "COORDINACION").contains(actor.getClaimAsString("tipo")))
      throw new AccessDeniedException("Acceso académico requerido");
  }

  public static void escritura(Jwt actor) { lectura(actor); }

  public static void direccion(Jwt actor) {
    if (actor == null || !"DIRECCION".equals(actor.getClaimAsString("tipo")))
      throw new AccessDeniedException("Permiso de Dirección requerido");
  }
}
