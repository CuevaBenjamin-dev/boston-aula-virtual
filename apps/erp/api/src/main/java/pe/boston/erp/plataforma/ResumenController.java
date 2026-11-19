package pe.boston.erp.plataforma;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumenController {
  private final JdbcTemplate jdbc;
  public ResumenController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record Resumen(Integer anio, long personas, long borradores, long vigentes,
    long retiradas, long finalizadas, long secciones, long docentesActivos, long integracionesPendientes) {}

  @GetMapping("/api/erp/v1/resumen")
  public Resumen resumen(Authentication auth) {
    AccesoErp.actor(auth);
    Integer anio=jdbc.queryForObject("SELECT max(anio) FROM ofe_anios",Integer.class);
    long personas=valor("SELECT count(*) FROM per_personas");
    long borradores=valor("SELECT count(*) FROM mat_matriculas WHERE estado='BORRADOR'");
    long vigentes=valor("SELECT count(*) FROM mat_matriculas WHERE estado='VIGENTE'");
    long retiradas=valor("SELECT count(*) FROM mat_matriculas WHERE estado='RETIRADA'");
    long finalizadas=valor("SELECT count(*) FROM mat_matriculas WHERE estado='FINALIZADA'");
    long secciones=valor("SELECT count(*) FROM ofe_secciones");
    long docentes=valor("SELECT count(DISTINCT persona_id) FROM per_condiciones WHERE tipo='DOCENTE' AND periodo @> now()");
    long pendientes=valor("SELECT count(*) FROM erp_solicitudes_externas WHERE estado<>'CONFIRMADA'");
    return new Resumen(anio,personas,borradores,vigentes,retiradas,finalizadas,secciones,docentes,pendientes);
  }
  private long valor(String sql) { return jdbc.queryForObject(sql,Long.class); }
}
