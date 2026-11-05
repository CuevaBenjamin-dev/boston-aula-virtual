# Estado — Personas

| Verificación | Estado | Evidencia | Pendiente |
|---|---|---|---|
| Requisitos | En progreso | [Nueve RF, seis RNF y RI-PER-001 a 037](ESPECIFICACION.md#1-requisitos-funcionales) con criterios y límites documentados. | Aprobar catálogos/formatos y políticas de protección y retención pendientes. |
| Análisis funcional | En progreso | [Siete casos de uso con alternativas y errores](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Definir ámbito de Coordinación, alumnos asignados a Docentes y comprobación institucional de facultades. |
| Arquitectura | En progreso | [PostgreSQL V2, diccionario, índices y contrato](ESPECIFICACION.md#6-arquitectura-y-modelo-de-datos); límites Spring Modulith verificados. | Aprobación institucional del diseño físico y contratos de ámbito. |
| Implementación | En progreso | [Migración V2](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql), [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasController.java) y [tres vistas](../../../../apps/erp/web/src/modules/personas/PersonasModulo.tsx). | Integrar alcance efectivo de Coordinación/Docentes y comprobar interfaz en navegador. |
| Verificación y pruebas | En progreso | [Testcontainers PostgreSQL](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasEsquemaTest.java), [HTTP](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java), [rendimiento](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasRendimientoTest.java) y [React](../../../../apps/erp/web/src/modules/personas/PersonasModulo.test.tsx). | Auditoría WCAG completa, prueba manual autenticada de navegador y rendimiento HTTP extremo a extremo; ver [informe](../../RESULTADO_PERSONAS_PARA_CHATGPT.md). |

Estados admitidos: Pendiente, En progreso, Bloqueada y Completada. Solo usar Completada con evidencia enlazada.
