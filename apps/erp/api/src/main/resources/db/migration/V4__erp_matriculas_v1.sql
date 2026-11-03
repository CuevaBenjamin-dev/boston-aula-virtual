ALTER TABLE personas ADD COLUMN tipo_documento VARCHAR(20), ADD COLUMN fecha_nacimiento DATE, ADD COLUMN nacionalidad VARCHAR(80), ADD COLUMN telefono VARCHAR(40), ADD COLUMN correo VARCHAR(160), ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX ux_persona_tipo_documento ON personas(tipo_documento,documento) WHERE tipo_documento IS NOT NULL AND documento IS NOT NULL;
ALTER TABLE estudiantes ADD COLUMN datos_complementarios JSONB NOT NULL DEFAULT '{}'::jsonb, ADD COLUMN creado_en TIMESTAMPTZ NOT NULL DEFAULT now(), ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE SEQUENCE codigo_estudiante_seq START WITH 1;
ALTER TABLE apoderados ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';
ALTER TABLE vinculos_estudiante_apoderado ADD COLUMN rol VARCHAR(24) NOT NULL DEFAULT 'PRINCIPAL', ADD COLUMN contacto_emergencia BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN telefono VARCHAR(40), ADD COLUMN correo VARCHAR(160), ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE docentes ADD COLUMN codigo VARCHAR(30), ADD COLUMN correo_institucional VARCHAR(160), ADD COLUMN telefono VARCHAR(40), ADD COLUMN cuenta_identidad_id UUID, ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX ux_docente_codigo ON docentes(codigo) WHERE codigo IS NOT NULL;
CREATE UNIQUE INDEX ux_docente_correo ON docentes(lower(correo_institucional)) WHERE correo_institucional IS NOT NULL;
CREATE SEQUENCE codigo_docente_seq START WITH 1;
ALTER TABLE periodos_academicos ADD COLUMN habilitado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE niveles_educativos ADD COLUMN habilitado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE grados ADD COLUMN habilitado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE secciones ADD COLUMN habilitada BOOLEAN NOT NULL DEFAULT TRUE, ADD COLUMN capacidad INTEGER CHECK(capacidad IS NULL OR capacidad>0);
ALTER TABLE asignaturas ADD COLUMN activa BOOLEAN NOT NULL DEFAULT TRUE, ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE TABLE asignaturas_grado (
  id UUID PRIMARY KEY, asignatura_id UUID NOT NULL REFERENCES asignaturas(id), grado_id UUID NOT NULL REFERENCES grados(id),
  periodo_id UUID REFERENCES periodos_academicos(id), orden INTEGER NOT NULL DEFAULT 0, activa BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(), actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_asignatura_grado_general ON asignaturas_grado(asignatura_id,grado_id) WHERE periodo_id IS NULL;
CREATE UNIQUE INDEX ux_asignatura_grado_periodo ON asignaturas_grado(asignatura_id,grado_id,periodo_id) WHERE periodo_id IS NOT NULL;
INSERT INTO asignaturas_grado(id,asignatura_id,grado_id,periodo_id,orden)
SELECT gen_random_uuid(),o.asignatura_id,s.grado_id,s.periodo_id,0 FROM ofertas_academicas o JOIN secciones s ON s.id=o.seccion_id
GROUP BY o.asignatura_id,s.grado_id,s.periodo_id ON CONFLICT DO NOTHING;

ALTER TABLE matriculas ADD COLUMN periodo_id UUID REFERENCES periodos_academicos(id), ADD COLUMN grado_id UUID REFERENCES grados(id),
  ADD COLUMN procedencia VARCHAR(20) NOT NULL DEFAULT 'ANTIGUO', ADD COLUMN procedencia_detalle JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN responsable_financiero_id UUID REFERENCES personas(id), ADD COLUMN contactos_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN confirmado_en TIMESTAMPTZ, ADD COLUMN confirmado_por VARCHAR(120), ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN cierre_motivo VARCHAR(30), ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
UPDATE matriculas m SET periodo_id=s.periodo_id,grado_id=s.grado_id FROM secciones s WHERE s.id=m.seccion_id;
UPDATE matriculas SET cierre_motivo=estado, vigente=FALSE WHERE estado IN ('RETIRADA','FINALIZADA');
ALTER TABLE matriculas DROP CONSTRAINT matriculas_estado_check;
UPDATE matriculas SET estado='MATRICULADO', confirmado_en=creado_en, confirmado_por='MIGRACION_V4';
ALTER TABLE matriculas ALTER COLUMN periodo_id SET NOT NULL;
ALTER TABLE matriculas ALTER COLUMN seccion_id DROP NOT NULL;
ALTER TABLE matriculas ADD CONSTRAINT matriculas_estado_check CHECK(estado IN ('PROCESO_PENDIENTE','VACANTE_RESERVADA','MATRICULADO'));
ALTER TABLE matriculas ADD CONSTRAINT matriculas_procedencia_check CHECK(procedencia IN ('NUEVO','ANTIGUO','TRASLADO','REINCORPORACION'));
ALTER TABLE matriculas DROP CONSTRAINT matriculas_estudiante_id_seccion_id_key;
DROP INDEX ux_matricula_activa;
CREATE UNIQUE INDEX ux_matricula_estudiante_periodo ON matriculas(estudiante_id,periodo_id);
CREATE INDEX ix_matricula_periodo_estado ON matriculas(periodo_id,estado);

DROP INDEX ux_docente_activo_oferta;
CREATE UNIQUE INDEX ux_docente_oferta_activo ON asignaciones_docentes(oferta_id,docente_id) WHERE estado='ACTIVA';
ALTER TABLE asignaciones_docentes ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE historial_matriculas (
  id UUID PRIMARY KEY, matricula_id UUID NOT NULL REFERENCES matriculas(id), actor VARCHAR(120) NOT NULL,
  accion VARCHAR(80) NOT NULL, antes JSONB, despues JSONB, instante TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_historial_matricula ON historial_matriculas(matricula_id,instante);
CREATE TABLE referencias_documentales (
  id UUID PRIMARY KEY, estudiante_id UUID REFERENCES estudiantes(id), matricula_id UUID REFERENCES matriculas(id),
  tipo VARCHAR(80) NOT NULL, version INTEGER NOT NULL DEFAULT 1, clave_privada VARCHAR(300),
  nombre_archivo VARCHAR(200), tipo_mime VARCHAR(100), estado VARCHAR(20) NOT NULL DEFAULT 'REFERENCIA',
  registrado_por VARCHAR(120) NOT NULL, creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK(estudiante_id IS NOT NULL OR matricula_id IS NOT NULL)
);
CREATE TABLE importaciones_matriculas (
  id UUID PRIMARY KEY, sha256 VARCHAR(64) NOT NULL UNIQUE, archivo_nombre VARCHAR(200) NOT NULL,
  actor VARCHAR(120) NOT NULL, estado VARCHAR(20) NOT NULL, reporte JSONB NOT NULL DEFAULT '{}'::jsonb,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(), confirmado_en TIMESTAMPTZ
);
