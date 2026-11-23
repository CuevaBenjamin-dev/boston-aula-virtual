# Estado — Matrículas

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | Pendiente | [Alcance provisional](ESPECIFICACION.md). | Definir modalidades, estados, condiciones de confirmación, reversión y responsabilidades antes de diseñar el flujo. |
| Análisis funcional | Pendiente | [Casos aún sin definir](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Definir quién registra, confirma y revierte cada estado; requisitos de pago y documentos no están aprobados en esta base. |
| Arquitectura | Pendiente | [Diseño pendiente](ARQUITECTURA.md), [datos pendientes](MODELO_DATOS.md). | Contratos, seguridad, transacciones y aprobación técnica. |
| Implementación | En progreso | [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/api/MatriculasController.java), [Web](../../../../apps/erp/web/src/modules/matriculas/MatriculasModulo.tsx), [Flyway V3](../../../../apps/erp/api/src/main/resources/db/migration/V3__mvp_oferta_matriculas.sql) y [V7](../../../../apps/erp/api/src/main/resources/db/migration/V7__borradores_descartados.sql). Listado paginado, filtros y descarte exclusivo de borradores con constancia mínima. | Reconciliar reglas completas y decisiones institucionales pendientes. |
| Verificación y pruebas | En progreso | [Pruebas HTTP de Matrículas](../../../../apps/erp/api/src/test/java/pe/boston/erp/matriculas/MatriculasHttpTest.java), [cierre anual](../../../../apps/erp/api/src/test/java/pe/boston/erp/matriculas/FinalizacionAnioTest.java) y [prueba Web](../../../../apps/erp/web/src/modules/matriculas/MatriculasModulo.test.tsx). | Ampliar variantes y reconciliar requisitos/casos formales posteriores a MODULA 532. |

Estados admitidos: Pendiente, En progreso, Bloqueada y Completada. Solo usar `Completada` con evidencia conforme al [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md).
