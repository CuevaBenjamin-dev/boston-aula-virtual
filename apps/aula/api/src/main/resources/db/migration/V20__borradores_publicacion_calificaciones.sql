ALTER TABLE calificaciones_tarea
  ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT 'PUBLICADA'
    CHECK (estado IN ('BORRADOR','PUBLICADA')),
  ADD COLUMN publicada_en TIMESTAMPTZ,
  ADD COLUMN publicada_por UUID,
  ADD COLUMN valor_borrador NUMERIC(8,2),
  ADD COLUMN comentario_borrador TEXT,
  ADD COLUMN borrador_actualizado_en TIMESTAMPTZ,
  ADD COLUMN borrador_actualizado_por UUID;

ALTER TABLE calificaciones_tarea ALTER COLUMN valor DROP NOT NULL;

UPDATE calificaciones_tarea
SET publicada_en=actualizado_en, publicada_por=actualizado_por;

ALTER TABLE historial_calificaciones
  ADD COLUMN comentario_anterior TEXT,
  ADD COLUMN estado_anterior VARCHAR(12),
  ADD COLUMN estado_nuevo VARCHAR(12) NOT NULL DEFAULT 'PUBLICADA',
  ADD COLUMN operacion_id UUID UNIQUE;

UPDATE historial_calificaciones h
SET comentario_anterior=anterior.comentario_anterior
FROM (
  SELECT id,LAG(comentario) OVER
    (PARTITION BY calificacion_id ORDER BY instante,id) AS comentario_anterior
  FROM historial_calificaciones
) anterior
WHERE anterior.id=h.id;
