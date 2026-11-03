# Revisión E³ — CP-06: gestionar tareas

Fecha: 17 de octubre de 2026. Entorno: Docker local de `boston-aula`. Esta revisión parte de CP-05 y conserva su conjunto ficticio de referencia: 66 estudiantes, nueve cursos y siete tareas.

## E¹ — Esencial

La tarea pertenece a **un curso y una sección**. Tiene título, instrucciones, fecha y hora límite en `America/Lima`, materiales opcionales y estado propio. Un borrador (`FALTA_COMPLETAR`) solo lo ve el docente; publicar avisa a los matriculados. La fecha límite define si una entrega es tardía, **sin cerrar la tarea**. Mientras esté `PUBLICADA` y el periodo académico siga vigente, recibe trabajos. Cerrar y cancelar bloquean nuevas entregas, pero conservan las anteriores, sus notas y el historial. Reabrir solo aplica a una tarea cerrada manualmente que no haya sido sustituida.

Una ampliación o corrección menor conserva la tarea y todas sus entregas. Un cambio sustancial con entregas usa la sucesora de CP-05; cada entrega y nota permanece en la versión que la recibió. Para varias secciones se crea una tarea independiente por curso, con sus propios estudiantes, plazo, estado, entregas, notas y avisos.

## E² — Escenarios

| Caso | Regla verificada o completada | Evidencia |
|---|---|---|
| CP-06.01 Creación | Título, instrucciones, curso y sección oficiales, plazo y adjuntos. | Formularios de curso y módulo general; prueba API de publicación. |
| CP-06.02 Borrador | `FALTA_COMPLETAR` sigue privado; una carga interrumpida puede retomarse. | Mecanismo CP-05 reutilizado. |
| CP-06.03 Publicación | Publicación explícita y aviso por curso. | Prueba API y bandeja CP-05; publicación de lote CP-06. |
| CP-06.04 Materiales | Varios adjuntos por tarea; asociación controlada por el docente. | Mecanismo CP-05; archivo compartido por clave S3 en la prueba de lote. |
| CP-06.05 Cambio menor | Ampliación o corrección que no cambia el trabajo: mismo ID, entregas intactas, auditoría y reintento idempotente. | Prueba de ampliación, entrega conservada y aviso único. |
| CP-06.06 Cambio sustancial | Con entregas exige confirmación y crea sucesora; original histórica, sin reapertura. | Prueba de nueva versión, historial y bloqueo de reapertura. |
| CP-06.07 Reasignación | Solo cursos oficiales del docente; entregas y notas anteriores no se trasladan. | Validación de asignación en API; transición CP-05 reutilizada. |
| CP-06.08 Ampliación | Nuevo plazo en Lima; entrega previa conserva el plazo aplicable al recibirse; aviso sin duplicación. | Prueba de ampliación y consulta del plazo guardado en la entrega. |
| CP-06.09 Entrega tardía | Todas las tareas publicadas y abiertas aceptan después del plazo; estado `TARDIA`, hora real, sin penalización automática. | Entrega después del vencimiento con tarea aún `PUBLICADA`. |
| CP-06.10 Cancelación | Confirmación docente; bloquea entregas, conserva registros y avisa. No se reabre ordinariamente. | Cancelación de tarea temporal y rechazo de nueva entrega. |
| CP-06.11 Varias secciones | Creación transaccional e idempotente; tareas, avisos y permisos independientes; objeto de archivo reutilizado. | Dos secciones, reintento sin duplicados, publicación y cancelación aisladas. |
| CP-06.12 Seguimiento | Total, entregaron, tardías, pendientes y por revisar; cuenta alumnos únicos y permite abrir cada grupo. | Conteos 22/2/2/20/2 en prueba focalizada; filtro de pendientes visto en navegador. |
| CP-06.13 Seguridad | API exige asignación docente vigente antes de editar y limita consulta de alumnos por sección. | Edición de tarea ajena rechazada con 403; acceso estudiantil cruzado rechazado. |
| CP-06.14 Cierre y reapertura | Cierre manual bloquea; reapertura conserva trabajos y admite tardías si sigue vencida. | Prueba de cierre, rechazo, reapertura y nueva tardía. |

## E³ — Experiencia de usuario

- **Docente, Tareas y entregas:** filtros por curso, estado y fecha; tarea vencida abierta identificada; contadores compactos. Las versiones sustituidas se consultan por separado.
- **Nueva tarea:** desde Mis cursos hereda el curso; desde Tareas y entregas los selectores empiezan vacíos. La selección de secciones muestra que se crearán tareas independientes. Borrador y publicación son acciones separadas.
- **Detalle docente:** plazo en Lima, recepción abierta o bloqueada, seguimiento por indicador, entregas recibidas, edición, cierre, reapertura y cancelación. La interfaz pide confirmar cierre, cancelación y cambio sustancial.
- **Estudiante:** ve pendiente, urgente, vencida abierta, entregada, tardía, cerrada o cancelada según corresponda. Antes de una tardía se informa la consecuencia; después se muestra fecha y hora de recepción y el estado registrado.

## Revisión transversal de calidad

La API decide permisos, estado y carácter tardío dentro de la transacción; la interfaz solo presenta estas reglas. Cada entrega conserva su fecha real, estado y plazo al recibirse. Los avisos de cambio usan la identidad de tarea y versión, y las operaciones de ampliación y creación múltiple admiten reintentos sin duplicar resultados. Las tareas del lote son independientes después de crearse. El periodo oficial sigue rigiendo el acceso académico. La migración V15 agrega los campos y restricciones necesarios sin borrar datos ni volúmenes.

## Ficha de evaluación y evidencia

| Comprobación | Resultado |
|---|---|
| `mvn -q test` en `api-aula` | Correcto. |
| `npm run build` y reconstrucción de `frontend` y `api-aula` | Correctos; contenedores operativos. |
| `python pruebas/cp06-verificar-flujos.py` | 15 comprobaciones correctas; elimina sus tareas y avisos temporales. |
| Conjunto CP-05 después de las pruebas | 66 estudiantes, nueve cursos y siete tareas; sin lotes ni tareas temporales CP-06. |
| Navegador local | Lista docente, formulario con secciones 5.º A y B, detalle, contadores y filtro de pendientes comprobados. |

## Brechas corregidas

El vencimiento ya no equivale a cierre; la entrega tardía queda distinguida y conserva su plazo original. El docente dispone de cierre, reapertura y cancelación separados. La ampliación no reinicia entregas. La creación para varias secciones ya no mezcla estudiantes ni resultados. Los contadores parten de alumnos únicos y muestran la versión vigente. La validación de asignación se mantiene en el backend.

## Pendientes de decisión institucional

- Delimitar qué correcciones de instrucciones o materiales son **menores** en situaciones académicas ambiguas; el docente declara el tipo de cambio y confirma que un cambio menor no altera el trabajo solicitado.
- Definir la política concreta de reentregas y de revisión/calificación de trabajos tardíos; CP-06 no introduce penalizaciones ni desarrolla CP-07.
- Definir fecha y procedimiento institucional del cierre de cada periodo; el Aula respeta la vigencia conocida del ERP.
- En entregas anteriores a V15 se conserva como referencia el plazo conocido al migrar: no existe historial para reconstruir un plazo previo diferente.

Prueba manual: abrir [Boston Aula Virtual](http://localhost:8088), entrar como `docente.demo`, crear una tarea para Primaria 5.º A y B, verificar los dos detalles y, con una cuenta ficticia de estudiante, probar una entrega después del vencimiento. Las pruebas automatizadas ya cubrieron ese flujo sin modificar las siete tareas de referencia.
