# Estado — Oferta académica

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | Pendiente | [Alcance provisional](ESPECIFICACION.md). | Definir alcance de periodos, grados, secciones y asignaturas y reglas de publicación; no heredar la oferta del ERP anterior. |
| Análisis funcional | Pendiente | [Casos aún sin definir](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Aprobar quién crea/modifica oferta, cuándo se activa y cómo impacta matrículas y asignaciones existentes. |
| Arquitectura | Pendiente | [Diseño pendiente](ARQUITECTURA.md), [datos pendientes](MODELO_DATOS.md). | Contratos, seguridad, transacciones y aprobación técnica. |
| Implementación | En progreso | [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/oferta/api/OfertaController.java), [Web](../../../../apps/erp/web/src/modules/oferta/OfertaModulo.tsx), [Flyway V3](../../../../apps/erp/api/src/main/resources/db/migration/V3__mvp_oferta_matriculas.sql). | Calendario lectivo, excepciones, edición/publicación y reglas completas restantes. |
| Verificación y pruebas | En progreso | [Prueba HTTP](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java). | Más escenarios de años y límites, aprobación formal posterior a MODULA 532. |

Estados admitidos: Pendiente, En progreso, Bloqueada y Completada. Solo usar `Completada` con evidencia conforme al [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md).
