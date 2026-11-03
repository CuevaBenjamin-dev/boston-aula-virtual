# CP-11 · Notificaciones y comunicaciones internas

## E¹ · Resultado esencial

Los usuarios se comunican dentro de Aula según su relación académica y consultan avisos de eventos autorizados. La bandeja conserva recibidos, enviados, contenido, remitente, fecha, lectura individual e historial. No hay correo externo, chat en tiempo real ni adjuntos.

## E² · Reglas y escenarios aplicados

| Emisor o evento | Regla |
| --- | --- |
| Estudiante | Puede escribir a otro estudiante con matrícula activa en el mismo grado, sección y periodo. No escribe a personal académico ni envía a grupos; se valida también en peticiones directas y respuestas. |
| Docente | Puede escribir a uno, varios o todos los estudiantes de un curso asignado vigente. No envía a cursos ajenos. |
| Coordinación | Envía mensajes institucionales individuales o agrupados a usuarios del Aula. No lee conversaciones ajenas. |
| Administración | Envía mensajes institucionales individuales o agrupados. El permiso de envío no concede lectura privada ajena. |
| Avisos automáticos | Se conservan tareas, entregas y reentregas, notas, clases y Zoom; se agregan publicación/corrección de grabación y cambios de asignación docente sincronizados. |
| Lectura y visibilidad | Abrir la bandeja no marca todo como leído. Se marca el aviso o mensaje individual; el historial permanece. Los avisos de recursos se filtran por acceso académico vigente. |
| Integridad | No se recrean eventos previos; las claves de evento/destinatario evitan duplicados. Continúa la agrupación de entregas de CP-05. |

## Reutilización y brechas corregidas

Se reutilizaron la bandeja, directorio de usuarios, mensajes, avisos CP-05/09, autorizaciones de curso y proyección ERP. La API ahora valida destinatarios mediante matrícula y asignación vigentes aun si se alteran IDs en la petición. Se añadió selección funcional de grupo institucional y el aviso de alta/baja de asignación docente. La lectura de un mensaje permanece limitada a sus participantes.

## Evidencia técnica

`MensajesController` resuelve el directorio y los destinatarios en servidor, `NotificacionesController` filtra recursos y permite lectura individual, y `Sincronizacion` registra los cambios de asignación ERP. `V25__avisos_asignacion_cp11.sql` crea la persistencia e índice de deduplicación para esos avisos. `pruebas/cp10-cp11-cp13-verificar-flujos.py` pasó con envío entre compañeros de sección, docente a estudiante asignado, mensajes individuales de Coordinación y grupales de Administración, lectura individual y rechazo de destinatarios o conversaciones ajenas. La bandeja cargó en navegador local. Las pruebas unitarias focalizadas de `YoutubeUrlTest` y `MensajesControllerTest` pasaron.

## Pendientes y ficha de evaluación

- La mensajería de cuentas académicas no incluye contacto con docentes, Coordinación o Administración hasta que la institución lo autorice expresamente.
- E³ — Experiencia UX/UI está pendiente; solo se incorporaron controles funcionales indispensables.

| Criterio | Estado |
| --- | --- |
| Fuente de permisos | Matrículas, asignaciones y secciones ERP sincronizadas. |
| Privacidad | Solo participantes leen mensajes; el envío institucional no amplía la consulta. |
| Consistencia | Historial de mensajes/avisos y deduplicación por evento. |
| Alcance | E¹ y E² implementados; E³ pendiente. |
