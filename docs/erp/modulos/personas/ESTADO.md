# Estado — Personas

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | Completada | [RF, RNF, RI-PER-001–037 y trazabilidad](ESPECIFICACION.md), [política transversal](../../POLITICAS_TRANSVERSALES.md) e [informe de auditoría](../../RESULTADO_CIERRE_REQUISITOS_PERSONAS_PARA_CHATGPT.md). | Decisiones funcionales D1–D5 y P1–P6 incorporadas; obligaciones operativas anteriores a datos reales y diferencias técnicas asignadas a verificaciones posteriores. |
| Análisis funcional | En progreso | [CU-PER-01–07 y criterios](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Detallar flujos de corrección excepcional, alcance/revocación de contactos, información pendiente y permisos por operación con contratos de Identidad, Matrículas y Asignaciones. |
| Arquitectura | En progreso | [Diseño](ARQUITECTURA.md), [V2](MODELO_DATOS.md), [test modular](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java). | Migración correctiva para documento histórico, modelar alcances y catálogos, aprobar diseño físico; separar DTO HTTP y revisar `contratos/`/`@NamedInterface`. |
| Implementación | En progreso | [V2](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql), [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasController.java), [ERP Web](../../../../apps/erp/web/src/modules/personas/PersonasModulo.tsx). | Alinear unicidad histórica, alcances y campos con requisitos; corregir alta por rol de Coordinación e identidad sin facultad específica; integrar permisos verificables. |
| Verificación y pruebas | En progreso | [Resultados históricos y escenarios futuros](PRUEBAS.md). | Ejecutar escenarios nuevos, seguridad y contratos, WCAG 2.2 AA, navegador, rendimiento HTTP y pruebas de capas. |

Estados admitidos: Pendiente, En progreso, Bloqueada, Completada. La Verificación 1 cierra únicamente la definición funcional aprobada y trazable según el [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md). No certifica implementación, pruebas ni cumplimiento legal/operativo para datos reales; las verificaciones 2–5 conservan sus pendientes.
