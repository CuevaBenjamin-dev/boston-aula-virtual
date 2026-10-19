package pe.boston.aula;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service public class AvisosTarea {
  private final JdbcTemplate db;
  public AvisosTarea(JdbcTemplate db) { this.db=db; }

  public void estudiantes(UUID tarea,UUID curso,String tipo,String titulo,int version) {
    String mensaje=switch(tipo) {
      case "PUBLICADA" -> "Nueva tarea: "+titulo;
      case "REINICIADA" -> "La tarea cambió. Debes entregarla nuevamente: "+titulo;
      case "CERRADA" -> "Tarea cerrada: "+titulo;
      case "CANCELADA" -> "Tarea cancelada: "+titulo;
      case "REABIERTA" -> "Tarea reabierta: "+titulo;
      case "AMPLIADA" -> "Se amplió el plazo de la tarea: "+titulo;
      default -> "Tarea actualizada: "+titulo;
    };
    db.update("""
      INSERT INTO notificaciones_tarea(id,tarea_id,curso_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,clave_evento)
      SELECT gen_random_uuid(),?,?,'ESTUDIANTE',i.estudiante_erp_id,?,?,
             ? || ':' || ?::text || ':' || ?::text || ':' || i.estudiante_erp_id::text
      FROM inscripciones_curso i WHERE i.curso_id=? AND i.activo
      ON CONFLICT (clave_evento) DO NOTHING
      """,tarea,curso,tipo,mensaje,tipo,tarea,version,curso);
  }

  public void traslado(UUID tarea,UUID curso,String titulo,int version) {
    db.update("""
      INSERT INTO notificaciones_tarea(id,tarea_id,curso_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,clave_evento)
      SELECT gen_random_uuid(),?,?,'ESTUDIANTE',i.estudiante_erp_id,'TRASLADADA',
             'La tarea se trasladó a otra sección: ' || ?,
             'TRASLADADA:' || ?::text || ':' || ?::text || ':' || i.estudiante_erp_id::text
      FROM inscripciones_curso i WHERE i.curso_id=? AND i.activo
      ON CONFLICT (clave_evento) DO NOTHING
      """,tarea,curso,titulo,tarea,version,curso);
  }

  public void entrega(UUID entrega,UUID tarea,UUID curso,String titulo) {
    var docentes=db.queryForList("SELECT docente_erp_id FROM docentes_curso WHERE curso_id=? AND activo",UUID.class,curso);
    for(UUID docente:docentes) {
      int nuevo=db.update("INSERT INTO eventos_notificacion_entrega(entrega_id,docente_erp_id) VALUES (?,?) ON CONFLICT DO NOTHING",entrega,docente);
      if(nuevo==0) continue;
      UUID aviso=db.queryForObject("""
        INSERT INTO notificaciones_tarea(id,tarea_id,curso_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,nuevas_entregas)
        VALUES (gen_random_uuid(),?,?,'DOCENTE',?,'ENTREGAS','Nueva entrega en ' || ?,1)
        ON CONFLICT (destinatario_erp_id,tarea_id)
          WHERE destinatario_tipo='DOCENTE' AND tipo='ENTREGAS' AND leida_en IS NULL
        DO UPDATE SET nuevas_entregas=notificaciones_tarea.nuevas_entregas+1,actualizada_en=now(),
                      mensaje=CASE WHEN notificaciones_tarea.nuevas_entregas+1=1 THEN 'Nueva entrega en ' || ?
                                   ELSE (notificaciones_tarea.nuevas_entregas+1)::text || ' entregas nuevas en ' || ? END
        RETURNING id
        """,UUID.class,tarea,curso,docente,titulo,titulo,titulo);
      db.update("UPDATE eventos_notificacion_entrega SET notificacion_id=? WHERE entrega_id=? AND docente_erp_id=?",aviso,entrega,docente);
    }
  }

  public void calificacion(UUID tarea,UUID curso,UUID estudiante,String titulo,UUID nota,int version,boolean actualizada) {
    db.update("""
      INSERT INTO notificaciones_tarea(id,tarea_id,curso_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,clave_evento,calificacion_id)
      VALUES (gen_random_uuid(),?,?,'ESTUDIANTE',?,'CALIFICADA',?,
              'CALIFICADA:' || ?::text || ':' || ?::text,?)
      ON CONFLICT (clave_evento) DO NOTHING
      """,tarea,curso,estudiante,
      (actualizada?"Se actualizó la calificación de tu tarea: ":"Tu tarea fue calificada: ")+titulo,
      nota,version,nota);
  }
  public void reentrega(UUID tarea,UUID curso,UUID estudiante,String titulo,int intento) {
    db.update("""
      INSERT INTO notificaciones_tarea(id,tarea_id,curso_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,clave_evento)
      VALUES (gen_random_uuid(),?,?,'ESTUDIANTE',?,'REENTREGA_AUTORIZADA',?,
              'REENTREGA:' || ?::text || ':' || ?::text || ':' || ?::text)
      ON CONFLICT (clave_evento) DO NOTHING
      """,tarea,curso,estudiante,"Puedes realizar una nueva entrega de la tarea: "+titulo,tarea,estudiante,intento);
  }
}
