package pe.boston.erp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class Tesoreria {
  private final JdbcTemplate db;
  public Tesoreria(JdbcTemplate db) { this.db=db; }
  @Transactional public UUID concepto(String nombre,Jwt actor) {
    Institucional.finanzas(actor);if(nombre==null||nombre.isBlank()||nombre.length()>120)throw new IllegalArgumentException("Concepto inválido");
    UUID id=UUID.randomUUID();db.update("INSERT INTO conceptos_cobro(id,nombre) VALUES (?,?)",id,nombre.trim());
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),actor.getSubject(),"CONCEPTO_CREADO",id);return id;
  }
  @Transactional public UUID obligacion(UUID estudiante,UUID concepto,String descripcion,BigDecimal importe,LocalDate vencimiento,Jwt actor) {
    Institucional.finanzas(actor);
    if(descripcion==null||descripcion.isBlank()||descripcion.length()>200||importe==null||importe.signum()<0||vencimiento==null)throw new IllegalArgumentException("Obligación inválida");
    UUID id=UUID.randomUUID();db.update("INSERT INTO obligaciones_pago(id,estudiante_id,concepto_id,descripcion,importe,vencimiento) VALUES (?,?,?,?,?,?)",id,estudiante,concepto,descripcion,importe,vencimiento);
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),actor.getSubject(),"OBLIGACION_CREADA",id);return id;
  }
  public Map<String,Object> estado(UUID estudiante, Jwt actor) {
    Institucional.lectura(actor,estudiante);
    List<Map<String,Object>> obligaciones=db.queryForList("""
      SELECT o.id,o.descripcion,o.importe,o.vencimiento,o.estado,
      COALESCE((SELECT sum(a.importe) FROM aplicaciones_pago a JOIN pagos p ON p.id=a.pago_id WHERE a.obligacion_id=o.id AND p.estado='REGISTRADO'),0) AS pagado,
      o.importe-COALESCE((SELECT sum(a.importe) FROM aplicaciones_pago a JOIN pagos p ON p.id=a.pago_id WHERE a.obligacion_id=o.id AND p.estado='REGISTRADO'),0) AS saldo
      FROM obligaciones_pago o WHERE o.estudiante_id=? ORDER BY o.vencimiento
      """,estudiante);
    List<Map<String,Object>> pagos=db.queryForList("SELECT id,referencia,importe,estado,registrado_en FROM pagos WHERE estudiante_id=? ORDER BY registrado_en DESC",estudiante);
    BigDecimal saldo=obligaciones.stream().map(o->(BigDecimal)o.get("saldo")).reduce(BigDecimal.ZERO,BigDecimal::add);
    return Map.of("obligaciones",obligaciones,"pagos",pagos,"saldoTotal",saldo,"pagoEnLineaHabilitado",false);
  }
  @Transactional public UUID registrar(UUID estudiante,UUID obligacion,BigDecimal importe,String referencia,Jwt actor) {
    Institucional.finanzas(actor);
    if (importe==null || importe.signum()<=0 || importe.scale()>2 || referencia==null || referencia.isBlank() || referencia.length()>100) throw new IllegalArgumentException("Importe o referencia inválidos");
    BigDecimal saldo=db.queryForObject("""
      SELECT o.importe-COALESCE((SELECT sum(a.importe) FROM aplicaciones_pago a JOIN pagos p ON p.id=a.pago_id WHERE a.obligacion_id=o.id AND p.estado='REGISTRADO'),0)
      FROM obligaciones_pago o WHERE o.id=? AND o.estudiante_id=? FOR UPDATE
      """,BigDecimal.class,obligacion,estudiante);
    if (saldo==null || importe.compareTo(saldo)>0) throw new IllegalArgumentException("Importe supera saldo o obligación ajena");
    UUID pago=UUID.randomUUID();
    db.update("INSERT INTO pagos(id,referencia,estudiante_id,importe,estado,registrado_por) VALUES (?,?,?,?,'REGISTRADO',?)",pago,referencia,estudiante,importe,actor.getSubject());
    db.update("INSERT INTO aplicaciones_pago(id,pago_id,obligacion_id,importe) VALUES (?,?,?,?)",UUID.randomUUID(),pago,obligacion,importe);
    db.update("UPDATE obligaciones_pago SET estado=? WHERE id=?",importe.compareTo(saldo)==0 ? "PAGADA":"PARCIAL",obligacion);
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),actor.getSubject(),"PAGO_REGISTRADO",pago);
    return pago;
  }
  @Transactional public void anular(UUID pago,String motivo,Jwt actor) {
    Institucional.finanzas(actor);
    if (motivo==null || motivo.isBlank()) throw new IllegalArgumentException("Motivo requerido");
    int n=db.update("UPDATE pagos SET estado='ANULADO',motivo_anulacion=? WHERE id=? AND estado='REGISTRADO'",motivo,pago);
    if(n==0) throw new IllegalArgumentException("Pago inexistente o anulado");
    List<UUID> obligaciones=db.query("SELECT obligacion_id FROM aplicaciones_pago WHERE pago_id=?",(rs,i)->(UUID)rs.getObject(1),pago);
    for(UUID id:obligaciones) db.update("""
      UPDATE obligaciones_pago o SET estado=CASE WHEN x.pagado=0 THEN 'PENDIENTE' WHEN x.pagado>=o.importe THEN 'PAGADA' ELSE 'PARCIAL' END
      FROM (SELECT COALESCE(sum(a.importe),0) AS pagado FROM aplicaciones_pago a JOIN pagos p ON p.id=a.pago_id WHERE a.obligacion_id=? AND p.estado='REGISTRADO') x WHERE o.id=?
      """,id,id);
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id,detalle) VALUES (?,?,?, ?,jsonb_build_object('motivo',?::text))",UUID.randomUUID(),actor.getSubject(),"PAGO_ANULADO",pago,motivo);
  }
}

