# Incremento ERP navegable — 19/11/2026

## Alcance y procedencia

Se trabajó sobre el ERP del repositorio existente, rama `main`, sin modificar código de Aula ni Identidad. Se leyó el encargo recibido y sus cuatro anexos funcionales. La última exportación MODULA mencionada en esos anexos es la revisión 532; no fue adjuntada en esta tarea. AF-Q01–17 se tratan como decisiones del responsable funcional posteriores a esa exportación, no como cambios ya importados en MODULA.

## Implementación comprobable

| Área | Código/persistencia | Resultado actual |
|---|---|---|
| Acceso | [SeguridadWeb](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/SeguridadWeb.java), [sesión](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/SesionController.java) | OIDC de Identidad; solo DIRECCION y COORDINACION acceden a `/api/erp/v1/**`; cookie ERP separada. |
| Shell y resumen | [App Web](../../apps/erp/web/src/app/router/App.tsx), [resumen API](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/ResumenController.java) | Siete módulos visibles, cifras consultadas de PostgreSQL, estados de módulo explícitos. |
| Personas | [Servicio](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasServicio.java), [Web](../../apps/erp/web/src/modules/personas/PersonasModulo.tsx) | Ficha única y vínculos existentes; fecha de nacimiento exigida al estudiante; teléfono exigido al docente, apoderado y personal; correo opcional. |
| Oferta | [Flyway V3](../../apps/erp/api/src/main/resources/db/migration/V3__mvp_oferta_matriculas.sql), [API](../../apps/erp/api/src/main/java/pe/boston/erp/oferta/api/OfertaController.java), [Web](../../apps/erp/web/src/modules/oferta/OfertaModulo.tsx) | Año escolar, secciones flexibles y asignaturas codificadas por año/grado; no se copian entre años. |
| Matrículas | [Flyway V3](../../apps/erp/api/src/main/resources/db/migration/V3__mvp_oferta_matriculas.sql), [API](../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/api/MatriculasController.java), [Web](../../apps/erp/web/src/modules/matriculas/MatriculasModulo.tsx) | Borrador privado sin crear estudiante; confirmación crea/valida persona y apoderado, exige sección; Vigente→Retirada→Vigente y cierre anual API; peticiones remotas quedan pendientes. |
| Asignaciones | [Flyway V4](../../apps/erp/api/src/main/resources/db/migration/V4__asignaciones_docentes.sql), [API](../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/api/AsignacionesController.java), [Web](../../apps/erp/web/src/modules/asignaciones/AsignacionesModulo.tsx) | Docente activo + sección y asignatura compatibles; vigencia, titular/reemplazo, cierre y alerta de cobertura cuando se desactiva Docente. |

Las solicitudes hacia Identidad y Aula se guardan en `erp_solicitudes_externas` como `PENDIENTE_NO_CONECTADO`; no se afirma que se haya creado una cuenta o un curso en los sistemas remotos. Las tres bases permanecen separadas. La operación institucional local no hace HTTP dentro de su transacción.

## Verificación efectuada

- `mvn -q test` en `apps/erp/api`: **19 pruebas, 0 errores, 0 fallos**. Testcontainers aplicó Flyway V1–V4 en PostgreSQL 18.6. La prueba HTTP verifica oferta, borrador sin Persona, confirmación con apoderado, solicitudes externas, retirada, reincorporación y asignación docente.
- `npm test -- --run` en `apps/erp/web`: **6 pruebas, 0 fallos**, incluido avanzar/volver en el asistente conservando el borrador.
- `npm run build` en `apps/erp/web`: completado; Vite emitió advertencias no bloqueantes del paquete React Router.
- `docker compose up -d --build`: servicios ERP, Aula, Identidad, PostgreSQL y proxys levantados; `api-erp`, `api-aula`, Identidad y PostgreSQL saludables. La base local aplicó Flyway V4.
- Respuestas HTTP 200 en `http://127.0.0.1:8089/`, `/api/erp/v1/sesion`, `http://127.0.0.1:8088/` y discovery OIDC en `http://127.0.0.1:9000/.well-known/openid-configuration`.
- Navegador integrado: pantalla de acceso ERP visible con logo y botón de inicio de sesión. El recorrido autenticado en navegador requiere que el responsable ingrese con su cuenta local; los flujos de negocio autenticados se verificaron por prueba HTTP con Identidad simulada y PostgreSQL real.

## Límites visibles y siguientes incrementos

1. **Integración remota:** falta contrato/versionado, consumidor con reintentos e idempotencia confirmada y reconciliación de solicitudes para Identidad y Aula. Hasta entonces, el alumno/docente no obtiene acceso por la sola operación ERP.
2. **Asistencia:** falta calendario lectivo con excepciones, captura institucional histórica, justificaciones, avisos y contrato del docente en Aula. Su ruta Web se etiqueta “En desarrollo”.
3. **Documentos:** falta almacenamiento protegido y controles de conservación antes de aceptar archivos de personas reales; las imágenes opcionales DNI aún no se cargan. Su ruta Web se etiqueta “En desarrollo”.
4. **Atención institucional:** falta contrato de consultas de Aula y bandeja Libre/Ocupada. Su ruta Web se etiqueta “En desarrollo”.
5. **Matrículas:** cancelación controlada de borradores, vista Web de cierre anual, aviso por pérdida del último apoderado de matrícula vigente y variantes de cobertura aún faltan. No se pausa automáticamente la matrícula ni Aula.
6. **Oferta/Asignaciones:** calendario, publicación/edición de oferta, horarios estructurados y reglas de superposición de reemplazos aún faltan. El campo actual de horario es solo una referencia textual, no motor de programación.
7. **Aprobación formal:** reconciliar las decisiones posteriores a MODULA 532 con la exportación más reciente y pasar las verificaciones modulares pendientes. No usar datos personales reales antes de cerrar protección y conservación.

## Apertura manual

En la raíz, ejecutar `docker compose up -d --build`, luego abrir <http://127.0.0.1:8089>. Usar una de las cuentas locales de Dirección o Coordinación de `cuentas-iniciales.local.txt` (archivo ignorado por Git); no copiar la clave a código ni a incidencias. El módulo Oferta permite crear el año y la sección; Personas permite registrar a un docente; Matrículas guarda un borrador y confirma con datos válidos; Asignaciones utiliza la oferta y el docente. Para parar conservando datos, `docker compose stop`.
