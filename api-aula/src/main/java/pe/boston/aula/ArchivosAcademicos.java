package pe.boston.aula;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service public class ArchivosAcademicos {
  private final JdbcTemplate db;
  private final UsuarioActual usuario;
  private final AlmacenamientoObjetos objetos;
  public ArchivosAcademicos(JdbcTemplate db,UsuarioActual usuario,AlmacenamientoObjetos objetos) { this.db=db;this.usuario=usuario;this.objetos=objetos; }
  private String extension(String nombre,String tipo,long tamano) {
    if(nombre==null||nombre.isBlank()||nombre.length()>200||nombre.contains("/")||nombre.contains("\\")||tamano<1||tamano>100L*1024*1024) throw new IllegalArgumentException("Nombre o tamaño de archivo inválido (máximo 100 MB)");
    String n=nombre.toLowerCase(java.util.Locale.ROOT);
    if(n.endsWith(".pdf")&&"application/pdf".equals(tipo)) return "pdf";
    if(n.endsWith(".png")&&"image/png".equals(tipo)) return "png";
    if((n.endsWith(".jpg")||n.endsWith(".jpeg"))&&"image/jpeg".equals(tipo)) return "jpg";
    if(n.endsWith(".docx")&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(tipo)) return "docx";
    throw new IllegalArgumentException("Solo PDF, PNG, JPG o DOCX");
  }
  public Map<String,Object> iniciarTarea(UUID tarea,String nombre,String tipo,long tamano,Authentication a) {
    var t=db.queryForMap("SELECT curso_id,estado FROM tareas WHERE id=?",tarea);
    usuario.docente(a,(UUID)t.get("curso_id"));
    if(!"BORRADOR".equals(t.get("estado"))) throw new IllegalArgumentException("Adjunte materiales antes de publicar");
    return iniciar("archivos_tarea","tarea_id",tarea,"tareas",nombre,tipo,tamano,a);
  }
  public Map<String,Object> iniciarEntrega(UUID entrega,String nombre,String tipo,long tamano,Authentication a) {
    var e=db.queryForMap("SELECT e.estudiante_erp_id,e.tarea_id,t.curso_id,t.estado FROM entregas e JOIN tareas t ON t.id=e.tarea_id WHERE e.id=?",entrega);
    usuario.estudianteCurso(a,(UUID)e.get("curso_id"));
    if(!usuario.referencia(a).equals(e.get("estudiante_erp_id"))) throw new AccessDeniedException("Entrega ajena");
    if(!"PUBLICADA".equals(e.get("estado"))) throw new IllegalArgumentException("Tarea cerrada");
    if(db.queryForObject("SELECT count(*) FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=?",Long.class,e.get("tarea_id"),e.get("estudiante_erp_id"))>0) throw new IllegalArgumentException("Entrega ya calificada");
    return iniciar("archivos_entrega","entrega_id",entrega,"entregas",nombre,tipo,tamano,a);
  }
  private Map<String,Object> iniciar(String tabla,String columna,UUID padre,String prefijo,String nombre,String tipo,long tamano,Authentication a) {
    String ext=extension(nombre,tipo,tamano);
    UUID id=UUID.randomUUID(); String clave=prefijo+"/"+padre+"/"+id+"."+ext;
    String upload=objetos.iniciar(clave,tipo);
    db.update("INSERT INTO "+tabla+"(id,"+columna+",nombre,clave_objeto,tamano,tipo,estado,upload_id) VALUES (?,?,?,?,?,?,'INICIADO',?)",id,padre,nombre,clave,tamano,tipo,upload);
    auditar(a,"ARCHIVO_INICIADO",id);
    return Map.of("id",id,"tamanoParte",8*1024*1024);
  }
  private Map<String,Object> archivo(UUID id) {
    return db.queryForMap("""
      SELECT 'TAREA' AS origen,a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,t.curso_id,t.estado AS estado_tarea,NULL::uuid AS estudiante_erp_id
      FROM archivos_tarea a JOIN tareas t ON t.id=a.tarea_id WHERE a.id=?
      UNION ALL
      SELECT 'ENTREGA',a.id,a.nombre,a.clave_objeto,a.tamano,a.tipo,a.estado,a.upload_id,t.curso_id,t.estado,e.estudiante_erp_id
      FROM archivos_entrega a JOIN entregas e ON e.id=a.entrega_id JOIN tareas t ON t.id=e.tarea_id WHERE a.id=?
      """,id,id);
  }
  private Map<String,Object> autorizado(UUID id,Authentication a,boolean escritura) {
    var f=archivo(id); UUID curso=(UUID)f.get("curso_id");
    if(escritura) {
      if("TAREA".equals(f.get("origen"))) usuario.docente(a,curso);
      else { usuario.estudianteCurso(a,curso); if(!usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno"); }
      if(!"INICIADO".equals(f.get("estado"))) throw new IllegalArgumentException("Carga no iniciada");
    } else {
      usuario.leerCurso(a,curso);
      if("ENTREGA".equals(f.get("origen")) && "ESTUDIANTE".equals(usuario.tipo(a)) && !usuario.referencia(a).equals(f.get("estudiante_erp_id"))) throw new AccessDeniedException("Archivo ajeno");
      if("TAREA".equals(f.get("origen")) && "ESTUDIANTE".equals(usuario.tipo(a)) && "BORRADOR".equals(f.get("estado_tarea"))) throw new AccessDeniedException("Material no publicado");
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
  public void completar(UUID id,List<Map<String,Object>> partes,Authentication a) {
    var f=autorizado(id,a,true);
    long maxPartes=(((Number)f.get("tamano")).longValue()+8*1024*1024-1)/(8*1024*1024);
    if(partes==null||partes.size()!=maxPartes) throw new IllegalArgumentException("Cantidad de partes inválida");
    for(int i=0;i<partes.size();i++) if(!Integer.valueOf(i+1).equals(partes.get(i).get("numero"))||!(partes.get(i).get("etag") instanceof String)) throw new IllegalArgumentException("Partes inválidas");
    String clave=(String)f.get("clave_objeto");
    objetos.completar(clave,(String)f.get("upload_id"),partes);
    byte[] cabecera=objetos.cabecera(clave);
    boolean firma= switch((String)f.get("tipo")) {
      case "application/pdf" -> prefijo(cabecera,37,80,68,70);
      case "image/png" -> prefijo(cabecera,137,80,78,71,13,10,26,10);
      case "image/jpeg" -> prefijo(cabecera,255,216,255);
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> prefijo(cabecera,80,75,3,4);
      default -> false;
    };
    if(objetos.tamano(clave)!=((Number)f.get("tamano")).longValue()||!firma) {
      objetos.eliminar(clave);
      db.update("UPDATE "+tabla(f)+" SET estado='RECHAZADO',upload_id=NULL WHERE id=?",id);
      throw new IllegalArgumentException("Archivo incompleto o tipo real distinto");
    }
    db.update("UPDATE "+tabla(f)+" SET estado='DISPONIBLE',upload_id=NULL WHERE id=?",id);
    auditar(a,"ARCHIVO_PUBLICADO",id);
  }
  private boolean prefijo(byte[] b,int... bytes) { if(b.length<bytes.length) return false; for(int i=0;i<bytes.length;i++) if((b[i]&255)!=bytes[i]) return false; return true; }
  private String tabla(Map<String,Object> f) { return "TAREA".equals(f.get("origen"))?"archivos_tarea":"archivos_entrega"; }
  public URI enlace(UUID id,Authentication a) { return objetos.leer((String)autorizado(id,a,false).get("clave_objeto"),Duration.ofMinutes(5)); }
  public List<Map<String,Object>> tarea(UUID tarea,Authentication a) {
    var t=db.queryForMap("SELECT curso_id,estado FROM tareas WHERE id=?",tarea); usuario.leerCurso(a,(UUID)t.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&"BORRADOR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_tarea WHERE tarea_id=? AND estado='DISPONIBLE' ORDER BY creado_en",tarea);
  }
  public List<Map<String,Object>> entrega(UUID entrega,Authentication a) {
    var e=db.queryForMap("SELECT e.estudiante_erp_id,t.curso_id FROM entregas e JOIN tareas t ON t.id=e.tarea_id WHERE e.id=?",entrega);
    usuario.leerCurso(a,(UUID)e.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&!usuario.referencia(a).equals(e.get("estudiante_erp_id"))) throw new AccessDeniedException("Entrega ajena");
    return db.queryForList("SELECT id,nombre,tamano,tipo,creado_en FROM archivos_entrega WHERE entrega_id=? AND estado='DISPONIBLE' ORDER BY creado_en",entrega);
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
