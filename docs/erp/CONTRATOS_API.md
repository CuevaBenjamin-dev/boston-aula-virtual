# Contratos HTTP actuales

La [especificación OpenAPI tipada](openapi.yaml) documenta las rutas del asistente de matrícula y pago verificadas contra records Java; **no cubre aún todo el ERP**. `docs/historico/openapi.yaml` es un inventario histórico con muchos esquemas genéricos y no debe usarse como contrato final.

Los prefijos son `/api/erp/v1` para consumidores con JWT y `/api/erp-web/v1` para el navegador autenticado con cookie y CSRF. El proxy `ErpWebProxy` reenvía hacia los controladores V1. Los endpoints legados `/api/erp/**` se preservaron, incluidos los eventos usados por Aula. La forma exacta de respuesta de muchas consultas sigue siendo `Map<String,Object>`; esa ambigüedad es deuda técnica documentada, no un esquema inventado.

| Recurso V1 | Controlador fuente | Operaciones |
|---|---|---|
| `/estudiantes`, `/personas`, `/estudiantes/{id}/responsables` | `personas/api/PersonasController.java` | consulta, alta, edición, verificación y vínculos |
| `/matriculas`, `/matriculas/{id}` y subacciones | `matriculas/api/MatriculasController.java` | inicio, edición, reserva, cancelación, confirmación, pago externo |
| `/matriculas/borradores` | `matriculas/api/BorradoresMatriculaController.java` | guardar, consultar y finalizar |
| `/importaciones` | `matriculas/api/ImportacionController.java` | plantilla, vista previa y confirmación |
| `/periodos`, `/niveles`, `/grados`, `/secciones`, `/ofertas`, `/asignaturas` | `oferta/api/OfertaController.java` | currículo y oferta |
| `/docentes` y asignaciones | `asignaciones/api/AsignacionesController.java` | cuentas, asignaciones y retiro |
| `/resumen`, `/catalogos` | `ErpV1Controller.java` | resumen transversal legado |

Los controladores devolvieron 401 sin sesión en la comprobación local de `/api/erp-web/v1/catalogos`; no se ejecutó una batería de autorización de cada recurso en esta reestructuración.
