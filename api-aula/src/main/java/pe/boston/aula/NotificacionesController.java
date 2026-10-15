package pe.boston.aula;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula/notificaciones") public class NotificacionesController {
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public NotificacionesController(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  @GetMapping public List<Map<String,Object>> listar(Authentication a) {
    usuario.estudiante(a);
    return db.queryForList("""
      SELECT n.id,n.clase_id,n.tipo,n.mensaje,n.creada_en,n.leida_en,c.tema,c.inicio,
             curso.asignatura,curso.grado,curso.seccion
      FROM notificaciones_clase n JOIN clases_academicas c ON c.id=n.clase_id
      JOIN cursos curso ON curso.id=c.curso_id
      JOIN inscripciones_curso i ON i.curso_id=c.curso_id AND i.estudiante_erp_id=n.estudiante_erp_id AND i.activo
      WHERE n.estudiante_erp_id=? ORDER BY n.creada_en DESC LIMIT 100
      """,usuario.referencia(a));
  }
  @PostMapping("/{id}/leer") public void leer(@PathVariable UUID id,Authentication a) {
    usuario.estudiante(a);
    db.update("""
      UPDATE notificaciones_clase n SET leida_en=COALESCE(leida_en,now())
      FROM clases_academicas c,inscripciones_curso i
      WHERE n.id=? AND n.estudiante_erp_id=? AND c.id=n.clase_id
        AND i.curso_id=c.curso_id AND i.estudiante_erp_id=n.estudiante_erp_id AND i.activo
      """,id,usuario.referencia(a));
  }
}
