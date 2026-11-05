-- Personas. PostgreSQL 18; la base histórica erp_boston no participa.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE per_personas (
  id UUID PRIMARY KEY,
  nombres TEXT NOT NULL CHECK (length(btrim(nombres)) BETWEEN 1 AND 200),
  apellidos TEXT NOT NULL CHECK (length(btrim(apellidos)) BETWEEN 1 AND 200),
  fecha_nacimiento DATE NOT NULL,
  sexo TEXT NOT NULL CHECK (length(btrim(sexo)) > 0),
  nacionalidad TEXT NOT NULL CHECK (length(btrim(nacionalidad)) > 0),
  telefono TEXT,
  correo TEXT,
  direccion TEXT NOT NULL CHECK (length(btrim(direccion)) > 0),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (telefono IS NULL OR length(btrim(telefono)) > 0),
  CHECK (correo IS NULL OR length(btrim(correo)) > 0)
);
CREATE INDEX per_personas_nombre_trgm_idx ON per_personas
  USING gin (lower(nombres || ' ' || apellidos) gin_trgm_ops);

CREATE TABLE per_tipos_documento (
  codigo TEXT PRIMARY KEY CHECK (codigo ~ '^[A-Z0-9_]{2,30}$'),
  nombre TEXT NOT NULL CHECK (length(btrim(nombre)) > 0),
  patron TEXT CHECK (patron IS NULL OR length(patron) BETWEEN 1 AND 128),
  activo BOOLEAN NOT NULL DEFAULT true
);
INSERT INTO per_tipos_documento(codigo,nombre,patron) VALUES
  ('DNI','Documento Nacional de Identidad','^[0-9]{8}$'),
  ('CE','Carné de extranjería',NULL),
  ('PASAPORTE','Pasaporte',NULL);

CREATE TABLE per_documentos (
  id UUID PRIMARY KEY,
  persona_id UUID NOT NULL REFERENCES per_personas(id),
  tipo_codigo TEXT NOT NULL REFERENCES per_tipos_documento(codigo),
  pais_emisor CHAR(2) NOT NULL CHECK (pais_emisor ~ '^[A-Z]{2}$'),
  numero TEXT NOT NULL CHECK (length(btrim(numero)) BETWEEN 1 AND 64),
  numero_normalizado TEXT NOT NULL CHECK (numero_normalizado = btrim(numero) AND length(numero_normalizado) > 0),
  principal BOOLEAN NOT NULL DEFAULT false,
  vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now(),
  vigente_hasta TIMESTAMPTZ,
  periodo TSTZRANGE GENERATED ALWAYS AS (tstzrange(vigente_desde,vigente_hasta,'[)')) STORED,
  CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
  CHECK (tipo_codigo <> 'DNI' OR (pais_emisor = 'PE' AND numero_normalizado ~ '^[0-9]{8}$')),
  CONSTRAINT per_documento_unico_vigencia UNIQUE (tipo_codigo,pais_emisor,numero_normalizado,periodo WITHOUT OVERLAPS),
  CONSTRAINT per_documento_principal_vigencia EXCLUDE USING gist (persona_id WITH =, periodo WITH &&) WHERE (principal)
);
CREATE INDEX per_documentos_busqueda_idx ON per_documentos(tipo_codigo,pais_emisor,numero_normalizado)
  WHERE vigente_hasta IS NULL;
CREATE UNIQUE INDEX per_documentos_principal_abierto_idx ON per_documentos(persona_id)
  WHERE principal AND vigente_hasta IS NULL;

CREATE TABLE per_condiciones (
  id UUID PRIMARY KEY,
  persona_id UUID NOT NULL REFERENCES per_personas(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('ESTUDIANTE','DOCENTE','PERSONAL_INSTITUCIONAL')),
  vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now(),
  vigente_hasta TIMESTAMPTZ,
  cierre_manual BOOLEAN NOT NULL DEFAULT false,
  motivo_desactivacion TEXT,
  periodo TSTZRANGE GENERATED ALWAYS AS (tstzrange(vigente_desde,vigente_hasta,'[)')) STORED,
  CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
  CHECK (NOT cierre_manual OR (vigente_hasta IS NOT NULL AND motivo_desactivacion IS NOT NULL
    AND length(btrim(motivo_desactivacion)) > 0)),
  CONSTRAINT per_condicion_sin_solape UNIQUE (persona_id,tipo,periodo WITHOUT OVERLAPS)
);
CREATE INDEX per_condiciones_vigentes_idx ON per_condiciones(persona_id,tipo)
  WHERE vigente_hasta IS NULL;

CREATE TABLE per_identificaciones_provisionales (
  id UUID PRIMARY KEY,
  persona_id UUID NOT NULL REFERENCES per_personas(id),
  motivo TEXT NOT NULL CHECK (length(btrim(motivo)) > 0),
  sustento_tipo TEXT NOT NULL CHECK (length(btrim(sustento_tipo)) > 0),
  sustento_referencia TEXT NOT NULL CHECK (length(btrim(sustento_referencia)) > 0),
  registrada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  regularizada_en TIMESTAMPTZ,
  CHECK (regularizada_en IS NULL OR regularizada_en >= registrada_en)
);
CREATE UNIQUE INDEX per_provisional_pendiente_idx ON per_identificaciones_provisionales(persona_id)
  WHERE regularizada_en IS NULL;

CREATE TABLE per_parentescos (
  id UUID PRIMARY KEY,
  estudiante_id UUID NOT NULL REFERENCES per_personas(id),
  familiar_id UUID NOT NULL REFERENCES per_personas(id),
  tipo TEXT NOT NULL CHECK (length(btrim(tipo)) > 0),
  vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now(),
  vigente_hasta TIMESTAMPTZ,
  periodo TSTZRANGE GENERATED ALWAYS AS (tstzrange(vigente_desde,vigente_hasta,'[)')) STORED,
  CHECK (estudiante_id <> familiar_id),
  CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
  CONSTRAINT per_parentesco_sin_solape UNIQUE (estudiante_id,familiar_id,tipo,periodo WITHOUT OVERLAPS)
);
CREATE INDEX per_parentescos_familiar_idx ON per_parentescos(familiar_id,vigente_desde DESC);

CREATE TABLE per_apoderamientos (
  id UUID PRIMARY KEY,
  estudiante_id UUID NOT NULL REFERENCES per_personas(id),
  apoderado_id UUID NOT NULL REFERENCES per_personas(id),
  sin_parentesco BOOLEAN NOT NULL,
  autorizador TEXT,
  verificador_sub TEXT,
  medio_verificacion TEXT,
  verificado_en TIMESTAMPTZ,
  sustento_tipo TEXT,
  sustento_referencia TEXT,
  vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now(),
  vigente_hasta TIMESTAMPTZ,
  motivo_desactivacion TEXT,
  periodo TSTZRANGE GENERATED ALWAYS AS (tstzrange(vigente_desde,vigente_hasta,'[)')) STORED,
  CHECK (estudiante_id <> apoderado_id),
  CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
  CHECK (NOT sin_parentesco OR (autorizador IS NOT NULL AND length(btrim(autorizador)) > 0
    AND verificador_sub IS NOT NULL AND length(btrim(verificador_sub)) > 0
    AND medio_verificacion IS NOT NULL AND length(btrim(medio_verificacion)) > 0
    AND verificado_en IS NOT NULL AND sustento_tipo IS NOT NULL AND length(btrim(sustento_tipo)) > 0
    AND sustento_referencia IS NOT NULL AND length(btrim(sustento_referencia)) > 0)),
  CONSTRAINT per_apoderamiento_sin_solape UNIQUE (estudiante_id,apoderado_id,periodo WITHOUT OVERLAPS)
);
CREATE INDEX per_apoderamientos_estudiante_idx ON per_apoderamientos(estudiante_id,vigente_desde DESC);
CREATE INDEX per_apoderamientos_apoderado_idx ON per_apoderamientos(apoderado_id,vigente_desde DESC);

CREATE TABLE per_eventos (
  id UUID PRIMARY KEY,
  persona_id UUID NOT NULL REFERENCES per_personas(id),
  recurso_id UUID,
  accion TEXT NOT NULL CHECK (length(btrim(accion)) > 0),
  actor_sub TEXT NOT NULL CHECK (length(btrim(actor_sub)) > 0),
  ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX per_eventos_persona_idx ON per_eventos(persona_id,ocurrido_en DESC);

-- Invariante entre tablas. El trigger diferido permite alta de persona, condición
-- y documento (o apoderamiento) en una sola transacción, en cualquier orden.
CREATE FUNCTION per_validar_integridad_persona(p_id UUID) RETURNS void LANGUAGE plpgsql AS $$
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
  IF (NULLIF(btrim(p.telefono),'') IS NULL OR NULLIF(btrim(p.correo),'') IS NULL) AND (
    EXISTS (SELECT 1 FROM per_condiciones WHERE persona_id=p_id
      AND tipo IN ('DOCENTE','PERSONAL_INSTITUCIONAL') AND periodo @> now())
    OR EXISTS (SELECT 1 FROM per_apoderamientos WHERE apoderado_id=p_id AND periodo @> now()
      AND NULLIF(btrim(p.telefono),'') IS NULL)
  ) THEN
    RAISE EXCEPTION 'Faltan datos de contacto exigidos por condición: %', p_id USING ERRCODE='23514';
  END IF;
END $$;

CREATE FUNCTION per_validar_vinculo_al_cierre() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v per_apoderamientos%ROWTYPE;
BEGIN
  SELECT * INTO v FROM per_apoderamientos WHERE id=NEW.id;
  IF NOT FOUND OR NOT v.periodo @> now() THEN RETURN NULL; END IF;
  IF NOT EXISTS (SELECT 1 FROM per_condiciones c WHERE c.persona_id=v.estudiante_id
      AND c.tipo='ESTUDIANTE' AND c.periodo @> now()) THEN
    RAISE EXCEPTION 'Apoderamiento sin estudiante activo: %', v.id USING ERRCODE='23514';
  END IF;
  IF NOT v.sin_parentesco AND NOT EXISTS (SELECT 1 FROM per_parentescos p
      WHERE p.estudiante_id=v.estudiante_id AND p.familiar_id=v.apoderado_id
        AND p.periodo @> now()) THEN
    RAISE EXCEPTION 'Apoderamiento sin parentesco registrado: %', v.id USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END $$;

CREATE FUNCTION per_validar_parentesco_al_cierre() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM per_apoderamientos a WHERE a.estudiante_id=OLD.estudiante_id
      AND a.apoderado_id=OLD.familiar_id AND NOT a.sin_parentesco AND a.periodo @> now())
    AND NOT EXISTS (SELECT 1 FROM per_parentescos p WHERE p.estudiante_id=OLD.estudiante_id
      AND p.familiar_id=OLD.familiar_id AND p.periodo @> now()) THEN
    RAISE EXCEPTION 'Parentesco requerido por apoderamiento activo' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END $$;

CREATE FUNCTION per_validar_estudiante_al_cierre() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.tipo='ESTUDIANTE'
    AND EXISTS (SELECT 1 FROM per_apoderamientos a WHERE a.estudiante_id=OLD.persona_id AND a.periodo @> now())
    AND NOT EXISTS (SELECT 1 FROM per_condiciones c WHERE c.persona_id=OLD.persona_id
      AND c.tipo='ESTUDIANTE' AND c.periodo @> now()) THEN
    RAISE EXCEPTION 'Estudiante con apoderamientos activos' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END $$;

CREATE FUNCTION per_validar_persona_al_cierre() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p_id UUID;
BEGIN
  IF TG_TABLE_NAME='per_personas' THEN p_id := COALESCE(NEW.id,OLD.id);
  ELSIF TG_TABLE_NAME='per_apoderamientos' THEN p_id := COALESCE(NEW.apoderado_id,OLD.apoderado_id);
  ELSE p_id := COALESCE(NEW.persona_id,OLD.persona_id); END IF;
  PERFORM per_validar_integridad_persona(p_id);
  RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER per_persona_integridad AFTER INSERT OR UPDATE ON per_personas
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_persona_al_cierre();
CREATE CONSTRAINT TRIGGER per_documento_integridad AFTER INSERT OR UPDATE OR DELETE ON per_documentos
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_persona_al_cierre();
CREATE CONSTRAINT TRIGGER per_condicion_integridad AFTER INSERT OR UPDATE OR DELETE ON per_condiciones
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_persona_al_cierre();
CREATE CONSTRAINT TRIGGER per_provisional_integridad AFTER INSERT OR UPDATE OR DELETE ON per_identificaciones_provisionales
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_persona_al_cierre();
CREATE CONSTRAINT TRIGGER per_apoderamiento_integridad AFTER INSERT OR UPDATE OR DELETE ON per_apoderamientos
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_persona_al_cierre();
CREATE CONSTRAINT TRIGGER per_apoderamiento_vinculo AFTER INSERT OR UPDATE ON per_apoderamientos
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_vinculo_al_cierre();
CREATE CONSTRAINT TRIGGER per_parentesco_vinculo AFTER UPDATE OR DELETE ON per_parentescos
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_parentesco_al_cierre();
CREATE CONSTRAINT TRIGGER per_condicion_estudiante AFTER UPDATE OR DELETE ON per_condiciones
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION per_validar_estudiante_al_cierre();
