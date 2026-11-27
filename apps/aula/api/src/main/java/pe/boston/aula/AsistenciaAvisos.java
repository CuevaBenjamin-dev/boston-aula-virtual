package pe.boston.aula;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.boston.aula.integraciones.ErpCliente;

/** Entrega idempotente de avisos ERP al centro común del Aula. */
@Component
public class AsistenciaAvisos {
  private final ErpCliente erp;
  private final JdbcTemplate db;
  public AsistenciaAvisos(ErpCliente erp,JdbcTemplate db) { this.erp=erp;this.db=db; }
  @Scheduled(fixedDelay=60000,initialDelay=45000)
  public void distribuir() {
    try {
      var pendientes=erp.avisos("GET","/avisos");
      for(var aviso:pendientes) {
        String clave=aviso.path("clave").asText();
        db.update("""
          INSERT INTO notificaciones_asistencia(id,clave,destinatario_tipo,destinatario_erp_id,mensaje)
          VALUES(gen_random_uuid(),?,?,?::uuid,?) ON CONFLICT (clave) DO NOTHING
          """,clave,aviso.path("tipo").asText(),aviso.path("destinatario_id").asText(),aviso.path("mensaje").asText());
        erp.avisos("POST","/avisos/"+clave+"/confirmar");
      }
    } catch(Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("Avisos AST pendientes de entrega: {}",e.getMessage());
    }
  }
}
