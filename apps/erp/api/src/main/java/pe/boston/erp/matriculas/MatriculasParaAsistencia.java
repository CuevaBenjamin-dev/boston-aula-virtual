package pe.boston.erp.matriculas;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Consulta temporal pública; Matrículas conserva la propiedad del padrón. */
public interface MatriculasParaAsistencia {
  record Miembro(UUID matriculaId, UUID personaId, UUID seccionId, String nombre, String estado) {}
  List<Miembro> padron(UUID seccionId, LocalDate fecha);
  List<Miembro> trayectoria(UUID personaId, int anio);
  boolean puedeSolicitar(UUID personaId, UUID matriculaId);
}
