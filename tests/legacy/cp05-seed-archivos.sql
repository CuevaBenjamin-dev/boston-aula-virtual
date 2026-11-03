\set ON_ERROR_STOP on
BEGIN;
INSERT INTO archivos_material(id,material_id,nombre,clave_objeto,tamano,tipo,estado,activo)
VALUES
 (md5('cp05:archivo:guia')::uuid,md5('cp05:material:guia')::uuid,'Guía de fracciones.pdf','materiales/cp05/guia.pdf',:pdf_size,'application/pdf','DISPONIBLE',TRUE),
 (md5('cp05:archivo:ficha')::uuid,md5('cp05:material:doc')::uuid,'Ficha de problemas.docx','materiales/cp05/ficha.docx',:docx_size,'application/vnd.openxmlformats-officedocument.wordprocessingml.document','DISPONIBLE',TRUE),
 (md5('cp05:archivo:presentacion')::uuid,md5('cp05:material:ppt')::uuid,'Funciones cuadráticas.pptx','materiales/cp05/funciones.pptx',:pptx_size,'application/vnd.openxmlformats-officedocument.presentationml.presentation','DISPONIBLE',TRUE),
 (md5('cp05:archivo:oculto')::uuid,md5('cp05:material:oculto')::uuid,'Guía retirada.pdf','materiales/cp05/oculto.pdf',:pdf_size,'application/pdf','DISPONIBLE',TRUE)
ON CONFLICT(id) DO NOTHING;
INSERT INTO archivos_tarea(id,tarea_id,nombre,clave_objeto,tamano,tipo,estado,activo)
VALUES
 (md5('cp05:archivo:tarea:pdf')::uuid,md5('cp05:tarea:entregas')::uuid,'Referencia de fracciones.pdf','tareas/cp05/fracciones.pdf',:pdf_size,'application/pdf','DISPONIBLE',TRUE),
 (md5('cp05:archivo:tarea:docx')::uuid,md5('cp05:tarea:entregas')::uuid,'Plantilla de respuesta.docx','tareas/cp05/fracciones.docx',:docx_size,'application/vnd.openxmlformats-officedocument.wordprocessingml.document','DISPONIBLE',TRUE)
ON CONFLICT(id) DO NOTHING;
COMMIT;
