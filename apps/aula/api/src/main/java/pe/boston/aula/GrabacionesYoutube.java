package pe.boston.aula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class GrabacionesYoutube {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final Clases clases; private final ErpCliente erp;
  public GrabacionesYoutube(JdbcTemplate db,UsuarioActual usuario,Clases clases,ErpCliente erp) { this.db=db;this.usuario=usuario;this.clases=clases;this.erp=erp; }
  private static final String BASE="""
    SELECT g.id,g.clase_id,g.orden,g.titulo,g.url,g.estado,g.creado_en,g.actualizado_en,
      cl.tema,cl.inicio,cl.fin,cl.docente_erp_id,cl.estado AS estado_clase,
      c.id AS curso_id,c.asignatura,c.grado,c.seccion,c.periodo
    FROM grabaciones_youtube g JOIN clases_academicas cl ON cl.id=g.clase_id JOIN cursos c ON c.id=cl.curso_id
    """;
  public List<Map<String,Object>> listarClase(UUID clase,Authentication a) {
    clases.comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase);
    if("ADMINISTRADOR".equals(usuario.tipo(a))) usuario.administrador(a); else usuario.leerCurso(a,curso);
    String condicion="ESTUDIANTE".equals(usuario.tipo(a))?" AND g.estado='PUBLICADA'":"";
    return conDocentes(db.queryForList(BASE+" WHERE g.clase_id=?"+condicion+" ORDER BY g.orden",clase));
  }
  public List<Map<String,Object>> listar(Authentication a) {
    clases.comprobar(); String tipo=usuario.tipo(a);
    List<Map<String,Object>> filas=switch(tipo) {
      case "ESTUDIANTE" -> db.queryForList(BASE+" JOIN inscripciones_curso i ON i.curso_id=c.id WHERE i.estudiante_erp_id=? AND i.activo AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) AND g.estado='PUBLICADA' ORDER BY cl.inicio DESC,g.orden",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(BASE+" JOIN docentes_curso d ON d.curso_id=c.id WHERE d.docente_erp_id=? AND d.activo AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) AND g.estado<>'RETIRADA' ORDER BY cl.inicio DESC,g.orden",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(BASE+" JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) AND g.estado<>'RETIRADA' ORDER BY cl.inicio DESC,g.orden",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin acceso académico");
    };
    return conDocentes(filas);
  }
  public List<Map<String,Object>> administracion(Authentication a) {
    clases.comprobar(); usuario.administrador(a); return conDocentes(db.queryForList(BASE+" ORDER BY cl.inicio DESC,g.orden LIMIT 200"));
  }
  public List<Map<String,Object>> historial(UUID id,Authentication a) {
    clases.comprobar(); var g=obtener(id);
    if("ESTUDIANTE".equals(usuario.tipo(a))) throw new AccessDeniedException("Historial reservado al personal académico");
    if("ADMINISTRADOR".equals(usuario.tipo(a))) usuario.administrador(a); else usuario.leerCurso(a,(UUID)g.get("curso_id"));
    return db.queryForList("SELECT accion,titulo_anterior,titulo_nuevo,url_anterior,url_nueva,orden_anterior,orden_nuevo,estado_anterior,estado_nuevo,actor_cuenta,creado_en FROM historial_grabaciones WHERE grabacion_id=? ORDER BY creado_en,id",id);
  }
  private List<Map<String,Object>> conDocentes(List<Map<String,Object>> filas) {
    if(filas.isEmpty()) return filas;
    Map<String,String> nombres=new HashMap<>();
    try { for(var d:erp.get("/api/erp/docentes/resumen")) nombres.put(d.get("id").asText(),d.get("nombre").asText()); }
    catch(Exception e) { /* La consulta sigue disponible si ERP está temporalmente fuera. */ }
    for(var fila:filas) { Object id=fila.get("docente_erp_id"); fila.put("docente",id==null?"Docente no identificado":nombres.getOrDefault(id.toString(),"Docente no identificado")); }
    return filas;
  }
  @Transactional public UUID registrar(UUID clase,int orden,String titulo,String url,boolean publicar,UUID operacion,Authentication a) {
    clases.comprobar();
    var datos=db.queryForMap("SELECT curso_id,estado FROM clases_academicas WHERE id=? FOR UPDATE",clase);
    usuario.docente(a,(UUID)datos.get("curso_id"));
    if(!"FINALIZADA".equals(datos.get("estado"))) throw new IllegalStateException("Solo se registran grabaciones de clases finalizadas");
    if(operacion!=null) {
      var previa=db.queryForList("SELECT id,clase_id FROM grabaciones_youtube WHERE operacion_id=?",operacion);
      if(!previa.isEmpty()) { if(!clase.equals(previa.get(0).get("clase_id"))) throw new IllegalArgumentException("Operación usada para otra clase"); return (UUID)previa.get(0).get("id"); }
    }
    String enlace=YoutubeUrl.normalizar(url);
    if(orden==0) orden=db.queryForObject("SELECT COALESCE(MAX(orden),0)+1 FROM grabaciones_youtube WHERE clase_id=? AND estado<>'RETIRADA'",Integer.class,clase);
    if(orden<1) throw new IllegalArgumentException("Número de parte inválido");
    String nombre=titulo(titulo,orden); UUID id=UUID.randomUUID();
    try { db.update("INSERT INTO grabaciones_youtube(id,clase_id,orden,titulo,url,estado,creado_por,operacion_id) VALUES (?,?,?,?,?,'OCULTA',?,?)",id,clase,orden,nombre,enlace,usuario.cuenta(a),operacion); }
    catch(DuplicateKeyException e) { throw new IllegalArgumentException("Ya existe esa parte o URL en la clase"); }
    evento(id,"CREADA",null,Map.of("titulo",nombre,"url",enlace,"orden",orden,"estado","OCULTA"),null,a);
    if(publicar) publicarInterno(id,a);
    return id;
  }
  @Transactional public void editar(UUID id,String titulo,Integer orden,String url,UUID operacion,Authentication a) {
    clases.comprobar(); var g=obtenerBloqueado(id); escritura(a,(UUID)g.get("curso_id"));
    if("RETIRADA".equals(g.get("estado"))) throw new IllegalStateException("La referencia está retirada");
    if(repetida(operacion,id)) return;
    String enlace=YoutubeUrl.normalizar(url); int numero=orden==null?(Integer)g.get("orden"):orden;
    if(numero<1) throw new IllegalArgumentException("Número de parte inválido");
    String nombre=titulo==null?(String)g.get("titulo"):titulo(titulo,numero);
    if(Objects.equals(g.get("url"),enlace)&&Objects.equals(g.get("titulo"),nombre)&&Objects.equals(g.get("orden"),numero)) return;
    try { db.update("UPDATE grabaciones_youtube SET titulo=?,url=?,orden=?,actualizado_en=now() WHERE id=?",nombre,enlace,numero,id); }
    catch(DuplicateKeyException e) { throw new IllegalArgumentException("Ya existe esa parte o URL en la clase"); }
    String accion="PUBLICADA".equals(g.get("estado"))&&!Objects.equals(g.get("url"),enlace)?"ENLACE_CORREGIDO":"EDITADA";
    evento(id,accion,g,Map.of("titulo",nombre,"url",enlace,"orden",numero,"estado",g.get("estado")),operacion,a);
  }
  @Transactional public void estado(UUID id,boolean publicar,UUID operacion,Authentication a) {
    clases.comprobar(); var g=obtenerBloqueado(id); escritura(a,(UUID)g.get("curso_id"));
    if("RETIRADA".equals(g.get("estado"))) throw new IllegalStateException("La referencia está retirada");
    if(repetida(operacion,id)) return;
    if(publicar) {
      if(!"FINALIZADA".equals(g.get("estado_clase"))) throw new IllegalStateException("La clase debe estar finalizada");
      if(!"PUBLICADA".equals(g.get("estado"))) publicarInterno(id,a,operacion,g);
    } else if(!"OCULTA".equals(g.get("estado"))) {
      db.update("UPDATE grabaciones_youtube SET estado='OCULTA',actualizado_en=now() WHERE id=?",id);
      evento(id,"OCULTA",g,Map.of("titulo",g.get("titulo"),"url",g.get("url"),"orden",g.get("orden"),"estado","OCULTA"),operacion,a);
    }
  }
  @Transactional public void retirar(UUID id,UUID operacion,Authentication a) {
    clases.comprobar(); var g=obtenerBloqueado(id); escritura(a,(UUID)g.get("curso_id"));
    if(repetida(operacion,id)||"RETIRADA".equals(g.get("estado"))) return;
    db.update("UPDATE grabaciones_youtube SET estado='RETIRADA',actualizado_en=now() WHERE id=?",id);
    evento(id,"RETIRADA",g,Map.of("titulo",g.get("titulo"),"url",g.get("url"),"orden",g.get("orden"),"estado","RETIRADA"),operacion,a);
  }
  private void publicarInterno(UUID id,Authentication a) { publicarInterno(id,a,null,obtenerBloqueado(id)); }
  private void publicarInterno(UUID id,Authentication a,UUID operacion,Map<String,Object> g) {
    db.update("UPDATE grabaciones_youtube SET estado='PUBLICADA',actualizado_en=now() WHERE id=?",id);
    UUID evento=evento(id,"PUBLICADA",g,Map.of("titulo",g.get("titulo"),"url",g.get("url"),"orden",g.get("orden"),"estado","PUBLICADA"),operacion,a);
    avisar(evento,id,(UUID)g.get("curso_id"),"Nueva grabación: "+g.get("titulo"));
  }
  private void avisar(UUID evento,UUID id,UUID curso,String mensaje) {
    for(UUID estudiante:db.queryForList("SELECT i.estudiante_erp_id FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id WHERE i.curso_id=? AND i.activo AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)",UUID.class,curso))
      db.update("INSERT INTO notificaciones_grabacion(id,evento_id,grabacion_id,estudiante_erp_id,mensaje) VALUES (?,?,?,?,?) ON CONFLICT(evento_id,estudiante_erp_id) DO NOTHING",UUID.randomUUID(),evento,id,estudiante,mensaje);
  }
  private boolean repetida(UUID operacion,UUID id) {
    if(operacion==null) return false;
    var eventos=db.queryForList("SELECT grabacion_id FROM historial_grabaciones WHERE operacion_id=?",operacion);
    if(eventos.isEmpty()) return false;
    if(!id.equals(eventos.get(0).get("grabacion_id"))) throw new IllegalArgumentException("Operación usada para otra grabación");
    return true;
  }
  private UUID evento(UUID id,String accion,Map<String,Object> antes,Map<String,Object> despues,UUID operacion,Authentication a) {
    UUID evento=UUID.randomUUID();
    db.update("INSERT INTO historial_grabaciones(id,grabacion_id,accion,titulo_anterior,titulo_nuevo,url_anterior,url_nueva,orden_anterior,orden_nuevo,estado_anterior,estado_nuevo,actor_cuenta,operacion_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
      evento,id,accion,valor(antes,"titulo"),valor(despues,"titulo"),valor(antes,"url"),valor(despues,"url"),valor(antes,"orden"),valor(despues,"orden"),valor(antes,"estado"),valor(despues,"estado"),usuario.cuenta(a),operacion);
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"GRABACION_"+accion,id);
    if("ENLACE_CORREGIDO".equals(accion)) avisar(evento,id,(UUID)antes.get("curso_id"),"Enlace de grabación corregido: "+despues.get("titulo"));
    return evento;
  }
  private static Object valor(Map<String,Object> m,String k) { return m==null?null:m.get(k); }
  private static String titulo(String valor,int orden) { String t=valor==null||valor.isBlank()?"Parte "+orden:valor.trim(); if(t.length()>200) throw new IllegalArgumentException("Título demasiado largo"); return t; }
  private Map<String,Object> obtener(UUID id) { return db.queryForMap("SELECT g.*,cl.curso_id,cl.estado AS estado_clase FROM grabaciones_youtube g JOIN clases_academicas cl ON cl.id=g.clase_id WHERE g.id=?",id); }
  private Map<String,Object> obtenerBloqueado(UUID id) { return db.queryForMap("SELECT g.*,cl.curso_id,cl.estado AS estado_clase FROM grabaciones_youtube g JOIN clases_academicas cl ON cl.id=g.clase_id WHERE g.id=? FOR UPDATE OF g",id); }
  private void escritura(Authentication a,UUID curso) { if("ADMINISTRADOR".equals(usuario.tipo(a))) usuario.administrador(a); else usuario.docente(a,curso); }
}
