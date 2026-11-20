package pe.boston.erp.plataforma;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/erp/v1/integracion")
public class IntegracionEstadoController {
  public record Estado(UUID id,String sistema,String accion,String estado,String motivo,String actualizadoEn) {}
  private final JdbcTemplate db;
  public IntegracionEstadoController(JdbcTemplate db) { this.db=db; }

  @GetMapping("/estado") public List<Estado> estado(@RequestParam String tipo,@RequestParam UUID id,Authentication auth) {
    AccesoErp.actor(auth);
    if (!List.of("MATRICULA","ASIGNACION","PERSONA").contains(tipo))
      throw new IllegalArgumentException("Tipo de integración inválido");
    return db.query("""
      SELECT id,sistema,accion,estado,motivo,actualizado_en::text FROM erp_solicitudes_externas
      WHERE entidad_tipo=? AND entidad_id=? ORDER BY creado_en DESC LIMIT 20
      """,(r,n)->new Estado(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
        r.getString(4),r.getString(5),r.getString(6)),tipo,id);
  }
  @PostMapping("/{id}/reintentar") public void reintentar(@PathVariable UUID id,Authentication auth) {
    AccesoErp.actor(auth);
    int filas=db.update("""
      UPDATE erp_solicitudes_externas SET estado='ERROR',proximo_intento=NULL,
        motivo='Reintento solicitado por Coordinación o Dirección',actualizado_en=now()
      WHERE id=? AND estado='INTERVENCION'
      """,id);
    if(filas!=1) throw new IllegalArgumentException("Solicitud no requiere intervención");
  }
}
