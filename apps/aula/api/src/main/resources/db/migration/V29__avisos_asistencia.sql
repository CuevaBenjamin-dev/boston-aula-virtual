-- Solo avisos de Aula; el expediente de Asistencia permanece exclusivamente en ERP.
CREATE TABLE notificaciones_asistencia (
  id uuid PRIMARY KEY,
  clave text NOT NULL UNIQUE,
  destinatario_tipo text NOT NULL CHECK(destinatario_tipo IN ('ESTUDIANTE','DOCENTE')),
  destinatario_erp_id uuid NOT NULL,
  mensaje text NOT NULL,
  creada_en timestamptz NOT NULL DEFAULT now(),
  leida_en timestamptz
);
CREATE INDEX notificaciones_asistencia_destinatario ON notificaciones_asistencia(destinatario_tipo,destinatario_erp_id,creada_en DESC);
