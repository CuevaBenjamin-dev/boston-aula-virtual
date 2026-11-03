ALTER TABLE estudiantes ALTER COLUMN codigo DROP NOT NULL;
UPDATE estudiantes e SET codigo=NULL
WHERE NOT EXISTS (
  SELECT 1 FROM matriculas m WHERE m.estudiante_id=e.id AND m.estado='MATRICULADO'
);

CREATE TABLE borradores_matricula (
  id UUID PRIMARY KEY,
  actor VARCHAR(120) NOT NULL,
  paso SMALLINT NOT NULL DEFAULT 1 CHECK (paso BETWEEN 1 AND 4),
  datos JSONB NOT NULL DEFAULT '{}'::jsonb,
  matricula_id UUID REFERENCES matriculas(id),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  finalizado_en TIMESTAMPTZ
);
CREATE INDEX ix_borradores_matricula_actor ON borradores_matricula(actor,actualizado_en DESC);

ALTER TABLE matriculas ADD COLUMN pago_estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  ADD COLUMN pago_medio VARCHAR(40),
  ADD COLUMN pago_monto NUMERIC(12,2),
  ADD COLUMN pago_registrado_en TIMESTAMPTZ,
  ADD COLUMN pago_registrado_por VARCHAR(120),
  ADD CONSTRAINT matriculas_pago_externo_check CHECK (
    (pago_estado='PENDIENTE' AND pago_medio IS NULL AND pago_monto IS NULL)
    OR (pago_estado='REGISTRADO' AND pago_medio IS NOT NULL AND pago_monto>0
        AND pago_registrado_en IS NOT NULL AND pago_registrado_por IS NOT NULL)
  );
