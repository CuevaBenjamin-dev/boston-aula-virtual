package pe.boston.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class Institucional {
  private final JdbcTemplate db;
  private final ObjectMapper json = new ObjectMapper();
  public Institucional(JdbcTemplate db) { this.db=db; }

  static void admin(Jwt jwt) { if (!"ADMINISTRADOR".equals(jwt.getClaimAsString("tipo"))) throw new AccessDeniedException("Solo administración institucional"); }
  static void finanzas(Jwt jwt) { admin(jwt); if (!Boolean.TRUE.equals(jwt.getClaim("tesoreria"))) throw new AccessDeniedException("Permiso de tesorería requerido"); }
  static void lectura(Jwt jwt, UUID estudiante) {
    Object alcance=jwt.getClaim("scope");
    if ((alcance instanceof String s && java.util.Arrays.asList(s.split(" ")).contains("erp.read")) ||
        (alcance instanceof java.util.Collection<?> lista && lista.contains("erp.read"))) return;
    if ("ESTUDIANTE".equals(jwt.getClaimAsString("tipo")) && estudiante.toString().equals(jwt.getClaimAsString("referencia_erp"))) return;
    if ("ADMINISTRADOR".equals(jwt.getClaimAsString("tipo")) && Boolean.TRUE.equals(jwt.getClaim("tesoreria"))) return;
    throw new AccessDeniedException("Estudiante ajeno");
  }

  @Transactional public UUID nuevoEstudiante(String nombres, String apellidos, String codigo, Jwt actor) {
    admin(actor); UUID persona=UUID.randomUUID(), estudiante=UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos) VALUES (?,?,?)",persona,nombres,apellidos);
    db.update("INSERT INTO estudiantes(id,persona_id,codigo,estado) VALUES (?,?,?,'ACTIVO')",estudiante,persona,codigo);
    auditar(actor,"ESTUDIANTE_CREADO",estudiante,Map.of("codigo",codigo)); return estudiante;
  }
  @Transactional public UUID matricular(UUID estudiante, UUID seccion, Jwt actor) {
    admin(actor); UUID id=UUID.randomUUID();
    if(db.queryForObject("SELECT count(*) FROM estudiantes WHERE id=? AND estado='ACTIVO'",Long.class,estudiante)==0) throw new IllegalArgumentException("Estudiante no activo");
    List<UUID> anteriores=db.query("SELECT seccion_id FROM matriculas WHERE estudiante_id=? AND estado='ACTIVA'",(rs,n)->(UUID)rs.getObject(1),estudiante);
    db.update("UPDATE matriculas SET estado='RETIRADA',actualizado_en=now() WHERE estudiante_id=? AND estado='ACTIVA'",estudiante);
    db.update("INSERT INTO matriculas(id,estudiante_id,seccion_id,estado) VALUES (?,?,?,'ACTIVA') ON CONFLICT (estudiante_id,seccion_id) DO UPDATE SET estado='ACTIVA',actualizado_en=now()",id,estudiante,seccion);
    anteriores.stream().filter(anterior -> !anterior.equals(seccion)).forEach(this::publicarSeccion);
    publicarSeccion(seccion); auditar(actor,"MATRICULA",estudiante,Map.of("seccion",seccion));
    return db.queryForObject("SELECT id FROM matriculas WHERE estudiante_id=? AND seccion_id=?",UUID.class,estudiante,seccion);
  }
  @Transactional public void cambiarSeccion(UUID estudiante, UUID nueva, Jwt actor) {
    admin(actor);
    if(db.queryForObject("SELECT count(*) FROM estudiantes WHERE id=? AND estado='ACTIVO'",Long.class,estudiante)==0) throw new IllegalArgumentException("Estudiante no activo");
    List<UUID> anteriores=db.query("SELECT seccion_id FROM matriculas WHERE estudiante_id=? AND estado='ACTIVA'",(rs,n)->(UUID)rs.getObject(1),estudiante);
    db.update("UPDATE matriculas SET estado='RETIRADA',actualizado_en=now() WHERE estudiante_id=? AND estado='ACTIVA'",estudiante);
    db.update("INSERT INTO matriculas(id,estudiante_id,seccion_id,estado) VALUES (?,?,?,'ACTIVA') ON CONFLICT (estudiante_id,seccion_id) DO UPDATE SET estado='ACTIVA',actualizado_en=now()",UUID.randomUUID(),estudiante,nueva);
    anteriores.forEach(this::publicarSeccion); publicarSeccion(nueva); auditar(actor,"CAMBIO_SECCION",estudiante,Map.of("nueva",nueva));
  }
  @Transactional public void retirar(UUID estudiante, Jwt actor) {
    admin(actor);
    List<UUID> secciones=db.query("SELECT seccion_id FROM matriculas WHERE estudiante_id=? AND estado='ACTIVA'",(rs,n)->(UUID)rs.getObject(1),estudiante);
    db.update("UPDATE matriculas SET estado='RETIRADA',actualizado_en=now() WHERE estudiante_id=? AND estado='ACTIVA'",estudiante);
    db.update("UPDATE estudiantes SET estado='RETIRADO' WHERE id=?",estudiante);
    secciones.forEach(this::publicarSeccion); auditar(actor,"RETIRO",estudiante,Map.of());
  }
  @Transactional public void asignarDocente(UUID oferta, UUID docente, Jwt actor) {
    admin(actor);
    if(db.queryForObject("SELECT count(*) FROM docentes WHERE id=? AND estado='ACTIVO'",Long.class,docente)==0) throw new IllegalArgumentException("Docente no activo");
    db.update("UPDATE asignaciones_docentes SET estado='INACTIVA' WHERE oferta_id=? AND estado='ACTIVA'",oferta);
    db.update("INSERT INTO asignaciones_docentes(id,oferta_id,docente_id,estado) VALUES (?,?,?,'ACTIVA')",UUID.randomUUID(),oferta,docente);
    publicarOferta(oferta); auditar(actor,"CAMBIO_DOCENTE",oferta,Map.of("docente",docente));
  }
  @Transactional public void estadoAcademico(UUID estudiante, String estado, Jwt actor) {
    admin(actor); if (!List.of("ACTIVO","RETIRADO").contains(estado)) throw new IllegalArgumentException("Estado inválido");
    db.update("UPDATE estudiantes SET estado=? WHERE id=?",estado,estudiante);
    db.query("SELECT seccion_id FROM matriculas WHERE estudiante_id=?",(rs,n)->(UUID)rs.getObject(1),estudiante).forEach(this::publicarSeccion);
    auditar(actor,"ESTADO_ACADEMICO",estudiante,Map.of("estado",estado));
  }

  public List<Map<String,Object>> eventos(long despues, int limite) {
    return db.queryForList("SELECT id,secuencia,tipo,version,entidad_id,datos,creado_en FROM eventos_pendientes WHERE secuencia>? ORDER BY secuencia LIMIT ?",despues,Math.min(200,Math.max(1,limite)));
  }
  public List<Map<String,Object>> ofertas() { return db.queryForList("SELECT o.id,a.nombre AS asignatura,s.nombre AS seccion,g.nombre AS grado FROM ofertas_academicas o JOIN asignaturas a ON a.id=o.asignatura_id JOIN secciones s ON s.id=o.seccion_id JOIN grados g ON g.id=s.grado_id ORDER BY a.nombre"); }
  public List<Map<String,Object>> docentesResumen(Jwt actor) {
    Object scope=actor.getClaim("scope");
    boolean lectura=(scope instanceof String s && java.util.Arrays.asList(s.split(" ")).contains("erp.read")) || (scope instanceof java.util.Collection<?> lista && lista.contains("erp.read"));
    if(!"ADMINISTRADOR".equals(actor.getClaimAsString("tipo")) && !lectura) throw new AccessDeniedException("Lectura ERP requerida");
    return db.queryForList("SELECT d.id,concat_ws(' ',p.nombres,p.apellidos) AS nombre FROM docentes d JOIN personas p ON p.id=d.persona_id");
  }
  public Map<String,Object> catalogo(Jwt actor) {
    admin(actor);
    return Map.of("periodos",db.queryForList("SELECT id,nombre FROM periodos_academicos ORDER BY nombre"),
      "grados",db.queryForList("SELECT id,nombre FROM grados ORDER BY nombre"),
      "secciones",db.queryForList("SELECT id,grado_id,periodo_id,nombre FROM secciones ORDER BY nombre"),
      "asignaturas",db.queryForList("SELECT id,nombre FROM asignaturas ORDER BY nombre"),
      "docentes",db.queryForList("SELECT d.id,p.nombres,p.apellidos FROM docentes d JOIN personas p ON p.id=d.persona_id WHERE d.estado='ACTIVO' ORDER BY p.apellidos"));
  }
  @Transactional public UUID nuevaSeccion(UUID grado,UUID periodo,String nombre,Jwt actor) {
    admin(actor); if(nombre==null||nombre.isBlank()||nombre.length()>30) throw new IllegalArgumentException("Sección inválida");
    UUID id=UUID.randomUUID(); db.update("INSERT INTO secciones(id,grado_id,periodo_id,nombre) VALUES (?,?,?,?)",id,grado,periodo,nombre.trim());
    auditar(actor,"SECCION_CREADA",id,Map.of("nombre",nombre));return id;
  }
  @Transactional public UUID nuevaOferta(UUID seccion,UUID asignatura,Jwt actor) {
    admin(actor); UUID id=UUID.randomUUID(); db.update("INSERT INTO ofertas_academicas(id,seccion_id,asignatura_id) VALUES (?,?,?)",id,seccion,asignatura);
    publicarOferta(id); auditar(actor,"OFERTA_CREADA",id,Map.of("seccion",seccion));return id;
  }
  @Transactional public UUID nuevoDocente(String nombres,String apellidos,Jwt actor) {
    admin(actor);if(nombres==null||nombres.isBlank()||apellidos==null||apellidos.isBlank()) throw new IllegalArgumentException("Docente inválido");
    UUID persona=UUID.randomUUID(),id=UUID.randomUUID();db.update("INSERT INTO personas(id,nombres,apellidos) VALUES (?,?,?)",persona,nombres,apellidos);
    db.update("INSERT INTO docentes(id,persona_id) VALUES (?,?)",id,persona);auditar(actor,"DOCENTE_CREADO",id,Map.of());return id;
  }
  @Transactional public UUID nuevoApoderado(String nombres,String apellidos,String documento,Jwt actor) {
    admin(actor);
    if(nombres==null||nombres.isBlank()||apellidos==null||apellidos.isBlank()||nombres.length()>120||apellidos.length()>120||documento!=null&&documento.length()>30) throw new IllegalArgumentException("Apoderado inválido");
    UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos,documento) VALUES (?,?,?,?)",persona,nombres.trim(),apellidos.trim(),documento==null||documento.isBlank()?null:documento.trim());
    db.update("INSERT INTO apoderados(id,persona_id) VALUES (?,?)",id,persona);
    auditar(actor,"APODERADO_CREADO",id,Map.of());return id;
  }
  @Transactional public void vincularApoderado(UUID estudiante,UUID apoderado,String vinculo,Jwt actor) {
    admin(actor);
    if(estudiante==null||apoderado==null||vinculo==null||vinculo.isBlank()||vinculo.length()>40) throw new IllegalArgumentException("Vínculo inválido");
    if(db.queryForObject("SELECT count(*) FROM estudiantes WHERE id=?",Long.class,estudiante)==0||db.queryForObject("SELECT count(*) FROM apoderados WHERE id=?",Long.class,apoderado)==0) throw new IllegalArgumentException("Estudiante o apoderado inexistente");
    db.update("INSERT INTO vinculos_estudiante_apoderado(estudiante_id,apoderado_id,vinculo) VALUES (?,?,?) ON CONFLICT (estudiante_id,apoderado_id) DO UPDATE SET vinculo=EXCLUDED.vinculo",estudiante,apoderado,vinculo.trim());
    auditar(actor,"APODERADO_VINCULADO",estudiante,Map.of("apoderado",apoderado,"vinculo",vinculo));
  }
  public List<Map<String,Object>> apoderados(UUID estudiante,Jwt actor) {
    if(!"ADMINISTRADOR".equals(actor.getClaimAsString("tipo"))) lectura(actor,estudiante);
    return db.queryForList("SELECT a.id,p.nombres,p.apellidos,v.vinculo FROM vinculos_estudiante_apoderado v JOIN apoderados a ON a.id=v.apoderado_id JOIN personas p ON p.id=a.persona_id WHERE v.estudiante_id=? ORDER BY p.apellidos,p.nombres",estudiante);
  }

  @Transactional public void publicarTodas() { db.query("SELECT id FROM ofertas_academicas",(rs,n)->(UUID)rs.getObject(1)).forEach(this::publicarOferta); }
  private void publicarSeccion(UUID seccion) { db.query("SELECT id FROM ofertas_academicas WHERE seccion_id=?",(rs,n)->(UUID)rs.getObject(1),seccion).forEach(this::publicarOferta); }
  private void publicarOferta(UUID oferta) {
    var base=db.queryForMap("SELECT o.id,o.seccion_id,a.nombre AS asignatura,s.nombre AS seccion,g.nombre AS grado,p.nombre AS periodo FROM ofertas_academicas o JOIN asignaturas a ON a.id=o.asignatura_id JOIN secciones s ON s.id=o.seccion_id JOIN grados g ON g.id=s.grado_id JOIN periodos_academicos p ON p.id=s.periodo_id WHERE o.id=?",oferta);
    var estudiantes=db.query("SELECT m.estudiante_id FROM matriculas m JOIN estudiantes e ON e.id=m.estudiante_id WHERE m.seccion_id=? AND m.estado='ACTIVA' AND e.estado='ACTIVO'",(rs,n)->rs.getString(1),base.get("seccion_id"));
    var docentes=db.query("SELECT docente_id FROM asignaciones_docentes WHERE oferta_id=? AND estado='ACTIVA'",(rs,n)->rs.getString(1),oferta);
    Map<String,Object> datos=Map.of("cursoId",oferta.toString(),"seccionId",base.get("seccion_id").toString(),"asignatura",base.get("asignatura"),"seccion",base.get("seccion"),"grado",base.get("grado"),"periodo",base.get("periodo"),"estudiantes",estudiantes,"docentes",docentes);
    db.update("INSERT INTO eventos_pendientes(id,tipo,version,entidad_id,datos) VALUES (?,'OFERTA_ACTUALIZADA',1,?,?::jsonb)",UUID.randomUUID(),oferta,json(datos));
  }
  private String json(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
  private void auditar(Jwt actor,String accion,UUID entidad,Object detalle) {
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id,detalle) VALUES (?,?,?,?,?::jsonb)",UUID.randomUUID(),actor.getSubject(),accion,entidad,json(detalle));
  }
}
