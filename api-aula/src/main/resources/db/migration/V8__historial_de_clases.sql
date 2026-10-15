CREATE TABLE historial_clases (
  id UUID PRIMARY KEY,
  clase_id UUID NOT NULL REFERENCES clases_academicas(id),
  accion VARCHAR(16) NOT NULL CHECK (accion IN ('MIGRADA','CREADA','EDITADA','CANCELADA')),
  tema VARCHAR(200) NOT NULL,
  inicio TIMESTAMPTZ NOT NULL,
  fin TIMESTAMPTZ NOT NULL,
  estado VARCHAR(16) NOT NULL,
  registrado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_historial_clases_fecha ON historial_clases(clase_id,registrado_en);
INSERT INTO historial_clases(id,clase_id,accion,tema,inicio,fin,estado)
SELECT gen_random_uuid(),id,'MIGRADA',tema,inicio,fin,estado FROM clases_academicas;
