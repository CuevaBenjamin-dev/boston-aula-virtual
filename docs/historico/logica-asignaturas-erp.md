# Lógica actual de asignaturas — Boston ERP local

Este documento describe el comportamiento implementado, para revisar las reglas institucionales antes de redefinirlas. No presupone que la malla de prueba sea la malla oficial aprobada.

## Entidades y relación entre ellas

1. **Catálogo (`asignaturas`)**. Cada asignatura tiene UUID permanente, código único, nombre y estado activo/inactivo. El código se normaliza a mayúsculas y admite de 3 a 40 letras, números o guiones. El nombre admite hasta 120 caracteres y puede repetirse. La base local contiene 109 asignaturas: códigos distintos por grado, por ejemplo `SEC-02-MAT` y `SEC-03-MAT`. Esta separación por código es una convención de la malla inicial; la API aún permite vincular una misma asignatura de catálogo a varios grados.
2. **Malla por grado (`asignaturas_grado`)**. Vincula una asignatura del catálogo con un grado. `periodo_id` vacío significa *malla base*, aplicable a cualquier periodo. Si contiene un periodo, la relación solo aplica a ese periodo. Ambas clases de relación se suman; una relación específica no reemplaza automáticamente la base. Cada vínculo tiene estado activo/inactivo y un número `orden` que solo ordena la consulta de asignaturas del grado (`ORDER BY orden, nombre`). No representa horario, número de horas, peso de evaluación ni prioridad curricular. Actualmente no se exige que ese número sea consecutivo o único.
3. **Curso ofrecido (`ofertas_academicas`)**. Representa una asignatura para una sección concreta, grado y periodo. Todas las secciones habilitadas de un grado reciben los mismos cursos de la malla activa cuando se crea la oferta para el grado o se habilita una sección. Se guarda una oferta por sección y asignatura; inactivar la asignatura o retirar la relación puede dejar la oferta histórica con estado inactivo.
4. **Docentes (`asignaciones_docentes`)**. Dirección asigna docentes a las ofertas por sección. Un docente puede tener varios cursos, grados y secciones; un curso admite varios docentes.
5. **Estudiantes (`matriculas`)**. Solo una matrícula confirmada, vigente y con sección aporta al estudiante a la proyección de los cursos de esa sección en Aula Virtual. Confirmar sin sección conserva la matrícula, pero la inscripción en cursos espera la asignación de sección.

## Casos de uso primordiales

- **Definir la malla**: crear una asignatura con código único, vincularla al grado como base o para un periodo, y decidir su posición de lectura. El catálogo por sí solo no crea cursos.
- **Ofrecer cursos**: habilitar periodo, grado y secciones; crear la oferta para un grado. La misma asignatura se aplica a las secciones A–D habilitadas. La oferta se sincroniza con Aula.
- **Asignar docentes**: registrar la ficha docente, crear/vincular su cuenta de Identidad por separado y asignarlo a las ofertas deseadas.
- **Inscribir estudiantes**: registrar y confirmar la matrícula; al asignarle sección se publican sus cursos al Aula.
- **Mantener la malla**: corregir código o nombre, reordenar la lista, activar/desactivar asignaturas, retirar/reactivar relaciones y conservar las ofertas históricas.

## Casos específicos y reglas vigentes

- El catálogo se consulta por código o nombre, se filtra por estado y se pagina de 15 en 15. Aparece cerrado al entrar en **Asignaturas** y se abre bajo demanda.
- Dirección puede crear, editar código/nombre y activar/desactivar. Una asignatura sin relaciones ni ofertas puede eliminarse definitivamente; si fue vinculada, debe desactivarse para no romper la malla ni el historial. Coordinación solo consulta esta administración.
- Dos asignaturas pueden llamarse igual y tener distinto código. Un mismo código no puede repetirse. La identidad técnica de una asignatura es su UUID, de modo que renombrarla conserva sus vínculos.
- La relación de malla base sirve a todos los periodos; la relación específica sirve solo a uno. Si ambas incluyen la misma asignatura para un grado y periodo, la oferta se deduplica por sección y asignatura.
- `orden` solo afecta la lista del grado en ERP. El catálogo se ordena por código, la lista de ofertas por periodo/nivel/grado/código y el Aula no recibe `orden` en la sincronización. La interfaz muestra la posición de lectura y permite cambiar el valor numérico, pero no administra una secuencia única de posiciones.
- Crear una relación activa o reactivar una asignatura sincroniza las ofertas que correspondan a secciones habilitadas. Desactivar la asignatura afecta todas sus relaciones y ofertas, no solo un grado. Retirar una relación afecta el grado y alcance de periodo de esa relación, salvo que otra relación activa siga cubriendo la misma oferta.
- La malla inicial tiene 11 grados (primero a sexto de Primaria y primero a quinto de Secundaria), cuatro secciones A–D por grado para 2026, 109 asignaturas y 436 ofertas por sección. Son datos base editables de la instalación local.

## Decisiones institucionales aún por definir

- Si un código de asignatura debe pertenecer obligatoriamente a un solo grado, o si se permitirá compartir una asignatura entre grados.
- Si una relación específica de periodo debe **reemplazar** la malla base o sumarse a ella, como ocurre hoy.
- Si la posición de lectura debe editarse mediante subir/bajar y mantenerse consecutiva, o si no hace falta orden manual.
- Si desactivar un grado, periodo o sección debe desactivar automáticamente las ofertas correspondientes. La sincronización actual se centra en el estado de la oferta y la asignatura; la política integral de cierre académico requiere definición.
- Cuándo puede modificarse código/nombre si ya existen cursos, tareas, notas o historial en Aula; hoy se permite la edición y se publica la actualización de oferta.
