-- Sustituye exclusivamente los registros fijos de V2; las nuevas matrículas quedan vacías.
DELETE FROM obligaciones_pago WHERE estudiante_id='00000000-0000-0000-0000-000000000101';
DELETE FROM matriculas WHERE id='00000000-0000-0000-0000-000000000801';
DELETE FROM vinculos_estudiante_apoderado WHERE estudiante_id='00000000-0000-0000-0000-000000000101';
DELETE FROM asignaciones_docentes WHERE id='00000000-0000-0000-0000-000000001101';
DELETE FROM ofertas_academicas WHERE id='00000000-0000-0000-0000-000000001001';
DELETE FROM asignaturas_grado WHERE asignatura_id='00000000-0000-0000-0000-000000000901';
DELETE FROM estudiantes WHERE id='00000000-0000-0000-0000-000000000101';
DELETE FROM docentes WHERE id='00000000-0000-0000-0000-000000000201';
DELETE FROM apoderados WHERE id='00000000-0000-0000-0000-000000000301';
DELETE FROM conceptos_cobro WHERE id='00000000-0000-0000-0000-000000001201';
DELETE FROM asignaturas WHERE id='00000000-0000-0000-0000-000000000901';
DELETE FROM secciones WHERE id='00000000-0000-0000-0000-000000000701';
DELETE FROM grados WHERE id='00000000-0000-0000-0000-000000000601';
DELETE FROM niveles_educativos WHERE id='00000000-0000-0000-0000-000000000501';
DELETE FROM periodos_academicos WHERE id='00000000-0000-0000-0000-000000000401';
DELETE FROM personas WHERE id IN ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003');

-- El nombre es visible; el codigo identifica de forma unica el curso de cada grado.
ALTER TABLE asignaturas DROP CONSTRAINT asignaturas_nombre_key;
ALTER TABLE asignaturas ADD COLUMN codigo VARCHAR(40);
UPDATE asignaturas SET codigo='LEG-'||upper(left(replace(id::text,'-',''),12)) WHERE codigo IS NULL;
ALTER TABLE asignaturas ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE asignaturas ADD CONSTRAINT uq_asignaturas_codigo UNIQUE(codigo);

INSERT INTO periodos_academicos(id,nombre,inicio,fin)
VALUES (gen_random_uuid(),'2026',DATE '2026-03-01',DATE '2026-12-31')
ON CONFLICT(nombre) DO NOTHING;

INSERT INTO niveles_educativos(id,nombre) VALUES
  (gen_random_uuid(),'Primaria'),(gen_random_uuid(),'Secundaria')
ON CONFLICT(nombre) DO NOTHING;

WITH nombres(orden,nombre) AS (VALUES
  (1,'Primero'),(2,'Segundo'),(3,'Tercero'),(4,'Cuarto'),(5,'Quinto'),(6,'Sexto'))
INSERT INTO grados(id,nivel_id,nombre)
SELECT gen_random_uuid(),n.id,g.nombre FROM niveles_educativos n JOIN nombres g
  ON g.orden<=CASE WHEN n.nombre='Primaria' THEN 6 ELSE 5 END
WHERE n.nombre IN ('Primaria','Secundaria')
ON CONFLICT(nivel_id,nombre) DO NOTHING;

WITH letras(nombre) AS (VALUES ('A'),('B'),('C'),('D'))
INSERT INTO secciones(id,grado_id,periodo_id,nombre,habilitada)
SELECT gen_random_uuid(),g.id,p.id,l.nombre,TRUE
FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id
CROSS JOIN periodos_academicos p CROSS JOIN letras l
WHERE p.nombre='2026' AND n.nombre IN ('Primaria','Secundaria')
ON CONFLICT(grado_id,periodo_id,nombre) DO NOTHING;

-- Malla curricular base editable. No representa una aprobacion oficial de la institucion.
WITH areas(nivel,sufijo,nombre,orden) AS (VALUES
  ('PRI','COM','Comunicación',1),('PRI','MAT','Matemática',2),
  ('PRI','PSO','Personal Social',3),('PRI','CYT','Ciencia y Tecnología',4),
  ('PRI','ING','Inglés',5),('PRI','AYC','Arte y Cultura',6),
  ('PRI','EFI','Educación Física',7),('PRI','REL','Educación Religiosa',8),
  ('PRI','TUT','Tutoría',9),
  ('SEC','COM','Comunicación',1),('SEC','MAT','Matemática',2),
  ('SEC','CYT','Ciencia y Tecnología',3),('SEC','CSO','Ciencias Sociales',4),
  ('SEC','DPC','Desarrollo Personal, Ciudadanía y Cívica',5),
  ('SEC','ING','Inglés',6),('SEC','AYC','Arte y Cultura',7),
  ('SEC','EFI','Educación Física',8),('SEC','REL','Educación Religiosa',9),
  ('SEC','EPT','Educación para el Trabajo',10),('SEC','TUT','Tutoría',11)),
grados_base AS (
  SELECT g.id,CASE WHEN n.nombre='Primaria' THEN 'PRI' ELSE 'SEC' END AS nivel,
    CASE g.nombre WHEN 'Primero' THEN 1 WHEN 'Segundo' THEN 2 WHEN 'Tercero' THEN 3
      WHEN 'Cuarto' THEN 4 WHEN 'Quinto' THEN 5 ELSE 6 END AS numero
  FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id
  WHERE n.nombre IN ('Primaria','Secundaria'))
INSERT INTO asignaturas(id,codigo,nombre)
SELECT gen_random_uuid(),b.nivel||'-'||lpad(b.numero::text,2,'0')||'-'||a.sufijo,a.nombre
FROM grados_base b JOIN areas a ON a.nivel=b.nivel
ON CONFLICT(codigo) DO NOTHING;

WITH grados_base AS (
  SELECT g.id,CASE WHEN n.nombre='Primaria' THEN 'PRI' ELSE 'SEC' END AS nivel,
    CASE g.nombre WHEN 'Primero' THEN 1 WHEN 'Segundo' THEN 2 WHEN 'Tercero' THEN 3
      WHEN 'Cuarto' THEN 4 WHEN 'Quinto' THEN 5 ELSE 6 END AS numero
  FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id
  WHERE n.nombre IN ('Primaria','Secundaria'))
INSERT INTO asignaturas_grado(id,asignatura_id,grado_id,periodo_id,orden,activa)
SELECT gen_random_uuid(),a.id,b.id,NULL,
  CASE right(a.codigo,3)
    WHEN 'COM' THEN 1 WHEN 'MAT' THEN 2 WHEN 'PSO' THEN 3 WHEN 'CYT' THEN 4
    WHEN 'CSO' THEN 4 WHEN 'DPC' THEN 5 WHEN 'ING' THEN 6 WHEN 'AYC' THEN 7
    WHEN 'EFI' THEN 8 WHEN 'REL' THEN 9 WHEN 'EPT' THEN 10 ELSE 11 END,TRUE
FROM grados_base b JOIN asignaturas a
  ON a.codigo LIKE b.nivel||'-'||lpad(b.numero::text,2,'0')||'-%'
ON CONFLICT DO NOTHING;

INSERT INTO ofertas_academicas(id,seccion_id,asignatura_id,estado)
SELECT gen_random_uuid(),s.id,ag.asignatura_id,'ACTIVA'
FROM secciones s JOIN periodos_academicos p ON p.id=s.periodo_id
JOIN asignaturas_grado ag ON ag.grado_id=s.grado_id AND ag.periodo_id IS NULL AND ag.activa
WHERE p.nombre='2026'
ON CONFLICT(seccion_id,asignatura_id) DO NOTHING;

-- Cuentas de practica: un estudiante por cada combinacion grado/seccion, aun sin matricula.
WITH base AS (
  SELECT n.nombre AS nivel,g.nombre AS grado,s.nombre AS seccion,
    CASE g.nombre WHEN 'Primero' THEN 1 WHEN 'Segundo' THEN 2 WHEN 'Tercero' THEN 3
      WHEN 'Cuarto' THEN 4 WHEN 'Quinto' THEN 5 ELSE 6 END AS numero,
    CASE s.nombre WHEN 'A' THEN 1 WHEN 'B' THEN 2 WHEN 'C' THEN 3 ELSE 4 END AS letra
  FROM secciones s JOIN grados g ON g.id=s.grado_id
  JOIN niveles_educativos n ON n.id=g.nivel_id
  JOIN periodos_academicos p ON p.id=s.periodo_id
  WHERE p.nombre='2026' AND n.nombre IN ('Primaria','Secundaria')),
slots AS (
  SELECT *,CASE WHEN nivel='Primaria' THEN (numero-1)*4+letra ELSE 24+(numero-1)*4+letra END AS slot
  FROM base)
INSERT INTO personas(id,nombres,apellidos)
SELECT ('b0560000-0000-4000-8000-'||lpad(slot::text,12,'0'))::uuid,
  'Estudiante', 'Prueba '||CASE WHEN nivel='Primaria' THEN 'P' ELSE 'S' END||numero||seccion
FROM slots
ON CONFLICT(id) DO NOTHING;

WITH slots AS (
  SELECT n.nombre AS nivel,g.nombre AS grado,s.nombre AS seccion,
    CASE g.nombre WHEN 'Primero' THEN 1 WHEN 'Segundo' THEN 2 WHEN 'Tercero' THEN 3
      WHEN 'Cuarto' THEN 4 WHEN 'Quinto' THEN 5 ELSE 6 END AS numero,
    CASE s.nombre WHEN 'A' THEN 1 WHEN 'B' THEN 2 WHEN 'C' THEN 3 ELSE 4 END AS letra
  FROM secciones s JOIN grados g ON g.id=s.grado_id
  JOIN niveles_educativos n ON n.id=g.nivel_id
  JOIN periodos_academicos p ON p.id=s.periodo_id
  WHERE p.nombre='2026' AND n.nombre IN ('Primaria','Secundaria')),
numerados AS (
  SELECT *,CASE WHEN nivel='Primaria' THEN (numero-1)*4+letra ELSE 24+(numero-1)*4+letra END AS slot
  FROM slots)
INSERT INTO estudiantes(id,persona_id,codigo,estado,datos_complementarios)
SELECT ('b0570000-0000-4000-8000-'||lpad(slot::text,12,'0'))::uuid,
  ('b0560000-0000-4000-8000-'||lpad(slot::text,12,'0'))::uuid,
  'PRUEBA-'||CASE WHEN nivel='Primaria' THEN 'P' ELSE 'S' END||lpad(numero::text,2,'0')||'-'||seccion,
  'ACTIVO',jsonb_build_object('cuenta_prueba',true,'grado_previsto',grado,'seccion_prevista',seccion)
FROM numerados
ON CONFLICT(id) DO NOTHING;
