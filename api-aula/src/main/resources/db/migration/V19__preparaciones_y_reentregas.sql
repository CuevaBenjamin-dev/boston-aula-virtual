CREATE TABLE preparaciones_entrega (
  id UUID PRIMARY KEY,
  tarea_id UUID NOT NULL REFERENCES tareas(id),
  estudiante_erp_id UUID NOT NULL,
  tarea_version INTEGER NOT NULL,
  intento INTEGER NOT NULL CHECK (intento>=1),
  respuesta TEXT NOT NULL DEFAULT '',
  estado VARCHAR(12) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA','CONFIRMADA')),
  entrega_id UUID UNIQUE REFERENCES entregas(id),
  clave_confirmacion UUID UNIQUE,
  creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(tarea_id,estudiante_erp_id,intento)
);
CREATE UNIQUE INDEX ux_preparacion_activa ON preparaciones_entrega(tarea_id,estudiante_erp_id) WHERE estado='ACTIVA';

CREATE TABLE autorizaciones_reentrega (
  tarea_id UUID NOT NULL REFERENCES tareas(id),
  estudiante_erp_id UUID NOT NULL,
  intento INTEGER NOT NULL CHECK (intento>=2),
  autorizada_por UUID NOT NULL,
  autorizada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  consumida_en TIMESTAMPTZ,
  PRIMARY KEY(tarea_id,estudiante_erp_id,intento)
);

ALTER TABLE archivos_entrega ALTER COLUMN entrega_id DROP NOT NULL;
ALTER TABLE archivos_entrega ADD COLUMN preparacion_id UUID REFERENCES preparaciones_entrega(id);
ALTER TABLE archivos_entrega ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE archivos_entrega ADD COLUMN sustituye_id UUID REFERENCES archivos_entrega(id);
ALTER TABLE archivos_entrega ADD CONSTRAINT archivo_entrega_un_padre
  CHECK ((entrega_id IS NOT NULL) <> (preparacion_id IS NOT NULL));
CREATE INDEX ix_archivos_preparacion ON archivos_entrega(preparacion_id,activo,estado);

ALTER TABLE calificaciones_tarea DROP CONSTRAINT calificaciones_tarea_tarea_id_estudiante_erp_id_key;
ALTER TABLE calificaciones_tarea ADD CONSTRAINT calificacion_por_intento
  UNIQUE(tarea_id,estudiante_erp_id,entrega_version);
ALTER TABLE notificaciones_tarea ADD COLUMN calificacion_id UUID REFERENCES calificaciones_tarea(id);
UPDATE notificaciones_tarea n SET calificacion_id=c.id
  FROM calificaciones_tarea c
  WHERE n.tipo='CALIFICADA' AND c.tarea_id=n.tarea_id AND c.estudiante_erp_id=n.destinatario_erp_id;
ALTER TABLE notificaciones_tarea DROP CONSTRAINT notificaciones_tarea_tipo_check;
ALTER TABLE notificaciones_tarea ADD CONSTRAINT notificaciones_tarea_tipo_check
  CHECK (tipo IN ('PUBLICADA','ACTUALIZADA','AMPLIADA','REABIERTA','REINICIADA',
                 'TRASLADADA','CERRADA','CANCELADA','ENTREGAS','CALIFICADA','REENTREGA_AUTORIZADA'));
