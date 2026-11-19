CREATE TABLE asi_asignaciones (
  id UUID PRIMARY KEY,
  docente_id UUID NOT NULL REFERENCES per_personas(id),
  seccion_id UUID NOT NULL REFERENCES ofe_secciones(id),
  asignatura_id UUID NOT NULL REFERENCES ofe_asignaturas(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('TITULAR','REEMPLAZO')),
  desde DATE NOT NULL,
  hasta DATE,
  horario TEXT,
  estado TEXT NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA','CERRADA')),
  actor_sub TEXT NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  cerrado_en TIMESTAMPTZ,
  CHECK (hasta IS NULL OR hasta >= desde)
);
CREATE INDEX asi_seccion_asignatura_idx ON asi_asignaciones(seccion_id,asignatura_id,estado);
CREATE INDEX asi_docente_idx ON asi_asignaciones(docente_id,estado);
