package pe.boston.aula;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class YoutubeUrl {
  private static final Pattern ID=Pattern.compile("[A-Za-z0-9_-]{11}");
  private YoutubeUrl() {}
  public record Video(String id,String url) {}
  public static Video normalizar(String entrada) {
    try {
      if(entrada==null||entrada.length()>500) throw new IllegalArgumentException();
      URI uri=URI.create(entrada.trim());
      String host=uri.getHost();
      if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null) throw new IllegalArgumentException();
      host=host.toLowerCase(Locale.ROOT);
      String path=uri.getRawPath(); String id=null;
      if(host.equals("youtu.be")||host.equals("www.youtu.be")) id=segmento(path,"/");
      else if(host.equals("youtube.com")||host.equals("www.youtube.com")||host.equals("m.youtube.com")) {
        if("/watch".equals(path)) {
          String query=uri.getRawQuery();
          if(query!=null) for(String par:query.split("&")) if(par.startsWith("v=")) { id=par.substring(2); break; }
        } else for(String prefijo:new String[]{"/shorts/","/live/","/embed/","/v/"}) if(path.startsWith(prefijo)) { id=segmento(path,prefijo); break; }
      } else if(host.equals("youtube-nocookie.com")||host.equals("www.youtube-nocookie.com")) id=segmento(path,"/embed/");
      if(id==null||!ID.matcher(id).matches()) throw new IllegalArgumentException();
      return new Video(id,"https://www.youtube.com/watch?v="+id);
    } catch(RuntimeException e) { throw new IllegalArgumentException("Enlace oficial de video YouTube inválido"); }
  }
  private static String segmento(String path,String prefijo) {
    if(path==null||!path.startsWith(prefijo)) return null;
    String resto=path.substring(prefijo.length());
    if(resto.endsWith("/")) resto=resto.substring(0,resto.length()-1);
    return resto.contains("/")?null:resto;
  }
}
