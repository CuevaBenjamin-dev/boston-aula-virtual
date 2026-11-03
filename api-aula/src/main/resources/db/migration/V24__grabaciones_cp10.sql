ALTER TABLE grabaciones_youtube ALTER COLUMN youtube_id DROP NOT NULL;
ALTER TABLE grabaciones_youtube ADD COLUMN titulo VARCHAR(200);
UPDATE grabaciones_youtube SET titulo='Parte ' || orden WHERE titulo IS NULL;
ALTER TABLE grabaciones_youtube ALTER COLUMN titulo SET NOT NULL;
ALTER TABLE grabaciones_youtube ADD COLUMN operacion_id UUID UNIQUE;
ALTER TABLE grabaciones_youtube DROP CONSTRAINT grabaciones_youtube_estado_check;
ALTER TABLE grabaciones_youtube ADD CONSTRAINT grabaciones_youtube_estado_check CHECK (estado IN ('PUBLICADA','OCULTA','RETIRADA'));
ALTER TABLE grabaciones_youtube DROP CONSTRAINT grabaciones_youtube_clase_id_orden_key;
CREATE UNIQUE INDEX ux_grabacion_parte_activa ON grabaciones_youtube(clase_id,orden) WHERE estado<>'RETIRADA';
CREATE UNIQUE INDEX ux_grabacion_url_activa ON grabaciones_youtube(clase_id,url) WHERE estado<>'RETIRADA';

CREATE TABLE historial_grabaciones (
  id UUID PRIMARY KEY,
  grabacion_id UUID NOT NULL REFERENCES grabaciones_youtube(id),
  accion VARCHAR(24) NOT NULL CHECK (accion IN ('CREADA','EDITADA','PUBLICADA','OCULTA','RETIRADA','ENLACE_CORREGIDO')),
  titulo_anterior VARCHAR(200), titulo_nuevo VARCHAR(200),
  url_anterior TEXT, url_nueva TEXT,
  orden_anterior INTEGER, orden_nuevo INTEGER,
  estado_anterior VARCHAR(16), estado_nuevo VARCHAR(16),
  actor_cuenta UUID NOT NULL,
  operacion_id UUID UNIQUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_historial_grabaciones ON historial_grabaciones(grabacion_id,creado_en DESC);

CREATE TABLE notificaciones_grabacion (
  id UUID PRIMARY KEY,
  evento_id UUID NOT NULL REFERENCES historial_grabaciones(id),
  grabacion_id UUID NOT NULL REFERENCES grabaciones_youtube(id),
  estudiante_erp_id UUID NOT NULL,
  mensaje VARCHAR(500) NOT NULL,
  creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  leida_en TIMESTAMPTZ,
  UNIQUE(evento_id,estudiante_erp_id)
);
CREATE INDEX ix_notificaciones_grabacion_destino ON notificaciones_grabacion(estudiante_erp_id,creada_en DESC);
