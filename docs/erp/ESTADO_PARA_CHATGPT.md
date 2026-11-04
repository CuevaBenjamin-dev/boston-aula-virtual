# ERP — estado verificable al cierre de Fase 2

**Corte:** 2026-11-04. **Rama local:** `refactor/erp-phase2-20261104`. **Resultado:** reorganización parcial y compatible; la extracción de las reglas heredadas **no está terminada**. Este documento describe código local sin commit, push ni despliegue. Para evaluar cambios frente a la fase anterior, comparar la rama local con `refactor/sistema-bbs-java25`.

## Resumen ejecutivo

La línea base de ERP y ERP Web se verificó antes de cambiar código. Se añadieron pruebas de caracterización sobre PostgreSQL desechable, se separaron componentes técnicos y se extrajeron el alta/edición de alumnos y las escrituras de responsables/vínculos hacia Personas. Aula y sus operaciones administrativas usan rutas `/api/erp/v1`; las rutas antiguas permanecen porque la paridad HTTP, autorización y efectos de escritura aún no están caracterizados por completo. El outbox conserva su implementación y límites actuales. Las decisiones de Identidad, Documentos y permisos efectivos siguen abiertas; no se creó una política nueva.

## Estructura real del backend ERP

| Ubicación | Estado y responsabilidad efectiva | Evidencia |
|---|---|---|
| Raíz `pe.boston.erp` | Arranque, controladores de compatibilidad/V1 transitorios y servicios heredados con reglas aún no extraídas | [ErpApplication](../../apps/erp/api/src/main/java/pe/boston/erp/ErpApplication.java), [ErpV1Service](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java), [Institucional](../../apps/erp/api/src/main/java/pe/boston/erp/Institucional.java) |
| `personas` | Consulta de alumnos y contratos públicos de alumno/responsables; sus escrituras viven en `application` | [Estudiantes](../../apps/erp/api/src/main/java/pe/boston/erp/personas/Estudiantes.java), [Responsables](../../apps/erp/api/src/main/java/pe/boston/erp/personas/Responsables.java), [PersonasService](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasService.java), [PersonasFacade](../../apps/erp/api/src/main/java/pe/boston/erp/personas/PersonasFacade.java) |
| `matriculas` | Borradores e importación propios; controlador V1; transiciones centrales heredadas | [carpeta](../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/) |
| `oferta` | Consulta de asignaturas por grado y controlador V1; escrituras de catálogo/malla/oferta heredadas | [carpeta](../../apps/erp/api/src/main/java/pe/boston/erp/oferta/) |
| `asignaciones` | Consulta de asignaciones y controlador V1; alta/asignación/retiro heredados | [carpeta](../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/) |
| `documentos` | Sólo módulo estructural; no se inventaron casos de uso | [package-info](../../apps/erp/api/src/main/java/pe/boston/erp/documentos/package-info.java) |
| `plataforma` | Seguridad, sesión, autorización académica, BFF, errores y cliente HTTP de Identidad; no es módulo funcional adicional | [carpeta](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/) |

Las carpetas `domain` e `infrastructure` de los cinco módulos **no se crearon vacías**. La ubicación final de servicios y persistencia se decidirá al extraer casos de uso con pruebas completas. El [mapa modular](MAPA_MODULOS.md) registra las cinco verificaciones por módulo.

## Archivos conservados, movidos y eliminados

| Resultado | Archivos | Motivo |
|---|---|---|
| Movidos a `plataforma` | `DirectorioIdentidad.java`, `ErpWebProxy.java`, `Errores.java`, `Seguridad.java` | Son adaptadores y configuración técnica, sin reglas de un módulo funcional. Se actualizaron imports y la prueba del BFF. |
| Movido temporalmente a raíz | `personas/api/PersonasController.java` → [PersonasController.java](../../apps/erp/api/src/main/java/pe/boston/erp/PersonasController.java) | Todavía llama métodos raíz; dejarlo en Personas produce ciclo detectado por Modulith. |
| Nuevos | [IntegracionV1Controller](../../apps/erp/api/src/main/java/pe/boston/erp/IntegracionV1Controller.java), contratos `Estudiantes` y `Responsables`, implementación de Personas, [prueba SQL](../../apps/erp/api/src/test/java/pe/boston/erp/ContratosErpCaracterizacionTest.java), [prueba HTTP](../../apps/erp/api/src/test/java/pe/boston/erp/IntegracionV1ContratoTest.java) | Rutas V1 de integración, primera extracción real y puertas de regresión. |
| Conservados con cambios | `ErpV1Service`, `Institucional`, `InstitucionalController`, `TesoreriaController`, [AsignacionesController](../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/api/AsignacionesController.java), ocho clientes de Aula | Mantener reglas y contratos existentes mientras se cubren los casos pendientes. |
| Conservados sin cambios funcionales | `ErpV1Controller`, `DatosIniciales`, `Tesoreria`, controladores de Matrículas/Oferta, migraciones históricas, ERP Web | Sus responsabilidades o consumidores aún no se han reemplazado de forma verificable. |
| Eliminados definitivamente | **Ninguno** | Las bajas visibles en Git son rutas de archivos movidos; no se borró lógica de negocio ni historial. |

## Avance de los cinco módulos

| Módulo | Requisitos y análisis | Arquitectura e implementación | Verificación y siguiente puerta |
|---|---|---|---|
| Personas | Parcial; reglas documentadas y otras pendientes | Alta/edición de alumnos, responsables y vínculos ya usan contratos públicos del módulo. Verificación histórica y ficha permanecen en raíz por sus lecturas de matrícula. | C01–C03 SQL parciales; faltan reloj fijo, concurrencia, contrato HTTP y autorización por recurso antes de mover el resto. |
| Matrículas | Parcial | Borrador/importación propios; proceso, pago externo e historial siguen en `ErpV1Service`. | C04–C07 parciales; C13 cubre previsualización, confirmación y reintento. Faltan modalidades, transacciones de cada transición, carrera de finalización, errores por fila y HTTP. |
| Oferta académica | Parcial | Consulta de malla propia; CRUD, propagación y eventos en raíz. | C08 sólo cubre propagación y versiones básicas; faltan estados, restricciones, concurrencia y contrato HTTP. |
| Asignaciones docentes | Parcial; política de cuenta en discusión | Consulta propia; alta, vínculo con Identidad y asignación en raíz. | C09 parcial; falta fallo remoto simulado, idempotencia, retiro y autorización por recurso. |
| Documentos | **Bloqueado** por requisitos no aprobados | Estructura existente sin casos de uso implementados. | No hay prueba funcional aplicable hasta definir acceso, tipos, estados y retención. |

La corrección puntual en [asignarDocenteLote](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java) evita el `NullPointerException` de `List.of(...).contains(null)` durante una asignación válida. La regresión C09 cubre lote válido, duplicados y elemento nulo.

## API oficial, contratos y consumidores

La ruta oficial es `/api/erp/v1`. [IntegracionV1Controller](../../apps/erp/api/src/main/java/pe/boston/erp/IntegracionV1Controller.java) expone perfiles, resúmenes, eventos, ámbitos, reconciliación y lecturas administrativas mediante `Institucional`; no duplica reglas. [InstitucionalController](../../apps/erp/api/src/main/java/pe/boston/erp/InstitucionalController.java) conserva `/api/erp` y añade alias V1 `/administracion/*` para operaciones heredadas. [TesoreriaController](../../apps/erp/api/src/main/java/pe/boston/erp/TesoreriaController.java) expone también `/api/erp/v1/tesoreria/*`. [Aula](../../apps/aula/api/src/main/java/pe/boston/aula/) apunta a V1 para las llamadas identificadas. [CONTRATOS_API.md](CONTRATOS_API.md) detalla la correspondencia.

**No se retiró `/api/erp`:** la comparación HTTP nueva cubre cinco lecturas con un servicio simulado, pero falta cubrir JWT/scope, BFF/CSRF, todas las escrituras y efectos SQL, además de consumidores externos no inventariados. [Tesorería](../../apps/erp/api/src/main/java/pe/boston/erp/Tesoreria.java) sigue activa y es consumida por Aula; retirarla rompería ese contrato. Tampoco se eliminó `Institucional` o `ErpV1Service` porque aún contienen reglas, consultas y orquestación en uso.

## Integraciones, outbox e Identidad

El outbox actual en [Institucional](../../apps/erp/api/src/main/java/pe/boston/erp/Institucional.java) se mantuvo. La caracterización C11 verifica en PostgreSQL aislado que rollback no deja evento y que dos publicaciones confirmadas conservan secuencia y versión. **No se migró** el ensamblado de snapshots: hoy consulta tablas de distintos dominios; hacerlo ahora sin proyecciones públicas y pruebas de consumo podría cambiar el JSON enviado a Aula. El puerto y el orden de extracción propuestos están en [DECISIONES_ARQUITECTURA.md](DECISIONES_ARQUITECTURA.md).

El cliente [DirectorioIdentidad](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/DirectorioIdentidad.java) se movió como adaptador técnico, sin alterar el flujo remoto. El alta de cuenta docente todavía hace HTTP dentro de una transacción ERP. Quedan pendientes el vínculo estable, clave idempotente, conciliación, revocación y pruebas de fallos; no se implementó concesión dinámica de permisos. La [matriz UX/UI](MATRIZ_PERMISOS.md) es especificación, no autorización operativa.

## Verificación realizada y límites

| Comprobación | Resultado y alcance |
|---|---|
| Línea base ERP `mvn -q test` y ERP Web `npm run build` | Pasaron antes de los cambios. |
| ERP `mvn -q test` al cierre | **23 pruebas en 8 suites, 0 fallos/errores**, ejecución completa finalizada. Incluye PostgreSQL 18.6 desechable, 11 migraciones, contratos SQL y `ApplicationModules.verify()`. |
| Aula `mvn -q test` al cierre | **16 pruebas en 6 suites, 0 fallos/errores.** El paquete API también compiló tras migrar sus llamadas. |
| `IntegracionV1ContratoTest` | Pasó: cinco pares de lecturas heredadas/V1 con igual status, tipo y JSON; no prueba filtros de seguridad ni servicio real. |
| `git diff --check` | Sin errores de whitespace; Git sólo advirtió normalización LF/CRLF en Windows. |

Las pruebas C01–C14 están **parcialmente implementadas**; su detalle y escenarios faltantes constan en [INVENTARIO_PRUEBAS_PENDIENTES.md](INVENTARIO_PRUEBAS_PENDIENTES.md) y [MATRIZ_TRAZABILIDAD.md](MATRIZ_TRAZABILIDAD.md). No hubo pruebas end-to-end con ERP, Identidad y Aula levantados simultáneamente. No se alteraron bases reales, migraciones ni volúmenes.

## Decisiones institucionales pendientes y continuación

| Prioridad | Decisión o trabajo | Condición para continuar |
|---|---|---|
| 1 | Completar C01–C14 aplicables, incluido HTTP, seguridad, concurrencia y fallos de Identidad. | Comparar la misma entrada/estado antes y después de cada extracción; no borrar API antigua por inferencia. |
| 2 | Extraer verificación/ficha de alumno, luego Oferta, Matrículas y Asignaciones. | Interfaces públicas sin ciclos; `ApplicationModules.verify()`, PostgreSQL y contrato HTTP en verde tras cada cambio. |
| 3 | Definir política de cuenta docente y permisos efectivos, incluidos ámbito, revocación y conciliación. | Aprobación institucional de [reglas abiertas](REGLAS_INSTITUCIONALES.md); no conceder privilegios por UI. |
| 4 | Reensamblar snapshots del outbox por contratos públicos de los módulos. | C11 completo con snapshots, reintentos y consumo ERP–Aula; mantener transacción local del cambio+evento. |
| 5 | Documentos. | Aprobar requisitos funcionales antes de crear endpoints o políticas de conservación. |
| 6 | Eliminar servicios raíz y `/api/erp` heredada. | Cero consumidores verificados y todas las pruebas pertinentes superadas. |

## Documentación de referencia

- [MAPA_MODULOS.md](MAPA_MODULOS.md): propiedad y cinco verificaciones por módulo.
- [REGLAS_INSTITUCIONALES.md](REGLAS_INSTITUCIONALES.md): reglas aprobadas, observadas y por validar.
- [REQUISITOS_SRS.md](REQUISITOS_SRS.md): requisitos verificables.
- [CASOS_DE_USO.md](CASOS_DE_USO.md): flujos principales y excepciones.
- [MATRIZ_PERMISOS.md](MATRIZ_PERMISOS.md): especificación funcional/visual, sin grants.
- [DECISIONES_ARQUITECTURA.md](DECISIONES_ARQUITECTURA.md): decisiones vigentes y propuestas.
- [MATRIZ_TRAZABILIDAD.md](MATRIZ_TRAZABILIDAD.md): vínculo entre requisito, código y prueba.
- [PLAN_MODULARIZACION.md](PLAN_MODULARIZACION.md), [MATRIZ_RESPONSABILIDADES.md](MATRIZ_RESPONSABILIDADES.md), [ANALISIS_PARA_CHATGPT.md](ANALISIS_PARA_CHATGPT.md): inventario y plan de Fase 1; conservados como corte histórico.
