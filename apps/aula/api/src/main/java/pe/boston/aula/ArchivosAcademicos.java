package pe.boston.aula;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service public class ArchivosAcademicos {
  private final JdbcTemplate db;
  private final UsuarioActual usuario;
  private final AlmacenamientoObjetos objetos;
  private final MaterialesController materiales;
  private final AvisosTarea avisos;
  @Value("${boston.archivos.max-mb:100}") private long maxMb;
  @Value("${boston.archivos.capacidad-padre-mb:1024}") private long capacidadMb;
  @Value("${boston.archivos.tipos:application/pdf,image/png,image/jpeg,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.presentationml.presentation}") private String tipos;
  public ArchivosAcademicos(JdbcTemplate db,UsuarioActual usuario,AlmacenamientoObjetos objetos,MaterialesController materiales,AvisosTarea avisos) { this.db=db;this.usuario=usuario;this.objetos=objetos;this.materiales=materiales;this.avisos=avisos; }
  private String extension(String nombre,String tipo,long tamano) {
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
  private String extensionEntrega(String nombre,String tipo,long tamano) {
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
  public Map<String,Object> iniciarTarea(UUID tarea,String nombre,String tipo,long tamano,UUID sustituye,Authentication a) {
    var t=db.queryForMap("SELECT curso_id,estado FROM tareas WHERE id=?",tarea);
    usuario.docente(a,(UUID)t.get("curso_id"));
    if(!List.of("FALTA_COMPLETAR","PUBLICADA").contains(t.get("estado"))) throw new IllegalArgumentException("Tarea no editable");
    if(db.queryForObject("SELECT count(*) FROM entregas WHERE tarea_id=?",Long.class,tarea)>0) throw new IllegalArgumentException("Primero crea una nueva versión de la tarea; ya hay entregas");
    return iniciar("archivos_tarea","tarea_id",tarea,"tareas",nombre,tipo,tamano,sustituye,a);
  }
  public Map<String,Object> iniciarMaterial(UUID material,String nombre,String tipo,long tamano,UUID sustituye,Authentication a) {
    materiales.acceso(material,a,true);
    return iniciar("archivos_material","material_id",material,"materiales",nombre,tipo,tamano,sustituye,a);
  }
  public Map<String,Object> iniciarEntrega(UUID entrega,String nombre,String tipo,long tamano,Authentication a) {
    throw new IllegalArgumentException("Una entrega confirmada no admite archivos; usa su preparación");
  }
  @Transactional public Map<String,Object> iniciarPreparacion(UUID preparacion,String nombre,String tipo,long tamano,UUID sustituye,Authentication a) {
    var p=db.queryForMap("SELECT p.*,t.curso_id,t.estado AS estado_tarea,t.version AS version_actual FROM preparaciones_entrega p JOIN tareas t ON t.id=p.tarea_id WHERE p.id=? FOR UPDATE OF p",preparacion);
    usuario.estudianteCurso(a,(UUID)p.get("curso_id"));
    if(!usuario.referencia(a).equals(p.get("estudiante_erp_id"))) throw new AccessDeniedException("Preparación ajena");
    if(!"ACTIVA".equals(p.get("estado"))||!"PUBLICADA".equals(p.get("estado_tarea"))||
       ((Number)p.get("tarea_version")).intValue()!=((Number)p.get("version_actual")).intValue()||
       db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,p.get("tarea_id"))>0)
      throw new IllegalArgumentException("La preparación no admite archivos nuevos");
    return iniciar("archivos_entrega","preparacion_id",preparacion,"preparaciones",nombre,tipo,tamano,sustituye,a);
  }
  private Map<String,Object> iniciar(String tabla,String columna,UUID padre,String prefijo,String nombre,String tipo,long tamano,UUID sustituye,Authentication a) {
    boolean preparacion="preparacion_id".equals(columna);
    String ext=preparacion?extensionEntrega(nombre,tipo,tamano):extension(nombre,tipo,tamano);
    if(preparacion) {
      long sustituido=0,descontar=0;
      if(sustituye!=null) {
        var anterior=db.queryForList("SELECT tamano FROM archivos_entrega WHERE id=? AND preparacion_id=? AND activo AND estado='DISPONIBLE'",sustituye,padre);
        if(anterior.isEmpty()) throw new IllegalArgumentException("Archivo a reemplazar inválido");
        sustituido=((Number)anterior.getFirst().get("tamano")).longValue(); descontar=1;
      }
      var uso=db.queryForMap("SELECT count(*) AS cantidad,COALESCE(sum(tamano),0) AS bytes FROM archivos_entrega WHERE preparacion_id=? AND activo AND estado IN ('INICIADO','DISPONIBLE')",padre);
      if(((Number)uso.get("cantidad")).longValue()-descontar+1>4 || ((Number)uso.get("bytes")).longValue()-sustituido+tamano>100L*1024*1024)
        throw new IllegalArgumentException("Máximo cuatro archivos y 100 MB en total");
    } else if(!"archivos_entrega".equals(tabla)) {
      long sustituido=0;
      if(sustituye!=null) {
        var anterior=db.queryForList("SELECT tamano FROM "+tabla+" WHERE id=? AND "+columna+"=? AND estado='DISPONIBLE' AND activo",sustituye,padre);
        if(anterior.isEmpty()) throw new IllegalArgumentException("Archivo a sustituir inválido");
        sustituido=((Number)anterior.getFirst().get("tamano")).longValue();
      }
      long usado=db.queryForObject("SELECT COALESCE(sum(tamano),0) FROM "+tabla+" WHERE "+columna+"=? AND estado IN ('INICIADO','DISPONIBLE') AND activo",Long.class,padre);
      if(usado-sustituido+tamano>capacidadMb*1024*1024) throw new IllegalArgumentException("Capacidad de adjuntos superada");
    } else if(sustituye!=null) throw new IllegalArgumentException("Sustitución no disponible para entregas");
    UUID id=UUID.randomUUID(); String clave=prefijo+"/"+padre+"/"+id+"."+ext;
    String upload=objetos.iniciar(clave,tipo);
    if(preparacion) db.update("INSERT INTO archivos_entrega(id,preparacion_id,nombre,clave_objeto,tamano,tipo,estado,upload_id,sustituye_id) VALUES (?,?,?,?,?,?,'INICIADO',?,?)",id,padre,nombre,clave,tamano,tipo,upload,sustituye);
    else if("archivos_entrega".equals(tabla)) db.update("INSERT INTO "+tabla+"(id,"+columna+",nombre,clave_objeto,tamano,tipo,estado,upload_id) VALUES (?,?,?,?,?,?,'INICIADO',?)",id,padre,nombre,clave,tamano,tipo,upload);
    else db.update("INSERT INTO "+tabla+"(id,"+columna+",nombre,clave_objeto,tamano,tipo,estado,upload_id,sustituye_id) VALUES (?,?,?,?,?,?,'INICIADO',?,?)",id,padre,nombre,clave,tamano,tipo,upload,sustituye);
    auditar(a,"ARCHIVO_INICIADO",id);
    return Map.of("id",id,"tamanoParte",8*1024*1024);
  }
  private Map<String,Object> archivo(UUID id) {
    return db.queryForMap("""
      SELECT 'TAREA' AS origen,a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,t.curso_id,t.estado AS estado_tarea,NULL::uuid AS estudiante_erp_id,a.activo,a.tarea_id AS padre_id,a.sustituye_id
      FROM archivos_tarea a JOIN tareas t ON t.id=a.tarea_id WHERE a.id=?
      UNION ALL
      SELECT 'ENTREGA',a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,t.curso_id,t.estado,e.estudiante_erp_id,NULL::boolean,a.entrega_id,NULL::uuid
      FROM archivos_entrega a JOIN entregas e ON e.id=a.entrega_id JOIN tareas t ON t.id=e.tarea_id WHERE a.id=?
      UNION ALL
      SELECT 'MATERIAL',a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,m.curso_id,m.estado,NULL::uuid,a.activo,a.material_id,a.sustituye_id
      FROM archivos_material a JOIN materiales_curso m ON m.id=a.material_id WHERE a.id=?
      UNION ALL
      SELECT 'PREPARACION',a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,t.curso_id,t.estado,p.estudiante_erp_id,a.activo,p.id,a.sustituye_id
      FROM archivos_entrega a JOIN preparaciones_entrega p ON p.id=a.preparacion_id JOIN tareas t ON t.id=p.tarea_id WHERE a.id=?
      """,id,id,id,id);
  }
  private Map<String,Object> autorizado(UUID id,Authentication a,boolean escritura) {
    var f=archivo(id); UUID curso=(UUID)f.get("curso_id");
    if(escritura) {
      if("TAREA".equals(f.get("origen"))) {
        usuario.docente(a,curso);
        if(!List.of("FALTA_COMPLETAR","PUBLICADA").contains(f.get("estado_tarea"))) throw new IllegalArgumentException("Tarea no editable");
        if(db.queryForObject("SELECT count(*) FROM entregas WHERE tarea_id=?",Long.class,f.get("padre_id"))>0) throw new IllegalArgumentException("Primero crea una nueva versión de la tarea");
      }
      else if("MATERIAL".equals(f.get("origen"))) materiales.acceso((UUID)f.get("padre_id"),a,true);
      else if("PREPARACION".equals(f.get("origen"))) {
        var p=db.queryForMap("SELECT p.estado,p.tarea_id,p.tarea_version,t.version AS version_actual FROM preparaciones_entrega p JOIN tareas t ON t.id=p.tarea_id WHERE p.id=? FOR UPDATE OF p",f.get("padre_id"));
        usuario.estudianteCurso(a,curso);
        if(!usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno");
        if(!"ACTIVA".equals(p.get("estado"))||!"PUBLICADA".equals(f.get("estado_tarea"))||
           ((Number)p.get("tarea_version")).intValue()!=((Number)p.get("version_actual")).intValue()||
           db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,p.get("tarea_id"))>0)
          throw new IllegalArgumentException("Preparación no editable");
        if(!Boolean.TRUE.equals(f.get("activo"))) throw new IllegalArgumentException("Archivo retirado");
      }
      else if("ENTREGA".equals(f.get("origen"))) throw new IllegalArgumentException("Entrega confirmada no editable");
      else { usuario.estudianteCurso(a,curso); if(!usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno"); }
      if(!"INICIADO".equals(f.get("estado"))) throw new IllegalArgumentException("Carga no iniciada");
    } else {
      if("MATERIAL".equals(f.get("origen"))) materiales.acceso((UUID)f.get("padre_id"),a,false);
      else usuario.leerCurso(a,curso);
      if(("ENTREGA".equals(f.get("origen"))||"PREPARACION".equals(f.get("origen"))) && "ESTUDIANTE".equals(usuario.tipo(a)) && !usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno");
      if("PREPARACION".equals(f.get("origen")) && !"ESTUDIANTE".equals(usuario.tipo(a))) throw new AccessDeniedException("Preparación privada");
      if("PREPARACION".equals(f.get("origen")) && !Boolean.TRUE.equals(f.get("activo"))) throw new AccessDeniedException("Archivo retirado");
      if(!"ENTREGA".equals(f.get("origen")) && !Boolean.TRUE.equals(f.get("activo"))) throw new AccessDeniedException("Archivo retirado");
      if("TAREA".equals(f.get("origen")) && "ESTUDIANTE".equals(usuario.tipo(a)) && "FALTA_COMPLETAR".equals(f.get("estado_tarea"))) throw new AccessDeniedException("Material no publicado");
      if(!"DISPONIBLE".equals(f.get("estado"))) throw new AccessDeniedException("Archivo no disponible");
    }
    return f;
  }
  public URI parte(UUID id,int numero,Authentication a) {
    var f=autorizado(id,a,true);
    long maxPartes=(((Number)f.get("tamano")).longValue()+8*1024*1024-1)/(8*1024*1024);
    if(numero<1||numero>maxPartes) throw new IllegalArgumentException("Número de parte inválido");
    return objetos.autorizarParte((String)f.get("clave_objeto"),(String)f.get("upload_id"),numero);
  }
  @Transactional(noRollbackFor=IllegalArgumentException.class) public void completar(UUID id,List<Map<String,Object>> partes,Authentication a) {
    var f=autorizado(id,a,true);
    long maxPartes=(((Number)f.get("tamano")).longValue()+8*1024*1024-1)/(8*1024*1024);
    if(partes==null||partes.size()!=maxPartes) throw new IllegalArgumentException("Cantidad de partes inválida");
    for(int i=0;i<partes.size();i++) if(!Integer.valueOf(i+1).equals(partes.get(i).get("numero"))||!(partes.get(i).get("etag") instanceof String)) throw new IllegalArgumentException("Partes inválidas");
    String clave=(String)f.get("clave_objeto");
    boolean yaCompleto=false;
    try { yaCompleto=objetos.tamano(clave)==((Number)f.get("tamano")).longValue(); }
    catch(Exception ignored) { /* Todavía no existe el objeto completo. */ }
    if(!yaCompleto) objetos.completar(clave,(String)f.get("upload_id"),partes);
    byte[] cabecera=objetos.cabecera(clave);
    boolean firma= switch((String)f.get("tipo")) {
      case "application/pdf" -> prefijo(cabecera,37,80,68,70);
      case "image/png" -> prefijo(cabecera,137,80,78,71,13,10,26,10);
      case "image/jpeg" -> prefijo(cabecera,255,216,255);
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> prefijo(cabecera,80,75,3,4);
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> prefijo(cabecera,80,75,3,4);
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> prefijo(cabecera,80,75,3,4);
      case "text/plain" -> true;
      default -> false;
    };
    if(firma&&"PREPARACION".equals(f.get("origen"))) firma=contenidoEntregaValido(clave,(String)f.get("tipo"));
    if(objetos.tamano(clave)!=((Number)f.get("tamano")).longValue()||!firma) {
      objetos.eliminar(clave);
      db.update("UPDATE "+tabla(f)+" SET estado='RECHAZADO',activo=FALSE,upload_id=NULL WHERE id=?",id);
      throw new IllegalArgumentException("Archivo incompleto o tipo real distinto");
    }
    if(f.get("sustituye_id")!=null && db.update("UPDATE "+tabla(f)+" SET activo=FALSE WHERE id=? AND activo AND estado='DISPONIBLE'",f.get("sustituye_id"))!=1)
      throw new IllegalArgumentException("El archivo a sustituir ya no está disponible");
    db.update("UPDATE "+tabla(f)+" SET estado='DISPONIBLE',upload_id=NULL WHERE id=?",id);
    if("TAREA".equals(f.get("origen")) && "PUBLICADA".equals(f.get("estado_tarea"))) cambioAdjuntoTarea((UUID)f.get("padre_id"));
    auditar(a,"ARCHIVO_PUBLICADO",id);
  }
  private boolean prefijo(byte[] b,int... bytes) { if(b.length<bytes.length) return false; for(int i=0;i<bytes.length;i++) if((b[i]&255)!=bytes[i]) return false; return true; }
  private boolean contenidoEntregaValido(String clave,String tipo) {
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
  private String tabla(Map<String,Object> f) { return switch((String)f.get("origen")) { case "TAREA" -> "archivos_tarea"; case "MATERIAL" -> "archivos_material"; default -> "archivos_entrega"; }; }
  public URI enlace(UUID id,Authentication a) { return objetos.leer((String)autorizado(id,a,false).get("clave_objeto"),Duration.ofMinutes(5)); }
  public List<Map<String,Object>> tarea(UUID tarea,Authentication a) {
    var t=db.queryForMap("SELECT curso_id,estado FROM tareas WHERE id=?",tarea); usuario.leerCurso(a,(UUID)t.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&"FALTA_COMPLETAR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_tarea WHERE tarea_id=? AND estado='DISPONIBLE' AND activo ORDER BY creado_en DESC",tarea);
  }
  public List<Map<String,Object>> material(UUID material,Authentication a) {
    materiales.acceso(material,a,false);
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_material WHERE material_id=? AND estado='DISPONIBLE' AND activo ORDER BY creado_en",material);
  }
  @Transactional public void retirar(UUID id,Authentication a) {
    var f=archivo(id);
    if("PREPARACION".equals(f.get("origen"))) {
      var p=db.queryForMap("SELECT p.estado,t.curso_id,t.estado AS estado_tarea FROM preparaciones_entrega p JOIN tareas t ON t.id=p.tarea_id WHERE p.id=? FOR UPDATE OF p",f.get("padre_id"));
      usuario.estudianteCurso(a,(UUID)p.get("curso_id"));
      if(!usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno");
      if(!"ACTIVA".equals(p.get("estado"))) throw new IllegalArgumentException("Preparación ya confirmada");
      if(db.update("UPDATE archivos_entrega SET activo=FALSE WHERE id=? AND activo",id)>0) {
        try {
          if("INICIADO".equals(f.get("estado"))) objetos.abortar((String)f.get("clave_objeto"),(String)f.get("upload_id"));
          else if("DISPONIBLE".equals(f.get("estado"))) objetos.eliminar((String)f.get("clave_objeto"));
        } catch(Exception ignored) { /* El archivo retirado ya no puede confirmarse; la limpieza S3 es recuperable. */ }
        auditar(a,"ARCHIVO_RETIRADO",id);
      }
      return;
    }
    if("ENTREGA".equals(f.get("origen"))) throw new IllegalArgumentException("No se retiran archivos de entregas");
    if("MATERIAL".equals(f.get("origen"))) {
      materiales.acceso((UUID)f.get("padre_id"),a,true);
      if("PUBLICADO".equals(f.get("estado_tarea")) &&
         db.queryForObject("SELECT enlace FROM materiales_curso WHERE id=?",String.class,f.get("padre_id"))==null &&
         db.queryForObject("SELECT count(*) FROM archivos_material WHERE material_id=? AND id<>? AND estado='DISPONIBLE' AND activo",Long.class,f.get("padre_id"),id)==0)
        throw new IllegalArgumentException("Añade otro archivo o enlace antes de retirar el último");
    }
    else {
      usuario.docente(a,(UUID)f.get("curso_id"));
      if(!List.of("FALTA_COMPLETAR","PUBLICADA").contains(f.get("estado_tarea"))) throw new IllegalArgumentException("Tarea no editable");
      if(db.queryForObject("SELECT count(*) FROM entregas WHERE tarea_id=?",Long.class,f.get("padre_id"))>0) throw new IllegalArgumentException("Primero crea una nueva versión de la tarea");
    }
    if(db.update("UPDATE "+tabla(f)+" SET activo=FALSE WHERE id=? AND activo",id)>0) {
      if("TAREA".equals(f.get("origen")) && "PUBLICADA".equals(f.get("estado_tarea"))) cambioAdjuntoTarea((UUID)f.get("padre_id"));
      auditar(a,"ARCHIVO_RETIRADO",id);
    }
  }
  private void cambioAdjuntoTarea(UUID tarea) {
    var t=db.queryForMap("UPDATE tareas SET version=version+1,cambio_en=now() WHERE id=? RETURNING curso_id,titulo,version",tarea);
    avisos.estudiantes(tarea,(UUID)t.get("curso_id"),"ACTUALIZADA",(String)t.get("titulo"),((Number)t.get("version")).intValue());
  }
  public List<Map<String,Object>> entrega(UUID entrega,Authentication a) {
    var e=db.queryForMap("SELECT e.estudiante_erp_id,t.curso_id FROM entregas e JOIN tareas t ON t.id=e.tarea_id WHERE e.id=?",entrega);
    usuario.leerCurso(a,(UUID)e.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&!usuario.referencia(a).equals(e.get("estudiante_erp_id"))) throw new AccessDeniedException("Entrega ajena");
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_entrega WHERE entrega_id=? AND estado='DISPONIBLE' AND activo ORDER BY creado_en",entrega);
  }
  public List<Map<String,Object>> preparacion(UUID preparacion,Authentication a) {
    var p=db.queryForMap("SELECT p.estudiante_erp_id,t.curso_id FROM preparaciones_entrega p JOIN tareas t ON t.id=p.tarea_id WHERE p.id=?",preparacion);
    usuario.estudianteCurso(a,(UUID)p.get("curso_id"));
    if(!usuario.referencia(a).equals(p.get("estudiante_erp_id"))) throw new AccessDeniedException("Preparación ajena");
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_entrega WHERE preparacion_id=? AND estado='DISPONIBLE' AND activo ORDER BY creado_en",preparacion);
  }
  @Scheduled(initialDelay=3600000,fixedDelay=3600000)
  @Transactional public void limpiarCargasAbandonadas() {
    var antiguas=db.queryForList("SELECT id,clave_objeto,upload_id,estado FROM archivos_entrega WHERE preparacion_id IS NOT NULL AND creado_en<now()-interval '1 day' AND (estado='INICIADO' OR (NOT activo AND estado='DISPONIBLE')) FOR UPDATE SKIP LOCKED");
    for(var archivo:antiguas) {
      try {
        if("INICIADO".equals(archivo.get("estado"))) objetos.abortar((String)archivo.get("clave_objeto"),(String)archivo.get("upload_id"));
        else objetos.eliminar((String)archivo.get("clave_objeto"));
      }
      catch(Exception ignored) { continue; }
      db.update("UPDATE archivos_entrega SET estado='RECHAZADO',activo=FALSE,upload_id=NULL WHERE id=?",archivo.get("id"));
    }
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
