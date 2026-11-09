# Pruebas y evidencia — Personas

## Versión y alcance de la evidencia

El [informe de implementación previo](../../RESULTADO_PERSONAS_PARA_CHATGPT.md) registra una ejecución anterior: **18 pruebas Java aprobadas**, **5 pruebas React aprobadas**, `npm run build` aprobado, PostgreSQL **18.6** con Testcontainers y datos sintéticos. Sus cifras son resultado histórico de esa entrega; **esta reorganización documental no volvió a ejecutar Maven, React, Docker ni mediciones**. El HEAD inicial de esta tarea fue `0c8357091394c0a176bac308bd63718ac764e497`. El estado actual está en [ESTADO.md](ESTADO.md).

## Matriz de cobertura existente y pendiente

| Criterio / escenario | Prueba y fuente | Evidencia previa | Límite pendiente |
|---|---|---|---|
| RI-PER-001–037 y CU-PER-01–07 | [ReglasPersonasTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/ReglasPersonasTest.java), [PersonasEsquemaTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasEsquemaTest.java) | Dominio y restricciones PostgreSQL V2, incluido commit inválido/rollback, reportados antes. | Mapear cada RI a prueba específica y ampliar eventos de auditoría. |
| API, errores, sesión, CSRF y facultades | [PersonasHttpTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java) | Tres pruebas HTTP/seguridad reportadas. | Ámbito real de Coordinación y relación Docente–alumno aún no disponibles. |
| Cinco módulos y límites | [ArquitecturaModularTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java) | Una prueba reportada. | Añadir prueba de capas para DTO HTTP; hoy hay importaciones indebidas. |
| Directorio, registro, ficha y estados | [PersonasModulo.test.tsx](../../../../apps/erp/web/src/modules/personas/PersonasModulo.test.tsx) | Cinco pruebas React reportadas. | Recorrido manual autenticado y accesibilidad completa. |
| Rendimiento JDBC/BD | [PersonasRendimientoTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasRendimientoTest.java) | 30 000 personas sintéticas, 30 clientes, 300 búsquedas p95 **283 ms** (objetivo ≤ 2 s), 30 guardados p95 **214 ms** (objetivo ≤ 3 s) en Docker Desktop. | Medición HTTP extremo a extremo, navegador y muestra representativa institucional. |

El informe previo registra `EXPLAIN (ANALYZE, BUFFERS)`: búsqueda por nombre con `Seq Scan` 15,829 ms y 492 buffers; documento exacto con `Index Scan` 0,082 ms. [MODELO_DATOS.md](MODELO_DATOS.md) conserva el diseño de índices; una medición local no asegura comportamiento en producción. La cifra anterior de 183/218 ms en la especificación procedía de otra suite y se retiró de la fuente funcional para evitar dos resultados presentados como actuales.

## Comandos para próxima ejecución

Desde `apps/erp/api`: `mvn test` y, para PostgreSQL real, `mvn '-Dtest=PersonasEsquemaTest,PersonasHttpTest,PersonasRendimientoTest' test`. Desde `apps/erp/web`: `npm test` y `npm run build`. Registrar fecha, commit, entorno, resultados y fallos nuevos aquí antes de elevar estados. `git diff --check` valida formato, no comportamiento.

## Defectos y bloqueos conocidos

No se afirma que la suite presente tenga fallos: no se ejecutó en esta tarea. Las brechas conocidas por código son DTO HTTP usados en capas internas y ausencia de contratos de ámbito verificables para Coordinación y alumnos asignados a Docente. Auditoría WCAG 2.2 AA, recorrido autenticado y rendimiento HTTP permanecen **no ejecutados o sin evidencia suficiente** para cerrar la verificación. Ver [decisiones pendientes](ESPECIFICACION.md#8-decisiones-pendientes) y [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md).

## Escenarios derivados de la Verificación 1 consolidada — no implementados ni ejecutados

| Área | Escenarios futuros | Reglas |
|---|---|---|
| Documentos e identidad | DNI de ocho dígitos con cero inicial; duplicado vigente; documento histórico ajeno sin solape rechazado; numeración igual con país emisor distinto; rectificación excepcional autorizada con motivo/evidencia; rectificación denegada; UUID e historiales conservados; ninguna fusión automática. | RI-PER-001, 012–016; RNF-PER-002–003. |
| Solicitud preliminar e identificación | Solicitud incompleta en Matrículas sin persona ficticia; estudiante real con provisional sustentada; regularización sin nuevo UUID; ausencia de datos obligatorios impide registro definitivo; normativa de Educación Básica no aplicada por defecto a programa de idiomas. | RI-PER-006–010, 035–036; CU-PER-01. |
| Facultades | Coordinación con permiso expreso y ámbito institucional; alta rechazada sin autorización verificable; docente con estudiante asignado recibe solo datos permitidos; no asignado denegado; identidad oficial reservada a Dirección/Administración con facultad expresa; cuenta sin permiso no hereda facultad del cargo. | RI-PER-024–025, 037; RNF-PER-001, 005. |
| Contactos y apoderados | Contacto autorizado solo para comunicaciones; apoderado con alcance ordinario; autorización verbal comprobada; otorgante sin facultad rechazado; estudiante mayor de edad autoriza cuando corresponda; modificación y revocación con historial; operación fuera del alcance denegada; restricción judicial conocida bloquea solo facultad afectada. | RI-PER-019–023, 027–034; CU-PER-05. |
| Catálogos y protección | CE/pasaporte legítimo admitido según formato configurado; sexo documental pendiente cuando la operación lo permita; nacionalidad desconocida/no determinada/apatridia diferenciadas; «sin parentesco» sin relación familiar ficticia; histórico con acceso restringido; trazabilidad funcional proporcional y control transversal de accesos/sesiones separado. | RI-PER-006–012, 019, 021, 026; RNF-PER-005. |

La matriz compacta [RF/RI/RNF/CU](ESPECIFICACION.md#trazabilidad-compacta-de-requisitos) establece criterios; estos escenarios son cobertura planificada para Verificación 5, no resultados de pruebas. El diseño de escenarios intermodulares requiere los contratos de Identidad, Matrículas y Asignaciones docentes.
