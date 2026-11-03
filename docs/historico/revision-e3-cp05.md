# Revisión E³ — CP-05: cursos, materiales y tareas

Fecha: 17 de octubre de 2026. Alcance: entorno Docker **local** de `boston-aula`. ERP mantiene las ofertas, matrículas y asignaciones docentes; Aula conserva una proyección y los trabajos. La carga CP-05 está separada de Flyway.

## Alcance funcional

| Caso | Resultado y regla aplicada | Evidencia local |
|---|---|---|
| CP-05.01 Cursos | Matrícula o asignación vigente, grado, sección, nivel y periodo proceden de ERP. Un curso finalizado sale de la lista activa. | Estudiante de Primaria 5.º A vio sus tres asignaturas; docente de Matemática vio 5.º A, 5.º B y Secundaria 5.º A. |
| CP-05.02 Avisos | Publicar avisa a cada matriculado del curso; guardar borrador no avisa. Una entrega nueva incrementa el único aviso docente no leído por tarea. Abrir la bandeja no marca como leído. | Prueba de dos entregas, lectura del aviso y una tercera entrega: historial leído + un aviso pendiente nuevo. |
| CP-05.03 Borradores | Tareas y materiales se guardan privados, admiten edición y tienen acción de publicación. | Consulta estudiantil no mostró borradores; publicación activó visibilidad. |
| CP-05.04 Adjuntos y edición | Varios adjuntos, alta, sustitución y retiro individual. Nueva tarea desde curso con selección previa; desde Tareas y entregas sin selección. Edición con entregas requiere confirmación y crea sucesora vinculada. | Prueba API de dos archivos, sustitución y retiro; navegador de ambos formularios; prueba de sucesora y reintento. |
| CP-05.05 Ocultar material | Ocultarlo retira acceso estudiantil, conserva registros y exige permiso docente. | Consulta y enlace de archivo oculto rechazados. |
| CP-05.06 Varias secciones | Un material puede tener varios destinos del mismo docente y un único objeto físico. | Material temporal visible en 5.º A y 5.º B, rechazado en Secundaria 5.º A. |
| CP-05.07 Periodos anteriores | Solo cursos dentro del periodo aparecen activos. Coordinación y Administración tienen consulta histórica de cursos, matrículas, tareas, entregas y notas, sin edición. | Migración y rutas de consulta verificadas; falta simular el cierre real de 2026 y un traslado institucional. |
| CP-05.08 Seguridad | API verifica curso, matrícula/asignación, estado del material y vigencia antes de mostrar metadatos o emitir enlace S3 temporal. | Acceso directo a material de otra sección, borrador u oculto rechazado. |

## Regla de notificaciones

`notificaciones_tarea` conserva fecha y lectura por destinatario. La publicación y los cambios usan una clave de evento única para que un reintento no duplique avisos. El índice único parcial garantiza como máximo un aviso `ENTREGAS` pendiente para cada pareja docente/tarea. `eventos_notificacion_entrega` registra una sola vez cada entrega/docente. La transacción inserta el evento y crea o incrementa el aviso. Cuando se lee **ese** aviso, queda en el historial; una entrega posterior inicia otro grupo. La bandeja muestra los avisos sin leerlos automáticamente. Los avisos de traslado conservan el curso original y no ofrecen un enlace sin permiso vigente.

## Regla de versiones

Una tarea sin entregas se corrige en el mismo registro; si cambia de sección, el docente debe estar asignado en ambos cursos. Una tarea con entregas exige confirmación y clave de operación: se cierra el original y se crea una sucesora activa. Las entregas, notas e historial de calificación permanecen exclusivamente en el original. Los archivos vigentes se referencian en la sucesora sin duplicar su objeto S3. Un reintento con la misma clave devuelve la misma sucesora. El docente ve el vínculo con la versión previa; el estudiante de la nueva sección solo puede consultar la tarea vigente y sus propias entregas.

## Datos locales y acceso

La carga 2026 contiene Primaria 5.º A y 5.º B, Secundaria 5.º A, 22 alumnos ficticios por sección, tres asignaturas por sección y tres docentes ficticios. Incluye tareas publicadas, en borrador y con entregas/notas; PDF, DOCX, PPTX y enlace; material publicado, borrador y oculto; avisos docentes leídos y pendientes. Las cuentas `estudiante.demo`, `otro.demo`, `estudiante.p5a3.demo`, `estudiante.p5b.demo`, `estudiante.s5a.demo`, `docente.demo`, `coordinacion.demo` y `admin.demo` usan la contraseña de prueba `SEED_PASSWORD` de `.env` (o el valor local por defecto de Compose). No se usaron datos reales.

Las copias de las tres bases anteriores se guardaron en `pruebas/.cp05-backup/` y permanecen fuera de Git. El reinicio ficticio autorizado ya se realizó **una vez**; para repetir la carga sin borrar nuevos datos use `./pruebas/cp05-datos-locales.ps1` desde PowerShell. El modificador `-Reiniciar` solo se admite tras verificar manualmente que los registros sean los ficticios esperados y que existan copias válidas. La carga normal usa `ON CONFLICT` y conserva cantidades e IDs al repetirse. No usar `docker compose down -v`.

## Verificación y pendientes

`python pruebas/cp05-verificar-flujos.py` pasó 16 comprobaciones de publicación, permisos, archivos, agrupación, sucesora, reintento y materiales de varias secciones. El script elimina únicamente los registros temporales que crea. Se comprobó en navegador la selección de curso de ambos formularios, cursos visibles por perfil, resumen estudiantil, materiales y bandeja. Las pruebas Maven de Aula/ERP y la compilación del frontend deben ejecutarse tras los cambios finales.

Pendiente de decisión institucional: fecha y política exactas para cerrar cada periodo y retención de historial; comunicación específica ante retiro o traslado oficial de matrícula; si los estudiantes deben poder consultar sus cursos antiguos al terminar el periodo. No se asumió ninguna regla adicional para esos casos.
