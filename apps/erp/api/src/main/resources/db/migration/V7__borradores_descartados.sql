-- Una constancia mínima permanece después de retirar un borrador erróneo.
CREATE TABLE mat_borradores_descartados (
  matricula_id UUID PRIMARY KEY,
  descartado_por TEXT NOT NULL,
  descartado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
