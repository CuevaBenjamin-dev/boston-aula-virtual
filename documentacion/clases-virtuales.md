# Clases virtuales

El docente programa clases únicamente en cursos de sus asignaciones ERP vigentes. El formulario selecciona asignatura → grado → sección; horario, tema y enlace oficial de Zoom son obligatorios. Puede editar tema, horario y enlace, o cancelar con confirmación. La cancelación conserva la clase y su historial. Si continúa una reunión con otro enlace, el sistema crea otro `bloques_zoom` en la misma clase académica y usa el último como vigente. No se usa API ni OAuth de Zoom.

El listado permite buscar por tema y filtrar por asignatura, grado, sección, fecha y estado. La URL conserva filtros y página cuando se abre una clase y se regresa. El estudiante ve sus clases según matrículas activas, puede abrir el Zoom vigente y distingue clases modificadas o canceladas. Coordinación consulta las clases de sus secciones autorizadas.

La programación, cancelación y cambio de fecha u horario crean avisos internos persistentes para estudiantes con inscripción activa en el curso. Cada estudiante consulta sus avisos, ve el número pendiente y puede marcarlos como leídos. Los cambios de tema o enlace sin cambio de horario no generan aviso de reprogramación. La consulta de avisos vuelve a comprobar la matrícula activa.

El interruptor `clases_y_grabaciones` bloquea las operaciones de clases y grabaciones y oculta sus entradas de menú sin borrar registros. La API también comprueba asignación docente, matrícula o ámbito de coordinación en cada operación.
