\set ON_ERROR_STOP on
BEGIN;
INSERT INTO periodos_academicos(id,nombre,inicio,fin)
VALUES (md5('cp05:periodo:2026')::uuid,'2026',DATE '2026-03-01',DATE '2026-12-31') ON CONFLICT(id) DO NOTHING;
INSERT INTO niveles_educativos(id,nombre)
VALUES (md5('cp05:nivel:Primaria')::uuid,'Primaria'),(md5('cp05:nivel:Secundaria')::uuid,'Secundaria') ON CONFLICT(id) DO NOTHING;
INSERT INTO grados(id,nivel_id,nombre)
VALUES (md5('cp05:grado:Primaria:Quinto')::uuid,md5('cp05:nivel:Primaria')::uuid,'Quinto'),
       (md5('cp05:grado:Secundaria:Quinto')::uuid,md5('cp05:nivel:Secundaria')::uuid,'Quinto') ON CONFLICT(id) DO NOTHING;
INSERT INTO asignaturas(id,nombre)
VALUES (md5('cp05:asignatura:Matemática')::uuid,'Matemática'),
       (md5('cp05:asignatura:Comunicación')::uuid,'Comunicación'),
       (md5('cp05:asignatura:Inglés')::uuid,'Inglés') ON CONFLICT(id) DO NOTHING;

DO $$
DECLARE s integer; n integer; materia text; codigo text; nivel text; letra text;
  nombres text[]:=ARRAY['Alma','Bruno','Camila','Diego','Elena','Fabio','Gina','Hugo','Iris','Joaquín','Kiara','Leo','Mara','Nicolás','Olivia','Pablo','Renata','Samuel','Tania','Ulises','Vera','Yago'];
  apellidos text[]:=ARRAY['Salcedo','Luján','Varela','Montes','Rivas','Del Valle','Cárdenas','Beltrán','Aguilar','Solis','Villena','Arce','Padilla','Mendoza','Cornejo','Valdivia','Pinto','Estrada','Reyes','Campos','Ferrero','Quiroz'];
BEGIN
  FOR n IN 1..3 LOOP
    INSERT INTO personas(id,nombres,apellidos,documento) VALUES
      (md5('cp05:persona:docente:'||n)::uuid,
       (ARRAY['Lucía','Rafael','Valeria'])[n],
       (ARRAY['Torres Valverde','Paredes Molina','Muñoz Salazar'])[n],
       'FICT-DOC-'||n) ON CONFLICT(id) DO NOTHING;
    INSERT INTO docentes(id,persona_id,estado) VALUES
      (md5('cp05:docente:'||n)::uuid,md5('cp05:persona:docente:'||n)::uuid,'ACTIVO') ON CONFLICT(id) DO NOTHING;
  END LOOP;
  FOR s IN 1..3 LOOP
    codigo:=(ARRAY['P5A','P5B','S5A'])[s];
    nivel:=CASE WHEN s=3 THEN 'Secundaria' ELSE 'Primaria' END;
    letra:=CASE WHEN s=2 THEN 'B' ELSE 'A' END;
    INSERT INTO secciones(id,grado_id,periodo_id,nombre) VALUES
      (md5('cp05:seccion:'||codigo)::uuid,md5('cp05:grado:'||nivel||':Quinto')::uuid,
       md5('cp05:periodo:2026')::uuid,letra) ON CONFLICT(id) DO NOTHING;
    FOR n IN 1..22 LOOP
      INSERT INTO personas(id,nombres,apellidos,documento) VALUES
        (md5('cp05:persona:estudiante:'||s||':'||n)::uuid,
         nombres[n],apellidos[((n+s-2)%22)+1]||' Ficticio',NULL) ON CONFLICT(id) DO NOTHING;
      INSERT INTO estudiantes(id,persona_id,codigo,estado) VALUES
        (md5('cp05:estudiante:'||s||':'||n)::uuid,
         md5('cp05:persona:estudiante:'||s||':'||n)::uuid,
         'CP05-'||codigo||'-'||lpad(n::text,2,'0'),'ACTIVO') ON CONFLICT(id) DO NOTHING;
      INSERT INTO matriculas(id,estudiante_id,seccion_id,estado) VALUES
        (md5('cp05:matricula:'||s||':'||n)::uuid,
         md5('cp05:estudiante:'||s||':'||n)::uuid,
         md5('cp05:seccion:'||codigo)::uuid,'ACTIVA') ON CONFLICT(id) DO NOTHING;
    END LOOP;
    FOREACH materia IN ARRAY ARRAY['Matemática','Comunicación','Inglés'] LOOP
      INSERT INTO ofertas_academicas(id,seccion_id,asignatura_id,estado) VALUES
        (md5('cp05:oferta:'||codigo||':'||materia)::uuid,
         md5('cp05:seccion:'||codigo)::uuid,
         md5('cp05:asignatura:'||materia)::uuid,'ACTIVA') ON CONFLICT(id) DO NOTHING;
      INSERT INTO asignaciones_docentes(id,oferta_id,docente_id,estado) VALUES
        (md5('cp05:asignacion:'||codigo||':'||materia)::uuid,
         md5('cp05:oferta:'||codigo||':'||materia)::uuid,
         md5('cp05:docente:'||CASE materia WHEN 'Matemática' THEN 1 WHEN 'Comunicación' THEN 2 ELSE 3 END)::uuid,
         'ACTIVA') ON CONFLICT(id) DO NOTHING;
    END LOOP;
  END LOOP;
END $$;

INSERT INTO ambitos_coordinacion(cuenta_id,seccion_id)
SELECT :'coord_id'::uuid,s.id FROM secciones s WHERE s.periodo_id=md5('cp05:periodo:2026')::uuid
ON CONFLICT DO NOTHING;

INSERT INTO versiones_eventos(entidad_id,version)
SELECT o.id,1 FROM ofertas_academicas o WHERE o.seccion_id IN
  (SELECT id FROM secciones WHERE periodo_id=md5('cp05:periodo:2026')::uuid)
ON CONFLICT DO NOTHING;
INSERT INTO versiones_eventos(entidad_id,version)
VALUES ('00000000-0000-0000-0000-000000000000'::uuid,1) ON CONFLICT DO NOTHING;

INSERT INTO eventos_pendientes(id,tipo,version,entidad_id,datos)
SELECT gen_random_uuid(),'OFERTA_ACTUALIZADA',1,o.id,
  jsonb_build_object(
    'cursoId',o.id::text,'seccionId',s.id::text,'asignatura',a.nombre,
    'seccion',s.nombre,'grado',g.nombre,'nivel',niv.nombre,
    'periodo',p.nombre,'periodoFin',p.fin::text,
    'estudiantes',COALESCE((SELECT jsonb_agg(m.estudiante_id::text ORDER BY m.estudiante_id)
      FROM matriculas m JOIN estudiantes e ON e.id=m.estudiante_id
      WHERE m.seccion_id=s.id AND m.estado='ACTIVA' AND e.estado='ACTIVO'),'[]'::jsonb),
    'docentes',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',d.id::text,'nombre',concat_ws(' ',per.nombres,per.apellidos)))
      FROM asignaciones_docentes ad JOIN docentes d ON d.id=ad.docente_id JOIN personas per ON per.id=d.persona_id
      WHERE ad.oferta_id=o.id AND ad.estado='ACTIVA'),'[]'::jsonb)
  )
FROM ofertas_academicas o JOIN secciones s ON s.id=o.seccion_id
JOIN asignaturas a ON a.id=o.asignatura_id JOIN grados g ON g.id=s.grado_id
JOIN niveles_educativos niv ON niv.id=g.nivel_id JOIN periodos_academicos p ON p.id=s.periodo_id
WHERE p.nombre='2026' AND NOT EXISTS (SELECT 1 FROM auditoria_erp WHERE accion='CP05_SEED');

INSERT INTO eventos_pendientes(id,tipo,version,entidad_id,datos)
SELECT gen_random_uuid(),'AMBITOS_RECONCILIACION',1,'00000000-0000-0000-0000-000000000000'::uuid,
  jsonb_build_object('ambitos',COALESCE((SELECT jsonb_agg(jsonb_build_object('cuentaId',cuenta_id::text,'seccionId',seccion_id::text))
    FROM ambitos_coordinacion),'[]'::jsonb))
WHERE NOT EXISTS (SELECT 1 FROM auditoria_erp WHERE accion='CP05_SEED');

INSERT INTO auditoria_erp(id,actor,accion,detalle)
SELECT gen_random_uuid(),'fixture-local','CP05_SEED',jsonb_build_object('periodo','2026')
WHERE NOT EXISTS (SELECT 1 FROM auditoria_erp WHERE accion='CP05_SEED');
COMMIT;
