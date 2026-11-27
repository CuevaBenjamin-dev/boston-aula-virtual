package pe.boston.erp.asignaciones;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Destinatarios vigentes de avisos de asistencia por sección, sin duplicar asignaturas. */
public interface AsignacionesParaAsistencia {
  List<UUID> docentes(UUID seccionId,LocalDate fecha);
}
