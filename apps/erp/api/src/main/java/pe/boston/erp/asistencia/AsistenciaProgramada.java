package pe.boston.erp.asistencia;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Configuration @EnableScheduling
class AsistenciaReloj {
  @Bean Clock relojAsistencia() { return Clock.system(AsistenciaService.LIMA); }
}

/** Recupera días AST habilitados tras una interrupción; cada jornada cierra una sola vez. */
@Component
public class AsistenciaProgramada {
  private final AsistenciaService ast;
  private final JdbcTemplate db;
  public AsistenciaProgramada(AsistenciaService ast,JdbcTemplate db) {
    this.ast=ast;this.db=db;
  }
  @Scheduled(fixedDelay=300000,initialDelay=30000)
  public void recuperar() {
    LocalDate ayer=ast.hoy().minusDays(1);
    var calendarios=db.query("SELECT anio FROM ast_calendarios WHERE habilitado_desde<=?",
      (r,n)->r.getInt(1),ayer);
    var vistos=new HashSet<String>();
    for(Integer anio:calendarios) {
      for(var pendiente:ast.pendientes(anio)) {
        if(!"JORNADA_ABIERTA".equals(pendiente.get("causa"))) continue;
        UUID seccion=(UUID)pendiente.get("seccionId");
        LocalDate dia=(LocalDate)pendiente.get("fecha");
        vistos.add(seccion+":"+dia);
        cerrar(seccion,dia);
      }
    }
    // Una jornada completa también debe cerrar, aunque no figure entre pendientes.
    var completas=db.query("SELECT seccion_id,fecha FROM ast_jornadas WHERE cerrada_en IS NULL AND fecha<?",
      (r,n)->new Object[]{r.getObject(1,UUID.class),r.getDate(2).toLocalDate()},ast.hoy());
    for(Object[] fila:completas) if(vistos.add(fila[0]+":"+fila[1])) cerrar((UUID)fila[0],(LocalDate)fila[1]);
    try { ast.generarRecordatorios(); }
    catch(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Recordatorios AST pendientes: {}",e.getMessage()); }
  }
  private void cerrar(UUID seccion,LocalDate dia) {
    try { ast.cerrar(seccion,dia); }
    catch(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cierre AST pendiente en {} {}: {}",
      seccion,dia,e.getMessage()); }
  }
}
