ALTER TABLE tareas DROP CONSTRAINT tareas_estado_check;
ALTER TABLE tareas ADD CONSTRAINT tareas_estado_check
  CHECK (estado IN ('FALTA_COMPLETAR','PUBLICADA','CERRADA','CANCELADA'));
ALTER TABLE tareas ALTER COLUMN acepta_tarde SET DEFAULT TRUE;
UPDATE tareas SET acepta_tarde=TRUE WHERE NOT acepta_tarde;

-- El estado y el plazo se congelan al recibir cada entrega. Para registros previos
-- se conserva el plazo actualmente conocido; no es posible reconstruir uno anterior.
ALTER TABLE entregas ADD COLUMN fecha_limite_al_entregar TIMESTAMPTZ;
UPDATE entregas e SET fecha_limite_al_entregar=t.fecha_limite
  FROM tareas t WHERE t.id=e.tarea_id;
ALTER TABLE entregas ALTER COLUMN fecha_limite_al_entregar SET NOT NULL;

CREATE TABLE lotes_tarea (
  id UUID PRIMARY KEY,
  creado_por UUID NOT NULL,
  solicitud_hash VARCHAR(64) NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE tareas ADD COLUMN lote_id UUID REFERENCES lotes_tarea(id);
CREATE INDEX ix_tareas_lote ON tareas(lote_id) WHERE lote_id IS NOT NULL;

ALTER TABLE registros_auditoria ADD COLUMN operacion_id UUID UNIQUE;

ALTER TABLE notificaciones_tarea DROP CONSTRAINT notificaciones_tarea_tipo_check;
ALTER TABLE notificaciones_tarea ADD CONSTRAINT notificaciones_tarea_tipo_check
  CHECK (tipo IN ('PUBLICADA','ACTUALIZADA','AMPLIADA','REABIERTA','REINICIADA',
                 'TRASLADADA','CERRADA','CANCELADA','ENTREGAS'));
