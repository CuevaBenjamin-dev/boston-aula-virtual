ALTER TABLE notificaciones_tarea DROP CONSTRAINT notificaciones_tarea_tipo_check;
ALTER TABLE notificaciones_tarea ADD CONSTRAINT notificaciones_tarea_tipo_check
  CHECK (tipo IN ('PUBLICADA','ACTUALIZADA','AMPLIADA','REABIERTA','REINICIADA',
                 'TRASLADADA','CERRADA','CANCELADA','ENTREGAS','CALIFICADA'));
