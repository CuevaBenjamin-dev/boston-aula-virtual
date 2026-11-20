package pe.boston.aula.integraciones;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Proyecciones académicas: nunca modifica tareas, entregas ni notas. */
@RestController
@RequestMapping("/api/aula/integracion/v1/cursos")
public class ProyeccionesController {
  public record Docente(UUID personaId,String nombre) {}
  public record Curso(int version,UUID cursoId,UUID seccionId,UUID asignaturaId,
    int anio,String nivel,int grado,String seccion,String asignatura,String codigo,
    List<UUID> estudiantes,List<UUID> finalizados,List<Docente> docentes) {}
  public record Resultado(UUID cursoId,String estado) {}
  private final JdbcTemplate jdbc;
  public ProyeccionesController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  @PutMapping("/{id}") @Transactional
  public Resultado proyectar(@PathVariable UUID id,@RequestBody Curso entrada) {
    if (entrada==null || entrada.version()!=2 || !id.equals(entrada.cursoId())
      || entrada.seccionId()==null || entrada.asignaturaId()==null || entrada.anio()<2000
      || entrada.estudiantes()==null || entrada.finalizados()==null || entrada.docentes()==null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Proyección institucional inválida");
    var previo=jdbc.query("SELECT seccion_erp_id FROM cursos WHERE id=? FOR UPDATE",
      (rs,n)->rs.getObject(1,UUID.class),id);
    if (!previo.isEmpty() && !entrada.seccionId().equals(previo.getFirst()))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Curso institucional asociado a otra sección");
    jdbc.update("""
      INSERT INTO cursos(id,seccion_erp_id,asignatura,seccion,grado,nivel,periodo)
      VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
      asignatura=EXCLUDED.asignatura,seccion=EXCLUDED.seccion,grado=EXCLUDED.grado,
      nivel=EXCLUDED.nivel,periodo=EXCLUDED.periodo,actualizado_en=now()
      WHERE (cursos.asignatura,cursos.seccion,cursos.grado,cursos.nivel,cursos.periodo)
        IS DISTINCT FROM (EXCLUDED.asignatura,EXCLUDED.seccion,EXCLUDED.grado,EXCLUDED.nivel,EXCLUDED.periodo)
      """,id,entrada.seccionId(),entrada.asignatura(),entrada.seccion(),
      entrada.grado()+"°",entrada.nivel(),Integer.toString(entrada.anio()));
    Set<UUID> estudiantes=new HashSet<>(entrada.estudiantes());
    Set<UUID> finalizados=new HashSet<>(entrada.finalizados());
    if (estudiantes.size()!=entrada.estudiantes().size() || estudiantes.contains(null)
      || finalizados.size()!=entrada.finalizados().size() || finalizados.contains(null)
      || !java.util.Collections.disjoint(estudiantes,finalizados))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estudiantes repetidos o inválidos");
    for(UUID estudiante:estudiantes)
      jdbc.update("INSERT INTO inscripciones_curso(curso_id,estudiante_erp_id,activo,estado_matricula) VALUES(?,?,TRUE,'VIGENTE') ON CONFLICT(curso_id,estudiante_erp_id) DO UPDATE SET activo=TRUE,estado_matricula='VIGENTE',actualizado_en=now() WHERE NOT inscripciones_curso.activo OR inscripciones_curso.estado_matricula<>'VIGENTE'",id,estudiante);
    for(UUID estudiante:finalizados)
      jdbc.update("INSERT INTO inscripciones_curso(curso_id,estudiante_erp_id,activo,estado_matricula) VALUES(?,?,FALSE,'FINALIZADA') ON CONFLICT(curso_id,estudiante_erp_id) DO UPDATE SET activo=FALSE,estado_matricula='FINALIZADA',actualizado_en=now() WHERE inscripciones_curso.activo OR inscripciones_curso.estado_matricula<>'FINALIZADA'",id,estudiante);
    Set<UUID> anteriores=new HashSet<>(jdbc.queryForList(
      "SELECT estudiante_erp_id FROM inscripciones_curso WHERE curso_id=?",UUID.class,id));
    for(UUID estudiante:anteriores) if (!estudiantes.contains(estudiante) && !finalizados.contains(estudiante))
      jdbc.update("UPDATE inscripciones_curso SET activo=FALSE,estado_matricula='RETIRADA',actualizado_en=now() WHERE curso_id=? AND estudiante_erp_id=? AND (activo OR estado_matricula<>'RETIRADA')",id,estudiante);
    Set<UUID> docentes=new HashSet<>();
    Set<UUID> docentesAnteriores=new HashSet<>(jdbc.queryForList(
      "SELECT docente_erp_id FROM docentes_curso WHERE curso_id=? AND activo",UUID.class,id));
    for(Docente docente:entrada.docentes()) {
      if (docente==null || docente.personaId()==null || !docentes.add(docente.personaId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Docentes repetidos o inválidos");
      jdbc.update("""
        INSERT INTO docentes_curso(curso_id,docente_erp_id,activo,nombre_completo) VALUES(?,?,TRUE,?)
        ON CONFLICT(curso_id,docente_erp_id) DO UPDATE SET activo=TRUE,nombre_completo=EXCLUDED.nombre_completo
        WHERE NOT docentes_curso.activo OR docentes_curso.nombre_completo IS DISTINCT FROM EXCLUDED.nombre_completo
        """,id,docente.personaId(),docente.nombre());
    }
    for(UUID docente:docentesAnteriores) if (!docentes.contains(docente))
      jdbc.update("UPDATE docentes_curso SET activo=FALSE WHERE curso_id=? AND docente_erp_id=? AND activo",id,docente);
    return new Resultado(id,"CONFIRMADA");
  }
}
