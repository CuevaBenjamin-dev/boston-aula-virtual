package pe.boston.erp.personas;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/** Única consulta de Personas necesaria para habilitar una asignación docente. */
public interface PersonasParaAsignaciones {
  boolean docenteActivo(UUID personaId, Authentication actor);
}
