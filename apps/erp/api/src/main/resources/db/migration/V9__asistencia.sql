-- Asistencia conserva hechos y movimientos; no reescribe matrículas ni datos históricos.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE ast_calendarios (
  anio integer PRIMARY KEY REFERENCES ofe_anios(anio),
  inicio date NOT NULL, fin date NOT NULL,
  dias_semana integer[] NOT NULL,
  habilitado_desde date NOT NULL,
  recordatorio_hora time NOT NULL DEFAULT '16:00',
  version integer NOT NULL DEFAULT 0,
  CHECK (inicio <= fin AND extract(year from inicio)=anio AND extract(year from fin)=anio),
  CHECK (array_length(dias_semana,1) BETWEEN 1 AND 7)
);
CREATE TABLE ast_excepciones (
  id uuid PRIMARY KEY, fecha date NOT NULL, seccion_id uuid REFERENCES ofe_secciones(id),
  lectivo boolean NOT NULL, motivo text NOT NULL CHECK (length(btrim(motivo))>0),
  actor_sub text NOT NULL, creado_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE NULLS NOT DISTINCT (fecha,seccion_id)
);
CREATE TABLE mat_tramos_asistencia (
  id uuid PRIMARY KEY, matricula_id uuid NOT NULL REFERENCES mat_matriculas(id),
  persona_id uuid NOT NULL REFERENCES per_personas(id), seccion_id uuid NOT NULL REFERENCES ofe_secciones(id),
  desde date NOT NULL, hasta date, motivo text NOT NULL, actor_sub text NOT NULL,
  creado_en timestamptz NOT NULL DEFAULT now(), CHECK (hasta IS NULL OR hasta>=desde)
);
CREATE UNIQUE INDEX mat_tramo_abierto ON mat_tramos_asistencia(matricula_id) WHERE hasta IS NULL;
CREATE INDEX mat_tramos_fecha ON mat_tramos_asistencia(seccion_id,desde,hasta);
ALTER TABLE mat_tramos_asistencia ADD CONSTRAINT mat_tramos_sin_solape
  EXCLUDE USING gist (matricula_id WITH =, daterange(desde,coalesce(hasta+1,'infinity'::date),'[)') WITH &&);

-- Matrículas mantiene la trayectoria efectiva sin depender del módulo Asistencia.
CREATE FUNCTION mat_actualizar_tramo_asistencia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE dia date := (now() AT TIME ZONE 'America/Lima')::date;
        inicio date;
        crear_tramo boolean := false;
        causa text := 'INCORPORACION';
BEGIN
  IF TG_OP='INSERT' THEN
    crear_tramo := NEW.estado='VIGENTE';
  ELSE
    IF OLD.estado='VIGENTE' AND
        (NEW.estado IN ('RETIRADA','FINALIZADA') OR
         (NEW.estado='VIGENTE' AND NEW.seccion_id IS DISTINCT FROM OLD.seccion_id)) THEN
      UPDATE mat_tramos_asistencia SET hasta=dia-1
        WHERE matricula_id=OLD.id AND hasta IS NULL AND desde<dia;
      DELETE FROM mat_tramos_asistencia WHERE matricula_id=OLD.id AND hasta IS NULL AND desde>=dia;
    END IF;
    crear_tramo := NEW.estado='VIGENTE' AND
      (OLD.estado IS DISTINCT FROM 'VIGENTE' OR OLD.seccion_id IS DISTINCT FROM NEW.seccion_id);
    IF OLD.estado='RETIRADA' THEN causa := 'REINCORPORACION';
    ELSIF OLD.seccion_id IS DISTINCT FROM NEW.seccion_id THEN causa := 'CAMBIO_SECCION';
    END IF;
  END IF;
  IF crear_tramo THEN
    SELECT greatest(c.inicio,c.habilitado_desde,dia) INTO inicio
      FROM ast_calendarios c WHERE c.anio=NEW.anio;
    IF inicio IS NOT NULL AND inicio<= (SELECT fin FROM ast_calendarios WHERE anio=NEW.anio) THEN
      INSERT INTO mat_tramos_asistencia(id,matricula_id,persona_id,seccion_id,desde,motivo,actor_sub)
      VALUES (gen_random_uuid(),NEW.id,NEW.persona_id,NEW.seccion_id,inicio,
        causa,NEW.creado_por);
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER mat_tramo_ast AFTER INSERT OR UPDATE OF estado,seccion_id ON mat_matriculas
  FOR EACH ROW EXECUTE FUNCTION mat_actualizar_tramo_asistencia();

-- Al habilitar el calendario, toma solo matrículas aún vigentes y fechas respaldadas.
CREATE FUNCTION mat_iniciar_tramos_calendario() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO mat_tramos_asistencia(id,matricula_id,persona_id,seccion_id,desde,motivo,actor_sub)
  SELECT gen_random_uuid(),m.id,m.persona_id,m.seccion_id,
    greatest(NEW.inicio,NEW.habilitado_desde,(m.confirmado_en AT TIME ZONE 'America/Lima')::date),
    'HABILITACION_CALENDARIO',m.creado_por
  FROM mat_matriculas m WHERE m.anio=NEW.anio AND m.estado='VIGENTE'
    AND NOT EXISTS(SELECT 1 FROM mat_tramos_asistencia t WHERE t.matricula_id=m.id)
    AND greatest(NEW.inicio,NEW.habilitado_desde,(m.confirmado_en AT TIME ZONE 'America/Lima')::date)<=NEW.fin;
  RETURN NEW;
END $$;
CREATE TRIGGER mat_tramo_al_habilitar_calendario AFTER INSERT ON ast_calendarios
  FOR EACH ROW EXECUTE FUNCTION mat_iniciar_tramos_calendario();

CREATE TABLE ast_jornadas (
  seccion_id uuid NOT NULL REFERENCES ofe_secciones(id), fecha date NOT NULL,
  version integer NOT NULL DEFAULT 0, confirmada boolean NOT NULL DEFAULT false,
  cerrada_en timestamptz, alerta_sin_marcas boolean NOT NULL DEFAULT false,
  PRIMARY KEY (seccion_id,fecha)
);
CREATE TABLE ast_borradores (
  seccion_id uuid NOT NULL, fecha date NOT NULL, persona_id uuid NOT NULL REFERENCES per_personas(id),
  marca text NOT NULL CHECK (marca IN ('PRESENTE','TARDANZA','FALTA')),
  actor_sub text NOT NULL, recibido_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (seccion_id,fecha,persona_id),
  FOREIGN KEY (seccion_id,fecha) REFERENCES ast_jornadas(seccion_id,fecha)
);
CREATE TABLE ast_registros (
  id uuid PRIMARY KEY, persona_id uuid NOT NULL REFERENCES per_personas(id),
  matricula_id uuid NOT NULL REFERENCES mat_matriculas(id), seccion_id uuid NOT NULL REFERENCES ofe_secciones(id),
  fecha date NOT NULL, marca text NOT NULL CHECK (marca IN ('PRESENTE','TARDANZA','FALTA')),
  origen text NOT NULL CHECK (origen IN ('HUMANO','SISTEMA')),
  valido boolean NOT NULL DEFAULT true, version integer NOT NULL DEFAULT 1,
  actor_sub text NOT NULL, publicado_en timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ast_registro_vigente_dia ON ast_registros(persona_id,fecha) WHERE valido;
CREATE INDEX ast_registros_seccion_fecha ON ast_registros(seccion_id,fecha) WHERE valido;
CREATE TABLE ast_auditoria (
  id uuid PRIMARY KEY, registro_id uuid REFERENCES ast_registros(id),
  seccion_id uuid NOT NULL, fecha date NOT NULL, persona_id uuid,
  accion text NOT NULL, anterior text, nuevo text, motivo text,
  actor_sub text NOT NULL, ocurrido_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ast_operaciones (
  clave text PRIMARY KEY, actor_sub text NOT NULL, respuesta jsonb NOT NULL,
  creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ast_justificaciones (
  id uuid PRIMARY KEY, registro_id uuid NOT NULL REFERENCES ast_registros(id),
  estudiante_id uuid NOT NULL REFERENCES per_personas(id),
  marca_origen text NOT NULL CHECK (marca_origen IN ('FALTA','TARDANZA')),
  estado text NOT NULL CHECK (estado IN ('EN_REVISION','APROBADA','RECHAZADA','OBSOLETA')),
  motivo text NOT NULL CHECK (length(btrim(motivo))>0), version integer NOT NULL DEFAULT 1,
  creado_en timestamptz NOT NULL DEFAULT now(), resuelto_en timestamptz,
  resuelto_por text, comentario_resolucion text
);
CREATE UNIQUE INDEX ast_justificacion_activa ON ast_justificaciones(registro_id)
  WHERE estado IN ('EN_REVISION','APROBADA');
CREATE TABLE ast_justificacion_eventos (
  id uuid PRIMARY KEY, justificacion_id uuid NOT NULL REFERENCES ast_justificaciones(id),
  accion text NOT NULL, contenido text, actor_sub text NOT NULL,
  creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ast_adjuntos (
  id uuid PRIMARY KEY, justificacion_id uuid NOT NULL REFERENCES ast_justificaciones(id),
  nombre text NOT NULL, tipo text NOT NULL, objeto_key text NOT NULL UNIQUE,
  tamano bigint NOT NULL CHECK (tamano>0 AND tamano<=2000000),
  creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ast_salidas (
  id uuid PRIMARY KEY, persona_id uuid NOT NULL REFERENCES per_personas(id),
  matricula_id uuid NOT NULL REFERENCES mat_matriculas(id), seccion_id uuid NOT NULL REFERENCES ofe_secciones(id),
  estado text NOT NULL CHECK (estado IN ('AUTORIZADA','NO_AUTORIZADA')),
  motivo text NOT NULL CHECK (length(btrim(motivo))>0),
  actor_sub text NOT NULL, ocurrido_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ast_avisos_pendientes (
  clave text PRIMARY KEY, tipo text NOT NULL, destinatario_id uuid NOT NULL,
  seccion_id uuid, fecha date, mensaje text NOT NULL,
  enviado_en timestamptz, intentos integer NOT NULL DEFAULT 0,
  creado_en timestamptz NOT NULL DEFAULT now()
);
