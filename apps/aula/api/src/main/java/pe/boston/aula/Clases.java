package pe.boston.aula;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class Clases {
  public record Programacion(UUID cursoId,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace) {}
  private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));
  private static final String DATOS="""
    SELECT c.*,
      CASE WHEN c.estado='PROGRAMADA' AND c.fin<=now() THEN 'PASADA' ELSE c.estado END AS estado_visible,
      CASE WHEN c.estado='PROGRAMADA' THEN
        (SELECT b.enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY b.numero DESC LIMIT 1)
        ELSE NULL END AS enlace_actual,
      (SELECT max(b.actualizado_en) FROM bloques_zoom b WHERE b.clase_id=c.id) AS enlace_actualizado
    FROM clases_academicas c
    """;
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public Clases(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  public boolean habilitado() { return Boolean.TRUE.equals(db.queryForObject("SELECT habilitado FROM configuraciones_modulos WHERE clave='clases_y_grabaciones'",Boolean.class)); }
  public void comprobar() { if(!habilitado()) throw new IllegalStateException("Clases y grabaciones desactivadas"); }
  public List<Map<String,Object>> listar(UUID curso,Authentication a) {
    comprobar(); usuario.leerCurso(a,curso);
    return db.queryForList(DATOS+" WHERE c.curso_id=? ORDER BY c.inicio DESC",curso);
  }
  public List<Map<String,Object>> listarTodas(Authentication a) {
    comprobar();
    String base=DATOS.replace("SELECT c.*,","SELECT c.*,curso.asignatura,curso.grado,curso.seccion,curso.periodo,")
      .replace("FROM clases_academicas c","FROM clases_academicas c JOIN cursos curso ON curso.id=c.curso_id");
    return switch(usuario.tipo(a)) {
      case "ESTUDIANTE" -> db.queryForList(base+"JOIN inscripciones_curso i ON i.curso_id=c.curso_id WHERE i.estudiante_erp_id=? AND i.activo AND (curso.periodo_fin IS NULL OR curso.periodo_fin>=CURRENT_DATE) ORDER BY c.inicio DESC",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(base+"JOIN docentes_curso d ON d.curso_id=c.curso_id WHERE d.docente_erp_id=? AND d.activo AND (curso.periodo_fin IS NULL OR curso.periodo_fin>=CURRENT_DATE) ORDER BY c.inicio DESC",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(base+"JOIN coordinaciones_seccion x ON x.seccion_erp_id=curso.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp AND (curso.periodo_fin IS NULL OR curso.periodo_fin>=CURRENT_DATE) ORDER BY c.inicio DESC",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin acceso académico");
    };
  }
  public Map<String,Object> detalle(UUID id,Authentication a) {
    comprobar(); var clase=db.queryForMap(DATOS+" WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)clase.get("curso_id")); return clase;
  }
  private void validar(String tema,OffsetDateTime inicio,OffsetDateTime fin) {
    if(tema==null||tema.isBlank()||tema.length()>200) throw new IllegalArgumentException("Tema inválido");
    if(inicio==null||fin==null||!fin.isAfter(inicio)) throw new IllegalArgumentException("Horario inválido");
  }
  private String validarEnlace(String enlace) {
    if(enlace==null||enlace.isBlank()) throw new IllegalArgumentException("Ingresa un enlace");
    return enlace.trim();
  }
  private String huella(List<UUID> cursos,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace) {
    String datos=cursos+"|"+tema.trim()+"|"+inicio.toInstant()+"|"+fin.toInstant()+"|"+enlace;
    return resumen(datos);
  }
  private String huellaProgramaciones(List<Programacion> planes,boolean grupal) {
    StringBuilder datos=new StringBuilder("programaciones|"+grupal);
    for(Programacion p:planes) datos.append('|').append(p.cursoId()).append('|').append(p.tema()).append('|')
      .append(p.inicio().toInstant()).append('|').append(p.fin().toInstant()).append('|').append(p.enlace());
    return resumen(datos.toString());
  }
  private String resumen(String datos) {
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8))); }
    catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private void bloquear(List<String> claves) {
    claves.stream().distinct().sorted().forEach(clave->db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",clave));
  }
  private List<String> claves(UUID operacion,UUID docente,List<UUID> cursos) {
    var claves=new ArrayList<String>(); claves.add("operacion:"+operacion); claves.add("docente:"+docente);
    for(UUID curso:cursos) claves.add("seccion:"+db.queryForObject("SELECT seccion_erp_id FROM cursos WHERE id=?",UUID.class,curso));
    return claves;
  }
  private List<Map<String,Object>> conflictos(List<UUID> cursos,OffsetDateTime inicio,OffsetDateTime fin,UUID excluir,UUID docente) {
    String plazas=String.join(",",java.util.Collections.nCopies(cursos.size(),"?"));
    String sql="""
      SELECT DISTINCT c.id,c.tema,c.inicio,c.fin,curso.asignatura,curso.grado,curso.seccion
      FROM clases_academicas c JOIN cursos curso ON curso.id=c.curso_id
      WHERE c.estado<>'CANCELADA' AND c.inicio<? AND c.fin>?
        AND (curso.seccion_erp_id IN (SELECT seccion_erp_id FROM cursos WHERE id IN (%s))
          OR c.docente_erp_id=?
          OR EXISTS (SELECT 1 FROM docentes_curso d WHERE d.curso_id=c.curso_id AND d.docente_erp_id=? AND d.activo))
      """.formatted(plazas);
    var parametros=new ArrayList<Object>(); parametros.add(fin); parametros.add(inicio); parametros.addAll(cursos); parametros.add(docente); parametros.add(docente);
    if(excluir!=null) { sql+=" AND c.id<>?"; parametros.add(excluir); }
    return db.queryForList(sql+" ORDER BY c.inicio",parametros.toArray());
  }
  private void exigirConfirmacion(List<Map<String,Object>> conflictos,int secciones,boolean confirmado) {
    if(!confirmado&&(!conflictos.isEmpty()||secciones>1)) {
      String detalle=conflictos.isEmpty()?"el mismo docente dicta en varias secciones a esa hora":
        conflictos.stream().distinct().limit(3).map(c->"«"+c.get("tema")+"» · "+c.get("asignatura")+" "+c.get("grado")+" "+c.get("seccion")+
          " · "+FECHA.format(((Timestamp)c.get("inicio")).toInstant())+" a "+FECHA.format(((Timestamp)c.get("fin")).toInstant()))
          .collect(java.util.stream.Collectors.joining("; "));
      throw new IllegalStateException("CONFLICTO_HORARIO: Coincide con "+detalle+". ¿Guardar de todos modos?");
    }
  }
  @Transactional public List<Map<String,Object>> crearLote(List<UUID> destinos,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,UUID operacion,boolean confirmarConflicto,Authentication a) {
    comprobar(); validar(tema,inicio,fin); String zoom=validarEnlace(enlace);
    if(operacion==null||destinos==null||destinos.isEmpty()||destinos.stream().anyMatch(java.util.Objects::isNull)||new HashSet<>(destinos).size()!=destinos.size())
      throw new IllegalArgumentException("Cursos u operación inválidos");
    var cursos=destinos.stream().sorted().toList();
    var planes=cursos.stream().map(c->new Programacion(c,tema.trim(),inicio,fin,zoom)).toList();
    return crearPlanes(planes,operacion,huella(cursos,tema,inicio,fin,zoom),false,true,confirmarConflicto,a);
  }
  @Transactional public List<Map<String,Object>> crearProgramaciones(List<Programacion> programaciones,UUID operacion,boolean grupal,boolean confirmarConflicto,Authentication a) {
    comprobar();
    if(operacion==null||programaciones==null||programaciones.isEmpty()||programaciones.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Programaciones u operación inválidas");
    var planes=new ArrayList<Programacion>();
    for(Programacion p:programaciones) {
      if(p.cursoId()==null) throw new IllegalArgumentException("Falta un curso");
      validar(p.tema(),p.inicio(),p.fin());
      planes.add(new Programacion(p.cursoId(),p.tema().trim(),p.inicio(),p.fin(),validarEnlace(p.enlace())));
    }
    planes.sort(Comparator.comparing(Programacion::cursoId));
    if(grupal&&planes.size()>1) {
      Programacion primera=planes.getFirst();
      if(planes.stream().anyMatch(p->!p.tema().equals(primera.tema())||!p.inicio().toInstant().equals(primera.inicio().toInstant())
          ||!p.fin().toInstant().equals(primera.fin().toInstant())||!p.enlace().equals(primera.enlace())))
        throw new IllegalArgumentException("La clase compartida debe tener el mismo tema, horario y enlace Zoom");
    }
    return crearPlanes(planes,operacion,huellaProgramaciones(planes,grupal),grupal,false,confirmarConflicto,a);
  }
  private List<Map<String,Object>> crearPlanes(List<Programacion> planes,UUID operacion,String hash,boolean grupal,boolean advertirMultiples,boolean confirmarConflicto,Authentication a) {
    var cursos=planes.stream().map(Programacion::cursoId).toList();
    if(new HashSet<>(cursos).size()!=cursos.size()) throw new IllegalArgumentException("Cada sección debe aparecer una sola vez");
    UUID docente=usuario.referencia(a);
    for(UUID curso:cursos) usuario.docente(a,curso);
    var familias=db.queryForList("SELECT DISTINCT asignatura,nivel,grado FROM cursos WHERE id IN ("+String.join(",",java.util.Collections.nCopies(cursos.size(),"?"))+")",cursos.toArray());
    if(familias.size()!=1) throw new IllegalArgumentException("Selecciona secciones de la misma asignatura y grado");
    bloquear(claves(operacion,docente,cursos));
    var anteriores=db.queryForList("SELECT id,curso_id,creado_por,solicitud_hash FROM clases_academicas WHERE operacion_id=? ORDER BY curso_id",operacion);
    if(!anteriores.isEmpty()) {
      if(anteriores.size()!=cursos.size()||anteriores.stream().anyMatch(c->!usuario.cuenta(a).equals(c.get("creado_por"))||!hash.equals(c.get("solicitud_hash"))||!cursos.contains(c.get("curso_id"))))
        throw new IllegalStateException("Identificador de operación reutilizado con otros datos");
      return anteriores.stream().map(c->Map.<String,Object>of("id",c.get("id"),"curso_id",c.get("curso_id"))).toList();
    }
    if(!grupal&&!advertirMultiples) for(int i=0;i<planes.size();i++) for(int j=i+1;j<planes.size();j++) {
      Programacion uno=planes.get(i),dos=planes.get(j);
      if(uno.inicio().isBefore(dos.fin())&&uno.fin().isAfter(dos.inicio()))
        throw new IllegalArgumentException("Las clases con horarios separados se superponen: «"+uno.tema()+"» · sección "+
          db.queryForObject("SELECT seccion FROM cursos WHERE id=?",String.class,uno.cursoId())+" · "+FECHA.format(uno.inicio().toInstant())+
          " a "+FECHA.format(uno.fin().toInstant())+"; «"+dos.tema()+"» · sección "+
          db.queryForObject("SELECT seccion FROM cursos WHERE id=?",String.class,dos.cursoId())+" · "+FECHA.format(dos.inicio().toInstant())+
          " a "+FECHA.format(dos.fin().toInstant())+". Ajusta uno de los horarios.");
    }
    var existentes=new ArrayList<Map<String,Object>>();
    for(Programacion p:planes) existentes.addAll(conflictos(List.of(p.cursoId()),p.inicio(),p.fin(),null,docente));
    exigirConfirmacion(existentes,advertirMultiples?cursos.size():1,confirmarConflicto);
    var creadas=new ArrayList<Map<String,Object>>();
    for(Programacion p:planes) {
      UUID curso=p.cursoId();
      UUID id=UUID.randomUUID();
      db.update("INSERT INTO clases_academicas(id,curso_id,tema,inicio,fin,creado_por,docente_erp_id,operacion_id,solicitud_hash) VALUES (?,?,?,?,?,?,?,?,?)",id,curso,p.tema(),p.inicio(),p.fin(),usuario.cuenta(a),docente,operacion,hash);
      guardarEnlace(id,p.enlace(),null,a);
      UUID evento=registrarHistorial(id,"CREADA",null,a);
      notificar(evento,id,curso,"PROGRAMADA",mensaje("Nueva clase",curso,p.tema(),p.inicio(),p.fin()));
      auditar(id,"CLASE_CREADA",a);
      creadas.add(Map.of("id",id,"curso_id",curso));
    }
    return creadas;
  }
  @Transactional public UUID crear(UUID curso,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,UUID operacion,boolean confirmarConflicto,Authentication a) {
    return (UUID)crearLote(List.of(curso),tema,inicio,fin,enlace,operacion,confirmarConflicto,a).getFirst().get("id");
  }
  @Transactional public void editar(UUID id,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,UUID operacion,boolean confirmarConflicto,Authentication a) {
    comprobar(); validar(tema,inicio,fin); String zoom=validarEnlace(enlace);
    if(operacion==null) throw new IllegalArgumentException("Falta identificador de operación");
    var anterior=db.queryForMap("SELECT curso_id,tema,inicio,fin,estado FROM clases_academicas WHERE id=? FOR UPDATE",id);
    UUID curso=(UUID)anterior.get("curso_id"); usuario.docente(a,curso);
    var repetida=db.queryForList("SELECT clase_id,accion,tema,inicio,fin,enlace FROM historial_clases WHERE operacion_id=?",operacion);
    if(!repetida.isEmpty()) {
      var r=repetida.getFirst();
      if(!id.equals(r.get("clase_id"))||!"EDITADA".equals(r.get("accion"))||!tema.trim().equals(r.get("tema"))||!inicio.toInstant().equals(((Timestamp)r.get("inicio")).toInstant())||!fin.toInstant().equals(((Timestamp)r.get("fin")).toInstant())||!zoom.equals(r.get("enlace")))
        throw new IllegalStateException("Identificador de operación reutilizado con otros datos");
      return;
    }
    if(!"PROGRAMADA".equals(anterior.get("estado"))) throw new IllegalStateException("La clase ya fue cancelada o finalizada");
    String actual=db.queryForObject("SELECT enlace FROM bloques_zoom WHERE clase_id=? ORDER BY numero DESC LIMIT 1",String.class,id);
    boolean horario=!inicio.toInstant().equals(((Timestamp)anterior.get("inicio")).toInstant())||!fin.toInstant().equals(((Timestamp)anterior.get("fin")).toInstant());
    boolean temaCambio=!tema.trim().equals(anterior.get("tema")),enlaceCambio=!zoom.equals(actual);
    if(!horario&&!temaCambio&&!enlaceCambio) return;
    bloquear(claves(operacion,usuario.referencia(a),List.of(curso)));
    if(horario) exigirConfirmacion(conflictos(List.of(curso),inicio,fin,id,usuario.referencia(a)),1,confirmarConflicto);
    db.update("UPDATE clases_academicas SET tema=?,inicio=?,fin=?,modificado_en=now() WHERE id=?",tema.trim(),inicio,fin,id);
    if(enlaceCambio) guardarEnlace(id,zoom,null,a);
    UUID evento=registrarHistorial(id,"EDITADA",operacion,a);
    notificar(evento,id,curso,horario?"REPROGRAMADA":enlaceCambio?"CONTINUACION":"ACTUALIZADA",
      mensaje(enlaceCambio?"Clase actualizada con nuevo enlace Zoom":"Clase actualizada",curso,tema,inicio,fin));
    auditar(id,"CLASE_EDITADA",a);
  }
  @Transactional public void cancelar(UUID id,UUID operacion,Authentication a) { cambiarEstado(id,operacion,"CANCELADA",a); }
  @Transactional public void finalizar(UUID id,UUID operacion,Authentication a) { cambiarEstado(id,operacion,"FINALIZADA",a); }
  private void cambiarEstado(UUID id,UUID operacion,String nuevo,Authentication a) {
    comprobar(); if(operacion==null) throw new IllegalArgumentException("Falta identificador de operación");
    var clase=db.queryForMap("SELECT curso_id,tema,inicio,fin,estado FROM clases_academicas WHERE id=? FOR UPDATE",id);
    UUID curso=(UUID)clase.get("curso_id"); usuario.docente(a,curso);
    var repetida=db.queryForList("SELECT clase_id,accion FROM historial_clases WHERE operacion_id=?",operacion);
    if(!repetida.isEmpty()) {
      if(!id.equals(repetida.getFirst().get("clase_id"))||!nuevo.equals(repetida.getFirst().get("accion"))) throw new IllegalStateException("Identificador de operación reutilizado");
      return;
    }
    if(!"PROGRAMADA".equals(clase.get("estado"))) throw new IllegalStateException("La clase ya fue cancelada o finalizada");
    db.update("UPDATE clases_academicas SET estado=?,modificado_en=now() WHERE id=?",nuevo,id);
    UUID evento=registrarHistorial(id,nuevo,operacion,a);
    notificar(evento,id,curso,nuevo,mensaje("CANCELADA".equals(nuevo)?"Clase cancelada":"Clase finalizada",curso,(String)clase.get("tema"),((Timestamp)clase.get("inicio")).toInstant().atOffset(ZoneOffset.UTC),((Timestamp)clase.get("fin")).toInstant().atOffset(ZoneOffset.UTC)));
    auditar(id,"CLASE_"+nuevo,a);
  }
  @Transactional public void actualizar(UUID clase,String enlace,UUID operacion,Authentication a) {
    comprobar(); String zoom=validarEnlace(enlace);
    if(operacion==null) throw new IllegalArgumentException("Falta identificador de operación");
    var estado=db.queryForMap("SELECT curso_id,tema,inicio,fin,estado FROM clases_academicas WHERE id=? FOR UPDATE",clase);
    UUID curso=(UUID)estado.get("curso_id"); usuario.docente(a,curso);
    var repetida=db.queryForList("SELECT clase_id,enlace FROM bloques_zoom WHERE operacion_id=?",operacion);
    if(!repetida.isEmpty()) {
      if(!clase.equals(repetida.getFirst().get("clase_id"))||!zoom.equals(repetida.getFirst().get("enlace"))) throw new IllegalStateException("Identificador de operación reutilizado");
      return;
    }
    if(!"PROGRAMADA".equals(estado.get("estado"))) throw new IllegalStateException("La clase ya fue cancelada o finalizada");
    String anterior=db.queryForObject("SELECT enlace FROM bloques_zoom WHERE clase_id=? ORDER BY numero DESC LIMIT 1",String.class,clase);
    if(zoom.equals(anterior)) return;
    guardarEnlace(clase,zoom,operacion,a);
    UUID evento=registrarHistorial(clase,"ENLACE",operacion,a);
    notificar(evento,clase,curso,"CONTINUACION",mensaje("Clase continúa con nuevo enlace Zoom",curso,(String)estado.get("tema"),((Timestamp)estado.get("inicio")).toInstant().atOffset(ZoneOffset.UTC),((Timestamp)estado.get("fin")).toInstant().atOffset(ZoneOffset.UTC)));
    auditar(clase,"ENLACE_ZOOM_ACTUALIZADO",a);
  }
  private void guardarEnlace(UUID clase,String enlace,UUID operacion,Authentication a) {
    Integer numero=db.queryForObject("SELECT COALESCE(max(numero),0)+1 FROM bloques_zoom WHERE clase_id=?",Integer.class,clase);
    db.update("INSERT INTO bloques_zoom(id,clase_id,numero,enlace,actualizado_por,operacion_id) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),clase,numero,enlace,usuario.cuenta(a),operacion);
  }
  private String mensaje(String accion,UUID curso,String tema,OffsetDateTime inicio,OffsetDateTime fin) {
    var c=db.queryForMap("SELECT asignatura,grado,seccion FROM cursos WHERE id=?",curso);
    return accion+": "+tema+" · "+c.get("asignatura")+" "+c.get("grado")+" "+c.get("seccion")+" · "+FECHA.format(inicio.toInstant())+" a "+FECHA.format(fin.toInstant());
  }
  private void notificar(UUID evento,UUID clase,UUID curso,String tipo,String mensaje) {
    db.update("""
      INSERT INTO notificaciones_clase(id,clase_id,estudiante_erp_id,tipo,mensaje,evento_id)
      SELECT gen_random_uuid(),?,i.estudiante_erp_id,?,?,? FROM inscripciones_curso i
      WHERE i.curso_id=? AND i.activo ON CONFLICT DO NOTHING
      """,clase,tipo,mensaje,evento,curso);
  }
  private UUID registrarHistorial(UUID clase,String accion,UUID operacion,Authentication a) {
    UUID evento=UUID.randomUUID();
    db.update("""
      INSERT INTO historial_clases(id,clase_id,accion,tema,inicio,fin,estado,registrado_por,enlace,operacion_id,registrado_en)
      SELECT ?,c.id,?,c.tema,c.inicio,c.fin,c.estado,?,
        (SELECT b.enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY b.numero DESC LIMIT 1),?,clock_timestamp()
      FROM clases_academicas c WHERE c.id=?
      """,evento,accion,usuario.cuenta(a),operacion,clase);
    return evento;
  }
  private void auditar(UUID clase,String accion,Authentication a) {
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,clase);
  }
  public List<Map<String,Object>> historial(UUID clase,Authentication a) {
    comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) throw new AccessDeniedException("Historial reservado al personal académico");
    return db.queryForList("SELECT numero,enlace,actualizado_en,actualizado_por FROM bloques_zoom WHERE clase_id=? ORDER BY numero",clase);
  }
  public List<Map<String,Object>> historialClase(UUID clase,Authentication a) {
    comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) throw new AccessDeniedException("Historial reservado al personal académico");
    return db.queryForList("""
      SELECT accion,tema,inicio,fin,estado,enlace,registrado_por,registrado_en,
        lag(tema) OVER (ORDER BY registrado_en,id) AS tema_anterior,
        lag(inicio) OVER (ORDER BY registrado_en,id) AS inicio_anterior,
        lag(fin) OVER (ORDER BY registrado_en,id) AS fin_anterior,
        lag(enlace) OVER (ORDER BY registrado_en,id) AS enlace_anterior
      FROM historial_clases WHERE clase_id=? ORDER BY registrado_en,id
      """,clase);
  }
}
