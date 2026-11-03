# CP-09 · Gestionar clases virtuales

## E¹ · Resultado esencial

El docente asignado oficialmente puede programar, modificar, cancelar, finalizar y consultar clases de sus cursos. El estudiante matriculado consulta sus clases y abre el enlace Zoom vigente. Cada docente aporta el enlace de su propia cuenta Zoom; Aula no crea ni controla reuniones en Zoom. Las grabaciones de YouTube pertenecen a CP-10.

## E² · Escenarios y reglas

| Caso | Resultado |
| --- | --- |
| CP-09.01–02 Programar y asignar | Tema, asignatura, grado, secciones, horario de Lima y enlace Zoom. El fin debe ser posterior al inicio. Los destinos provienen de las asignaciones ERP. Varias secciones generan clases independientes en una sola transacción. |
| CP-09.03 Avisar | Cada clase genera un aviso interno para los estudiantes con matrícula activa de esa sección, con curso, tema y horario. |
| CP-09.04–05 Consultar e ingresar | Las consultas comprueban la matrícula o asignación vigente. Se expone únicamente el enlace actual para ingresar; Zoom abre en una pestaña externa. |
| CP-09.06 Continuar | Un enlace nuevo añade un bloque Zoom a la misma clase, conserva los anteriores en el historial académico y avisa una sola vez a los estudiantes. |
| CP-09.07 Editar | El docente vigente cambia tema, horario o enlace. Se registra responsable, momento y valores anteriores/nuevos; se avisa sobre cambios relevantes. |
| CP-09.08 Cancelar | Acción manual con confirmación; conserva clase e historial, retira acceso al enlace y avisa. No cancela la reunión en Zoom. |
| CP-09.09 Finalizar | Acción manual del docente. `PASADA` se calcula cuando vence el horario y no acredita realización; `FINALIZADA` requiere confirmación explícita. |
| CP-09.10 Conflictos | Se detectan intervalos superpuestos del docente o de la sección. Se advierte y solo se guarda tras confirmar; horarios contiguos no son conflicto. |
| CP-09.11 Cambios ERP | Matrículas y asignaciones vigentes rigen cada lectura o escritura. El historial conserva responsables anteriores. |
| CP-09.12 Reintentos | Identificadores de operación, transacciones y restricciones únicas evitan duplicar clases, enlaces y avisos; otra operación puede crear una clase similar. |
| CP-09.13 Desactivación | El interruptor institucional conjunto oculta y deniega clases y grabaciones; no borra historial ni afecta los demás módulos. |
| CP-09.14 Seguridad | El enlace solo debe estar lleno; no se restringe su formato ni dominio. No se consulta desde el servidor. Acceso por curso y apertura externa protegida. |
| CP-09.15 Auditoría | Historial de creación, edición, enlace, cancelación y finalización con fecha y actor; conserva instantáneas de datos académicos y enlace. |

Se reutilizaron el catálogo y sincronización ERP → Aula, los permisos académicos, el módulo de avisos, el historial de clases, la auditoría, el interruptor institucional y la pantalla de Clases virtuales. Los controles añadidos son funcionales; E³ (Experiencia UX/UI) permanece pendiente.

### Ajuste de programación por secciones

Al marcar varias secciones, el docente elige entre una reunión conjunta con tema, horario y enlace compartidos, o clases separadas con esos datos propios para cada sección. En ambos casos Aula crea una clase independiente por sección. Las clases separadas deben tener horarios sin superposición. Una clase ya programada que se superpone muestra en la advertencia su tema, curso, grado, sección, fecha y horario; el docente puede confirmar ese conflicto según CP-09.10. La operación completa sigue siendo transaccional e idempotente mediante `POST /clases/programaciones`.

## Servicio, migración y evidencia

`POST /clases/lotes` crea una clase por sección. Las operaciones de cambio requieren `operacionId` para reintentos idempotentes. `GET /clases/{id}` entrega `estado_visible` y solo el enlace actual en clases activas. `GET /clases/{id}/enlaces` y `/historial` quedan para personal académico autorizado. La migración incremental `V22__clases_cp09.sql` añade claves de operación, historial de actor/enlace, estado `FINALIZADA` y unicidad de avisos por evento y destinatario. Conserva las clases y datos previos; los eventos históricos anteriores pueden no tener actor registrado.

Pruebas focalizadas: `python pruebas/cp09-verificar-flujos.py` cubrió los seis flujos pedidos con 14 comprobaciones explícitas, rechazo de enlaces vacíos, docente ajeno e IDs ajenos. `python pruebas/cp09-programacion-secciones.py` comprobó programación conjunta y separada con un enlace libre, reintentos, alerta con datos de una clase anterior, atomicidad y rechazo de horarios separados superpuestos. Sus clases temporales llevan el prefijo `CP09 temporal` y se retiran al finalizar; restaura el interruptor institucional. Compilaron API Aula y frontend. En navegador local, el docente `cp06.carlos.demo` inició sesión, abrió Clases virtuales y visualizó la selección conjunta y los formularios individuales para Matemática, Primaria, Quinto y secciones A/B. Flyway V22 quedó aplicado y el módulo activo.

## Brechas pendientes y ficha técnica

- E³ queda para una fase posterior; no se evaluó la experiencia de uso.
- CP-10 gestionará grabaciones YouTube, sin mezclar su operación con las reuniones Zoom de CP-09.
- La marca `FINALIZADA` depende de la declaración del docente; no verifica asistencia ni el estado real de Zoom.

| Criterio | Estado |
| --- | --- |
| Fuente académica | ERP determina cursos, secciones, docentes y matrículas; Aula administra las clases. |
| Consistencia | Una transacción por lote; reintentos con identificador único; avisos vinculados a evento. |
| Seguridad | Autorización vigente y comprobación de enlace no vacío en backend. |
| Datos previos | Migración Flyway V22 sin reiniciar volúmenes. |
| Alcance | E¹ y E² implementados; E³ pendiente. |
