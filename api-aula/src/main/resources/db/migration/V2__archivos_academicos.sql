ALTER TABLE archivos_tarea ADD COLUMN tamano BIGINT NOT NULL DEFAULT 0, ADD COLUMN tipo VARCHAR(80) NOT NULL DEFAULT 'application/octet-stream', ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE', ADD COLUMN upload_id TEXT, ADD COLUMN creado_en TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE archivos_entrega ADD COLUMN tamano BIGINT NOT NULL DEFAULT 0, ADD COLUMN tipo VARCHAR(80) NOT NULL DEFAULT 'application/octet-stream', ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE', ADD COLUMN upload_id TEXT, ADD COLUMN creado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX ux_archivo_tarea_clave ON archivos_tarea(clave_objeto);
CREATE UNIQUE INDEX ux_archivo_entrega_clave ON archivos_entrega(clave_objeto);
CREATE INDEX ix_archivos_tarea_estado ON archivos_tarea(tarea_id,estado);
CREATE INDEX ix_archivos_entrega_estado ON archivos_entrega(entrega_id,estado);
