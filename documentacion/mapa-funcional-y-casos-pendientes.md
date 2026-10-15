# Boston Aula Virtual V1 · Mapa funcional y casos por definir

**Corte:** 15 de octubre de 2026. Este documento describe lo que muestra y permite hacer la aplicación local actual. Sirve para decidir reglas escolares antes de ampliar el sistema. Los diez casos iniciales tienen pruebas técnicas documentadas en [pruebas.md](pruebas.md); eso no significa que todos los procesos de negocio estén cerrados.

## 1. Alcance actual

- **Boston Identidad** gestiona cuentas, acceso, recuperación y sesiones.
- **ERP mínimo** es la fuente de estudiantes, apoderados, docentes, matrículas, cursos oficiales y tesorería. Sus altas y pagos administrativos funcionan mediante API; no existe todavía un panel completo del ERP.
- **Aula Virtual** muestra cursos asignados y gestiona tareas, entregas, notas de actividades, clases con enlaces Zoom manuales, grabaciones y supervisión.
- **Cuenta académica:** estudiante y apoderado comparten la misma cuenta del estudiante. El sistema no distingue quién de los dos hizo una entrega o consultó un pago.
- **Fuera de esta V1:** cobro bancario en línea, notas oficiales del ERP, integración automática con Zoom y servicios de producción.

**Lectura de las tablas:** «Disponible» significa que hay una pantalla operativa; «Parcial» significa que la pantalla existe pero falta un paso o regla relevante; «Solo API» significa que no hay pantalla para esa operación.

## 2. Menú y funciones visibles por rol

### Estudiante / apoderado — una misma cuenta

| Módulo visible | Qué puede hacer hoy | Alcance que conviene conocer |
|---|---|---|
| Inicio | Ver tarjetas de sus cursos activos e indicador de avisos de clases pendientes. | No hay tablero general de actividad académica. |
| Mis cursos | Entrar al curso, ver tareas, clases, materiales adjuntos a tareas y notas de actividades. | No hay una biblioteca independiente de materiales del curso. Solo ve cursos con matrícula activa. |
| Clases virtuales | Buscar y filtrar sus clases, abrir Zoom vigente y reconocer clases modificadas o canceladas. | El enlace lo pega el docente; no hay integración ni control de asistencia. |
| Grabaciones | Buscar partes publicadas de sus clases y abrir cada video en YouTube. | No hay reproducción ni descarga de videos en Aula; YouTube administra disponibilidad y privacidad. |
| Notificaciones | Ver avisos persistentes de clase programada, reprogramada o cancelada y marcarlos leídos. | No cubren tareas, notas ni cambios del enlace Zoom sin cambio horario. |
| Mis tareas | Ver tareas publicadas, responder, adjuntar archivo y consultar versiones de entrega. | La carga de texto y archivo ocurre en dos pasos; la reentrega con archivo después de una nota tiene una restricción contradictoria. |
| Mis notas | Ver notas de actividades ya registradas. | La lista no muestra el comentario; este aparece en el curso. No son notas oficiales del ERP. |
| Mis pagos | Ver obligaciones, vencimientos, saldo e historial informado por ERP. | Solo consulta. El pago en línea está expresamente deshabilitado. |
| Mi cuenta | Ver usuario y perfil; cerrar sesión. | No cambia datos personales ni contraseña desde esta pantalla; el restablecimiento se hace en Identidad con código. |

### Docente

| Módulo visible | Qué puede hacer hoy | Alcance que conviene conocer |
|---|---|---|
| Inicio | Ver tarjetas de sus cursos asignados. | No hay bandeja de pendientes o avisos. |
| Mis cursos | Entrar al curso, crear borrador de tarea, adjuntar material, publicar, programar clase y ver notas. | Solo opera cursos asignados desde ERP; no crea cursos oficiales. |
| Clases virtuales | Filtrar, programar desde asignaciones ERP, editar tema/horario/Zoom, cancelar con confirmación y continuar con otro enlace en la misma clase. | No hay control de asistencia ni integración Zoom API. |
| Grabaciones | Seleccionar clase, registrar partes con URL YouTube, editar y publicar u ocultar referencias. | El MP4 se sube a YouTube fuera del Aula; no se valida allí su disponibilidad. |
| Tareas y entregas | Crear y editar borradores, publicar/cerrar tareas, revisar respuestas y versiones, descargar adjuntos y calificar la última entrega. | La lista muestra identificadores del ERP en lugar de nombres de estudiantes. No hay edición de una tarea ya publicada. |
| Calificaciones | Ver notas ya guardadas y entrar a la tarea para corregirlas. | No aparecen entregas aún sin calificar; el historial de correcciones se guarda, pero no tiene vista propia. |
| Mi cuenta | Ver usuario y perfil; cerrar sesión. | La cuenta docente se vincula manualmente con su identificador ERP al crearla. |

### Coordinación académica

| Módulo visible | Qué puede hacer hoy | Alcance que conviene conocer |
|---|---|---|
| Inicio | Ver cursos de las secciones asignadas a su cuenta. | Sin ámbito asignado, no ve cursos. |
| Cursos y secciones | Entrar a cursos, revisar tareas, clases y notas dentro de su ámbito. | Es supervisión; no crea secciones ni modifica cursos oficiales. |
| Docentes | Ver identificadores ERP, cantidad de cursos y asignaciones de su ámbito. | No muestra nombres ni ficha docente. |
| Seguimiento y reportes | Ver recuentos por curso de estudiantes, docentes, tareas, entregas y calificaciones. | No hay tendencias, alertas, exportación ni desglose por estudiante. |
| Tareas y evaluaciones | Ver tareas de sus cursos y abrir su detalle. | No califica ni modifica tareas; no recibe una bandeja de pendientes. |
| Clases virtuales | Buscar clases de sus secciones y revisar detalle e historial. | Supervisión sin edición de enlaces ni cancelación. |
| Grabaciones | Buscar referencias de sus secciones, incluidas partes ocultas, y revisar sus enlaces. | Supervisión sin registro, edición ni publicación. |
| Mi cuenta | Ver usuario y perfil; cerrar sesión. | El administrador asigna o retira las secciones de su ámbito. |

### Administrador — menú normal

| Módulo visible | Qué puede hacer hoy | Alcance que conviene conocer |
|---|---|---|
| Inicio | Ver la entrada al panel de administración. | No muestra indicadores operativos en esta vista. |
| Usuarios y accesos | Crear cuentas, suspender/activar, emitir códigos de recuperación y asignar secciones a coordinación. | El vínculo ERP se introduce por identificador; no hay alta integral persona–matrícula–cuenta. El permiso financiero se asigna fuera de esta pantalla. |
| Módulos del aula | Activar o desactivar el conjunto Clases virtuales + Grabaciones. | Es un solo interruptor para ambos; no afecta cursos, tareas, notas ni pagos. |
| Control de clases virtuales | Ver inventario de clases, curso, fecha y número de bloques. | No abre ni modifica la clase desde esta lista. |
| Grabaciones | Ver referencias YouTube, editar URL y publicar u ocultar partes. | Ocultar en Aula no elimina videos de YouTube; MP4 históricos quedan para inventario Developer. |
| Mi cuenta | Ver usuario y perfil; cerrar sesión. | Ser administrador no concede automáticamente permiso Developer ni tesorería. |

**Modo Developer:** solo aparece para un administrador con permiso `developer.acceder` que active el interruptor superior. Añade **Almacenamiento** (vista previa y eliminación manual por periodo), **Integraciones** (salud de ERP, Identidad y S3; reintento de sincronización), **Seguridad y auditoría** (últimas 50 operaciones del Aula) y **Estado del sistema** (salud, cursos, grabaciones y secuencia ERP). El interruptor cambia la visibilidad del menú; la autorización real la comprueba la API. El administrador con permiso financiero puede operar la tesorería por API, pero no tiene un módulo visible de tesorería en el Aula.

**Condición común:** al desactivar el módulo Clases virtuales + Grabaciones desaparecen esas entradas académicas y se bloquean sus operaciones. Los avisos de clase se ocultan del menú. Las referencias YouTube y MP4 históricos permanecen registrados.

## 3. Los diez casos acordados: cobertura y brechas

| Caso | Resultado disponible hoy | Cobertura de negocio y punto abierto |
|---|---|---|
| **CU-01 Acceso institucional** | Acceso, cierre, suspensión y restablecimiento probados. | **Operativo.** Falta definir cómo se verifica al titular y se le entrega el código de recuperación fuera de la prueba local. |
| **CU-02 Asignación desde ERP** | ERP sincroniza cursos, matrículas y docentes al Aula. | **Parcial como proceso de alta.** Crear estudiante/docente en ERP no crea ni vincula automáticamente su cuenta de Identidad; administración debe hacerlo por separado. |
| **CU-03 Cambio de sección o retiro** | La matrícula activa determina acceso; los cambios se sincronizan. | **Operativo con regla pendiente.** El historial queda guardado, pero el usuario retirado pierde acceso incluso a su trabajo anterior. Hay que definir consulta histórica y conservación. |
| **CU-04 Crear y publicar tareas** | Borrador, adjunto, edición previa, publicación y cierre. | **Operativo con regla pendiente.** No hay corrección, prórroga, retiro o cancelación de una tarea ya publicada. |
| **CU-05 Entrega y reentrega** | Texto, adjunto y versiones de entrega. | **Brecha confirmada.** Si ya existe una nota, el Aula acepta otra respuesta cuando se permite reentrega, pero bloquea adjuntar archivo a esa nueva versión. Además, texto y archivo se guardan en dos pasos. |
| **CU-06 Calificación y corrección** | Nota, comentario, corrección e historial guardado. | **Parcial en pantalla.** «Calificaciones» solo lista notas existentes; no muestra entregas pendientes. El historial de correcciones no es consultable desde la interfaz. Falta decidir cuándo se publica una nota. |
| **CU-07 Clases Zoom** | Crear, filtrar, editar, cancelar y continuar una clase con historial y avisos internos. | **Operativo con reglas pendientes.** Definir antelación permitida para reprogramar/cancelar y quién debe recibir avisos adicionales. |
| **CU-08 Grabación en partes** | Referencias YouTube por clase, edición, publicación, ocultamiento y acceso por matrícula. | **Operativo con regla pendiente.** Definir quién verifica disponibilidad, privacidad y vigencia del video en YouTube. |
| **CU-09 Módulo y eliminación** | Interruptor, inventario, vista previa, confirmación y borrado auditado. | **Brecha confirmada frente al acuerdo inicial.** Las grabaciones del periodo inicial deben permanecer disponibles hasta el 31/12/2026 y eliminarse manualmente después; la API hoy permite borrarlas antes. Falta impedir ese borrado anticipado y definir la regla de otros periodos. |
| **CU-10 Consultar pagos** | Estudiante ve deuda y pagos vigentes; ERP registra pagos parciales, completos y anulaciones. | **Lectura en pantalla; operación solo API.** No hay módulo administrativo de caja, comprobantes ni conciliación. El cobro en línea permanece fuera de V1. |

## 4. Casos adicionales propuestos para decidir

Estos **no están aprobados ni implementados como flujos completos**. Los códigos `P` sirven para debatirlos sin modificar los diez CU existentes.

| Propuesta | Actor y resultado deseado | Decisión de negocio necesaria |
|---|---|---|
| **P-01 Alta institucional integral** | Administración crea persona, matrícula, vínculo familiar y cuenta académica/docente en un flujo coherente. | ¿Cuándo se habilita la cuenta? ¿Quién verifica el vínculo y qué ocurre con un apoderado de varios hijos? |
| **P-02 Materiales independientes** | Docente publica archivos o enlaces de consulta del curso sin crear una tarea. | ¿Quién puede editar/retirar material y por cuánto tiempo queda visible? |
| **P-03 Cambios a tarea publicada** | Docente corrige instrucciones, amplía plazo o cancela una actividad. | ¿Qué cambios se permiten después de recibir entregas y cómo se informa al alumnado? |
| **P-04 Entrega completa y recuperable** | Estudiante envía respuesta y archivos como una entrega claramente «completa»; puede reintentar cargas fallidas. | ¿Se acepta entrega solo con archivo? ¿Cuál es la hora efectiva si la carga termina después del plazo? |
| **P-05 Bandeja de revisión y publicación de notas** | Docente ve entregas pendientes, califica y publica; estudiante ve nota y retroalimentación. | ¿La nota se publica al guardarse o tras una acción/validación? ¿Puede coordinación aprobarla? |
| **P-06 Solicitud de revisión de nota** | Estudiante solicita revisión y docente responde con historial visible. | ¿Plazo de reclamo, responsables y posibilidad de segunda revisión? |
| **P-07 Historial tras traslado o retiro** | Alumno y colegio consultan tareas, entregas y notas anteriores con acceso controlado. | ¿Quién conserva acceso, durante cuánto tiempo y qué se exporta al expediente oficial? |
| **P-08 Política de cambios de clase** | Docente reprograma o cancela, y el alumno recibe aviso; se define la operación escolar alrededor del cambio. | ¿Con cuánta antelación se permite, quién puede intervenir y qué ocurre con asistencia o evaluación? |
| **P-09 Ciclo de vida de grabaciones** | El colegio publica, oculta, reemplaza y retira videos según periodo y excepciones. | ¿Quién verifica el video en YouTube y cuándo se retira? ¿Qué plazo rige para otros periodos y qué hacer con MP4 históricos? |
| **P-10 Caja administrativa** | Operador autorizado registra pagos, ajustes y anulaciones desde una pantalla; estudiante ve el resultado. | ¿Quién aprueba, qué comprobante se entrega y qué separación de funciones exige Boston? |
| **P-11 Cierre de periodo académico** | Coordinación cierra cursos, congela actividades y conserva historial para el año siguiente. | ¿Qué queda editable, quién accede después y qué datos pasan al ERP oficial? |
| **P-12 Avisos académicos adicionales** | Estudiante/docente recibe avisos por nueva tarea, nota, cambio de enlace o plazo, además de los tres eventos de clase ya implementados. | ¿En el Aula, correo o ambos? ¿Qué eventos adicionales son obligatorios y quién puede desactivar avisos? |
| **P-13 Supervisión con acciones** | Coordinación identifica entregas sin revisar, cursos sin actividad y situaciones que requieren intervención. | ¿Qué indicadores importan y qué intervención puede hacer sin alterar notas? |
| **P-14 Revisión administrativa de contenido** | Administración investiga incidencias de clases, archivos o videos. | ¿Puede ver el contenido o solo metadatos? ¿Quién autoriza acceso a datos de menores y cómo se audita? |

## 5. Decisiones que conviene resolver primero

1. **Reentrega después de calificar:** confirmar que la nueva versión pueda incluir archivos y definir si la nota anterior sigue visible hasta la revisión.
2. **Conservación de videos:** aplicar el bloqueo de borrado anticipado del periodo inicial ya acordado; definir plazos de otros periodos y posibles excepciones.
3. **Publicación de notas:** decidir si guardar equivale a publicar, quién puede corregir y cómo se presenta el historial.
4. **Acceso histórico:** definir derechos de estudiante, apoderado, docente y coordinación después de un traslado o retiro.
5. **Alta de cuentas:** definir quién verifica persona, matrícula y parentesco antes de habilitar credenciales.
6. **Tesorería operativa:** decidir si el registro de pagos seguirá solo en API/ERP o necesita pantalla de caja en Aula/ERP.

Con estas seis respuestas puedo convertir las propuestas aprobadas en casos de uso completos (actor, precondiciones, flujo, excepciones, permisos y criterios de aceptación) y luego implementar las mejoras técnicas correspondientes.
