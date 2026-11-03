-- Los alumnos ficticios de V7 no deben aparecer al iniciar el periodo real.
-- Se conservan grados, secciones, asignaturas y oferta académica.
DELETE FROM estudiantes
WHERE codigo LIKE 'PRUEBA-%'
  AND datos_complementarios ->> 'cuenta_prueba' = 'true';

DELETE FROM personas p
WHERE p.id::text LIKE 'b0560000-0000-4000-8000-%'
  AND p.nombres = 'Estudiante'
  AND p.apellidos LIKE 'Prueba %'
  AND NOT EXISTS (SELECT 1 FROM estudiantes e WHERE e.persona_id = p.id);
