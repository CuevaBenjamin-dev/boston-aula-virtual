# Aula 02 · Integración real ERP–Identidad–Aula

**Fecha:** 20/11/2026. **Rama:** `codex/aula-01-2026-11-20`. **HEAD:** `a90fc19c645c06b378a96acb1e09484c151f88f6`. Trabajo local sin commit, push ni merge. Se conservaron los cambios previos, las bases, los volúmenes, el cursor legado y `CAMBIOS_DESPUES_DE_DESARROLLO_PARCIAL.md`.

## Implementación

- **ERP:** `IntegracionInstitucional` concilia periódicamente docentes, matrículas y cada par sección–asignatura del ERP. Usa `erp_solicitudes_externas` como outbox transaccional para las operaciones que ya lo registraban, crea solicitudes de conciliación con clave de foto estable, entrega por HTTP, reintenta fallas transitorias y separa `CONFIRMADA`, `ERROR` e `INTERVENCION`. Una solicitud solo queda confirmada después de una respuesta válida del destino. La migración aditiva V6 agrega intentos, próximo reintento y fecha de actualización. El estado y el reintento controlado se consultan en `/api/erp/v1/integracion/estado` y `/api/erp/v1/integracion/{id}/reintentar`, visibles en las fichas ERP de matrícula, asignación y docente.
- **Identidad:** el cliente técnico `erp-integracion` obtiene un token `client_credentials` con scopes `identidad.provision` y `aula.proyectar`. `PUT /api/identidad/integracion/v1/cuentas/{personaId}/{tipo}` crea o reutiliza por UUID ERP y tipo, preserva la contraseña de cuentas existentes, actualiza usuario documental o estado y rechaza colisiones. Las nuevas cuentas usan la clave temporal institucional ya aprobada y exigen cambio en el primer ingreso. V7 agrega únicamente el indicador de cambio obligatorio; `/clave-inicial` efectúa ese cambio antes de continuar al Aula.
- **Aula:** `PUT /api/aula/integracion/v1/cursos/{id}` requiere JWT con scope `aula.proyectar`, materializa el curso con UUID determinista por sección y asignatura y actualiza solo los vínculos que cambiaron. Tareas, entregas y notas quedan fuera de la proyección. El consumidor legado permanece conservado, pero su ejecución programada está desactivada por defecto para evitar dos escritores; su secuencia 436 no se usa en este flujo.
- **Contrato:** proyección de curso versión 1, UUID institucionales, `X-Operacion-Id` estable por solicitud, estado de respuesta del destino y claves de idempotencia del outbox. El ERP calcula la foto vigente en cada entrega y vuelve a conciliar tras cambios. Las llamadas externas ocurren fuera de la transacción académica. No se comparten tablas entre aplicaciones.

Código principal: `apps/erp/api/src/main/java/pe/boston/erp/plataforma/IntegracionInstitucional.java`, `IntegracionEstadoController.java`; `services/identidad/src/main/java/pe/boston/identidad/IntegracionCuentasController.java`, `ClaveInicialController.java`; `apps/aula/api/src/main/java/pe/boston/aula/integraciones/ProyeccionesController.java`; `apps/erp/web/src/shared/ui/EstadoIntegracion.tsx`. Configuración en `compose.yaml`, seguridad en ambos consumidores y migraciones `apps/erp/api/.../V6__integracion_aula_02.sql`, `services/identidad/.../V7__cuentas_integracion.sql`.

## Evidencia ejecutada

| Verificación | Resultado |
| --- | --- |
| Línea base antes del cambio | ERP API 21, Aula API 21, Identidad 4 pruebas; Aula Web 3 pruebas y compilación: pasaron. |
| Después del cambio | `mvn verify`: ERP API 22, Aula API 22, Identidad 9 pruebas; 0 fallas. `npm run test` Aula Web: 3/3. `npm run build` Aula Web y ERP Web: pasaron. |
| Conciliación local existente | Dos matrículas vigentes de 2027 y la docente activa del ERP se vincularon con 3 cuentas existentes o reutilizadas en Identidad; Aula contiene 4 cursos de 2027, 4 inscripciones activas y 2 vínculos docentes activos. Las consultas que emplea el listado de Aula devuelven 2 cursos para cada estudiante y 2 para la docente (Matemática 3.º A y B). |
| Outbox y reejecución | 19 solicitudes, 19 claves distintas y 19 `CONFIRMADA`; el conteo permaneció estable después de varias conciliaciones y del reinicio de ERP. No quedó ninguna solicitud sin confirmar. |
| Autorización HTTP real | En Aula e Identidad, `PUT` sin token respondió 401, con token de scope insuficiente 403 y con token técnico autorizado más cuerpo inválido 400. El último resultado acredita que la solicitud llegó al validador protegido. |
| Primer ingreso | Una cuenta nueva con clave temporal llegó por HTTP al formulario obligatorio `/clave-inicial` (200). No se cambió su clave durante la comprobación. |
| Conservación | Aula conserva 440 cursos en total, incluidos los 436 previos; `estado_sincronizacion.ultima_secuencia` sigue en 436. No se borraron datos ni se generaron dumps. |
| Disponibilidad | ERP `http://127.0.0.1:8089/`, Aula `http://localhost:8088/` e Identidad `http://localhost:9000/login` devolvieron HTML con HTTP 200. API ERP, API Aula, Identidad y PostgreSQL quedaron saludables en Docker. |

## Escenarios del encargo

| Caso | Estado y alcance comprobado |
| --- | --- |
| 1. Docente activo sin asignatura | **PASÓ en prueba automatizada:** genera la misma clave de solicitud para Identidad en dos conciliaciones, aun sin asignaciones. Alta humana nueva: **NO EJECUTADO**. |
| 2. Borrador | **PASÓ:** el borrador existente conserva `persona_id` vacío y no figura en la selección de cuentas ni inscripciones. |
| 3. Matrícula confirmada y cursos 2027 | **PASÓ por conciliación** de dos matrículas previamente confirmadas, sus cuentas y cursos. Confirmar una matrícula nueva en esta ejecución: **NO EJECUTADO** para no añadir registros de ensayo. |
| 4. Matemática 3.º A y B | **PASÓ:** dos asignaciones activas de la misma docente aparecen como dos cursos en la consulta de Aula. |
| 5. Replay y preservación | **PASÓ:** claves y conteos estables; pruebas de cuenta reutilizada sin recodificar clave y de proyección sin desactivar vínculos iguales. No había tareas o notas en los cuatro cursos nuevos para comprobar su preservación con contenido real. |
| 6. Caída y retorno de un consumidor | **NO EJECUTADO** con una solicitud nueva mientras el servicio está detenido; la recuperación de errores temporales sí se observó durante el arranque inicial de Aula. |
| 7. Retirada y reincorporación | **NO EJECUTADO** sobre una matrícula real para no cambiar los expedientes existentes; la proyección calcula los vínculos desde el estado actual. |
| 8. Corrección de DNI y colisión | **PASÓ en pruebas automatizadas** de actualización sin recodificar contraseña y conflicto sin escritura. Cambio documental real: **NO EJECUTADO**. |
| 9. Permisos técnicos | **PASÓ** con respuestas HTTP 401/403/400 reales. |
| 10. Reinicio y persistencia | **PASÓ** con reinicio de API ERP y ERP Web; solicitudes y proyecciones persistieron sin duplicación. Reinicio de Identidad y Aula: **NO EJECUTADO**. |
| 11. Cursor histórico 436 | **PASÓ:** valor intacto; el nuevo flujo no lo consulta. |
| 12. Smoke y acceso humano | **PASÓ** HTML y salud de los servicios. Inicio de sesión hasta listado como docente y estudiante: **NO EJECUTADO** con contraseñas personales; se verificó por SQL la misma consulta de cursos y el primer ingreso obligatorio. |

## Límites y trabajo pendiente

El recorrido mínimo ERP existente → Identidad → Aula funciona en datos persistentes. Los casos 6, 7, la confirmación de una matrícula nueva y la visualización después de iniciar sesión necesitan todavía la prueba manual indicada abajo para cerrar el incremento íntegro. El ERP aún no distingue con datos suficientes un traslado efectivo de sección frente a la corrección de un registro erróneo; no se automatizó esa decisión. La lectura histórica completa de matrículas `FINALIZADA` sigue reservada a Aula 03. Una persona con dos roles académicos simultáneos exigiría una decisión y un modelo de permisos de Identidad más amplio que el campo `tipo` actual; el sistema muestra conflicto en vez de inventar un segundo usuario. La versión 1 usa foto vigente y orden serial del outbox; una revisión de entidad explícita y la prueba de entregas fuera de orden siguen pendientes antes de operación multiinstancia.

## Comprobación manual

1. Abrir [ERP](http://127.0.0.1:8089/) como Coordinación. En **Personas**, registrar un docente activo; comprobar en su ficha el estado de Identidad. La cuenta debe existir incluso sin asignación. En **Asignaciones docentes**, asignarlo a Matemática de 3.º A y B de 2027; esperar la confirmación de Aula en cada fila.
2. En **Matrículas**, crear un borrador y comprobar que no aparece como cuenta ni curso. Completar apoderado válido y confirmar; en el expediente verificar que **Identidad** y **Aula** estén `Confirmada`. Si aparece `Error reintentable`, esperar la recuperación; si requiere intervención, revisar el motivo y usar **Reintentar** después de corregir la causa.
3. Abrir [Aula](http://localhost:8088/) como docente y estudiante usando las credenciales ficticias institucionales ya conocidas, sin pegarlas en el informe. Una cuenta nueva debe pedir cambio de clave en [Identidad](http://localhost:9000/login) antes de entrar. Actualizar **Mis cursos**: el docente debe ver los cursos asignados; el estudiante, los de su sección y año. Repetir la consulta o recargar no debe producir cursos duplicados.

No se hizo commit ni push. El árbol permanece con los cambios locales previos de Aula 01 y ERP más los archivos de este incremento; las modificaciones existentes ajenas a Aula 02 no se descartaron.
