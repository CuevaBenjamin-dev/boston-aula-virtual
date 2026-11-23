package pe.boston.erp.plataforma;

/** Expresión SQL compartida para comparar nombres y documentos sin diferencias de tildes o caja. */
public final class BusquedaTexto {
  private static final String ACENTOS = "áàâäãéèêëíìîïóòôöõúùûüñç";
  private static final String BASE = "aaaaaeeeeiiiiooooouuuunc";

  private BusquedaTexto() {}

  public static String sql(String expresion) {
    return "translate(lower(" + expresion + "), '" + ACENTOS + "', '" + BASE + "')";
  }
}
