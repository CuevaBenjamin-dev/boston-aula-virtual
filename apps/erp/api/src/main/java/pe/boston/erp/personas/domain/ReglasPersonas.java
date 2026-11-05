package pe.boston.erp.personas.domain;

import java.time.LocalDate;
import java.util.Set;

/** Invariantes de entrada que complementan las restricciones de PostgreSQL. */
public final class ReglasPersonas {
  private static final Set<String> CONDICIONES = Set.of("ESTUDIANTE", "DOCENTE", "PERSONAL_INSTITUCIONAL", "APODERADO");

  private ReglasPersonas() {}

  public static String requerido(String valor, String campo) {
    if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " es obligatorio");
    return valor.trim();
  }

  public static String opcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }

  /** Conserva todos los caracteres internos; únicamente elimina espacios exteriores. */
  public static String documento(String tipo, String pais, String numero) {
    String n = requerido(numero, "Número de documento");
    if (n.length()>64) throw new IllegalArgumentException("Número de documento demasiado largo");
    if ("DNI".equals(tipo) && (!"PE".equals(pais) || !n.matches("[0-9]{8}")))
      throw new IllegalArgumentException("El DNI peruano debe tener ocho dígitos");
    return n;
  }

  public static void condicion(String tipo) {
    if (!CONDICIONES.contains(tipo)) throw new IllegalArgumentException("Condición institucional inválida");
  }

  public static void nacimiento(LocalDate fecha) {
    if (fecha == null || fecha.isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Fecha de nacimiento inválida");
  }

  public static void contactos(String condicion, String telefono, String correo) {
    if (("DOCENTE".equals(condicion) || "PERSONAL_INSTITUCIONAL".equals(condicion))
      && (opcional(telefono) == null || opcional(correo) == null))
      throw new IllegalArgumentException("Esta condición requiere teléfono y correo");
    if ("APODERADO".equals(condicion) && opcional(telefono) == null)
      throw new IllegalArgumentException("El apoderado requiere teléfono");
  }
}
