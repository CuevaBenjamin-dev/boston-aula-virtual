ALTER TABLE tareas DROP CONSTRAINT IF EXISTS tareas_estado_check;
UPDATE tareas SET estado='FALTA_COMPLETAR' WHERE estado='BORRADOR';
ALTER TABLE tareas ADD CONSTRAINT tareas_estado_check CHECK (estado IN ('FALTA_COMPLETAR','PUBLICADA','CERRADA'));
ALTER TABLE tareas ADD COLUMN publicado_en TIMESTAMPTZ;
UPDATE tareas SET publicado_en=creado_en WHERE estado IN ('PUBLICADA','CERRADA');
ALTER TABLE archivos_tarea ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX ix_archivos_tarea_activos ON archivos_tarea(tarea_id,activo,creado_en DESC);
