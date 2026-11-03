CREATE TABLE perfiles_aula (
  cuenta_id UUID PRIMARY KEY,
  foto BYTEA,
  foto_tipo VARCHAR(32),
  actualizada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((foto IS NULL AND foto_tipo IS NULL) OR (foto IS NOT NULL AND foto_tipo IN ('image/jpeg','image/png')))
);

CREATE TABLE mensajes (
  id UUID PRIMARY KEY,
  emisor_cuenta UUID NOT NULL,
  emisor_usuario VARCHAR(120) NOT NULL,
  asunto VARCHAR(200) NOT NULL,
  cuerpo TEXT NOT NULL,
  curso_id UUID REFERENCES cursos(id),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mensajes_emisor ON mensajes(emisor_cuenta,creado_en DESC);

CREATE TABLE mensajes_destinatarios (
  mensaje_id UUID NOT NULL REFERENCES mensajes(id),
  cuenta_id UUID NOT NULL,
  usuario VARCHAR(120) NOT NULL,
  leida_en TIMESTAMPTZ,
  PRIMARY KEY(mensaje_id,cuenta_id)
);
CREATE INDEX ix_mensajes_destinatario ON mensajes_destinatarios(cuenta_id,leida_en,mensaje_id);

CREATE TABLE notificaciones_vistas (
  cuenta_id UUID NOT NULL,
  historial_id UUID NOT NULL REFERENCES historial_clases(id),
  leida_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(cuenta_id,historial_id)
);
