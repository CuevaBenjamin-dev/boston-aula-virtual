ALTER TABLE cursos ADD COLUMN nivel VARCHAR(80);
ALTER TABLE cursos ADD COLUMN periodo_fin DATE;
UPDATE cursos SET periodo_fin=make_date(periodo::integer,12,31) WHERE periodo ~ '^[0-9]{4}$';

ALTER TABLE tareas ADD COLUMN tarea_anterior_id UUID REFERENCES tareas(id);
ALTER TABLE tareas ADD COLUMN clave_operacion UUID UNIQUE;
ALTER TABLE tareas ADD COLUMN cambio_en TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_tarea_sucesora ON tareas(tarea_anterior_id) WHERE tarea_anterior_id IS NOT NULL;
CREATE INDEX ix_tareas_antecedente ON tareas(tarea_anterior_id);

-- Una versión posterior puede referir al mismo objeto sin copiarlo ni eliminar la evidencia anterior.
DROP INDEX ux_archivo_tarea_clave;
ALTER TABLE archivos_tarea ADD COLUMN sustituye_id UUID REFERENCES archivos_tarea(id);

CREATE TABLE notificaciones_tarea (
  id UUID PRIMARY KEY,
  tarea_id UUID NOT NULL REFERENCES tareas(id),
  destinatario_tipo VARCHAR(12) NOT NULL CHECK (destinatario_tipo IN ('ESTUDIANTE','DOCENTE')),
  destinatario_erp_id UUID NOT NULL,
  tipo VARCHAR(24) NOT NULL CHECK (tipo IN ('PUBLICADA','ACTUALIZADA','REINICIADA','TRASLADADA','CERRADA','ENTREGAS')),
  mensaje TEXT NOT NULL,
  nuevas_entregas INTEGER NOT NULL DEFAULT 0 CHECK (nuevas_entregas>=0),
  clave_evento TEXT UNIQUE,
  creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  leida_en TIMESTAMPTZ
);
CREATE INDEX ix_notificacion_tarea_destinatario ON notificaciones_tarea(destinatario_tipo,destinatario_erp_id,actualizada_en DESC);
CREATE UNIQUE INDEX ux_entregas_pendientes_docente_tarea ON notificaciones_tarea(destinatario_erp_id,tarea_id)
  WHERE destinatario_tipo='DOCENTE' AND tipo='ENTREGAS' AND leida_en IS NULL;
CREATE TABLE eventos_notificacion_entrega (
  entrega_id UUID NOT NULL REFERENCES entregas(id),
  docente_erp_id UUID NOT NULL,
  notificacion_id UUID REFERENCES notificaciones_tarea(id),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(entrega_id,docente_erp_id)
);

ALTER TABLE materiales_curso ADD COLUMN descripcion TEXT NOT NULL DEFAULT '';
ALTER TABLE materiales_curso ADD COLUMN enlace TEXT;
ALTER TABLE materiales_curso ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT 'BORRADOR'
  CHECK (estado IN ('BORRADOR','PUBLICADO','OCULTO'));
ALTER TABLE materiales_curso ADD COLUMN creado_por UUID;
ALTER TABLE materiales_curso ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE TABLE destinos_material (
  material_id UUID NOT NULL REFERENCES materiales_curso(id),
  curso_id UUID NOT NULL REFERENCES cursos(id),
  publicado_en TIMESTAMPTZ,
  PRIMARY KEY(material_id,curso_id)
);
INSERT INTO destinos_material(material_id,curso_id,publicado_en)
  SELECT id,curso_id,creado_en FROM materiales_curso;
CREATE INDEX ix_materiales_destino_curso ON destinos_material(curso_id,material_id);
CREATE TABLE archivos_material (
  id UUID PRIMARY KEY,
  material_id UUID NOT NULL REFERENCES materiales_curso(id),
  nombre VARCHAR(200) NOT NULL,
  clave_objeto TEXT NOT NULL,
  tamano BIGINT NOT NULL,
  tipo VARCHAR(100) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'INICIADO',
  upload_id TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  sustituye_id UUID REFERENCES archivos_material(id),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_archivos_material_activos ON archivos_material(material_id,activo,creado_en);
