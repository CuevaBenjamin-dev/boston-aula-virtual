package pe.boston.aula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class GrabacionesYoutube {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final Clases clases; private final ErpCliente erp;
  public GrabacionesYoutube(JdbcTemplate db,UsuarioActual usuario,Clases clases,ErpCliente erp) { this.db=db;this.usuario=usuario;this.clases=clases;this.erp=erp; }
  private static final String BASE="""
    SELECT g.id,g.clase_id,g.orden,g.youtube_id,g.url,g.estado,g.creado_en,g.actualizado_en,
      cl.tema,cl.inicio,cl.fin,cl.docente_erp_id,cl.estado AS estado_clase,
      c.id AS curso_id,c.asignatura,c.grado,c.seccion,c.periodo
    FROM grabaciones_youtube g JOIN clases_academicas cl ON cl.id=g.clase_id JOIN cursos c ON c.id=cl.curso_id
    """;
  public List<Map<String,Object>> listarClase(UUID clase,Authentication a) {
    clases.comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso);
    String condicion="ESTUDIANTE".equals(usuario.tipo(a))?" AND g.estado='PUBLICADA'":"";
    return conDocentes(db.queryForList(BASE+" WHERE g.clase_id=?"+condicion+" ORDER BY g.orden",clase));
  }
  public List<Map<String,Object>> listar(Authentication a) {
    clases.comprobar(); String tipo=usuario.tipo(a);
    List<Map<String,Object>> filas=switch(tipo) {
      case "ESTUDIANTE" -> db.queryForList(BASE+" JOIN inscripciones_curso i ON i.curso_id=c.id WHERE i.estudiante_erp_id=? AND i.activo AND g.estado='PUBLICADA' ORDER BY cl.inicio DESC,g.orden",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(BASE+" JOIN docentes_curso d ON d.curso_id=c.id WHERE d.docente_erp_id=? AND d.activo ORDER BY cl.inicio DESC,g.orden",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(BASE+" JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp ORDER BY cl.inicio DESC,g.orden",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin acceso académico");
    };
    return conDocentes(filas);
  }
  public List<Map<String,Object>> administracion(Authentication a) {
    clases.comprobar(); usuario.administrador(a); return conDocentes(db.queryForList(BASE+" ORDER BY cl.inicio DESC,g.orden LIMIT 200"));
  }
  private List<Map<String,Object>> conDocentes(List<Map<String,Object>> filas) {
    if(filas.isEmpty()) return filas;
    Map<String,String> nombres=new HashMap<>();
    try {
      for(var d:erp.get("/api/erp/docentes/resumen")) nombres.put(d.get("id").asText(),d.get("nombre").asText());
    } catch(Exception e) { /* La consulta de grabaciones sigue disponible si ERP está temporalmente fuera. */ }
    for(var fila:filas) {
      Object id=fila.get("docente_erp_id");
      fila.put("docente",id==null?"Docente no identificado":nombres.getOrDefault(id.toString(),"Docente no identificado"));
    }
    return filas;
  }
  @Transactional public UUID registrar(UUID clase,int orden,String url,boolean publicar,Authentication a) {
    clases.comprobar();
    var datos=db.queryForMap("SELECT curso_id,estado FROM clases_academicas WHERE id=?",clase);
    usuario.docente(a,(UUID)datos.get("curso_id"));
    if(orden<1) throw new IllegalArgumentException("Número de parte inválido");
    if(!"PROGRAMADA".equals(datos.get("estado"))) throw new IllegalStateException("La clase está cancelada");
    var video=YoutubeUrl.normalizar(url); UUID id=UUID.randomUUID();
    try { db.update("INSERT INTO grabaciones_youtube(id,clase_id,orden,youtube_id,url,estado,creado_por) VALUES (?,?,?,?,?,?,?)",id,clase,orden,video.id(),video.url(),publicar?"PUBLICADA":"OCULTA",usuario.cuenta(a)); }
    catch(DuplicateKeyException e) { throw new IllegalArgumentException("Ya existe una grabación para esa parte de la clase"); }
    auditar(id,"YOUTUBE_REGISTRADO",a); return id;
  }
  @Transactional public void editar(UUID id,String url,Authentication a) {
    clases.comprobar(); var g=obtener(id); escritura(a,(UUID)g.get("curso_id"));
    var video=YoutubeUrl.normalizar(url);
    db.update("UPDATE grabaciones_youtube SET youtube_id=?,url=?,actualizado_en=now() WHERE id=?",video.id(),video.url(),id);
    auditar(id,"YOUTUBE_EDITADO",a);
  }
  @Transactional public void estado(UUID id,boolean publicar,Authentication a) {
    clases.comprobar(); var g=obtener(id); escritura(a,(UUID)g.get("curso_id"));
    db.update("UPDATE grabaciones_youtube SET estado=?,actualizado_en=now() WHERE id=?",publicar?"PUBLICADA":"OCULTA",id);
    auditar(id,publicar?"YOUTUBE_PUBLICADO":"YOUTUBE_OCULTO",a);
  }
  private Map<String,Object> obtener(UUID id) { return db.queryForMap("SELECT g.id,cl.curso_id FROM grabaciones_youtube g JOIN clases_academicas cl ON cl.id=g.clase_id WHERE g.id=?",id); }
  private void escritura(Authentication a,UUID curso) {
    if("ADMINISTRADOR".equals(usuario.tipo(a))) usuario.administrador(a);
    else usuario.docente(a,curso);
  }
  private void auditar(UUID id,String accion,Authentication a) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
