ALTER TABLE matriculas DROP CONSTRAINT matriculas_estado_check;
ALTER TABLE matriculas ADD CONSTRAINT matriculas_estado_check CHECK (estado IN ('ACTIVA','RETIRADA','FINALIZADA'));

CREATE TABLE ambitos_coordinacion (
  cuenta_id UUID NOT NULL,
  seccion_id UUID NOT NULL REFERENCES secciones(id),
  asignado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (cuenta_id,seccion_id)
);

CREATE TABLE versiones_eventos (entidad_id UUID PRIMARY KEY, version INTEGER NOT NULL);
INSERT INTO versiones_eventos(entidad_id,version)
SELECT entidad_id,max(version) FROM eventos_pendientes GROUP BY entidad_id;
