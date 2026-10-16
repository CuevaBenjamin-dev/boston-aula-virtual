# Revisión E³ — CP-01 a CP-04

Fecha: 16 de octubre de 2026. Alcance: instalación local, API ERP provisional, Boston Identidad, API Aula y frontend. `aprobado` indica evidencia concreta descrita en la fila; `pendiente` no presupone fallo. No se eliminaron datos académicos históricos ni volúmenes.

## CP-01 — Acceso e identidad institucional

**Objetivo esencial (E¹).** Autenticar por OIDC a los cuatro perfiles y restringir las funciones por cuenta. La cuenta académica estudiante/apoderado sigue siendo una sola. **Resultado:** flujo principal operativo; faltan pruebas integrales de suspensión y recuperación.

**E² — Ficha de escenarios**

| Código | Estado | Esperado | Observado y evidencia | Corrección |
|---|---|---|---|---|
| CP-01.01 | aprobado | Login OIDC, sesión y comienzo según perfil | Navegador local: `admin.demo`, `coordinacion.demo`, `docente.demo` y `estudiante.demo` accedieron por Boston Identidad y recibieron menús distintos. La redirección terminó en `/`. | Ninguna. |
| CP-01.02 | aprobado | Credencial errónea rechazada con aviso genérico | Un intento incorrecto de `estudiante.demo` permaneció en `/login?error` y mostró un único mensaje que no distingue contraseña, usuario o bloqueo. | Ninguna. |
| CP-01.03 | pendiente | Suspensión impide login y revoca sesiones y API privadas | `EstadoCuentaFiltroTest` valida rechazo de token suspendido en Identidad; API Aula compara `sesion_version` con Identidad en cada solicitud. Falta suspender una cuenta con sesión activa en la prueba integral. | Se añadió comprobación de estado y versión a los endpoints privados de Identidad. |
| CP-01.04 | pendiente | Una sesión expirada exige autenticación | Tras reiniciar solo API Aula, `/cursos` mostró acceso institucional; `GET /api/sesion` sin sesión devolvió 401. Falta simular la caducidad real de una sesión. | El cliente vuelve al inicio ante 401 de la API Aula. |
| CP-01.05 | pendiente | Código temporal único, caducidad y revocación | Código: hash SHA-256, 30 minutos, bloqueo `FOR UPDATE`, `usado_en`, incremento de `sesion_version` y borrado de autorizaciones. `CuentasControllerTest` verificó revocación; falta ejecutar recuperación real dos veces en local. El código se entrega solo desde administración local: **no hay canal productivo de entrega**. | Ninguna adicional. |
| CP-01.06 | aprobado | Una cuenta académica compartida | `estudiante.demo` mostró cursos y pagos de su estudiante vinculado con la misma sesión; Identidad no modela un segundo login de apoderado. | Ninguna. |
| CP-01.07 | pendiente | Sesiones simultáneas y cierre de la actual | El botón `Cerrar sesión` devolvió al acceso institucional. La versión de sesión no cambia por login, así que no existe un límite por dispositivo. Falta prueba con dos navegadores simultáneos. | Documentar revocación de todas las sesiones: suspender/reactivar cuenta, cambiar contraseña o restablecerla incrementa `sesion_version`. |

**E³ — Experiencia.** Login y error comprensibles; cierre de sesión y retorno al formulario comprobados. El formulario de recuperación está disponible, aunque el canal real de entrega sigue pendiente. La navegación y el menú por rol se revisaron en escritorio; la validación manual responsive queda pendiente.

## CP-02 — Asignación de cursos y docentes

**Objetivo esencial (E¹).** El ERP determina matrículas, ofertas y docentes; Aula consulta la proyección local por matrícula/asignación activa. **Resultado:** proyección y accesos actuales funcionan; nuevas altas y varias secciones requieren prueba funcional con datos de prueba adicionales.

| Código | Estado | Esperado | Observado y evidencia | Corrección |
|---|---|---|---|---|
| CP-02.01 | pendiente | Matrícula ERP y cuenta habilitada muestran cursos | `estudiante.demo` vio Matemática de Primero A y la proyección tenía el curso. No se creó matrícula nueva durante esta revisión. | La matrícula repetida de la misma sección devuelve el ID previo sin nuevo evento. |
| CP-02.02 | pendiente | Docente solo ve asignaciones oficiales | `docente.demo` vio solo Matemática A; URL directa del curso B devolvió `Curso ajeno`. `UsuarioActualTest` rechaza un curso sin asignación. Falta comprobar una reasignación real. | No hay asignación manual desde Aula. |
| CP-02.03 | pendiente | Varias secciones sin mezcla | Se emplea `curso_id` exacto en acceso, tareas y estudiantes. Falta prueba de un mismo docente con dos secciones activas. | Ninguna. |
| CP-02.04 | aprobado | No duplicar matrícula vigente | `InstitucionalTest` comprobó la repetición de sección y el rechazo de otra sección en el mismo periodo; `ux_matricula_activa` y claves únicas refuerzan la regla. | Se separó alta de matrícula de cambio de sección. |
| CP-02.05 | aprobado | ERP caído: conservar proyección, registrar fallo y reintentar | Se detuvo `api-erp`: Mis cursos siguió mostrando Matemática con aviso y hora de última sincronización; Mis pagos mostró indisponibilidad; `estado_sincronizacion.ultimo_error` pasó a valor no nulo. Tras `docker compose start api-erp`, el error y aviso se limpiaron. | Se añadió estado de sincronización para avisar cuando los cursos son de la última copia local. |
| CP-02.06 | aprobado | Repeticiones y orden sin duplicados | `SincronizacionTest` rechazó secuencias repetidas/anteriores; reconciliación local creó secuencia 13 y el reintento dejó un único `eventos_procesados` para 13, con dos cursos. Tras serializar la publicación, otra reconciliación avanzó a 16 sin error. ERP incrementa `version` por entidad. | Bloqueo transaccional del cursor y control de secuencia antes de aplicar. |

**E³ — Experiencia.** Tarjetas indican asignatura, grado, sección y periodo. El resumen del curso muestra el nombre completo del docente asignado; se comprobó «Luis Demo Docente» en Matemática A desde `estudiante.demo`. El nombre queda en la proyección local mediante Aula V12 y eventos ERP; se reconciliaron los cursos existentes. Se retiró el año fijo `2026` del encabezado. Si ERP falla, la lista muestra cuándo se obtuvo la última copia y que habrá reintento. Búsqueda/filtros para catálogos académicos de docentes quedan pendientes de necesidad concreta.

## CP-03 — Cambios académicos durante el periodo

**Objetivo esencial (E¹).** Reflejar cambios oficiales preservando trabajos, notas e historial. **Resultado:** reglas y sincronizador corregidos, pero los cambios de estudiante/docente requieren una ronda de integración con datos aislados.

| Código | Estado | Esperado | Observado y evidencia | Corrección |
|---|---|---|---|---|
| CP-03.01 | pendiente | Cambio de sección retira acceso anterior y conserva historia | El servicio ERP publica instantáneas de sección anterior y nueva; Aula desactiva inscripciones previas sin borrar filas de trabajos. No se ejecutó cambio real en los datos locales actuales. | `cambiarSeccion` exige matrícula vigente del mismo periodo; otro periodo usa matrícula nueva. |
| CP-03.02 | pendiente | Retiro revoca operaciones sin borrar trabajos | `retirar` marca matrícula `RETIRADA`, cambia estado y publica oferta sin estudiante. `estadoAcademico(RETIRADO)` sigue la misma ruta. Falta prueba integral de sesión y enlaces antiguos. | Retiro repetido es idempotente. |
| CP-03.03 | pendiente | Cambio de docente revoca al anterior y habilita al nuevo | ERP desactiva asignación antigua y emite la nueva; Aula conserva `tareas`/`entregas` por `curso_id`. Falta cambio real con dos docentes de prueba. | Asignación repetida al mismo docente no emite otro evento. |
| CP-03.04 | pendiente | Nuevo periodo no sobrescribe el anterior | El código marca la matrícula anterior `FINALIZADA`, crea/activa la nueva y conserva la anterior en BD; migración V3 acepta ese estado. No se creó un segundo periodo en la prueba local. | Se diferenció fin de periodo de retiro. |
| CP-03.05 | pendiente | Entregas y notas permanecen, sin trasladar calificaciones | La sincronización modifica relaciones activas y no ejecuta `DELETE` de tareas, entregas ni notas. Falta una entrega seguida por traslado real para aprobar el escenario. | Ningún traslado automático de notas. |
| CP-03.06 | aprobado | Error registrado, reintento y reconciliación | La caída real del ERP registró error y recuperación; en Developer, `Reconciliar con ERP` volvió a publicar dos ofertas y la instantánea de ámbitos. Cursor 16 después de repetir el proceso, sin error; ámbito confirmado y dos cursos tras la primera reconciliación. | Se añadió reconciliación ERP/Aula y controles de orden/idempotencia. |

**E³ — Experiencia.** La lista de cursos vigentes no muestra cursos anteriores. Los enlaces a un curso que deje de corresponder son rechazados por backend. El mensaje de sincronización informa copia local; mensajes específicos de cambio de sección/retiro necesitan decisión sobre comunicación institucional.

## CP-04 — Autorización por perfil y recurso

**Objetivo esencial (E¹).** Aplicar permisos en API por actor y recurso. **Resultado:** accesos ajenos probados y ámbitos de coordinación trasladados al ERP; queda ampliar la matriz de mutaciones/IDs en la revisión manual.

| Código | Estado | Esperado | Observado y evidencia | Corrección |
|---|---|---|---|---|
| CP-04.01 | aprobado | Estudiante no consulta curso ajeno | `estudiante.demo` vio solo Primero A. URL directa `/api/aula/cursos/4df60eb8-d0e6-44a7-a427-6bfd85821c67/tareas` devolvió `Curso ajeno`. | Ninguna. |
| CP-04.02 | pendiente | Docente no modifica tarea ajena | `UsuarioActualTest` rechaza curso sin asignación; `CursosYTareas.editar` exige `usuario.docente`. Falta PUT real con tarea ajena y comparación antes/después. | Ninguna. |
| CP-04.03 | aprobado | Coordinación solo en ámbito oficial | El ámbito previo de `coordinacion.demo` para A se registró en API ERP y se proyectó confirmado en Aula. La interfaz mostró Matemática A; URL directa al curso B devolvió `Curso fuera del ámbito de coordinación`. | Tabla y API ERP para ámbitos; Aula solo autoriza ámbitos `confirmado_erp`. |
| CP-04.04 | aprobado | Administrador no califica por defecto | `UsuarioActualTest.administradorNoRecibePermisosParaCalificar` rechazó `docente`; `CursosYTareas.calificar` llama esa comprobación. | Ninguna. |
| CP-04.05 | aprobado | Developer visible y protegido solo para autorizado | Menú Developer apareció con `admin.demo` tras activar interruptor; `estudiante.demo` recibió `Solo administrador` en `/api/aula/integraciones` por URL directa. `UsuarioActualTest` cubre administrador sin permiso. | Ninguna. |
| CP-04.06 | aprobado | Pagos solo del estudiante vinculado, sin otra clave | `estudiante.demo` vio sus obligaciones y saldo con su sesión académica; `TesoreriaTest` rechazó UUID ajeno en API ERP. | Ninguna. |
| CP-04.07 | pendiente | IDs manipulados no revelan ni alteran recursos | Se probaron cursos ajenos para estudiante y coordinación. Faltan IDs ajenos de tarea, entrega, nota y archivo en la matriz manual. | Los servicios revisados comprueban relación con curso/actor en backend. |

**E³ — Experiencia.** Menús por rol revisados en navegador. La respuesta por ID ajeno dice `Curso ajeno` o `Curso fuera del ámbito de coordinación` y no incluye datos del recurso. La navegación al perder permisos en una página ya abierta requiere prueba manual.

## Revisión transversal y brechas

- **Seguridad:** antes, tokens de usuario aceptados por la propia API de Identidad podían seguir válidos hasta su caducidad tras suspensión; ahora se comprueban estado y versión. La API Aula ya hacía comprobación remota. Se mantiene verificación de identidad aun con ERP caído.
- **Integridad:** antes `matricular` reemplazaba automáticamente cualquier matrícula activa y etiquetaba el año anterior como retiro; ahora separa cambio de sección y finalización de periodo. Las instantáneas académicas no borran trabajos ni notas.
- **Orden y recuperación:** antes un evento viejo podía retroceder la proyección y todas las versiones ERP valían 1; ahora hay cursor bloqueado, secuencia monotónica, publicación serializada entre transacciones, versión por entidad, reintento y reconciliación.
- **Ámbitos:** antes un administrador escribía la autorización de coordinación directamente en Aula; ahora el ERP guarda y publica el ámbito. Las filas locales anteriores permanecen en BD sin autorizar hasta confirmación ERP. El único ámbito local previo (coordinación demo, sección A) se registró en ERP y quedó confirmado mediante el flujo administrativo.
- **Disponibilidad:** caída del ERP probada. Los cursos siguieron disponibles; tesorería no mostró datos viejos. El aviso de cursos usa `sincronizadoEn` y `pendiente` sin exponer detalles internos del error.
- **Rendimiento:** estado de sincronización se consulta solo en Mis cursos, con refresco de 30 segundos; no se agregaron consultas ERP a cada curso. La comprobación de cuenta en API Aula existente consulta Identidad por solicitud privada y falla cerrada si esta no responde.

## Decisiones institucionales pendientes

1. Visibilidad de cursos, entregas y notas después de retiro, cambio de sección y periodos anteriores. Por ahora permanecen en BD y no se habilitan automáticamente en el aula.
2. Si un estudiante puede tener matrículas activas en periodos solapados. La regla provisional conserva **una matrícula activa por estudiante**; el periodo previo pasa a `FINALIZADA` al crear el siguiente.
3. Quién comunica cambios académicos y qué mensajes debe mostrar cada perfil. No se inventó un flujo de notificaciones adicional.
4. Canal productivo de entrega del código de recuperación. El código de prueba solo se muestra a administración local una vez.

## Evidencia técnica y estado local

- Pruebas focalizadas: `mvn -q test` en Identidad (4 pruebas), `InstitucionalTest,TesoreriaTest` en ERP (7 pruebas), `SincronizacionTest,UsuarioActualTest,CursosYTareasTest` en Aula (10 pruebas), sin fallos en su ejecución final. Compilación frontend con `npm run build` dentro de Docker.
- Docker: Flyway ERP V3 y Aula V11/V12 aplicadas; Identidad, API ERP, API Aula, frontend, proxy y PostgreSQL sanos tras reconstruir solo servicios modificados. Se detuvo y reactivó API ERP para la prueba de disponibilidad, sin tocar volúmenes.
- Archivos clave: `Institucional.java`, `Sincronizacion.java`, ambos filtros de estado de cuenta, `UsuarioActual.java`, `frontend/src/api.ts`, `AcademicoTareas.tsx`, `Operacion.tsx` y las migraciones V3/V11.

Esta ficha registra lo observado hoy. Los escenarios pendientes necesitan datos académicos aislados y revisión manual posterior antes de pasar a `aprobado`.
