package pe.boston.aula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service public class Sincronizacion {
  private final JdbcTemplate db;
  private final ErpCliente erp;
  private final TransactionTemplate transaccion;
  private final ObjectMapper json=new ObjectMapper();
  public Sincronizacion(JdbcTemplate db,ErpCliente erp,TransactionTemplate transaccion) { this.db=db; this.erp=erp; this.transaccion=transaccion; }
  @Scheduled(fixedDelay=15000,initialDelay=3000) public void ejecutar() {
    try {
      long ultimo=db.queryForObject("SELECT ultima_secuencia FROM estado_sincronizacion WHERE id=1",Long.class);
      JsonNode eventos=erp.get("/api/erp/eventos?despues="+ultimo+"&limite=100");
      for(JsonNode evento:eventos) transaccion.executeWithoutResult(estado -> aplicar(evento));
      db.update("UPDATE estado_sincronizacion SET ultimo_exito=now(),ultimo_error=NULL WHERE id=1");
    } catch(Exception e) {
      db.update("UPDATE estado_sincronizacion SET ultimo_error=? WHERE id=1",e.getClass().getSimpleName()+": "+e.getMessage());
    }
  }
  public void aplicar(JsonNode evento) {
    long secuencia=evento.get("secuencia").asLong();
    if(db.queryForObject("SELECT count(*) FROM eventos_procesados WHERE secuencia_erp=?",Long.class,secuencia)>0) return;
    JsonNode d=evento.get("datos");
    try {
      if(d.isTextual()) d=json.readTree(d.asText());
      if(d.has("value")) d=json.readTree(d.get("value").asText());
    } catch(Exception e) { throw new IllegalArgumentException("Evento ERP inválido",e); }
    UUID curso=UUID.fromString(d.get("cursoId").asText());
    db.update("INSERT INTO cursos(id,seccion_erp_id,asignatura,seccion,grado,periodo) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET asignatura=EXCLUDED.asignatura,seccion=EXCLUDED.seccion,grado=EXCLUDED.grado,periodo=EXCLUDED.periodo,actualizado_en=now()",
      curso,UUID.fromString(d.get("seccionId").asText()),d.get("asignatura").asText(),d.get("seccion").asText(),d.get("grado").asText(),d.get("periodo").asText());
    db.update("UPDATE inscripciones_curso SET activo=FALSE,actualizado_en=now() WHERE curso_id=?",curso);
    for(JsonNode id:d.get("estudiantes")) db.update("INSERT INTO inscripciones_curso(curso_id,estudiante_erp_id,activo) VALUES (?,?,TRUE) ON CONFLICT(curso_id,estudiante_erp_id) DO UPDATE SET activo=TRUE,actualizado_en=now()",curso,UUID.fromString(id.asText()));
    db.update("UPDATE docentes_curso SET activo=FALSE WHERE curso_id=?",curso);
    for(JsonNode id:d.get("docentes")) db.update("INSERT INTO docentes_curso(curso_id,docente_erp_id,activo) VALUES (?,?,TRUE) ON CONFLICT(curso_id,docente_erp_id) DO UPDATE SET activo=TRUE",curso,UUID.fromString(id.asText()));
    db.update("INSERT INTO eventos_procesados(id,secuencia_erp) VALUES (?,?)",UUID.randomUUID(),secuencia);
    db.update("INSERT INTO eventos_integracion(id,secuencia_erp,tipo,estado,procesado_en) VALUES (?,?,?,'PROCESADO',now())",UUID.randomUUID(),secuencia,evento.get("tipo").asText());
    db.update("UPDATE estado_sincronizacion SET ultima_secuencia=? WHERE id=1",secuencia);
  }
}
