package pe.boston.erp.oferta.api;

import java.util.Locale;

/** Identificador visible; el UUID de la asignatura permanece independiente. */
final class CodigoAsignatura {
  private CodigoAsignatura() {}

  static String normalizar(int anio,String nivel,int grado,String entrada) {
    if (anio<2000 || anio>2100 || !("PRIMARIA".equals(nivel) && grado>=1 && grado<=6)
        && !("SECUNDARIA".equals(nivel) && grado>=1 && grado<=5)) {
      throw new IllegalArgumentException("Año, nivel o grado inválido");
    }
    String prefijo=String.format(Locale.ROOT,"%s-%02d-","PRIMARIA".equals(nivel)?"PR":"SE",grado);
    String sufijo=String.format(Locale.ROOT,"-%02d",anio%100);
    String valor=entrada==null?"":entrada.trim().toUpperCase(Locale.ROOT);
    if (valor.matches("[A-Z0-9_]{1,20}")) return prefijo+valor+sufijo;
    if (valor.startsWith(prefijo)) {
      String sigla=valor.substring(prefijo.length());
      if (sigla.matches("[A-Z0-9_]{1,20}")) return valor+sufijo;
      if (sigla.endsWith(sufijo) && sigla.substring(0,sigla.length()-sufijo.length()).matches("[A-Z0-9_]{1,20}")) return valor;
    }
    throw new IllegalArgumentException("Código inválido para el año, nivel o grado. Escribe solo la sigla de la asignatura (por ejemplo, MAT).");
  }
}
