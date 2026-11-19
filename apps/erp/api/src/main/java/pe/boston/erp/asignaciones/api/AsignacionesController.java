package pe.boston.erp.asignaciones.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.personas.PersonasParaAsignaciones;
import pe.boston.erp.plataforma.AccesoErp;

@RestController
@RequestMapping("/api/erp/v1/asignaciones")
public class AsignacionesController {
  public record Entrada(UUID docenteId, UUID seccionId, UUID asignaturaId, String tipo,
    LocalDate desde, LocalDate hasta, String horario) {}
  public record Asignacion(UUID id, UUID docenteId, String docente, UUID seccionId,
    String seccion, UUID asignaturaId, String asignatura, int anio, String tipo,
    LocalDate desde, LocalDate hasta, String horario, String estado, boolean coberturaPendiente) {}
  private final JdbcTemplate jdbc;
  private final PersonasParaAsignaciones personas;
  public AsignacionesController(JdbcTemplate jdbc,PersonasParaAsignaciones personas) {
    this.jdbc=jdbc;this.personas=personas;
  }

  @GetMapping public List<Asignacion> listar(@RequestParam(required=false) Integer anio, Authentication auth) {
    AccesoErp.actor(auth);
    return consultar(anio,null);
  }
  private List<Asignacion> consultar(Integer anio,UUID id) {
    String sql="""
      SELECT a.id,a.docente_id,p.nombres||' '||p.apellidos,a.seccion_id,
        s.nivel||' '||s.grado||'° '||s.nombre,a.asignatura_id,u.nombre,s.anio,
        a.tipo,a.desde,a.hasta,a.horario,a.estado,
        a.estado='ACTIVA' AND (a.hasta IS NULL OR a.hasta>=current_date)
          AND NOT EXISTS (SELECT 1 FROM per_condiciones c WHERE c.persona_id=a.docente_id
            AND c.tipo='DOCENTE' AND c.periodo @> now())
      FROM asi_asignaciones a JOIN per_personas p ON p.id=a.docente_id
      JOIN ofe_secciones s ON s.id=a.seccion_id JOIN ofe_asignaturas u ON u.id=a.asignatura_id
      WHERE (CAST(? AS INTEGER) IS NULL OR s.anio=?) AND (CAST(? AS UUID) IS NULL OR a.id=?)
      ORDER BY s.anio DESC,s.nivel,s.grado,s.nombre,u.nombre,a.desde DESC
      LIMIT 500
      """;
    return jdbc.query(sql,(r,n)->new Asignacion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),
      r.getObject(4,UUID.class),r.getString(5),r.getObject(6,UUID.class),r.getString(7),r.getInt(8),
      r.getString(9),r.getDate(10).toLocalDate(),r.getDate(11)==null?null:r.getDate(11).toLocalDate(),
      r.getString(12),r.getString(13),r.getBoolean(14)),anio,anio,id,id);
  }

  @PostMapping @Transactional public Asignacion crear(@RequestBody Entrada p, Authentication auth) {
    String actor=AccesoErp.actor(auth);
    if (p==null || p.docenteId()==null || p.seccionId()==null || p.asignaturaId()==null || p.desde()==null)
      throw new IllegalArgumentException("Docente, sección, asignatura y fecha de inicio son obligatorios");
    if (!"TITULAR".equals(p.tipo()) && !"REEMPLAZO".equals(p.tipo()))
      throw new IllegalArgumentException("Tipo de asignación inválido");
    if (p.hasta()!=null && p.hasta().isBefore(p.desde()))
      throw new IllegalArgumentException("La fecha final debe ser posterior al inicio");
    if (!personas.docenteActivo(p.docenteId(),auth))
      throw new IllegalArgumentException("La persona debe tener condición Docente activa");
    Long compatible=jdbc.queryForObject("""
      SELECT count(*) FROM ofe_secciones s JOIN ofe_asignaturas u
        ON s.anio=u.anio AND s.nivel=u.nivel AND s.grado=u.grado
      WHERE s.id=? AND u.id=?
      """,Long.class,p.seccionId(),p.asignaturaId());
    if (compatible==0) throw new IllegalArgumentException("La asignatura no pertenece al año y grado de esta sección");
    Long repetida=jdbc.queryForObject("""
      SELECT count(*) FROM asi_asignaciones WHERE docente_id=? AND seccion_id=? AND asignatura_id=?
        AND tipo=? AND desde=? AND hasta IS NOT DISTINCT FROM ? AND estado='ACTIVA'
      """,Long.class,p.docenteId(),p.seccionId(),p.asignaturaId(),p.tipo(),p.desde(),p.hasta());
    if (repetida>0) throw new IllegalArgumentException("Esta asignación activa ya existe");
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO asi_asignaciones(id,docente_id,seccion_id,asignatura_id,tipo,desde,hasta,horario,actor_sub) VALUES(?,?,?,?,?,?,?,?,?)",
      id,p.docenteId(),p.seccionId(),p.asignaturaId(),p.tipo(),p.desde(),p.hasta(),p.horario(),actor);
    solicitud(id,"ASIGNAR_DOCENTE");
    return consultar(null,id).getFirst();
  }

  @PostMapping("/{id}/cerrar") @Transactional public Asignacion cerrar(@PathVariable UUID id, Authentication auth) {
    AccesoErp.actor(auth);
    int filas=jdbc.update("UPDATE asi_asignaciones SET estado='CERRADA',cerrado_en=now() WHERE id=? AND estado='ACTIVA'",id);
    if (filas!=1) throw new IllegalArgumentException("Asignación activa no encontrada");
    solicitud(id,"CERRAR_ASIGNACION");
    return consultar(null,id).getFirst();
  }
  private void solicitud(UUID id,String accion) {
    jdbc.update("INSERT INTO erp_solicitudes_externas(id,clave_idempotencia,sistema,entidad_tipo,entidad_id,accion,motivo) VALUES(?,?,?,?,?,?,?) ON CONFLICT (clave_idempotencia) DO NOTHING",
      UUID.randomUUID(),"asignacion:"+id+":"+accion,"AULA","ASIGNACION",id,accion,"Contrato remoto de negocio aún no conectado");
  }
}
