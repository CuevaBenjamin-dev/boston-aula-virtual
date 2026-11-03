ALTER TABLE clases_academicas ADD COLUMN estado VARCHAR(16) NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA','CANCELADA'));
ALTER TABLE clases_academicas ADD COLUMN modificado_en TIMESTAMPTZ;
ALTER TABLE clases_academicas ADD COLUMN docente_erp_id UUID;
UPDATE clases_academicas c SET docente_erp_id=(SELECT d.docente_erp_id FROM docentes_curso d WHERE d.curso_id=c.curso_id AND d.activo ORDER BY d.docente_erp_id LIMIT 1);

CREATE TABLE notificaciones_clase (
  id UUID PRIMARY KEY,
  clase_id UUID NOT NULL REFERENCES clases_academicas(id),
  estudiante_erp_id UUID NOT NULL,
  tipo VARCHAR(24) NOT NULL CHECK (tipo IN ('PROGRAMADA','REPROGRAMADA','CANCELADA')),
  mensaje VARCHAR(500) NOT NULL,
  creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  leida_en TIMESTAMPTZ
);
CREATE INDEX ix_notificaciones_estudiante ON notificaciones_clase(estudiante_erp_id,leida_en,creada_en DESC);

CREATE TABLE grabaciones_youtube (
  id UUID PRIMARY KEY,
  clase_id UUID NOT NULL REFERENCES clases_academicas(id),
  orden INTEGER NOT NULL CHECK (orden>0),
  youtube_id VARCHAR(32) NOT NULL,
  url TEXT NOT NULL,
  estado VARCHAR(16) NOT NULL CHECK (estado IN ('PUBLICADA','OCULTA')),
  creado_por UUID NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(clase_id,orden)
);
CREATE INDEX ix_grabaciones_youtube_clase ON grabaciones_youtube(clase_id,estado,orden);
