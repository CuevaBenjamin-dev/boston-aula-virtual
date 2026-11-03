CREATE TABLE coordinaciones_seccion (
  cuenta_id UUID NOT NULL,
  seccion_erp_id UUID NOT NULL,
  asignado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(cuenta_id,seccion_erp_id)
);
CREATE INDEX ix_coordinacion_seccion ON coordinaciones_seccion(seccion_erp_id);
