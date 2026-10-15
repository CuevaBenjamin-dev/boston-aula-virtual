# Tareas y calificaciones

Al enviar el formulario docente, la tarea se registra temporalmente como `FALTA_COMPLETAR` mientras carga el material opcional. Después se publica; solo entonces la ve el estudiante. Una carga interrumpida conserva la tarea incompleta para retomarla. El docente puede editar título, instrucciones, plazo y material después de publicar, con control optimista de `version`. La escala máxima se fija en 20 puntos: permite validar las calificaciones y mostrar su denominador, pero ya no se pide al crear tareas.

Las tareas nuevas no admiten entregas fuera de plazo ni reentregas. Las tareas históricas conservan sus banderas previas para no alterar entregas existentes. Cada entrega conserva su versión y archivos privados. La nota se guarda en `calificaciones_tarea` con `entrega_version`; cada cambio queda en `historial_calificaciones` y auditoría. Un valor de `version` obsoleto provoca 409. Estas notas pertenecen al Aula y no modifican las oficiales del ERP. El curso del estudiante resume tareas publicadas durante la semana actual; el historial completo queda en Mis tareas. Mis notas tiene detalle independiente.

Materiales y archivos de entrega se cargan con multipart directamente al S3 local mediante URL temporal; API verifica tamaño, tipo y firma básica antes de asociarlos. Un PDF de tarea y otro de entrega fueron subidos y leídos con autorización. Los límites de inspección documental se describen en [brechas](brechas-logica-negocio.md).

