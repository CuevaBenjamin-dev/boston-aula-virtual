# Asistencia — modelo de datos

[Flyway V9](../../../../apps/erp/api/src/main/resources/db/migration/V9__asistencia.sql) añade calendarios y excepciones, tramos efectivos de matrícula, jornadas, borradores privados, registros oficiales, auditoría, operaciones idempotentes, justificaciones y eventos, metadatos de comprobantes, salidas y avisos pendientes. Los comprobantes binarios se almacenan en el servicio S3 ya existente; la base ERP conserva clave, tipo, tamaño y nombre.

El índice único parcial `(persona_id, fecha) WHERE valido` impide dos hechos oficiales válidos diarios y permite conservar versiones invalidadas. La exclusión temporal impide solapar tramos de una misma matrícula. Las marcas invalidadas no alimentan estadísticas ni pendientes.

[Flyway V29 de Aula](../../../../apps/aula/api/src/main/resources/db/migration/V29__avisos_asistencia.sql) contiene solo las notificaciones de Aula y su estado de lectura; no duplica el expediente ERP.
