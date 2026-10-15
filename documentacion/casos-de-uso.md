# Casos de uso V1

Los diez casos operan en Docker local. «Cuenta académica» designa la única cuenta compartida por estudiante y apoderado. Los enlaces Zoom y pagos de prueba son ficticios.

## CU-01 · Inicio de sesión institucional

- Flujo: abrir `http://localhost:8088`, iniciar sesión en Boston Identidad mediante OIDC Authorization Code con PKCE y consultar `/api/sesion`. El BFF guarda la autorización en sesión de servidor; la UI recibe una cookie HttpOnly y CSRF. La cuenta académica ve sus cursos y pagos.
- Reglas: una cuenta suspendida pierde acceso a la API; contraseña errónea no crea sesión; cerrar sesión revoca la sesión local y termina la sesión en Identidad. El apoderado usa las mismas credenciales académicas.
- Excepciones: sesión caducada o revocada responde 401; acceso a recurso ajeno, 403. Recuperación de acceso usa código temporal, de un solo uso y con caducidad, entregado por canal local de prueba. El titular introduce el código en `/restablecer`; el cambio revoca las sesiones BFF previas.
- Resultado esperado: perfil y recursos limitados al usuario autenticado.

## CU-02 · Asignación desde ERP

- Flujo: administración registra estudiante, sección, oferta, matrícula y asignación docente en API ERP. La transacción emite un evento en el outbox; Aula lo consulta y proyecta curso, inscripciones y docentes.
- Reglas: los UUID ERP son estables; `eventos_procesados.secuencia_erp` evita efectos de eventos duplicados. Aula nunca escribe las tablas ERP.
- Excepciones: si ERP no está disponible, el error y último éxito quedan registrados y el sincronizador reintenta; los cursos previamente sincronizados siguen disponibles.
- Resultado esperado: estudiante y docente ven el curso sin asignación manual en Aula.

## CU-03 · Cambio de sección o retiro

- Flujo: ERP actualiza matrícula o estado académico y publica un evento; Aula desactiva la inscripción anterior y activa la nueva, o deja al estudiante sin curso tras el retiro.
- Reglas: conservar tareas y entregas históricas; autorizar por inscripción activa, incluso si se conoce un UUID anterior.
- Excepciones: petición directa a curso anterior devuelve 403. El reprocesamiento de un evento no duplica la inscripción.
- Resultado esperado: el acceso refleja el estado académico vigente.

## CU-04 · Creación, publicación y edición de tareas

- Flujo: docente asignado escribe título, instrucciones y fecha/hora límite, elige material opcional y pulsa «Publicar tarea». El Aula registra temporalmente `FALTA_COMPLETAR`, sube el material y publica al terminar. Puede editar después título, instrucciones, plazo y reemplazar el material.
- Reglas: edición con versión optimista; el material se guarda privado en S3. La escala de calificación se fija en 20 puntos y se conserva en la base para validar notas, sin pedirla al docente en el formulario. Las tareas incompletas no son visibles para estudiantes.
- Excepciones: si se interrumpe la carga después de enviar el formulario, el docente ve `FALTA COMPLETAR` y puede retomarla; versión obsoleta, 409; otro docente, 403; adjunto inválido o mayor de 100 MB, 400. Una tarea antigua cerrada puede editarse y reabrirse.
- Resultado esperado: al terminar el formulario, el docente ve `PUBLICADO` y el estudiante ve `PENDIENTE` o `URGENTE` según el plazo.

## CU-05 · Entrega y reentrega

- Flujo: estudiante consulta la tarea desde Mis tareas, presenta texto y opcionalmente un archivo por carga directa; el historial muestra fecha y contenido de cada entrega.
- Reglas: las tareas nuevas aceptan una entrega hasta la fecha límite y no ofrecen reentrega. Las tareas históricas conservan sus reglas originales de entrega tardía y reentrega; los archivos son privados y asociados a la versión. El estado `TARDIA` distingue envíos fuera de plazo aceptados en esas tareas históricas.
- Excepciones: plazo vencido sin permiso, reentrega desactivada, tarea cerrada o matrícula ausente se rechazan; tipo/tamaño/cabecera del archivo se verifican.
- Resultado esperado: el estudiante ve `PRESENTADO`; el docente ve nombre y fecha de cada entrega, además del recuento de estudiantes que entregaron frente al total matriculado.

## CU-06 · Calificación y corrección

- Flujo: docente asignado revisa última entrega y registra nota; puede corregirla desde la revisión o desde Calificaciones mediante el mismo endpoint.
- Reglas: nota dentro de la escala de la tarea; `version` optimista; cada cambio se guarda en `historial_calificaciones` y `registros_auditoria`. La nota identifica la versión de entrega calificada y señala si llegó otra pendiente de revisión. Son notas de actividades, no oficiales del ERP.
- Excepciones: otro docente, 403; versión obsoleta, 409; estudiante sin entrega o nota fuera de escala, 400.
- Resultado esperado: Mis notas del estudiante muestra nota, escala, comentario y fecha en su propio detalle. Calificaciones del docente lista actividades con entregas para revisar, sin mostrar una nota como si fuera del docente.

## CU-07 · Programar, modificar, cancelar y continuar clase Zoom

- Flujo: docente elige curso, grado y sección de sus asignaciones ERP, programa tema, horario y Zoom; puede editar, cancelar con confirmación o continuar con otro enlace en la misma clase. Estudiante ve el estado y enlace vigente de su matrícula.
- Reglas: solo se aceptan enlaces HTTPS de Zoom. Cada bloque se conserva; V8 registra instantáneas de la clase. Programación, cambio de horario y cancelación crean avisos internos persistentes para matriculados activos. Los filtros y página se conservan al volver del detalle. No se conecta a Zoom API.
- Excepciones: docente no asignado o estudiante no matriculado, 403; módulo desactivado, 409; clase cancelada no se edita ni recibe enlaces nuevos.
- Resultado esperado: clase y avisos persisten, el último bloque es el enlace vigente y el historial sigue disponible tras cancelar.

## CU-08 · Referencias YouTube por partes

- Flujo: el docente sube el MP4 a YouTube fuera del Aula y registra una o varias URL en una clase académica existente. Puede editar la URL y publicar u ocultar cada parte. El estudiante abre las partes publicadas en YouTube desde el listado global o el detalle.
- Reglas: orden único por clase; frontend y backend validan URL oficial y normalizan ID/enlace. La disponibilidad y privacidad del video se gestionan en YouTube. No hay reproductor, descarga ni carga MP4 de clases en el Aula.
- Excepciones: URL falsa o malformada y parte repetida se rechazan; estudiante sin matrícula y docente ajeno reciben 403.
- Resultado esperado: solo las partes publicadas aparecen al estudiante, vinculadas a clase, curso y docente; los MP4 históricos quedan separados para revisión técnica.

## CU-09 · Configuración, supervisión y eliminación

- Flujo: administración desactiva clases/grabaciones; cursos, tareas, notas y pagos permanecen. Admin gestiona publicación y enlaces YouTube. Por separado, Developer inspecciona integraciones y el inventario histórico MP4 por periodo; cualquier eliminación técnica histórica usa cantidad confirmada y queda auditada.
- Reglas: el interruptor no concede permisos; la API controla cada ruta. Ocultar una referencia en Aula no elimina el video de YouTube. El borrado técnico histórico conserva metadatos en estado `ELIMINADA`. No existe borrado automático.
- Excepciones: cantidad que cambió o actor sin permiso se rechazan; el resultado del lote informa errores individuales.
- Resultado esperado: el módulo queda bloqueado para usuarios académicos, y el borrado no afecta otro periodo ni el bucket entero.

## CU-10 · Consulta de pagos

- Flujo: estudiante consulta `/api/aula/pagos`; BFF obtiene obligaciones y pagos actuales de ERP. Administración registra un abono parcial y luego el saldo restante por referencia única.
- Reglas: importe positivo, no exceder saldo, referencia única, anulaciones con motivo e historial; pagos en línea están explícitamente deshabilitados. Solo el ERP calcula deuda y estado.
- Excepciones: duplicado, 409; importe inválido, 400; ERP inaccesible, 503, sin inventar saldos ni confirmaciones.
- Resultado esperado: obligaciones, vencimientos, pagos y saldo se actualizan tras cada operación autorizada.
