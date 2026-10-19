package pe.boston.aula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/aula/historial") public class HistorialAcademicoController {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final ErpCliente erp;
  public HistorialAcademicoController(JdbcTemplate db,UsuarioActual usuario,ErpCliente erp) { this.db=db;this.usuario=usuario;this.erp=erp; }

  @GetMapping("/cursos") public List<Map<String,Object>> cursos(Authentication a) {
    String tipo=usuario.tipo(a);
    String sql="""
      SELECT c.id,c.asignatura,c.nivel,c.grado,c.seccion,c.periodo,c.periodo_fin,
             (SELECT count(*) FROM inscripciones_curso i WHERE i.curso_id=c.id AND NOT i.activo) AS estudiantes_historicos,
             (SELECT count(*) FROM tareas t WHERE t.curso_id=c.id) AS tareas
      FROM cursos c WHERE (c.periodo_fin<CURRENT_DATE OR EXISTS
        (SELECT 1 FROM inscripciones_curso i WHERE i.curso_id=c.id AND NOT i.activo))
      """;
    return switch(tipo) {
      case "ADMINISTRADOR" -> db.queryForList(sql+" ORDER BY c.periodo DESC,c.asignatura,c.grado,c.seccion");
      case "COORDINACION" -> db.queryForList(sql+" AND EXISTS (SELECT 1 FROM coordinaciones_seccion x WHERE x.seccion_erp_id=c.seccion_erp_id AND x.cuenta_id=? AND x.confirmado_erp) ORDER BY c.periodo DESC,c.asignatura,c.grado,c.seccion",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Historial reservado a Coordinación y Administración");
    };
  }

  @GetMapping("/cursos/{curso}/estudiantes") public List<Map<String,Object>> estudiantes(@PathVariable UUID curso,Authentication a) {
    autorizar(curso,a);
    var filas=db.queryForList("""
      SELECT i.estudiante_erp_id,i.activo,i.actualizado_en,
        (SELECT count(*) FROM entregas e JOIN tareas t ON t.id=e.tarea_id
         WHERE t.curso_id=i.curso_id AND e.estudiante_erp_id=i.estudiante_erp_id) AS entregas,
        (SELECT count(*) FROM calificaciones_tarea n JOIN tareas t ON t.id=n.tarea_id
         WHERE t.curso_id=i.curso_id AND n.estudiante_erp_id=i.estudiante_erp_id) AS calificaciones
      FROM inscripciones_curso i WHERE i.curso_id=? ORDER BY i.activo,i.actualizado_en DESC
      """,curso);
    Map<String,String> nombres=new HashMap<>();
    try { for(var e:erp.get("/api/erp/cursos/"+curso+"/estudiantes/resumen")) nombres.put(e.get("id").asText(),e.get("nombre").asText()); }
    catch(Exception ignored) { /* El historial permanece disponible si el ERP está temporalmente fuera de línea. */ }
    for(var fila:filas) fila.put("estudiante",nombres.getOrDefault(fila.get("estudiante_erp_id").toString(),"Estudiante "+fila.get("estudiante_erp_id")));
    return filas;
  }

  @GetMapping("/cursos/{curso}/estudiantes/{estudiante}") public List<Map<String,Object>> actividad(@PathVariable UUID curso,@PathVariable UUID estudiante,Authentication a) {
    autorizar(curso,a);
    if(db.queryForObject("SELECT count(*) FROM inscripciones_curso WHERE curso_id=? AND estudiante_erp_id=?",Long.class,curso,estudiante)==0)
      throw new AccessDeniedException("Estudiante ajeno al curso");
    return db.queryForList("""
      SELECT t.id AS tarea_id,t.titulo,t.estado,t.tarea_anterior_id,
        e.id AS entrega_id,e.entregado_en,e.respuesta,
        n.valor,n.comentario,n.actualizado_en AS calificada_en
      FROM tareas t LEFT JOIN entregas e ON e.tarea_id=t.id AND e.estudiante_erp_id=?
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=t.id AND n.estudiante_erp_id=?
      WHERE t.curso_id=? AND (e.id IS NOT NULL OR n.id IS NOT NULL)
      ORDER BY t.creado_en DESC,e.entregado_en DESC
      """,estudiante,estudiante,curso);
  }

  private void autorizar(UUID curso,Authentication a) {
    switch(usuario.tipo(a)) {
      case "ADMINISTRADOR" -> usuario.administrador(a);
      case "COORDINACION" -> {
        if(db.queryForObject("SELECT count(*) FROM cursos c JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE c.id=? AND x.cuenta_id=? AND x.confirmado_erp",Long.class,curso,usuario.cuenta(a))==0)
          throw new AccessDeniedException("Curso fuera del ámbito de coordinación");
      }
      default -> throw new AccessDeniedException("Historial reservado");
    }
  }
}
