package pe.boston.aula;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service class ValidacionArchivosAcademicos {
  private final AlmacenamientoObjetos objetos;
  @Value("${boston.archivos.max-mb:100}") private long maxMb;
  @Value("${boston.archivos.tipos:application/pdf,image/png,image/jpeg,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.presentationml.presentation}") private String tipos;
  ValidacionArchivosAcademicos(AlmacenamientoObjetos objetos) { this.objetos=objetos; }
  String extension(String nombre,String tipo,long tamano) {
    if(nombre==null||nombre.isBlank()||nombre.length()>200||nombre.contains("/")||nombre.contains("\\")||tamano<1||tamano>maxMb*1024*1024) throw new IllegalArgumentException("Nombre o tamaño de archivo inválido (máximo "+maxMb+" MB)");
    if(!List.of(tipos.split(",")).contains(tipo)) throw new IllegalArgumentException("Tipo de archivo no permitido");
    String n=nombre.toLowerCase(java.util.Locale.ROOT);
    if(n.endsWith(".pdf")&&"application/pdf".equals(tipo)) return "pdf";
    if(n.endsWith(".png")&&"image/png".equals(tipo)) return "png";
    if((n.endsWith(".jpg")||n.endsWith(".jpeg"))&&"image/jpeg".equals(tipo)) return "jpg";
    if(n.endsWith(".docx")&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(tipo)) return "docx";
    if(n.endsWith(".pptx")&&"application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(tipo)) return "pptx";
    throw new IllegalArgumentException("Solo PDF, PNG, JPG, DOCX o PPTX");
  }
  String extensionEntrega(String nombre,String tipo,long tamano) {
    if(nombre==null||nombre.isBlank()||nombre.length()>200||nombre.contains("/")||nombre.contains("\\")||tamano<1||tamano>25L*1024*1024)
      throw new IllegalArgumentException("Nombre o tamaño inválido (máximo 25 MB por archivo)");
    String n=nombre.toLowerCase(java.util.Locale.ROOT);
    if(n.endsWith(".pdf")&&"application/pdf".equals(tipo)) return "pdf";
    if(n.endsWith(".png")&&"image/png".equals(tipo)) return "png";
    if((n.endsWith(".jpg")||n.endsWith(".jpeg"))&&"image/jpeg".equals(tipo)) return "jpg";
    if(n.endsWith(".docx")&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(tipo)) return "docx";
    if(n.endsWith(".pptx")&&"application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(tipo)) return "pptx";
    if(n.endsWith(".xlsx")&&"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(tipo)) return "xlsx";
    if(n.endsWith(".txt")&&"text/plain".equals(tipo)) return "txt";
    throw new IllegalArgumentException("Solo PDF, DOCX, PPTX, XLSX, TXT, JPG o PNG");
  }
  boolean prefijo(byte[] b,int... bytes) { if(b.length<bytes.length) return false; for(int i=0;i<bytes.length;i++) if((b[i]&255)!=bytes[i]) return false; return true; }
  boolean contenidoEntregaValido(String clave,String tipo) {
    if(!"text/plain".equals(tipo)&&!tipo.startsWith("application/vnd.openxmlformats-officedocument")) return true;
    byte[] datos=objetos.contenido(clave);
    if(datos.length>25L*1024*1024) return false;
    if("text/plain".equals(tipo)) {
      try {
        String texto=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(datos)).toString();
        return texto.chars().noneMatch(c->c==0 || (c<32&&c!=9&&c!=10&&c!=13));
      } catch(java.nio.charset.CharacterCodingException e) { return false; }
    }
    String requerido=tipo.endsWith("wordprocessingml.document")?"word/document.xml":
      tipo.endsWith("presentationml.presentation")?"ppt/presentation.xml":"xl/workbook.xml";
    boolean contenido=false,principal=false;
    try(var zip=new ZipInputStream(new ByteArrayInputStream(datos))) {
      int entradas=0;long descomprimido=0;byte[] bloque=new byte[8192];
      for(var entrada=zip.getNextEntry();entrada!=null;entrada=zip.getNextEntry()) {
        if(++entradas>2000) return false;
        if("[Content_Types].xml".equals(entrada.getName())) contenido=true;
        if(requerido.equals(entrada.getName())) principal=true;
        int leidos;while((leidos=zip.read(bloque))!=-1) if((descomprimido+=leidos)>100L*1024*1024) return false;
      }
      return contenido&&principal;
    } catch(IOException e) { return false; }
  }
}
