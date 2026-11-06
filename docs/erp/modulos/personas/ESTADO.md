# Estado — Personas

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | En progreso | [RF, RNF y RI-PER-001–037](ESPECIFICACION.md#1-requisitos-funcionales). | Aprobar catálogos/formatos y retención/protección. |
| Análisis funcional | En progreso | [CU-PER-01–07 y criterios](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Ámbito de Coordinación, alumnos asignados y comprobación institucional de facultades. |
| Arquitectura | En progreso | [Diseño](ARQUITECTURA.md), [V2](MODELO_DATOS.md), [test modular](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java). | Aprobar diseño físico; separar DTO HTTP de capas internas y revisar `contratos/`/`@NamedInterface`. |
| Implementación | En progreso | [V2](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql), [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasController.java), [ERP Web](../../../../apps/erp/web/src/modules/personas/PersonasModulo.tsx). | Integrar ámbitos efectivos y refactorización de capas aprobada. |
| Verificación y pruebas | En progreso | [Resultados históricos y matriz](PRUEBAS.md). | WCAG 2.2 AA, recorrido manual autenticado, rendimiento HTTP y pruebas de capas. |

Estados admitidos: Pendiente, En progreso, Bloqueada, Completada. `Completada` requiere el criterio y evidencia del [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md); no se ha elevado ningún estado en esta reorganización.
