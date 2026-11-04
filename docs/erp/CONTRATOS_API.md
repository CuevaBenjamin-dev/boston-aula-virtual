# Contratos HTTP actuales

**Corte 2026-11-04.** La [especificación OpenAPI tipada](openapi.yaml) documenta las rutas del asistente de matrícula y pago verificadas contra records Java; **no cubre aún todo el ERP**. `docs/historico/openapi.yaml` es un inventario histórico con muchos esquemas genéricos y no debe usarse como contrato final.

Los prefijos son `/api/erp/v1` para consumidores con JWT y `/api/erp-web/v1` para el navegador autenticado con cookie y CSRF. El proxy [ErpWebProxy](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/ErpWebProxy.java) reenvía hacia los controladores V1. Aula cambió sus llamadas directas a V1. Los endpoints legados `/api/erp/**` se preservan hasta completar pruebas de consumidores, seguridad y forma HTTP. La forma exacta de respuesta de muchas consultas sigue siendo `Map<String,Object>`; esa ambigüedad es deuda técnica documentada, no un esquema inventado.

| Recurso V1 | Controlador fuente | Operaciones |
|---|---|---|
| `/estudiantes`, `/personas`, `/estudiantes/{id}/responsables` | [PersonasController transitorio en raíz](../../apps/erp/api/src/main/java/pe/boston/erp/PersonasController.java) | consulta, alta, edición, verificación y vínculos; responsables delegados al módulo Personas |
| `/matriculas`, `/matriculas/{id}` y subacciones | `matriculas/api/MatriculasController.java` | inicio, edición, reserva, cancelación, confirmación, pago externo |
| `/matriculas/borradores` | `matriculas/api/BorradoresMatriculaController.java` | guardar, consultar y finalizar |
| `/importaciones` | `matriculas/api/ImportacionController.java` | plantilla, vista previa y confirmación |
| `/periodos`, `/niveles`, `/grados`, `/secciones`, `/ofertas`, `/asignaturas` | `oferta/api/OfertaController.java` | currículo y oferta |
| `/docentes` y asignaciones | `asignaciones/api/AsignacionesController.java` | cuentas, asignaciones y retiro |
| `/resumen`, `/catalogos` | `ErpV1Controller.java` | resumen transversal legado |
| `/docentes/resumen`, `/cursos/{curso}/estudiantes/resumen`, `/perfiles/{tipo}/{id}` | [IntegracionV1Controller](../../apps/erp/api/src/main/java/pe/boston/erp/IntegracionV1Controller.java) | lecturas consumidas por Aula, delegadas a `Institucional` |
| `/eventos`, `/coordinaciones/{cuenta}/ambitos`, `/reconciliar` | `IntegracionV1Controller.java` | sincronización y ámbitos; `/eventos` conserva `SCOPE_erp.sync` |
| `/catalogo`, `/ofertas` | `IntegracionV1Controller.java` | lecturas de administración heredada; mismo servicio y permiso |
| `/administracion/*` | [InstitucionalController](../../apps/erp/api/src/main/java/pe/boston/erp/InstitucionalController.java) | alias V1 de altas/cambios administrativos todavía heredados |
| `/tesoreria/*` | [TesoreriaController](../../apps/erp/api/src/main/java/pe/boston/erp/TesoreriaController.java) | alias V1 del contrato financiero que Aula aún consume |

La [comparación MockMvc](../../apps/erp/api/src/test/java/pe/boston/erp/IntegracionV1ContratoTest.java) verifica que cinco lecturas de Aula conservan status, tipo y JSON entre ruta antigua y V1 mediante el mismo servicio simulado. No sustituye pruebas de JWT, CSRF, SQL ni de extremo a extremo. Los controladores devolvieron 401 sin sesión en la comprobación local anterior de `/api/erp-web/v1/catalogos`; no se ejecutó una batería de autorización de cada recurso en esta fase.
