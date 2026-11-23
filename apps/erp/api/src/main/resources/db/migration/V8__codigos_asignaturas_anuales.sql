-- Normalización del código visible sin modificar UUID ni relaciones.
-- Flyway ejecuta esta migración PostgreSQL en una transacción.
DO $$
DECLARE
  fila RECORD;
BEGIN
  SELECT anio,nivel,grado,codigo INTO fila
  FROM ofe_asignaturas
  WHERE NOT (
    codigo ~ '^[A-Z0-9_]{1,20}$'
    OR codigo ~ ('^' || CASE nivel WHEN 'PRIMARIA' THEN 'PR' ELSE 'SE' END
      || '-' || lpad(grado::text,2,'0') || '-[A-Z0-9_]{1,20}(-'
      || right(anio::text,2) || ')?$')
  ) LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'Código de asignatura ambiguo: % (% % / %). Revisar antes de normalizar.',
      fila.codigo,fila.nivel,fila.grado,fila.anio;
  END IF;
END $$;

WITH normalizados AS (
  SELECT id,anio,codigo,
    CASE
      WHEN codigo ~ '^[A-Z0-9_]{1,20}$' THEN
        CASE nivel WHEN 'PRIMARIA' THEN 'PR' ELSE 'SE' END || '-' || lpad(grado::text,2,'0') || '-' || codigo || '-' || right(anio::text,2)
      WHEN codigo ~ ('^(PR|SE)-[0-9]{2}-[A-Z0-9_]{1,20}-[0-9]{2}$') THEN codigo
      ELSE codigo || '-' || right(anio::text,2)
    END AS nuevo_codigo
  FROM ofe_asignaturas
)
UPDATE ofe_asignaturas a SET codigo=n.nuevo_codigo
FROM normalizados n WHERE a.id=n.id AND a.codigo IS DISTINCT FROM n.nuevo_codigo;
-- UNIQUE(anio,codigo) protege contra colisiones; cualquier fallo revierte la migración.
