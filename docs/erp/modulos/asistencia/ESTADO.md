# Asistencia — estado de verificaciones

| Verificación | Estado | Evidencia | Pendiente / dependencia |
|---|---|---|---|
| Requisitos | En progreso | [Decisiones aprobadas del paquete](ESPECIFICACION.md). | Trazabilidad exacta con exportación MODULA revisión 532 no disponible. |
| Análisis funcional | En progreso | [Casos aplicados](ESPECIFICACION.md). | Recorrido manual de Dirección, Coordinación, Docente y Estudiante. |
| Arquitectura | En progreso | [Contratos y límites](ARQUITECTURA.md), [datos](MODELO_DATOS.md); token técnico Aula→ERP respondió 200 en local. | Recorrido con sesión Docente/Estudiante pendiente. |
| Implementación | En progreso | [ERP API](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaService.java), [Aula Web](../../../../apps/aula/web/src/pages/AsistenciaAula.tsx); migraciones ERP V9 y Aula V29 aplicadas, servicios sanos y rutas Web 200. | Configurar calendario anual con fechas institucionales antes de registrar asistencia. |
| Verificación y pruebas | En progreso | [Matriz y resultados](PRUEBAS.md); nueve pruebas AST aisladas y compilaciones de ambas interfaces aprobadas. | Casos de navegador, adjuntos binarios y recorrido autenticado pendientes. |

No se declara ninguna etapa Completada sin evidencia de su criterio íntegro.
