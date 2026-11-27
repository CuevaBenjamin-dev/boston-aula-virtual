package pe.boston.erp.oferta;

import java.util.UUID;
import java.util.List;

/** Contrato público de Oferta para validar la elección de una matrícula. */
public interface OfertaConsulta {
  record Seccion(UUID id, int anio, String nivel, int grado, String nombre) {}
  Seccion seccion(UUID id);
  List<Seccion> secciones(int anio);
}
