-- Constancia mínima de una ficha creada por error y eliminada antes de su uso institucional.
-- No conserva nombres, documentos ni un FK a la persona que se elimina.
CREATE TABLE per_eliminaciones (
  persona_id UUID PRIMARY KEY,
  actor_sub TEXT NOT NULL CHECK (length(btrim(actor_sub)) > 0),
  motivo TEXT NOT NULL DEFAULT 'REGISTRO_ERRONEO' CHECK (motivo = 'REGISTRO_ERRONEO'),
  ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
