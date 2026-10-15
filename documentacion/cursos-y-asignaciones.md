# Cursos y asignaciones

ERP es la fuente de periodos, secciones, asignaturas, matrículas y docentes. Un curso de Aula corresponde al UUID de una oferta académica ERP. El docente no puede asignarse a sí mismo; Aula verifica `docentes_curso` sincronizado. El cambio de sección publica eventos de la sección anterior y nueva, desactivando una inscripción y activando otra. El retiro deja historial de tareas y entregas, pero revoca el acceso actual.

