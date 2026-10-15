package pe.boston.aula;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface AlmacenamientoObjetos {
  String iniciar(String clave,String tipo);
  URI autorizarParte(String clave,String uploadId,int numero);
  void completar(String clave,String uploadId,List<Map<String,Object>> partes);
  long tamano(String clave);
  byte[] cabecera(String clave);
  URI leer(String clave,Duration vigencia);
  URI descargar(String clave,String nombre,Duration vigencia);
  void guardarFixture(String clave,byte[] contenido,String tipo);
  void eliminar(String clave);
  boolean disponible();
}
