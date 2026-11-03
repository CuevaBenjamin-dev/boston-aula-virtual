package pe.boston.aula;

import java.net.URI;

/** Valida solo que la referencia externa sea una URL web navegable; el docente elige el proveedor. */
public final class YoutubeUrl {
  private YoutubeUrl() {}
  public static String normalizar(String entrada) {
    if(entrada==null || entrada.isBlank() || entrada.length()>2048) throw new IllegalArgumentException("URL de grabación inválida");
    String valor=entrada.trim();
    try {
      URI uri=URI.create(valor);
      String esquema=uri.getScheme();
      if((!"https".equalsIgnoreCase(esquema) && !"http".equalsIgnoreCase(esquema))
          || uri.getHost()==null || uri.getHost().isBlank() || uri.getUserInfo()!=null
          || valor.chars().anyMatch(c->Character.isISOControl(c))) throw new IllegalArgumentException();
      return valor;
    } catch(RuntimeException e) { throw new IllegalArgumentException("URL de grabación inválida"); }
  }
}
