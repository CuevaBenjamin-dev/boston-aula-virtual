# Asistencia — pruebas y evidencia

| Matriz aprobada | Prueba automática focalizada | Verificación adicional |
|---|---|---|
| AST-T01/T04/T06 | [AsistenciaFlujoTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/asistencia/AsistenciaFlujoTest.java): avance privado, versión/409, idempotencia, publicación y cierre con/sin marcas. | Concurrencia real de dos sesiones, recarga de Aula y padrón grande: pendientes. |
| AST-T02/T03/T05/T07 | Misma prueba: docente activo sin asignación, fechas vedadas, excepción parcial, tramos de traslado/retiro/reincorporación/finalización y regularización. | Cruce real de medianoche y cierre anual desde navegador: pendientes. |
| AST-T08/T09 | Misma prueba: permisos propios, ambos tipos de justificación, aprobación/rechazo, corrección obsoleta y dos salidas independientes; formato inválido y acceso ajeno a adjuntos rechazados. | Subida/descarga binaria S3 y avisos visibles en sesión estudiantil: pendientes. |
| AST-T10/T11 | Misma prueba: recordatorio deduplicado por docente/sección, token y actor inválidos; token real de servicio Aula→ERP obtuvo 200. `ErpClienteAsistenciaTest` confirma HTTP 503 cuando ERP está inaccesible. | Asignaciones futuras/inactivas, CSRF y revocación de sesión: pendientes. |
| AST-T12 | `ArquitecturaModularTest`, `MatriculasHttpTest`, `FinalizacionAnioTest`, `IntegracionInstitucionalTest`, pruebas focalizadas Aula/Identidad, builds ERP/Aula y `npm run test` Aula aprobados. | ERP Vitest: 22/24 pasan; dos fallos en Personas no modificadas (`Contacto rechazado`, `Vínculo desactivado`). |

Las nueve pruebas de `AsistenciaFlujoTest` pasaron sobre PostgreSQL aislado. No se ha ejecutado el recorrido funcional completo en navegador ni se declara cubierta toda la matriz AST-T01–T12.
