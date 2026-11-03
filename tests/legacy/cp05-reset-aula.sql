\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM cursos) <> 2 OR EXISTS (
    SELECT 1 FROM cursos WHERE id NOT IN (
      '00000000-0000-0000-0000-000000001001'::uuid,
      '4df60eb8-d0e6-44a7-a427-6bfd85821c67'::uuid
    )
  ) THEN RAISE EXCEPTION 'Aula contiene cursos no verificados; se canceló el reinicio local'; END IF;
  IF EXISTS (SELECT 1 FROM tareas WHERE titulo NOT IN (
    'Prueba de matemática','Material PDF de prueba','Borrador editado',
    'ECUACIONES DE PRIMER GRADO','Prueba local de tareas simplificadas','Actividad de demostración'
  )) THEN RAISE EXCEPTION 'Aula contiene tareas no verificadas; se canceló el reinicio local'; END IF;
  IF EXISTS (SELECT 1 FROM mensajes WHERE asunto NOT IN ('Hola','Prueba local de bandeja'))
    THEN RAISE EXCEPTION 'Aula contiene mensajes no verificados; se canceló el reinicio local'; END IF;
END $$;
TRUNCATE TABLE cursos CASCADE;
TRUNCATE TABLE coordinaciones_seccion,registros_auditoria,eventos_integracion,eventos_procesados,preparacion_demo;
UPDATE estado_sincronizacion SET ultima_secuencia=0,ultimo_exito=NULL,ultimo_error=NULL WHERE id=1;
COMMIT;
