# CP-08 · Evaluar y consultar calificaciones

## E¹ · Resultado esencial

El docente asignado en ERP revisa las entregas e intentos de sus cursos, guarda una nota numérica y observaciones en borrador, publica el resultado y puede preparar y publicar una corrección. El estudiante consulta únicamente sus resultados publicados. Las notas de tareas residen en API Aula; no se envían como calificaciones oficiales al ERP.

Estudiante y apoderado comparten una única cuenta académica por decisión institucional. No existe un rol ni una cuenta académica independiente de Apoderado.

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
| CP-08.10 Cierre de periodo | La fecha final del periodo sincronizada desde ERP bloquea provisionalmente la escritura académica ordinaria. No representa un cierre académico oficial aprobado. Las notas existentes permanecen. |
| CP-08.11 Tardías | Se pueden evaluar sin descuento automático; la marca de tardanza permanece en la entrega. |
| CP-08.12 Canceladas o sustituidas | Las notas anteriores se conservan y no se transfieren. Estas tareas no aceptan nuevas publicaciones o correcciones ordinarias. |
| CP-08.13 Escala | Solo valores numéricos 0–20 de hasta dos decimales; sin rúbricas, letras, promedios ni sincronización oficial ERP. |
| CP-08.14 Autorización | El docente edita solo cursos asignados, el estudiante ve solo sus notas publicadas y Coordinación consulta resultados publicados de su ámbito. Administración no edita notas. |

Las tareas cerradas manualmente sí permiten evaluar entregas recibidas. La puntualidad, la entrega, el borrador y la publicación son estados separados.

En Mis tareas, el intento confirmado vigente se muestra como **REVISADA** solo si su evaluación está publicada. Un borrador conserva **ENTREGADA** o **ENTREGADA TARDÍA**. Una reentrega nueva vuelve a mostrar su estado de entrega hasta recibir una publicación propia; las evaluaciones de intentos anteriores siguen en el historial. La tardanza se muestra aparte, incluso si el intento ya figura como REVISADA.

## Servicio y persistencia

`PUT /tareas/{id}/calificacion/borrador` guarda o modifica el borrador. `POST /tareas/{id}/calificacion/publicar` publica con versión e identificador de operación. `GET /notas/{id}/historial` expone las publicaciones y correcciones autorizadas. El servicio y la tabla son compartidos por Tareas y entregas y Calificaciones. Se retiró `PUT /tareas/{id}/calificacion`, que publicaba directamente; los dos scripts locales que lo usaban siguen el flujo de borrador y publicación.

La migración `V20__borradores_publicacion_calificaciones.sql` agrega estado, fecha/responsable de publicación, campos privados del borrador de corrección e identificador de publicación al historial. `V21__restaurar_valor_obligatorio_calificacion.sql` mantiene obligatorio el valor numérico de cada fila. Las notas previas quedan marcadas como publicadas. No se reinicia ningún dato.

## Pruebas y evidencia

- Compilación de API Aula y frontend: correcta.
- Pruebas unitarias focalizadas de `CursosYTareasTest`: correctas.
- `python pruebas/cp08-verificar-flujos.py`: 18 comprobaciones agrupadas correctas. También rechazó valores fuera de escala, versiones obsoletas, docente ajeno, estudiante editor y acceso a nota ajena. Creó dos tareas temporales y las eliminó al concluir.
- Flyway aplicó V20 correctamente; V21 restaura la restricción de valor obligatorio. Consulta posterior: cero tareas temporales CP-08.
- Navegador local: cargó la portada de `http://localhost:8088` y abrió el formulario de Boston Identidad. El flujo académico autenticado se comprobó con cuentas de prueba mediante la API local.

### Ajustes finales (19 de octubre de 2026)

- La ruta antigua de publicación directa devuelve HTTP 404 en una sesión docente. Frontend y scripts locales usan únicamente guardar borrador y publicar; autorizaciones, historial y avisos permanecen en los endpoints vigentes.
- La consulta de tareas del estudiante deriva REVISADA de una evaluación `PUBLICADA` correspondiente al intento más reciente. Una transacción de verificación revertida confirmó: borrador → no revisada; publicación → revisada; reentrega nueva → pendiente con nota anterior intacta. Una consulta autenticada comprobó la marca frente a las notas publicadas.
- El formulario docente está dentro del bloque de la entrega vigente, junto a su contenido y archivos; los intentos anteriores conservan su historial. Se comprobó su ubicación en el componente y compiló el frontend.
- Pasaron las cuatro pruebas focalizadas de `CursosYTareasTest`, la compilación de API Aula, la compilación de frontend y la validación sintáctica de los dos scripts modificados. Los servicios Docker locales quedaron en ejecución; no se aplicó migración nueva ni se conservó dato de prueba.

## Brechas pendientes

- Pendiente el contrato ERP → Aula que distinga fecha final del periodo, cierre académico oficial y excepciones administrativas autorizadas. La restricción actual usa la fecha final de manera provisional; no equivale a un cierre académico oficialmente aprobado. No se implementa aún ese contrato.
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
