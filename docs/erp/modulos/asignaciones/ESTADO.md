# Estado — Asignaciones docentes

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | Pendiente | [Alcance provisional](ESPECIFICACION.md). | Aprobar alcance de asignación por docente, curso, grado, sección, período y vigencia, sin asumir exclusividades. |
| Análisis funcional | Pendiente | [Casos aún sin definir](ESPECIFICACION.md#4-actores-y-casos-de-uso). | Definir actores, cambios de docente, superposiciones, permisos y efecto sobre acceso a estudiantes. |
| Arquitectura | Pendiente | [Diseño pendiente](ARQUITECTURA.md), [datos pendientes](MODELO_DATOS.md). | Contratos, seguridad, transacciones y aprobación técnica. |
| Implementación | En progreso | [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/api/AsignacionesController.java), [Web](../../../../apps/erp/web/src/modules/asignaciones/AsignacionesModulo.tsx), [Flyway V4](../../../../apps/erp/api/src/main/resources/db/migration/V4__asignaciones_docentes.sql). | Horario estructurado y reemplazos con validación de superposiciones; conexión de Aula. |
| Verificación y pruebas | En progreso | [Prueba HTTP](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java). | Más escenarios de cobertura, vigencias y concurrencia. |

Estados admitidos: Pendiente, En progreso, Bloqueada y Completada. Solo usar `Completada` con evidencia conforme al [estándar](../../ingenieria/ESTANDAR_VERIFICACION.md).
