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

@Service public class Grabaciones {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final Clases clases; private final AlmacenamientoObjetos objetos;
  public Grabaciones(JdbcTemplate db,UsuarioActual usuario,Clases clases,AlmacenamientoObjetos objetos) { this.db=db;this.usuario=usuario;this.clases=clases;this.objetos=objetos; }
  public Map<String,Object> iniciar(UUID clase,String nombre,int orden,long tamano,Authentication a) {
    clases.comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.docente(a,curso);
    if(orden<1||tamano<1||tamano>5L*1024*1024*1024||nombre==null||nombre.length()>200||nombre.contains("/")||nombre.contains("\\")||nombre.chars().anyMatch(Character::isISOControl)||!nombre.toLowerCase().endsWith(".mp4")) throw new IllegalArgumentException("MP4 inválido o supera 5 GB");
    var pendientes=db.queryForList("SELECT id,nombre,tamano,estado FROM grabaciones WHERE clase_id=? AND orden=? AND estado<>'ELIMINADA'",clase,orden);
    if(!pendientes.isEmpty()) {
      var anterior=pendientes.getFirst();
      if("INICIADA".equals(anterior.get("estado"))&&nombre.equals(anterior.get("nombre"))&&tamano==((Number)anterior.get("tamano")).longValue()) return Map.of("id",anterior.get("id"),"tamanoParte",8*1024*1024);
      throw new IllegalArgumentException("Ya existe una parte con ese orden");
    }
    UUID id=UUID.randomUUID(); String clave="grabaciones/"+curso+"/"+id+".mp4";
    String uploadId=objetos.iniciar(clave,"video/mp4");
    db.update("INSERT INTO grabaciones(id,clase_id,curso_id,orden,nombre,clave_objeto,tamano,estado,upload_id) VALUES (?,?,?,?,?,?,?,'INICIADA',?)",id,clase,curso,orden,nombre,clave,tamano,uploadId);
    return Map.of("id",id,"tamanoParte",8*1024*1024);
  }
  private Map<String,Object> obtener(UUID id) { return db.queryForMap("SELECT * FROM grabaciones WHERE id=?",id); }
  public URI parte(UUID id,int numero,Authentication a) {
    var g=obtener(id); clases.comprobar(); usuario.docente(a,(UUID)g.get("curso_id"));
    if(!"INICIADA".equals(g.get("estado"))||numero<1||numero>10000) throw new IllegalArgumentException("Carga inválida");
    return objetos.autorizarParte((String)g.get("clave_objeto"),(String)g.get("upload_id"),numero);
  }
  public void completar(UUID id,List<Map<String,Object>> partes,Authentication a) {
    var g=obtener(id); clases.comprobar(); usuario.docente(a,(UUID)g.get("curso_id"));
    long esperadas=(((Number)g.get("tamano")).longValue()+8*1024*1024-1)/(8*1024*1024);
    if(!"INICIADA".equals(g.get("estado"))||partes==null||partes.size()!=esperadas) throw new IllegalArgumentException("Partes inválidas");
    for(int i=0;i<partes.size();i++) if(!Integer.valueOf(i+1).equals(partes.get(i).get("numero"))||!(partes.get(i).get("etag") instanceof String)) throw new IllegalArgumentException("Partes inválidas");
    objetos.completar((String)g.get("clave_objeto"),(String)g.get("upload_id"),partes);
    long real=objetos.tamano((String)g.get("clave_objeto"));
    byte[] cabecera=objetos.cabecera((String)g.get("clave_objeto"));
    if(real!=((Number)g.get("tamano")).longValue()||cabecera.length<8||cabecera[4]!='f'||cabecera[5]!='t'||cabecera[6]!='y'||cabecera[7]!='p') {
      objetos.eliminar((String)g.get("clave_objeto")); db.update("UPDATE grabaciones SET estado='ELIMINADA',upload_id=NULL WHERE id=?",id);
      throw new IllegalArgumentException("MP4 incompleto o tipo real distinto");
    }
    db.update("UPDATE grabaciones SET estado='PUBLICADA',publicado_en=now(),upload_id=NULL WHERE id=?",id);
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"GRABACION_PUBLICADA",id);
  }
  public List<Map<String,Object>> listar(UUID clase,Authentication a) {
    clases.comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso);
    return db.queryForList("SELECT id,clase_id,curso_id,orden,nombre,tamano,publicado_en FROM grabaciones WHERE clase_id=? AND estado='PUBLICADA' ORDER BY orden",clase);
  }
  public URI enlace(UUID id,Authentication a) {
    clases.comprobar(); var g=obtener(id); usuario.leerCurso(a,(UUID)g.get("curso_id"));
    if(!"PUBLICADA".equals(g.get("estado"))) throw new AccessDeniedException("Grabación no publicada");
    return objetos.leer((String)g.get("clave_objeto"),Duration.ofMinutes(5));
  }
  public URI descarga(UUID id,Authentication a) {
    clases.comprobar(); var g=obtener(id); usuario.leerCurso(a,(UUID)g.get("curso_id"));
    if(!"PUBLICADA".equals(g.get("estado"))) throw new AccessDeniedException("Grabación no publicada");
    return objetos.descargar((String)g.get("clave_objeto"),(String)g.get("nombre"),Duration.ofMinutes(5));
  }
  public List<Map<String,Object>> vistaPrevia(String periodo,Authentication a) {
    usuario.administrador(a);
    return db.queryForList("SELECT g.id,g.nombre,g.tamano,g.publicado_en FROM grabaciones g JOIN cursos c ON c.id=g.curso_id WHERE c.periodo=? AND g.estado='PUBLICADA' ORDER BY g.creado_en",periodo);
  }
  public Map<String,Object> eliminarPeriodo(String periodo,int cantidadEsperada,Authentication a) {
    usuario.developerRequerido(a); var registros=db.queryForList("SELECT g.id,g.clave_objeto FROM grabaciones g JOIN cursos c ON c.id=g.curso_id WHERE c.periodo=? AND g.estado='PUBLICADA' ORDER BY g.creado_en",periodo);
    if(registros.size()!=cantidadEsperada) throw new IllegalStateException("La vista previa cambió; vuelva a confirmar");
    UUID lote=UUID.randomUUID(); db.update("INSERT INTO eliminaciones_grabaciones(id,periodo,solicitante) VALUES (?,?,?)",lote,periodo,usuario.oidc(a).getSubject());
    int eliminadas=0; var errores=new java.util.ArrayList<String>();
    for(var g:registros) {
      try { objetos.eliminar((String)g.get("clave_objeto")); db.update("UPDATE grabaciones SET estado='ELIMINADA' WHERE id=?",g.get("id")); eliminadas++; }
      catch(Exception e) { errores.add(g.get("id")+": "+e.getMessage()); }
    }
    db.update("UPDATE eliminaciones_grabaciones SET finalizada_en=now(),resultado=? WHERE id=?","eliminadas="+eliminadas+", errores="+errores,lote);
    db.update("INSERT INTO registros_auditoria(id,actor,accion,detalle) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"GRABACIONES_ELIMINADAS","periodo="+periodo+", eliminadas="+eliminadas);
    return Map.of("lote",lote,"eliminadas",eliminadas,"errores",errores);
  }
}
