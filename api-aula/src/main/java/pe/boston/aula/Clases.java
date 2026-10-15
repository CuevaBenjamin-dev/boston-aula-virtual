package pe.boston.aula;

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
  public List<Map<String,Object>> listar(UUID curso,Authentication a) { comprobar(); usuario.leerCurso(a,curso); return db.queryForList("SELECT c.*, (SELECT enlace FROM bloques_zoom b WHERE b.clase_id=c.id ORDER BY numero DESC LIMIT 1) AS enlace_actual,(SELECT max(actualizado_en) FROM bloques_zoom b WHERE b.clase_id=c.id) AS enlace_actualizado FROM clases_academicas c WHERE curso_id=? ORDER BY inicio DESC",curso); }
  @Transactional public UUID crear(UUID curso,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace,Authentication a) {
    comprobar(); usuario.docente(a,curso); validarEnlace(enlace); if(inicio==null||fin==null||!fin.isAfter(inicio)) throw new IllegalArgumentException("Horario inválido");
    UUID id=UUID.randomUUID(); db.update("INSERT INTO clases_academicas(id,curso_id,tema,inicio,fin,creado_por) VALUES (?,?,?,?,?,?)",id,curso,tema,inicio,fin,usuario.cuenta(a));
    actualizar(id,enlace,a); return id;
  }
  @Transactional public void actualizar(UUID clase,String enlace,Authentication a) {
    comprobar(); validarEnlace(enlace);
    UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.docente(a,curso);
    Integer numero=db.queryForObject("SELECT COALESCE(max(numero),0)+1 FROM bloques_zoom WHERE clase_id=?",Integer.class,clase);
    db.update("INSERT INTO bloques_zoom(id,clase_id,numero,enlace,actualizado_por) VALUES (?,?,?,?,?)",UUID.randomUUID(),clase,numero,enlace,usuario.cuenta(a));
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),"ENLACE_ZOOM_ACTUALIZADO",clase);
  }
  public List<Map<String,Object>> historial(UUID clase,Authentication a) { comprobar(); UUID curso=db.queryForObject("SELECT curso_id FROM clases_academicas WHERE id=?",UUID.class,clase); usuario.leerCurso(a,curso); return db.queryForList("SELECT numero,enlace,actualizado_en FROM bloques_zoom WHERE clase_id=? ORDER BY numero",clase); }
  private void validarEnlace(String enlace) { if(enlace==null || !enlace.matches("https://[^\\s]+")) throw new IllegalArgumentException("Enlace HTTPS requerido"); }
}
