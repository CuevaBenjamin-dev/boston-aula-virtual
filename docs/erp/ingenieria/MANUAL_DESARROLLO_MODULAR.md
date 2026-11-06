# Proceso estandarizado de desarrollo modular

## Propósito y responsables

El proceso rige Personas, Matrículas, Oferta académica, Asignaciones docentes y Documentos. Conserva reglas aprobadas, limita el alcance y produce evidencia para cada cambio. La persona responsable institucional aprueba políticas y facultades; analista/diseñador traduce decisiones a requisitos y diseño; Codex/desarrollador implementa y registra diferencias; verificador contrasta criterios con resultados. Una misma persona puede cumplir varios roles, pero no debe confundir propuesta, implementación y aprobación.

## Cuatro actividades, cinco verificaciones

| Actividad | Verificación | Entregable principal | Criterio de avance |
|---|---|---|---|
| A. Definición funcional | 1. Requisitos | RF, RNF medibles, RI, actores, límites y decisiones en `ESPECIFICACION.md` | Reglas identificadas, aprobadas o explícitamente pendientes. |
| A. Definición funcional | 2. Análisis funcional | CU, flujos, alternativas, errores, permisos y aceptación en `ESPECIFICACION.md` | Cada flujo responde a requisitos, incluidos casos de excepción. |
| B. Diseño técnico | 3. Arquitectura | `ARQUITECTURA.md`, `MODELO_DATOS.md` y estrategia de `PRUEBAS.md` | Integridad, contratos, seguridad, UX y dependencias diseñados y revisados. |
| C. Construcción | 4. Implementación | Código, migraciones nuevas, pruebas junto al código y documentación técnica | Comportamiento trazable y pruebas focalizadas ejecutadas. |
| D. Comprobación | 5. Verificación y pruebas | `PRUEBAS.md` con resultados, defectos, entorno y `ESTADO.md` | Evidencia suficiente frente a criterios, sin fallos críticos ocultos. |

Las cinco verificaciones son obligatorias en cada módulo; sus criterios detallados están en [ESTANDAR_VERIFICACION.md](ESTANDAR_VERIFICACION.md). Un documento creado o una compilación exitosa no equivale a `Completada`.

## Inicio, bloqueos y cambios

Antes de implementar un módulo, las verificaciones 1–3 deben estar suficientemente resueltas para el alcance escogido. Una excepción solo permite un incremento delimitado, con decisión pendiente, aislamiento técnico, reversibilidad y pruebas explícitas; nunca puede comprometer identidad, integridad, autorización ni datos. El responsable anota el límite en `ESTADO.md` y el requisito pendiente en `ESPECIFICACION.md`. No se deduce permiso por ausencia de respuesta institucional.

Una dependencia de Identidad, Aula u otro módulo se registra con contrato esperado, propietario, estado y operación afectada. Si bloquea una función, el resto de resultados demostrados se conserva, pero el estado de la verificación no se infla. Una modificación de alcance revisa RF/RI/CU, diseño, migración, pruebas y documentación afectada; no se modifica una migración Flyway ya aplicada. Si hay tensión entre una política aprobada y una propuesta, se detiene el comportamiento dependiente y se solicita decisión institucional.

## Entrega y mejora continua

Cada módulo mantiene seis documentos: `README.md`, `ESPECIFICACION.md`, `ARQUITECTURA.md`, `MODELO_DATOS.md`, `PRUEBAS.md`, `ESTADO.md`. El [índice](README.md) permite abrir solo lo necesario. Codex informa archivos cambiados, decisiones, resultados realmente ejecutados, fallos/bloqueos y estado de Git. El verificador revisa criterios y evidencia original, no solo el resumen.

Tras cada ciclo relevante se registra en [lecciones](LECCIONES_APRENDIDAS.md): aciertos, errores, retrabajo, causa solo cuando se pruebe, solución, regla mejorable y evidencia. La retrospectiva mejora los estándares; **no es una sexta verificación**. La profundidad de artefactos y pruebas debe corresponder al riesgo del cambio: un ajuste simple no exige diagramas o clases adicionales sin utilidad.
