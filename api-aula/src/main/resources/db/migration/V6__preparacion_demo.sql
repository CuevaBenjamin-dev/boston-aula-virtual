CREATE TABLE preparacion_demo (
  clave VARCHAR(80) PRIMARY KEY,
  aplicado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
