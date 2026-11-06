# Estado — Matrículas

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | Pendiente | [Alcance provisional](ESPECIFICACION.md). | Definir modalidades, estados, condiciones de confirmación, reversión y responsabilidades antes de diseñar el flujo. |
| Análisis funcional | Pendiente | [Casos aún sin definir](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Definir quién registra, confirma y revierte cada estado; requisitos de pago y documentos no están aprobados en esta base. |
| Arquitectura | Pendiente | [Diseño pendiente](ARQUITECTURA.md), [datos pendientes](MODELO_DATOS.md). | Contratos, seguridad, transacciones y aprobación técnica. |
| Implementación | Pendiente | [Paquete preparado](../../../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/package-info.java). | Construir solo casos aprobados. |
| Verificación y pruebas | Pendiente | [Plan de cobertura](PRUEBAS.md). | Crear y ejecutar pruebas con el comportamiento aprobado. |

Estados admitidos: Pendiente, En progreso, Bloqueada y Completada. Solo usar `Completada` con evidencia conforme al [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md).
