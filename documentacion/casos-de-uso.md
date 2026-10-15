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

## CU-04 · Creación y publicación de tareas

- Flujo: docente asignado crea borrador con instrucciones, fecha y escala, adjunta PDF/imagen/DOCX y edita mientras sigue en borrador; luego publica.
- Reglas: edición con versión optimista; el material se guarda privado en S3; alumnos no leen borradores. La escala máxima es configurable por tarea.
- Excepciones: versión obsoleta, 409; otro docente, 403; adjunto inválido o mayor de 100 MB, 400; tarea publicada no se edita como borrador.
- Resultado esperado: estudiantes matriculados ven tarea y material publicado.

## CU-05 · Entrega y reentrega

- Flujo: estudiante envía texto y opcionalmente archivo por carga multipart directa; si está permitido, vuelve a enviar. Cada envío crea una fila y versión nueva.
- Reglas: fecha límite y banderas de entrega tardía/reentrega; archivos privados y asociados a la versión. El estado `TARDIA` distingue envíos fuera de plazo aceptados.
- Excepciones: plazo vencido sin permiso, reentrega desactivada, tarea cerrada o matrícula ausente se rechazan; tipo/tamaño/cabecera del archivo se verifican.
- Resultado esperado: docente revisa versiones y el estudiante ve su historial.

## CU-06 · Calificación y corrección

- Flujo: docente asignado revisa última entrega y registra nota; puede corregirla desde la revisión o desde Calificaciones mediante el mismo endpoint.
- Reglas: nota dentro de la escala de la tarea; `version` optimista; cada cambio se guarda en `historial_calificaciones` y `registros_auditoria`. La nota identifica la versión de entrega calificada y señala si llegó otra pendiente de revisión. Son notas de actividades, no oficiales del ERP.
- Excepciones: otro docente, 403; versión obsoleta, 409; estudiante sin entrega o nota fuera de escala, 400.
- Resultado esperado: ambas vistas consultan la misma nota e historial persistente.

## CU-07 · Continuación de Zoom

- Flujo: docente crea clase con horario y enlace manual; tras terminar un bloque, publica otro enlace en la misma clase. Alumno actualiza la vista y abre el enlace vigente.
- Reglas: el enlace se valida y cada cambio queda en `bloques_zoom`; la interfaz consulta el curso cada 30 segundos. No se conecta a Zoom API.
- Excepciones: otro docente, 403; módulo desactivado, 409.
- Resultado esperado: una clase conserva varios bloques y muestra el último enlace.

## CU-08 · Grabación en partes

- Flujo: docente selecciona uno o varios MP4 y orden inicial; navegador solicita subida multipart, envía partes directamente a S3 local y completa. Aula verifica tamaño y cabecera MP4 antes de publicar. Estudiante autorizado reproduce por URL de cinco minutos, descarga y solicita rangos HTTP.
- Reglas: máximo 5 GB por archivo; orden único entre grabaciones activas; URL temporal emitida solo tras verificar curso. No se unen partes ni se adapta la calidad.
- Excepciones: subida fallida se reintenta por parte; MP4 falso o incompleto se elimina; estudiante sin matrícula y docente ajeno reciben 403.
- Resultado esperado: las partes aparecen en orden y los MP4 no pasan por Spring Boot.

## CU-09 · Configuración, supervisión y eliminación

- Flujo: administración desactiva clases/grabaciones; cursos, tareas, notas y pagos permanecen. Admin con permiso `developer.acceder` activa el interruptor visual, inspecciona integraciones y ve una vista previa de grabaciones por periodo. Para eliminar, escribe `ELIMINAR {periodo}`; API verifica permiso, cantidad confirmada, procesa objetos uno por uno y audita el lote.
- Reglas: el interruptor no concede permisos; la API controla cada ruta. El borrado conserva metadatos en estado `ELIMINADA` y permite reutilizar el orden. No existe borrado automático.
- Excepciones: cantidad que cambió o actor sin permiso se rechazan; el resultado del lote informa errores individuales.
- Resultado esperado: el módulo queda bloqueado para usuarios académicos, y el borrado no afecta otro periodo ni el bucket entero.

## CU-10 · Consulta de pagos

- Flujo: estudiante consulta `/api/aula/pagos`; BFF obtiene obligaciones y pagos actuales de ERP. Administración registra un abono parcial y luego el saldo restante por referencia única.
- Reglas: importe positivo, no exceder saldo, referencia única, anulaciones con motivo e historial; pagos en línea están explícitamente deshabilitados. Solo el ERP calcula deuda y estado.
- Excepciones: duplicado, 409; importe inválido, 400; ERP inaccesible, 503, sin inventar saldos ni confirmaciones.
- Resultado esperado: obligaciones, vencimientos, pagos y saldo se actualizan tras cada operación autorizada.
