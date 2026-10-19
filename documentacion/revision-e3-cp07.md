# CP-07 — Realizar y entregar tareas: revisión E¹ y E²

Fecha: 19 de octubre de 2026. Entorno: `boston-aula` en Docker local. Se revisaron brevemente CP-05 y CP-06 y se conservaron sus datos y mecanismos de tareas, versiones, avisos, matrícula y almacenamiento.

## E¹ — Resultado primordial

El estudiante consulta una tarea, prepara una respuesta con archivos, la guarda y retoma sin generar una entrega. Al confirmar recibe una constancia con fecha, hora e intento registrados por el servidor. Puede consultar el historial y reentregar por su cuenta tantas veces como necesite dentro del plazo. Una vez vencido, solo puede realizar una reentrega tardía. El docente consulta los intentos y califica el vigente. La API valida la matrícula, la asignación docente, la versión y el estado vigente; el frontend solo presenta las operaciones.

## E² — Escenarios y reglas aplicadas

| Caso | Estado y regla |
|---|---|
| CP-07.01 Consultar tarea | Reutiliza CP-05/06: instrucciones, adjuntos, plazo, estado, versiones y permisos. La API expone si puede iniciarse el siguiente intento. |
| CP-07.02 y 07.06 Archivos | Hasta cuatro por intento, 25 MB cada uno y 100 MB en total. Se pueden añadir juntos o por separado, retirar y reemplazar antes de confirmar. Los demás archivos permanecen. Límites en frontend y API. Los materiales docentes conservan sus límites previos. |
| CP-07.03 Guardar preparación | PostgreSQL guarda texto, intento y archivos asociados; S3 local guarda los objetos. La preparación activa es única por estudiante y tarea. Se retoma en otra sesión. No cuenta ni notifica como entrega. |
| CP-07.04 Confirmar | La transacción verifica tarea abierta, versión, matrícula, intento, archivos disponibles y contenido. Registra recepción, traslada metadatos de archivos al intento, devuelve constancia y emite el aviso docente agrupado. |
| CP-07.05 Tardía | Se reutiliza CP-06: publicada y abierta admite entrega después del plazo, con estado `TARDIA`, hora real y plazo aplicado a la recepción. Sin cierre ni penalización automática. |
| CP-07.07 Reentrega | Dentro del plazo, el estudiante puede reentregar sin autorización docente y sin límite de intentos. Fuera del plazo, solo puede confirmar una reentrega tardía. Cada intento conserva identificador y constancia independientes; la sucesora de tarea sigue siendo una actividad distinta. |
| CP-07.08 Después de calificar | La nota y comentario permanecen vinculados al intento anterior. El nuevo aparece pendiente y genera el aviso de entrega al docente. No se copia la nota. |
| CP-07.09 Fallos de carga | Una carga incompleta bloquea la confirmación. El estudiante puede retirar el archivo fallido y reintentar. La preparación persiste. La limpieza periódica aborta cargas abandonadas y elimina archivos retirados que quedaron en S3. |
| CP-07.10 Idempotencia | Cada confirmación lleva un UUID de operación; repetirlo devuelve la misma constancia. Bloqueo y restricciones de base evitan intentos o avisos duplicados. |
| CP-07.11 Cierre/cancelación | La confirmación vuelve a comprobar el estado dentro de la transacción. Cerrada, cancelada o sustituida rechaza nuevas entregas y conserva el historial. |
| CP-07.12 Nueva versión | Se reutiliza CP-05: tarea sucesora con intentos propios; los originales y sus calificaciones siguen en la tarea anterior. |
| CP-07.13 Validación | PDF, DOCX, PPTX, XLSX, TXT, JPG y PNG. La API coteja extensión, tipo declarado, tamaño y contenido básico (firmas, estructura Office y texto UTF-8). Usa claves internas UUID, objetos privados y enlaces de corta duración bajo autorización. |
| CP-07.14 Autorización | Estudiante limitado a su matrícula, preparación, entregas y archivos; docente limitado a cursos asignados. La API impide escrituras sobre entregas confirmadas y acceso a identificadores ajenos. |

La respuesta escrita es opcional cuando hay al menos un archivo. Una confirmación vacía se rechaza. El plazo no cierra automáticamente una tarea publicada.

## Correcciones y conservación de datos

La entrega antes se creaba antes de terminar la carga del archivo; ahora se confirma después de validar todos los adjuntos. `V19__preparaciones_y_reentregas.sql` añade preparaciones, archivos asociados a ellas, clave de confirmación y nota por intento. La tabla de autorizaciones creada por esa migración queda sin uso tras la regla de reentrega automática; se conserva para no alterar una migración ya aplicada. La migración conserva entregas, notas y archivos anteriores. El historial de calificaciones y las notificaciones apuntan al intento correspondiente. No se añadieron dependencias ni servicios.

Los controles de entrega se limitan a archivos, guardar preparación, confirmar y consultar intentos. El control docente de autorización se retiró conforme a la regla vigente.

## Ficha de evaluación técnica y evidencia

| Comprobación | Resultado |
|---|---|
| `mvn -q -Dtest=CursosYTareasTest test` en `api-aula` | Correcto. |
| `npm run build` en `frontend` | Correcto. |
| `python -u pruebas/cp07-verificar-flujos.py` | 16 comprobaciones correctas con Sofía, Mateo, Carlos y Patricia: preparación recuperable, límites y sustitución de archivos, confirmación repetida, reentregas dentro del plazo, una sola reentrega tardía, nota anterior, cierre y privacidad. Las tareas temporales se eliminan al terminar. |
| Seguridad adicional | Archivo ajeno y docente ajeno rechazados; extensión o contenido falso rechazados. |
| Navegador local | Inicio de sesión de Sofía, lista de Mis tareas y detalle de una tarea existente. Se ven el intento, texto opcional, selector con límites, Guardar preparación y Confirmar entrega. No se alteró esa tarea. |
| Docker y datos | Servicios locales operativos en `http://localhost:8088` (HTTP 200), API Aula saludable, Flyway V19 aplicada y cero tareas temporales CP-07. |

La prueba usa una tarea temporal creada por `cp06.carlos.demo` en Matemática 5.º A y no modifica tareas de referencia. Para repetirla: `python -u pruebas/cp07-verificar-flujos.py` desde la raíz del proyecto, con los servicios locales y las cuentas CP-06 activos.

## Brechas pendientes

- La validación de contenido detecta formatos básicos; no inspecciona malware ni macros. Antes de producción hacen falta controles de seguridad de archivos acordes a la infraestructura institucional.
- La institución debe definir si alguna tarea exige obligatoriamente texto escrito o archivo. Por ahora basta con uno de los dos.
- Los plazos especiales y la calificación posterior requieren definición institucional en sus casos de uso. La regla actual de reentrega aplica por tarea y cuenta una única reentrega confirmada fuera del plazo.

**E³ — Experiencia UX/UI: pendiente expresamente; no evaluada ni implementada en esta etapa.**
