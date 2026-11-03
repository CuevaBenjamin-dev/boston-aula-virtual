package pe.boston.aula;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/aula/mensajes") public class MensajesController {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final CuentasRemotasController identidad;
  private final ObjectMapper json=new ObjectMapper();
  public MensajesController(JdbcTemplate db,UsuarioActual usuario,CuentasRemotasController identidad) { this.db=db;this.usuario=usuario;this.identidad=identidad; }
  private record Destino(UUID id,String usuario,String tipo,UUID referencia) {}
  public record Nuevo(String asunto,String cuerpo,List<UUID> destinatarios,UUID cursoId,boolean cursoCompleto,String grupoTipo) {}

  private Set<UUID> estudiantesAutorizados(Authentication a) {
    String tipo=usuario.tipo(a);
    if("ESTUDIANTE".equals(tipo)) return Set.copyOf(db.queryForList("""
      SELECT DISTINCT ajeno.estudiante_erp_id FROM inscripciones_curso propio
      JOIN cursos origen ON origen.id=propio.curso_id
      JOIN cursos destino ON destino.seccion_erp_id=origen.seccion_erp_id AND destino.grado=origen.grado AND destino.periodo=origen.periodo
      JOIN inscripciones_curso ajeno ON ajeno.curso_id=destino.id AND ajeno.activo
      WHERE propio.estudiante_erp_id=? AND propio.activo
        AND (origen.periodo_fin IS NULL OR origen.periodo_fin>=CURRENT_DATE)
        AND (destino.periodo_fin IS NULL OR destino.periodo_fin>=CURRENT_DATE)
      """,UUID.class,usuario.referencia(a)));
    if("DOCENTE".equals(tipo)) return Set.copyOf(db.queryForList("""
      SELECT DISTINCT i.estudiante_erp_id FROM docentes_curso d
      JOIN cursos c ON c.id=d.curso_id JOIN inscripciones_curso i ON i.curso_id=c.id
      WHERE d.docente_erp_id=? AND d.activo AND i.activo
        AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)
      """,UUID.class,usuario.referencia(a)));
    return Set.of();
  }
  private boolean autorizado(Destino d,String tipo,Set<UUID> estudiantes) {
    return ("COORDINACION".equals(tipo)||"ADMINISTRADOR".equals(tipo))
      ||("ESTUDIANTE".equals(d.tipo())&&d.referencia()!=null&&estudiantes.contains(d.referencia()));
  }

  private List<Destino> directorioInterno(Authentication a,OAuth2AuthorizedClient client) throws Exception {
    var raiz=json.readTree(identidad.directorio(a,client));
    var lista=new ArrayList<Destino>();
    for(var c:raiz) lista.add(new Destino(UUID.fromString(c.get("id").asText()),c.get("usuario").asText(),c.get("tipo").asText(),c.path("referencia_erp").isNull()?null:UUID.fromString(c.get("referencia_erp").asText())));
    return lista;
  }
  @GetMapping("/directorio") public List<Map<String,Object>> directorio(Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception {
    UUID propio=usuario.cuenta(a); String tipo=usuario.tipo(a); Set<UUID> estudiantes=estudiantesAutorizados(a);
    return directorioInterno(a,client).stream().filter(d->!d.id().equals(propio)&&autorizado(d,tipo,estudiantes)).map(d->Map.<String,Object>of("id",d.id(),"usuario",d.usuario(),"tipo",d.tipo())).toList();
  }
  @GetMapping public List<Map<String,Object>> listar(@RequestParam(defaultValue="recibidos") String carpeta,Authentication a) {
    UUID cuenta=usuario.cuenta(a);
    if("recibidos".equals(carpeta)) return db.queryForList("""
      SELECT m.id,m.asunto,m.cuerpo,m.emisor_cuenta,m.emisor_usuario,m.curso_id,m.creado_en,d.leida_en
      FROM mensajes_destinatarios d JOIN mensajes m ON m.id=d.mensaje_id
      WHERE d.cuenta_id=? ORDER BY m.creado_en DESC LIMIT 200
      """,cuenta);
    if("enviados".equals(carpeta)) return db.queryForList("""
      SELECT m.id,m.asunto,m.cuerpo,m.emisor_cuenta,m.emisor_usuario,m.curso_id,m.creado_en,
             count(d.cuenta_id) AS total_destinatarios,string_agg(d.usuario,', ' ORDER BY d.usuario) AS destinatarios
      FROM mensajes m JOIN mensajes_destinatarios d ON d.mensaje_id=m.id
      WHERE m.emisor_cuenta=? GROUP BY m.id ORDER BY m.creado_en DESC LIMIT 200
      """,cuenta);
    throw new IllegalArgumentException("Carpeta inválida");
  }
  @GetMapping("/{id}") public Map<String,Object> detalle(@PathVariable UUID id,Authentication a) {
    var mensaje=db.queryForMap("SELECT * FROM mensajes WHERE id=?",id);
    boolean enviado=usuario.cuenta(a).equals(mensaje.get("emisor_cuenta"));
    if(!enviado&&db.queryForObject("SELECT count(*) FROM mensajes_destinatarios WHERE mensaje_id=? AND cuenta_id=?",Long.class,id,usuario.cuenta(a))==0) throw new AccessDeniedException("Mensaje ajeno");
    var resultado=new HashMap<String,Object>(mensaje);
    if(enviado) resultado.put("destinatarios",db.queryForList("SELECT usuario,leida_en FROM mensajes_destinatarios WHERE mensaje_id=? ORDER BY usuario",id));
    else resultado.put("leida_en",db.queryForObject("SELECT leida_en FROM mensajes_destinatarios WHERE mensaje_id=? AND cuenta_id=?",Object.class,id,usuario.cuenta(a)));
    return resultado;
  }
  @PostMapping @Transactional public Map<String,Object> enviar(@RequestBody Nuevo nuevo,Authentication a,@RegisteredOAuth2AuthorizedClient("boston") OAuth2AuthorizedClient client) throws Exception {
    if(nuevo.asunto()==null||nuevo.asunto().isBlank()||nuevo.asunto().length()>200||nuevo.cuerpo()==null||nuevo.cuerpo().isBlank()||nuevo.cuerpo().length()>10000) throw new IllegalArgumentException("Escribe asunto y mensaje");
    var cuentas=directorioInterno(a,client);
    Map<UUID,Destino> porId=cuentas.stream().collect(Collectors.toMap(Destino::id,d->d));
    UUID emisor=usuario.cuenta(a); String tipo=usuario.tipo(a); Set<UUID> estudiantes=estudiantesAutorizados(a);
    Map<UUID,Destino> elegidos=new LinkedHashMap<>();
    if(nuevo.destinatarios()!=null) for(UUID id:nuevo.destinatarios()) {
      Destino destino=porId.get(id);
      if(destino==null||id.equals(emisor)||!autorizado(destino,tipo,estudiantes)) throw new AccessDeniedException("Destinatario no autorizado");
      elegidos.put(id,destino);
    }
    if(nuevo.grupoTipo()!=null&&!nuevo.grupoTipo().isBlank()) {
      if(!"COORDINACION".equals(tipo)&&!"ADMINISTRADOR".equals(tipo)) throw new AccessDeniedException("Envío a grupos no autorizado");
      if(!Set.of("ESTUDIANTE","DOCENTE","COORDINACION","ADMINISTRADOR").contains(nuevo.grupoTipo())) throw new IllegalArgumentException("Grupo inválido");
      for(var d:cuentas) if(nuevo.grupoTipo().equals(d.tipo())&&!d.id().equals(emisor)) elegidos.put(d.id(),d);
    }
    UUID curso=null;
    if(nuevo.cursoCompleto()) {
      if(nuevo.cursoId()==null) throw new IllegalArgumentException("Elige un curso");
      if("DOCENTE".equals(tipo)) usuario.docente(a,nuevo.cursoId());
      else if(!"COORDINACION".equals(tipo)&&!"ADMINISTRADOR".equals(tipo)) throw new AccessDeniedException("Envío por curso no autorizado");
      curso=nuevo.cursoId();
      Set<UUID> delCurso=Set.copyOf(db.queryForList("SELECT i.estudiante_erp_id FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id WHERE i.curso_id=? AND i.activo AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)",UUID.class,curso));
      for(var d:cuentas) if("ESTUDIANTE".equals(d.tipo())&&d.referencia()!=null&&delCurso.contains(d.referencia())) elegidos.put(d.id(),d);
    }
    if("ESTUDIANTE".equals(tipo)&&elegidos.size()>1) throw new AccessDeniedException("La cuenta académica solo puede enviar un mensaje directo a la vez");
    if(elegidos.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un destinatario disponible");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO mensajes(id,emisor_cuenta,emisor_usuario,asunto,cuerpo,curso_id) VALUES (?,?,?,?,?,?)",id,emisor,usuario.oidc(a).getSubject(),nuevo.asunto().trim(),nuevo.cuerpo().trim(),curso);
    for(var d:elegidos.values()) db.update("INSERT INTO mensajes_destinatarios(mensaje_id,cuenta_id,usuario) VALUES (?,?,?)",id,d.id(),d.usuario());
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"MENSAJE_ENVIADO",id);
    return Map.of("id",id,"destinatarios",elegidos.size());
  }
  @PostMapping("/{id}/leer") public void leer(@PathVariable UUID id,Authentication a) {
    if(db.update("UPDATE mensajes_destinatarios SET leida_en=COALESCE(leida_en,now()) WHERE mensaje_id=? AND cuenta_id=?",id,usuario.cuenta(a))==0) throw new AccessDeniedException("Mensaje ajeno");
  }
}
