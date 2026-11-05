package pe.boston.erp.personas.application;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import pe.boston.erp.personas.api.PersonaDto.Capacidades;

/** El ámbito docente de alumnos permanece cerrado hasta que Asignaciones provea un contrato comprobable. */
@Component
public class AutorizacionPersonas {
  public record Actor(String sub, String rol, String ambito, UUID referencia) {}

  public Actor actor(Authentication autenticacion) {
    if (!(autenticacion instanceof OAuth2AuthenticationToken token)
        || !(token.getPrincipal() instanceof OidcUser oidc)) throw new AccesoDenegado();
    String rol = oidc.getClaimAsString("tipo");
    if (!"ADMINISTRADOR".equals(rol) && !"DIRECCION".equals(rol)
        && !"COORDINACION".equals(rol) && !"DOCENTE".equals(rol)) throw new AccesoDenegado();
    String ambito = oidc.getClaimAsString("personas_ambito");
    UUID referencia = null;
    try { referencia = UUID.fromString(oidc.getClaimAsString("referencia_erp")); }
    catch (IllegalArgumentException | NullPointerException ignored) { /* No concede acceso por ausencia de referencia. */ }
    return new Actor(oidc.getSubject(), rol, ambito, referencia);
  }

  public boolean direccion(Actor actor) {
    return "ADMINISTRADOR".equals(actor.rol()) || "DIRECCION".equals(actor.rol());
  }

  public boolean coordinacionConAmbito(Actor actor) {
    return "COORDINACION".equals(actor.rol()) && "INSTITUCIONAL".equals(actor.ambito());
  }

  public void registrar(Actor actor) {
    if (!direccion(actor) && !"COORDINACION".equals(actor.rol())) throw new AccesoDenegado();
  }

  public void consultar(Actor actor, UUID persona) {
    if (!direccion(actor) && !coordinacionConAmbito(actor)
        && !("DOCENTE".equals(actor.rol()) && persona.equals(actor.referencia()))) throw new AccesoDenegado();
  }

  public void gestionar(Actor actor) {
    if (!direccion(actor) && !coordinacionConAmbito(actor)) throw new AccesoDenegado();
  }

  public void identidad(Actor actor) {
    if (!direccion(actor)) throw new AccesoDenegado();
  }

  public void contacto(Actor actor, UUID persona) {
    if (!direccion(actor) && !coordinacionConAmbito(actor)
        && !("DOCENTE".equals(actor.rol()) && persona.equals(actor.referencia()))) throw new AccesoDenegado();
  }

  public Capacidades capacidades(Actor actor) {
    boolean gestor = direccion(actor) || coordinacionConAmbito(actor);
    boolean registro = gestor || "COORDINACION".equals(actor.rol());
    boolean docente = "DOCENTE".equals(actor.rol());
    return new Capacidades(registro,gestor,gestor || (docente && actor.referencia()!=null),
      direccion(actor),gestor,gestor,docente,docente ? actor.referencia() : null);
  }

  public static final class AccesoDenegado extends RuntimeException {}
}
