package pe.boston.aula;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prepara una sola vez las actividades de la instalación local, después de la primera sincronización ERP. */
@Component
@ConditionalOnProperty(name="BOSTON_DATOS_DEMO",havingValue="true")
public class DatosDemo {
  private static final UUID CURSO=UUID.fromString("00000000-0000-0000-0000-000000001001");
  private static final UUID DOCENTE=UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID TAREA=UUID.fromString("00000000-0000-0000-0000-000000002001");
  private static final UUID CLASE=UUID.fromString("00000000-0000-0000-0000-000000002002");
  private static final UUID BLOQUE=UUID.fromString("00000000-0000-0000-0000-000000002003");
  private final JdbcTemplate db;
  public DatosDemo(JdbcTemplate db) { this.db=db; }

  @Scheduled(initialDelay=10000,fixedDelay=30000)
  @Transactional
  public void preparar() {
    if(db.queryForObject("SELECT count(*) FROM preparacion_demo WHERE clave='academico_v1'",Integer.class)>0) return;
    if(db.queryForObject("SELECT count(*) FROM cursos WHERE id=?",Integer.class,CURSO)==0) return;
    db.update("INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,acepta_tarde,permite_reentrega,estado,escala_maxima,creado_por) VALUES (?,?,?,?,'2026-11-10 18:00:00-05',TRUE,TRUE,'PUBLICADA',20,?) ON CONFLICT (id) DO NOTHING",
      TAREA,CURSO,"Actividad de demostración","Resuelve los ejercicios de la clase. Puedes entregar y reentregar un archivo PDF o imagen.",DOCENTE);
    db.update("INSERT INTO clases_academicas(id,curso_id,tema,inicio,fin,creado_por) VALUES (?,?,?,'2026-10-20 09:00:00-05','2026-10-20 10:30:00-05',?) ON CONFLICT (id) DO NOTHING",
      CLASE,CURSO,"Clase de demostración",DOCENTE);
    db.update("INSERT INTO bloques_zoom(id,clase_id,numero,enlace,actualizado_por) VALUES (?,?,1,?,?) ON CONFLICT (id) DO NOTHING",
      BLOQUE,CLASE,"https://zoom.us/j/00000000000?pwd=DEMO-LOCAL",DOCENTE);
    db.update("INSERT INTO preparacion_demo(clave) VALUES ('academico_v1')");
  }
}
