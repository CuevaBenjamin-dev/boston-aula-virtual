# CP-08 · Evaluar y consultar calificaciones

## E¹ · Resultado esencial

El docente asignado en ERP revisa las entregas e intentos de sus cursos, guarda una nota numérica y observaciones en borrador, publica el resultado y puede preparar y publicar una corrección. El estudiante consulta únicamente sus resultados publicados. Las notas de tareas residen en API Aula; no se envían como calificaciones oficiales al ERP.

La escala inicial es de 0 a 20, con hasta dos decimales. El backend usa `BigDecimal` y PostgreSQL `NUMERIC(8,2)`. Los límites del backend se pueden configurar con `boston.calificaciones.minimo` y `boston.calificaciones.maximo`; la escala visible actual de la tarea sigue siendo 20.

## E² · Escenarios y reglas

| Caso | Resultado implementado |
| --- | --- |
| CP-08.01 Revisar entrega | El docente autorizado abre texto, archivos e historial de intentos desde Tareas y entregas o desde Calificaciones. |
| CP-08.02 Pendiente | Sin evaluación publicada, el intento figura pendiente; no se asigna cero. |
| CP-08.03 Borrador | Nota y observaciones persisten en API Aula. Solo el docente autorizado las consulta; no se avisa al estudiante. |
| CP-08.04 Publicar | La publicación registra fecha y responsable, vuelve visible el resultado y envía un aviso. La operación lleva identificador único para evitar duplicados. |
| CP-08.05 Corregir | El borrador de corrección conserva visible la nota previa. Al publicar, se registra valor y observación anteriores y nuevos, responsable y momento, y se envía otro aviso. |
| CP-08.06 Reentregas | Cada intento tiene nota independiente. El intento nuevo aparece pendiente hasta su publicación; la nota previa sigue en el historial. No se calcula promedio. |
| CP-08.07 Sin entrega | Se muestra «Sin entregar» y se impide crear una nota ordinaria sin entrega. |
| CP-08.08 Cambio de docente | Las escrituras comprueban la asignación ERP actual. El docente anterior pierde permiso y los responsables previos permanecen en el historial. |
| CP-08.09 Simultaneidad | La versión de la nota rechaza una edición o publicación obsoleta con conflicto. |
| CP-08.10 Cierre de periodo | La fecha final del periodo sincronizada desde ERP bloquea escritura académica ordinaria. Las notas existentes permanecen. |
| CP-08.11 Tardías | Se pueden evaluar sin descuento automático; la marca de tardanza permanece en la entrega. |
| CP-08.12 Canceladas o sustituidas | Las notas anteriores se conservan y no se transfieren. Estas tareas no aceptan nuevas publicaciones o correcciones ordinarias. |
| CP-08.13 Escala | Solo valores numéricos 0–20 de hasta dos decimales; sin rúbricas, letras, promedios ni sincronización oficial ERP. |
| CP-08.14 Autorización | El docente edita solo cursos asignados, el estudiante ve solo sus notas publicadas y Coordinación consulta resultados publicados de su ámbito. Administración no edita notas. |

Las tareas cerradas manualmente sí permiten evaluar entregas recibidas. La puntualidad, la entrega, el borrador y la publicación son estados separados.

## Servicio y persistencia

`PUT /tareas/{id}/calificacion/borrador` guarda o modifica el borrador. `POST /tareas/{id}/calificacion/publicar` publica con versión e identificador de operación. `GET /notas/{id}/historial` expone las publicaciones y correcciones autorizadas. El servicio y la tabla son compartidos por Tareas y entregas y Calificaciones. El endpoint anterior `PUT /tareas/{id}/calificacion` mantiene publicación directa para compatibilidad con clientes locales previos.

La migración `V20__borradores_publicacion_calificaciones.sql` agrega estado, fecha/responsable de publicación, campos privados del borrador de corrección e identificador de publicación al historial. `V21__restaurar_valor_obligatorio_calificacion.sql` mantiene obligatorio el valor numérico de cada fila. Las notas previas quedan marcadas como publicadas. No se reinicia ningún dato.

## Pruebas y evidencia

- Compilación de API Aula y frontend: correcta.
- Pruebas unitarias focalizadas de `CursosYTareasTest`: correctas.
- `python pruebas/cp08-verificar-flujos.py`: 18 comprobaciones agrupadas correctas. También rechazó valores fuera de escala, versiones obsoletas, docente ajeno, estudiante editor y acceso a nota ajena. Creó dos tareas temporales y las eliminó al concluir.
- Flyway aplicó V20 correctamente; V21 restaura la restricción de valor obligatorio. Consulta posterior: cero tareas temporales CP-08.
- Navegador local: cargó la portada de `http://localhost:8088` y abrió el formulario de Boston Identidad. El flujo académico autenticado se comprobó con cuentas de prueba mediante la API local.

## Brechas pendientes

- Boston Identidad aún no ofrece el rol Apoderado; falta acordar su vínculo y alcance de consulta antes de exponerle resultados.
- El ERP actual aporta la fecha final del periodo, pero no un estado explícito de «cierre académico oficial» ni excepciones administrativas aprobadas. Debe definirse ese contrato antes de implementarlas.
- Tras un cambio de sección o fin de matrícula, las consultas ordinarias usan el vínculo activo. La política para consultar notas históricas desde la cuenta del estudiante requiere definición de negocio.

## Ficha de evaluación técnica

| Criterio | Estado |
| --- | --- |
| Integridad por intento | Una fila de nota por tarea, estudiante e intento; historial de publicaciones por nota. |
| Privacidad | Borradores fuera de consultas estudiantiles y de Coordinación; autorizaciones comprobadas en backend. |
| Concurrencia | Bloqueo transaccional de tarea y nota, más versión optimista. |
| Avisos | Se emiten en la misma transacción de publicación o corrección; no al guardar borrador. |
| Datos previos | Migración incremental y conservación de notas ya existentes. |
| Alcance | E¹ y E². E³ (Experiencia UX/UI) queda expresamente pendiente para una etapa posterior. |
