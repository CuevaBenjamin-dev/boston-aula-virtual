# Roles y permisos

| Perfil | Lectura | Escritura |
|---|---|---|
| Estudiante/apoderado | Sus cursos, tareas, entregas, notas, clases, grabaciones, pagos, mensajes y avisos | Entrega en sus cursos; mensajes y cuenta personal |
| Docente | Sus cursos, clases, tareas, entregas, mensajes y avisos | Tareas, calificaciones, enlaces, grabaciones, mensajes a cuentas o a su aula y cuenta personal |
| Coordinación | Cursos y docentes de sus secciones, mensajes y avisos | Mensajes y cuenta personal; sin modificación de notas por defecto |
| Administrador | Cuentas, módulos, supervisión, mensajes y avisos | Crear/suspender cuentas, asignar ámbitos, activar módulos, mensajes y cuenta personal |
| Administrador con Developer | Integraciones y almacenamiento | Reintentar sincronización y eliminación por periodo |
| Administrador con tesorería explícita | Obligaciones y pagos ERP | Crear conceptos/obligaciones, registrar y anular pagos |

El backend aplica la autorización por curso, sección y referencia ERP. El interruptor Developer solo modifica el menú; la API verifica el claim `developer`. El ERP verifica administración institucional para cambios académicos y exige además `tesoreria=true` para acceso financiero. Un administrador recién creado carece de ese permiso.

La bandeja y Mi cuenta están disponibles para los cuatro perfiles. Todos pueden enviar mensajes a cuentas activas, y el envío a todo un curso exige que el emisor sea docente asignado a ese curso. La lectura de cada mensaje queda limitada a su emisor y destinatarios.

