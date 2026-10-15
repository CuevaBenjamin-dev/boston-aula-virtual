# Tareas y calificaciones

Las tareas nacen como borrador y pueden editarse con control optimista de `version`; solo publicadas son visibles al estudiante. La entrega conserva una fila por versión y respeta fecha límite, aceptación tardía y reentrega. La nota se guarda en `calificaciones_tarea` con `entrega_version`; cada cambio queda en `historial_calificaciones` y auditoría. El cliente debe enviar la versión de la nota; un valor obsoleto provoca 409. Una reentrega posterior aparece como pendiente de revisión hasta que se califique su versión. La escala máxima se configura por tarea. Estas notas pertenecen al Aula y no modifican las oficiales del ERP.

Materiales y archivos de entrega se cargan con multipart directamente al S3 local mediante URL temporal; API verifica tamaño, tipo y firma básica antes de asociarlos. Un PDF de tarea y otro de entrega fueron subidos y leídos con autorización. Los límites de inspección documental se describen en [brechas](brechas-logica-negocio.md).

