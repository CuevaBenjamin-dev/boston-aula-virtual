ALTER TABLE clases_academicas DROP CONSTRAINT clases_academicas_estado_check;
ALTER TABLE clases_academicas ADD CONSTRAINT clases_academicas_estado_check
  CHECK (estado IN ('PROGRAMADA','FINALIZADA','CANCELADA'));
ALTER TABLE clases_academicas ADD COLUMN operacion_id UUID;
ALTER TABLE clases_academicas ADD COLUMN solicitud_hash VARCHAR(64);
CREATE UNIQUE INDEX ux_clases_operacion_curso ON clases_academicas(operacion_id,curso_id)
  WHERE operacion_id IS NOT NULL;

ALTER TABLE bloques_zoom ADD COLUMN operacion_id UUID;
CREATE UNIQUE INDEX ux_bloques_zoom_operacion ON bloques_zoom(operacion_id)
  WHERE operacion_id IS NOT NULL;

ALTER TABLE historial_clases DROP CONSTRAINT historial_clases_accion_check;
ALTER TABLE historial_clases ADD CONSTRAINT historial_clases_accion_check
  CHECK (accion IN ('MIGRADA','CREADA','EDITADA','ENLACE','CANCELADA','FINALIZADA'));
ALTER TABLE historial_clases ADD COLUMN registrado_por UUID;
ALTER TABLE historial_clases ADD COLUMN enlace TEXT;
ALTER TABLE historial_clases ADD COLUMN operacion_id UUID UNIQUE;
UPDATE historial_clases h SET enlace=(
  SELECT b.enlace FROM bloques_zoom b WHERE b.clase_id=h.clase_id
    AND b.actualizado_en<=h.registrado_en ORDER BY b.actualizado_en DESC,b.numero DESC LIMIT 1
);

ALTER TABLE notificaciones_clase DROP CONSTRAINT notificaciones_clase_tipo_check;
ALTER TABLE notificaciones_clase ADD CONSTRAINT notificaciones_clase_tipo_check
  CHECK (tipo IN ('PROGRAMADA','REPROGRAMADA','ACTUALIZADA','CONTINUACION','FINALIZADA','CANCELADA'));
ALTER TABLE notificaciones_clase ADD COLUMN evento_id UUID REFERENCES historial_clases(id);
CREATE UNIQUE INDEX ux_notificacion_clase_evento_estudiante
  ON notificaciones_clase(evento_id,estudiante_erp_id) WHERE evento_id IS NOT NULL;
