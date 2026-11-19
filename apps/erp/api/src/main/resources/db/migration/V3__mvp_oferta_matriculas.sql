-- Extiende la base existente; conserva todos los expedientes y volúmenes.
ALTER TABLE per_personas ALTER COLUMN fecha_nacimiento DROP NOT NULL;
ALTER TABLE per_personas ALTER COLUMN sexo DROP NOT NULL;
ALTER TABLE per_personas ALTER COLUMN nacionalidad DROP NOT NULL;
ALTER TABLE per_personas ALTER COLUMN direccion DROP NOT NULL;

CREATE OR REPLACE FUNCTION per_validar_integridad_persona(p_id UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE p per_personas%ROWTYPE;
BEGIN
  SELECT * INTO p FROM per_personas WHERE id=p_id;
  IF NOT FOUND THEN RETURN; END IF;
  IF NOT EXISTS (SELECT 1 FROM per_condiciones WHERE persona_id=p_id)
    AND NOT EXISTS (SELECT 1 FROM per_apoderamientos WHERE apoderado_id=p_id) THEN
    RAISE EXCEPTION 'Persona sin condición institucional: %', p_id USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM per_documentos WHERE persona_id=p_id AND periodo @> now())
    AND NOT EXISTS (SELECT 1 FROM per_identificaciones_provisionales i
      JOIN per_condiciones c ON c.persona_id=i.persona_id AND c.tipo='ESTUDIANTE'
      WHERE i.persona_id=p_id AND i.regularizada_en IS NULL) THEN
    RAISE EXCEPTION 'Persona sin identificación válida: %', p_id USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM per_documentos WHERE persona_id=p_id AND periodo @> now())
    AND NOT EXISTS (SELECT 1 FROM per_documentos WHERE persona_id=p_id AND principal AND periodo @> now()) THEN
    RAISE EXCEPTION 'Persona con documentos, sin principal vigente: %', p_id USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM per_identificaciones_provisionales WHERE persona_id=p_id AND regularizada_en IS NULL)
    AND EXISTS (SELECT 1 FROM per_documentos WHERE persona_id=p_id AND periodo @> now()) THEN
    RAISE EXCEPTION 'Identificación provisional pendiente junto con documento vigente: %', p_id USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM per_condiciones WHERE persona_id=p_id AND tipo='ESTUDIANTE' AND periodo @> now())
    AND p.fecha_nacimiento IS NULL THEN
    RAISE EXCEPTION 'Estudiante sin fecha de nacimiento: %', p_id USING ERRCODE='23514';
  END IF;
  IF NULLIF(btrim(p.telefono),'') IS NULL AND (
    EXISTS (SELECT 1 FROM per_condiciones WHERE persona_id=p_id
      AND tipo IN ('DOCENTE','PERSONAL_INSTITUCIONAL') AND periodo @> now())
    OR EXISTS (SELECT 1 FROM per_apoderamientos WHERE apoderado_id=p_id AND periodo @> now())
  ) THEN
    RAISE EXCEPTION 'Falta teléfono exigido por condición: %', p_id USING ERRCODE='23514';
  END IF;
END $$;

CREATE TABLE ofe_anios (
  anio INTEGER PRIMARY KEY CHECK (anio BETWEEN 2000 AND 2100),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_sub TEXT NOT NULL
);
CREATE TABLE ofe_secciones (
  id UUID PRIMARY KEY,
  anio INTEGER NOT NULL REFERENCES ofe_anios(anio),
  nivel TEXT NOT NULL CHECK (nivel IN ('PRIMARIA','SECUNDARIA')),
  grado SMALLINT NOT NULL CHECK (grado BETWEEN 1 AND 6),
  nombre TEXT NOT NULL CHECK (nombre ~ '^[A-Z0-9]{1,6}$'),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ofe_grado_valido CHECK (nivel='PRIMARIA' OR grado<=5),
  UNIQUE (anio,nivel,grado,nombre)
);
CREATE TABLE ofe_asignaturas (
  id UUID PRIMARY KEY,
  anio INTEGER NOT NULL REFERENCES ofe_anios(anio),
  nivel TEXT NOT NULL CHECK (nivel IN ('PRIMARIA','SECUNDARIA')),
  grado SMALLINT NOT NULL CHECK (grado BETWEEN 1 AND 6),
  codigo TEXT NOT NULL CHECK (codigo ~ '^[A-Z0-9_-]{2,30}$'),
  nombre TEXT NOT NULL CHECK (length(btrim(nombre)) BETWEEN 1 AND 120),
  CONSTRAINT ofe_asignatura_grado_valido CHECK (nivel='PRIMARIA' OR grado<=5),
  UNIQUE (anio,codigo),
  UNIQUE (anio,nivel,grado,nombre)
);

CREATE TABLE mat_matriculas (
  id UUID PRIMARY KEY,
  persona_id UUID REFERENCES per_personas(id),
  anio INTEGER REFERENCES ofe_anios(anio),
  seccion_id UUID REFERENCES ofe_secciones(id),
  estado TEXT NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR','VIGENTE','RETIRADA','FINALIZADA')),
  borrador JSONB NOT NULL DEFAULT '{}'::jsonb,
  creado_por TEXT NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmado_en TIMESTAMPTZ,
  CHECK (estado='BORRADOR' OR (persona_id IS NOT NULL AND anio IS NOT NULL AND seccion_id IS NOT NULL AND confirmado_en IS NOT NULL))
);
CREATE UNIQUE INDEX mat_vigente_unico_anio ON mat_matriculas(persona_id,anio) WHERE estado='VIGENTE';
CREATE INDEX mat_anio_estado_idx ON mat_matriculas(anio,estado);
CREATE TABLE mat_eventos (
  id UUID PRIMARY KEY,
  matricula_id UUID NOT NULL REFERENCES mat_matriculas(id),
  accion TEXT NOT NULL,
  actor_sub TEXT NOT NULL,
  ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE erp_solicitudes_externas (
  id UUID PRIMARY KEY,
  clave_idempotencia TEXT NOT NULL UNIQUE,
  sistema TEXT NOT NULL CHECK (sistema IN ('IDENTIDAD','AULA')),
  entidad_tipo TEXT NOT NULL,
  entidad_id UUID NOT NULL,
  accion TEXT NOT NULL,
  estado TEXT NOT NULL DEFAULT 'PENDIENTE_NO_CONECTADO' CHECK (estado IN ('PENDIENTE_NO_CONECTADO','ERROR','CONFIRMADA')),
  motivo TEXT NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
