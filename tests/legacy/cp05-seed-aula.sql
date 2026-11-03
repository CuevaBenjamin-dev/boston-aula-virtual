\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM cursos WHERE id IN
    (SELECT md5('cp05:oferta:'||s.codigo||':'||a.nombre)::uuid
     FROM (VALUES ('P5A'),('P5B'),('S5A')) s(codigo)
     CROSS JOIN (VALUES ('Matemática'),('Comunicación'),('Inglés')) a(nombre)))<>9
    THEN RAISE EXCEPTION 'Aula aún no recibió las nueve ofertas desde ERP'; END IF;
END $$;

INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,estado,creado_por,publicado_en,creado_en)
VALUES
  (md5('cp05:tarea:entregas')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,
   'Problemas de fracciones','Resuelve los cinco problemas de fracciones y explica cada procedimiento.',
   now()+interval '7 days','PUBLICADA',:'docente_id'::uuid,now(),now()),
  (md5('cp05:tarea:urgente')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,
   'Cálculo mental semanal','Completa los ejercicios de cálculo mental en un archivo o en la respuesta.',
   now()+interval '8 hours','PUBLICADA',:'docente_id'::uuid,now(),now()),
  (md5('cp05:tarea:borrador')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,
   'Geometría en el entorno','Identifica figuras geométricas en tu entorno y prepara una explicación.',
   now()+interval '12 days','FALTA_COMPLETAR',:'docente_id'::uuid,NULL,now()),
  (md5('cp05:tarea:version1')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,
   'Medidas de longitud (versión anterior)','Actividad original conservada como evidencia de entregas.',
   now()-interval '2 days','CERRADA',:'docente_id'::uuid,now()-interval '8 days',now()-interval '8 days'),
  (md5('cp05:tarea:version2')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,
   'Medidas de longitud (versión vigente)','Resuelve la actividad corregida. La entrega anterior no cuenta para esta versión.',
   now()+interval '10 days','PUBLICADA',:'docente_id'::uuid,now(),now()),
  (md5('cp05:tarea:p5b')::uuid,md5('cp05:oferta:P5B:Matemática')::uuid,
   'Números decimales','Compara números decimales y escribe una justificación breve.',
   now()+interval '6 days','PUBLICADA',:'docente_id'::uuid,now(),now()),
  (md5('cp05:tarea:s5a')::uuid,md5('cp05:oferta:S5A:Matemática')::uuid,
   'Funciones cuadráticas','Construye la gráfica y explica sus puntos principales.',
   now()+interval '9 days','PUBLICADA',:'docente_id'::uuid,now(),now())
ON CONFLICT(id) DO NOTHING;
UPDATE tareas SET tarea_anterior_id=md5('cp05:tarea:version1')::uuid
WHERE id=md5('cp05:tarea:version2')::uuid AND tarea_anterior_id IS NULL;

INSERT INTO entregas(id,tarea_id,estudiante_erp_id,version,respuesta,estado,entregado_en)
VALUES
  (md5('cp05:entrega:fracciones:1')::uuid,md5('cp05:tarea:entregas')::uuid,md5('cp05:estudiante:1:1')::uuid,1,'Resolví los ejercicios y expliqué el procedimiento.','ENVIADA',now()-interval '3 hours'),
  (md5('cp05:entrega:fracciones:2')::uuid,md5('cp05:tarea:entregas')::uuid,md5('cp05:estudiante:1:2')::uuid,1,'Adjunto mi resolución de fracciones.','ENVIADA',now()-interval '2 hours'),
  (md5('cp05:entrega:fracciones:3')::uuid,md5('cp05:tarea:entregas')::uuid,md5('cp05:estudiante:1:3')::uuid,1,'Mis resultados están en la respuesta.','ENVIADA',now()-interval '1 hour'),
  (md5('cp05:entrega:anterior:1')::uuid,md5('cp05:tarea:version1')::uuid,md5('cp05:estudiante:1:1')::uuid,1,'Primera respuesta de la versión anterior.','ENVIADA',now()-interval '6 days'),
  (md5('cp05:entrega:anterior:2')::uuid,md5('cp05:tarea:version1')::uuid,md5('cp05:estudiante:1:2')::uuid,1,'Segunda respuesta de la versión anterior.','ENVIADA',now()-interval '5 days')
ON CONFLICT(id) DO NOTHING;

INSERT INTO calificaciones_tarea(id,tarea_id,estudiante_erp_id,valor,comentario,entrega_version,actualizado_por)
VALUES
  (md5('cp05:nota:anterior:1')::uuid,md5('cp05:tarea:version1')::uuid,md5('cp05:estudiante:1:1')::uuid,16,'Buen trabajo en la versión inicial.',1,:'docente_id'::uuid),
  (md5('cp05:nota:anterior:2')::uuid,md5('cp05:tarea:version1')::uuid,md5('cp05:estudiante:1:2')::uuid,15,'Revisa la conversión de unidades.',1,:'docente_id'::uuid)
ON CONFLICT(id) DO NOTHING;

INSERT INTO materiales_curso(id,curso_id,nombre,descripcion,enlace,estado,creado_por)
VALUES
  (md5('cp05:material:guia')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,'Guía de fracciones','Guía compartida en Quinto A y B de Primaria.',NULL,'PUBLICADO',:'docente_id'::uuid),
  (md5('cp05:material:doc')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,'Ficha de problemas','Documento de apoyo para practicar. ',NULL,'PUBLICADO',:'docente_id'::uuid),
  (md5('cp05:material:ppt')::uuid,md5('cp05:oferta:S5A:Matemática')::uuid,'Presentación de funciones','Diapositivas introductorias.',NULL,'PUBLICADO',:'docente_id'::uuid),
  (md5('cp05:material:enlace')::uuid,md5('cp05:oferta:P5B:Matemática')::uuid,'Enlace de consulta','Acceso local de referencia para la demostración.','http://localhost:8088/','PUBLICADO',:'docente_id'::uuid),
  (md5('cp05:material:borrador')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,'Material en preparación','Aún no visible para estudiantes.',NULL,'BORRADOR',:'docente_id'::uuid),
  (md5('cp05:material:oculto')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,'Guía retirada','Se conserva para consulta docente.',NULL,'OCULTO',:'docente_id'::uuid)
ON CONFLICT(id) DO NOTHING;
INSERT INTO destinos_material(material_id,curso_id,publicado_en)
VALUES
  (md5('cp05:material:guia')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,now()),
  (md5('cp05:material:guia')::uuid,md5('cp05:oferta:P5B:Matemática')::uuid,now()),
  (md5('cp05:material:doc')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,now()),
  (md5('cp05:material:ppt')::uuid,md5('cp05:oferta:S5A:Matemática')::uuid,now()),
  (md5('cp05:material:enlace')::uuid,md5('cp05:oferta:P5B:Matemática')::uuid,now()),
  (md5('cp05:material:borrador')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,NULL),
  (md5('cp05:material:oculto')::uuid,md5('cp05:oferta:P5A:Matemática')::uuid,now())
ON CONFLICT DO NOTHING;

INSERT INTO notificaciones_tarea(id,tarea_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,clave_evento,leida_en)
SELECT gen_random_uuid(),t.id,'ESTUDIANTE',i.estudiante_erp_id,'PUBLICADA','Nueva tarea: '||t.titulo,
  'CP05:PUBLICADA:'||t.id::text||':'||i.estudiante_erp_id::text,
  CASE WHEN i.estudiante_erp_id=md5('cp05:estudiante:1:1')::uuid AND t.id=md5('cp05:tarea:entregas')::uuid THEN now() ELSE NULL END
FROM tareas t JOIN inscripciones_curso i ON i.curso_id=t.curso_id AND i.activo
WHERE t.estado='PUBLICADA' AND t.id IN
 (SELECT md5('cp05:tarea:'||x)::uuid FROM unnest(ARRAY['entregas','urgente','version2','p5b','s5a']) x)
ON CONFLICT(clave_evento) DO NOTHING;

INSERT INTO notificaciones_tarea(id,tarea_id,destinatario_tipo,destinatario_erp_id,tipo,mensaje,nuevas_entregas,leida_en)
VALUES
 (md5('cp05:aviso:docente:leido')::uuid,md5('cp05:tarea:entregas')::uuid,'DOCENTE',md5('cp05:docente:1')::uuid,'ENTREGAS','Nueva entrega en Problemas de fracciones',1,now()-interval '2 hours'),
 (md5('cp05:aviso:docente:pendiente')::uuid,md5('cp05:tarea:entregas')::uuid,'DOCENTE',md5('cp05:docente:1')::uuid,'ENTREGAS','2 entregas nuevas en Problemas de fracciones',2,NULL)
ON CONFLICT(id) DO NOTHING;
INSERT INTO eventos_notificacion_entrega(entrega_id,docente_erp_id,notificacion_id)
VALUES
 (md5('cp05:entrega:fracciones:1')::uuid,md5('cp05:docente:1')::uuid,md5('cp05:aviso:docente:leido')::uuid),
 (md5('cp05:entrega:fracciones:2')::uuid,md5('cp05:docente:1')::uuid,md5('cp05:aviso:docente:pendiente')::uuid),
 (md5('cp05:entrega:fracciones:3')::uuid,md5('cp05:docente:1')::uuid,md5('cp05:aviso:docente:pendiente')::uuid)
ON CONFLICT DO NOTHING;
COMMIT;
