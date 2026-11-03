CREATE TABLE notificaciones_asignacion (
  id UUID PRIMARY KEY,
  secuencia_erp BIGINT NOT NULL,
  curso_id UUID NOT NULL REFERENCES cursos(id),
  docente_erp_id UUID NOT NULL,
  tipo VARCHAR(12) NOT NULL CHECK (tipo IN ('ASIGNADA','RETIRADA')),
  mensaje VARCHAR(500) NOT NULL,
  creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  leida_en TIMESTAMPTZ,
  UNIQUE(secuencia_erp,docente_erp_id,tipo)
);
CREATE INDEX ix_notificaciones_asignacion_docente ON notificaciones_asignacion(docente_erp_id,creada_en DESC);
