# Roles y permisos

| Perfil | Lectura | Escritura |
|---|---|---|
| Estudiante/apoderado | Sus cursos, tareas, entregas, notas, clases, grabaciones y pagos | Entrega en sus cursos |
| Docente | Sus cursos, clases, tareas y entregas | Tareas, calificaciones, enlaces y grabaciones de sus cursos |
| Coordinación | Cursos y docentes de sus secciones asignadas | Sin modificación de notas por defecto |
| Administrador | Cuentas, módulos y supervisión | Crear/suspender cuentas, asignar ámbitos y activar módulos |
| Administrador con Developer | Integraciones y almacenamiento | Reintentar sincronización y eliminación por periodo |
| Administrador con tesorería explícita | Obligaciones y pagos ERP | Crear conceptos/obligaciones, registrar y anular pagos |

El backend aplica la autorización por curso, sección y referencia ERP. El interruptor Developer solo modifica el menú; la API verifica el claim `developer`. El ERP verifica administración institucional para cambios académicos y exige además `tesoreria=true` para acceso financiero. Un administrador recién creado carece de ese permiso.

