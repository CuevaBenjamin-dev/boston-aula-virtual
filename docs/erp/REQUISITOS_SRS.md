# ERP: requisitos verificables (SRS)

**Corte 2026-11-04.** Esta es la referencia actual de requisitos observables. «Implementado» describe código existente, no aprobación institucional. Las reglas aprobadas y abiertas se separan en [REGLAS_INSTITUCIONALES.md](REGLAS_INSTITUCIONALES.md). [REQUISITOS_Y_TRAZABILIDAD.md](REQUISITOS_Y_TRAZABILIDAD.md) queda como foto del 2026-11-03.

| ID | Módulo | Requisito y criterio observable | Estado | Evidencia / prueba |
|---|---|---|---|---|
| RF-01 | Personas | Alta/edición y búsqueda de alumno; documento único; código nulo antes de matrícula confirmada. | Alta/edición extraídas; búsqueda en raíz | [PersonasService](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasService.java), [ErpV1Service](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java), C01 parcial. |
| RF-02 | Personas | Verificar por documento/UUID e informar historial y responsable; mantener personas y vínculos. | Implementado, extracción parcial | [PersonasService](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasService.java), C02–C03 parciales. |
| RF-03 | Matrículas | Borrador 1–4, privado por operador, reanudable y finalización idempotente. | Implementado | [BorradoresMatricula](../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/BorradoresMatricula.java), C04 SQL. |
| RF-04 | Matrículas | Iniciar, reservar, cancelar, editar y confirmar; asignar código al confirmar y registrar historial. | Implementado en raíz | [ErpV1Service](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java), C05–C06 parciales. |
| RF-05 | Matrículas | Registrar pago externo con medio y monto positivo, vinculado a matrícula confirmada. | Implementado en raíz | `ErpV1Service.registrarPagoExterno`, C07 parcial. |
| RF-06 | Oferta | Administrar periodos, niveles, grados, secciones, asignaturas y malla; propagar ofertas. | Implementado en raíz | [OfertaController](../../apps/erp/api/src/main/java/pe/boston/erp/oferta/api/OfertaController.java), C08 parcial. |
| RF-07 | Asignaciones | Alta/edición de docente, vinculación única a Identidad y asignación/retiro de ofertas. | Implementado parcialmente; recuperación HTTP pendiente | [AsignacionesController](../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/api/AsignacionesController.java), C09 parcial. |
| RF-08 | Integración | Publicar eventos con secuencia, versión y snapshot; Aula consume y reanuda por secuencia. | Implementado, extracción pendiente | [Institucional](../../apps/erp/api/src/main/java/pe/boston/erp/Institucional.java), [Sincronizacion](../../apps/aula/api/src/main/java/pe/boston/aula/Sincronizacion.java), C11 parcial. |
| RF-09 | Documentos | Gestionar referencias y flujos documentales. | Bloqueado: requisitos funcionales sin aprobar | [tabla V4](../../apps/erp/api/src/main/resources/db/migration/V4__erp_matriculas_v1.sql), [MAPA_MODULOS](MAPA_MODULOS.md). |
| RNF-01 | Seguridad | JWT API, OIDC Web, sesión aislada ERP y autorización por operación. | Implementado parcialmente; IDOR y scopes por ruta requieren prueba | [Seguridad](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/Seguridad.java), C12 pendiente. |
| RNF-02 | Arquitectura | Cinco módulos funcionales sin ciclo y contratos públicos explícitos. | En progreso | [ArquitecturaModularTest](../../apps/erp/api/src/test/java/pe/boston/erp/ArquitecturaModularTest.java), [MAPA_MODULOS](MAPA_MODULOS.md). |
| RNF-03 | Compatibilidad | API oficial `/api/erp/v1`, preservar rutas antiguas hasta probar consumidores y contrato. | En progreso | [CONTRATOS_API](CONTRATOS_API.md), C10/C14 pendientes. |

**Restricción de esta fase:** no modificar datos reales, migraciones históricas, volúmenes ni reglas sin aprobación. La implementación se valida en PostgreSQL desechable. La cobertura restante se especifica en [INVENTARIO_PRUEBAS_PENDIENTES.md](INVENTARIO_PRUEBAS_PENDIENTES.md).
