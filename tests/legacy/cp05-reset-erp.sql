\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM estudiantes WHERE codigo NOT LIKE 'DEMO-%' AND codigo NOT LIKE 'LOCAL-%')
    OR EXISTS (SELECT 1 FROM personas WHERE documento IS NOT NULL AND documento NOT LIKE 'DEMO-%')
    OR (SELECT count(*) FROM ofertas_academicas)<>2
    OR (SELECT count(*) FROM docentes)<>2
  THEN RAISE EXCEPTION 'ERP contiene registros no verificados; se canceló el reinicio local'; END IF;
END $$;
TRUNCATE TABLE personas,periodos_academicos,niveles_educativos,asignaturas,conceptos_cobro CASCADE;
TRUNCATE TABLE eventos_pendientes RESTART IDENTITY;
TRUNCATE TABLE versiones_eventos,auditoria_erp;
COMMIT;
