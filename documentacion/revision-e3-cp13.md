# CP-13 · Supervisión académica

## E¹ · Resultado esencial

Coordinación consulta el desarrollo de cursos, docentes y estudiantes dentro de sus secciones autorizadas, en modo de solo lectura. Se amplió «Seguimiento y reportes» sin crear otro módulo ni asumir operaciones docentes o administrativas.

## E² · Reglas y escenarios aplicados

| Consulta | Información y límite |
| --- | --- |
| Docentes | Asignaciones vigentes, cursos, tareas publicadas y entregas pendientes de calificación. |
| Cursos y tareas | Actividades publicadas, pendientes de seguimiento y estados de clases cuando el interruptor institucional está habilitado. |
| Estudiantes | Matrículas y secciones vigentes, tareas entregadas y pendientes, entregas tardías, calificaciones publicadas y acumulación de pendientes. |
| Historial | Traslados y retiros mediante la consulta histórica existente, dentro del ámbito autorizado. |
| Filtros | Nivel, grado, sección, curso y periodo cuando hay datos. |
| Protección | No se incluyen borradores privados de notas, promedios oficiales no aprobados ni mensajes ajenos. Coordinación no puede cambiar calificaciones, entregas, matrículas o pagos. |
| Administración | Se conserva el acceso histórico institucional existente; no se agregan funciones de CP-14. |

## Reutilización y brechas corregidas

Se reutilizaron «Seguimiento y reportes», autorizaciones de secciones, proyección académica sincronizada con ERP, tareas, entregas, notas publicadas y el historial existente. Se agregaron consultas de supervisión por docente y estudiante, contadores de pendientes y clases por estado, siempre dentro del ámbito de Coordinación. El interruptor CP-09 controla la consulta de clases y grabaciones.

## Evidencia técnica

`SupervisionController` expone resumen, docentes, tareas, estudiantes y clases con restricciones por `coordinaciones_seccion` confirmadas en ERP. La pantalla `Academico` presenta esas consultas y aplica filtros funcionales. `pruebas/cp10-cp11-cp13-verificar-flujos.py` pasó con consultas restringidas al ámbito ERP, exclusión de borradores, historial de traslados y retiros, denegación a otros roles y desactivación de clases por el interruptor CP-09. No se necesitó una migración propia de CP-13. API Aula y frontend compilaron.

## Pendientes y ficha de evaluación

- La validez de matrículas y asignaciones depende de la sincronización con ERP y de la calidad de sus eventos.
- La información histórica anterior al alcance de la proyección conserva los límites del historial ya existente.
- E³ — Experiencia UX/UI está pendiente; no se evaluó el diseño de los reportes.

| Criterio | Estado |
| --- | --- |
| Fuente académica | ERP o proyección sincronizada; actividades y notas desde API Aula. |
| Permisos | Consulta de Coordinación por sección confirmada, sin escritura académica. |
| Datos | Solo información real y notas publicadas; sin indicadores predictivos. |
| Alcance | E¹ y E² implementados; E³ pendiente. |
