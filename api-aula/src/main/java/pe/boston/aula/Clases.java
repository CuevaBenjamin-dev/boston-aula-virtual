package pe.boston.aula;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class Clases {
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public Clases(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  public boolean habilitado() { return Boolean.TRUE.equals(db.queryForObject("SELECT habilitado FROM configuraciones_modulos WHERE clave='clases_y_grabaciones'",Boolean.class)); }
  public void comprobar() { if(!habilitado()) throw new IllegalStateException("Clases y grabaciones desactivadas"); }
  public List<Map<String,Object>> listar(UUID curso,Authentication a) {
    comprobar(); usuario.leerCurso(a,curso);
    return db.queryForList("SELECT c.*, (SELECT enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY numero DESC LIMIT 1) AS enlace_actual,(SELECT max(actualizado_en) FROM bloques_zoom b WHERE b.clase_id=c.id) AS enlace_actualizado FROM clases_academicas c WHERE curso_id=? ORDER BY inicio DESC",curso);
  }
  public List<Map<String,Object>> listarTodas(Authentication a) {
    comprobar();
    String base="SELECT c.*,curso.asignatura,curso.grado,curso.seccion,curso.periodo, (SELECT enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY numero DESC LIMIT 1) AS enlace_actual,(SELECT max(actualizado_en) FROM bloques_zoom b WHERE b.clase_id=c.id) AS enlace_actualizado FROM clases_academicas c JOIN cursos curso ON curso.id=c.curso_id ";
    return switch(usuario.tipo(a)) {
      case "ESTUDIANTE" -> db.queryForList(base+"JOIN inscripciones_curso i ON i.curso_id=c.curso_id WHERE i.estudiante_erp_id=? AND i.activo ORDER BY c.inicio DESC",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(base+"JOIN docentes_curso d ON d.curso_id=c.curso_id WHERE d.docente_erp_id=? AND d.activo ORDER BY c.inicio DESC",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(base+"JOIN coordinaciones_seccion x ON x.seccion_erp_id=curso.seccion_erp_id WHERE x.cuenta_id=? ORDER BY c.inicio DESC",usuario.cuenta(a));
      default -> throw new org.springframework.security.access.AccessDeniedException("Sin acceso académico");
    };
  }
  public Map<String,Object> detalle(UUID id,Authentication a) {
    comprobar(); var clase=db.queryForMap("SELECT c.*, (SELECT enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY numero DESC LIMIT 1) AS enlace_actual,(SELECT max(actualizado_en) FROM bloques_zoom b WHERE b.clase_id=c.id) AS enlace_actualizado FROM clases_academicas c WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)clase.get("curso_id")); return clase;
  }
  private void validar(String tema,OffsetDateTime inicio,OffsetDateTime fin) {
    if(tema==null||tema.isBlank()||tema.length()>200) throw new IllegalArgumentException("Tema inválido");
    if(inicio==null||fin==null||!fin.isAfter(inicio)) throw new IllegalArgumentException("Horario inválido");
  }
  private void validarEnlace(String enlace) {
    try {
      URI uri=URI.create(enlace);
      String host=uri.getHost();
      if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||!(host.equalsIgnoreCase("zoom.us")||host.toLowerCase().endsWith(".zoom.us"))) throw new IllegalArgumentException("Enlace oficial de Zoom requerido");
    } catch(RuntimeException e) { throw new IllegalArgumentException("Enlace oficial de Zoom requerido"); }
  }
  @Transactional public UUID crear(UUID curso,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,Authentication a) {
    comprobar(); usuario.docente(a,curso); validar(tema,inicio,fin); validarEnlace(enlace);
    UUID id=UUID.randomUUID(); db.update("INSERT INTO clases_academicas(id,curso_id,tema,inicio,fin,creado_por,docente_erp_id) VALUES (?,?,?,?,?,?,?)",id,curso,tema.trim(),inicio,fin,usuario.cuenta(a),usuario.referencia(a));
    guardarEnlace(id,enlace,a); registrarHistorial(id,"CREADA"); notificar(id,curso,"PROGRAMADA","Nueva clase: "+tema.trim()); return id;
  }
  @Transactional public void editar(UUID id,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,Authentication a) {
    comprobar(); validar(tema,inicio,fin);
    var anterior=db.queryForMap("SELECT curso_id,estado FROM clases_academicas WHERE id=? FOR UPDATE",id);
    UUID curso=(UUID)anterior.get("curso_id"); usuario.docente(a,curso);
    if(!"PROGRAMADA".equals(anterior.get("estado"))) throw new IllegalStateException("La clase está cancelada");
    boolean horario=Boolean.TRUE.equals(db.queryForObject("SELECT inicio<>?::timestamptz OR fin<>?::timestamptz FROM clases_academicas WHERE id=?",Boolean.class,inicio,fin,id));
    db.update("UPDATE clases_academicas SET tema=?,inicio=?,fin=?,modificado_en=now() WHERE id=?",tema.trim(),inicio,fin,id);
    if(enlace!=null&&!enlace.isBlank()) {
      validarEnlace(enlace);
      String actual=db.queryForObject("SELECT enlace FROM bloques_zoom WHERE clase_id=? ORDER BY numero DESC LIMIT 1",String.class,id);
      if(!enlace.equals(actual)) guardarEnlace(id,enlace,a);
    }
    registrarHistorial(id,"EDITADA");
    if(horario) notificar(id,curso,"REPROGRAMADA","Horario actualizado: "+tema.trim());
    auditar(id,"CLASE_EDITADA",a);
  }
  @Transactional public void cancelar(UUID id,Authentication a) {
    comprobar(); var clase=db.queryForMap("SELECT curso_id,tema,estado FROM clases_academicas WHERE id=? FOR UPDATE",id);
    UUID curso=(UUID)clase.get("curso_id"); usuario.docente(a,curso);
    if("CANCELADA".equals(clase.get("estado"))) throw new IllegalStateException("La clase ya está cancelada");
    db.update("UPDATE clases_academicas SET estado='CANCELADA',modificado_en=now() WHERE id=?",id);
    registrarHistorial(id,"CANCELADA"); notificar(id,curso,"CANCELADA","Clase cancelada: "+clase.get("tema")); auditar(id,"CLASE_CANCELADA",a);
  }
  @Transactional public void actualizar(UUID clase,String enlace,Authentication a) {
    comprobar(); validarEnlace(enlace);
    var estado=db.queryForMap("SELECT curso_id,estado FROM clases_academicas WHERE id=? FOR UPDATE",clase);
    usuario.docente(a,(UUID)estado.get("curso_id"));
    if(!"PROGRAMADA".equals(estado.get("estado"))) throw new IllegalStateException("La clase está cancelada");
    guardarEnlace(clase,enlace,a);
  }
  private void guardarEnlace(UUID clase,String enlace,Authentication a) {
    Integer numero=db.queryForObject("SELECT COALESCE(max(numero),0)+1 FROM bloques_zoom WHERE clase_id=?",Integer.class,clase);
    db.update("INSERT INTO bloques_zoom(id,clase_id,numero,enlace,actualizado_por) VALUES (?,?,?,?,?)",UUID.randomUUID(),clase,numero,enlace,usuario.cuenta(a));
    auditar(clase,"ENLACE_ZOOM_ACTUALIZADO",a);
  }
  private void notificar(UUID clase,UUID curso,String tipo,String mensaje) {
    db.update("INSERT INTO notificaciones_clase(id,clase_id,estudiante_erp_id,tipo,mensaje) SELECT gen_random_uuid(),?,i.estudiante_erp_id,?,? FROM inscripciones_curso i WHERE i.curso_id=? AND i.activo",clase,tipo,mensaje,curso);
  }
  private void registrarHistorial(UUID clase,String accion) {
    db.update("INSERT INTO historial_clases(id,clase_id,accion,tema,inicio,fin,estado) SELECT gen_random_uuid(),id,?,tema,inicio,fin,estado FROM clases_academicas WHERE id=?",accion,clase);
  }
  private void auditar(UUID clase,String accion,Authentication a) {
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,clase);
  }
  public List<Map<String,Object>> historial(UUID clase,Authentication a) { comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso); return db.queryForList("SELECT numero,enlace,actualizado_en FROM bloques_zoom WHERE clase_id=? ORDER BY numero",clase); }
  public List<Map<String,Object>> historialClase(UUID clase,Authentication a) { comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso); return db.queryForList("SELECT accion,tema,inicio,fin,estado,registrado_en FROM historial_clases WHERE clase_id=? ORDER BY registrado_en,id",clase); }
}
