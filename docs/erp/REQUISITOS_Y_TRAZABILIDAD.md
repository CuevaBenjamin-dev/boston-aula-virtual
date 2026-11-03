# ERP — alcance, requisitos y trazabilidad

Estado al 2026-11-03. **C** = confirmado en código, **H** = descrito en documentación histórica, **P** = propuesta, **D** = decisión institucional pendiente. Ninguna propuesta constituye una política aprobada.

## Visión de producto (PRD)

El ERP administra el registro institucional de alumnos y responsables, el proceso de matrícula, períodos y oferta de cursos por grado y sección, docentes y sus asignaciones. Identidad autentica usuarios; Aula recibe los cambios académicos por integración. El ERP actual incluye además Tesorería legada, fuera de los cinco módulos de esta reestructuración, que se conserva para evitar pérdida de contratos y datos.

**Usuarios observados en código:** Dirección, Coordinación Académica, administración técnica por permisos específicos y cuentas de servicio. El alcance actual no autoriza convertir Documentos en un proceso de aprobación ni establecer retenciones legales.

## SRS y casos de uso confirmados

| ID | Tipo | Estado | Requisito/caso y aceptación observable | Código/evidencia |
|---|---|---|---|---|
| RF-01 | Funcional | C | Consultar, crear y editar alumno; verificar por documento y recuperar historial; el código institucional se asigna al culminar matrícula | `personas/api/PersonasController.java`, `ErpV1Service.java`, migración V11 |
| RF-02 | Funcional | C | Mantener personas responsables y vínculos con alumnos; vincular exige actor con permiso de escritura | `ErpV1Service.java`, `Institucional.java` |
| RF-03 | Funcional | C | Guardar borrador de matrícula por pasos, consultarlo sólo por su actor y finalizar una vez; el borrador finalizado retorna el ID existente | `matriculas/BorradoresMatricula.java` |
| RF-04 | Funcional | C | Iniciar, reservar, cancelar pendiente, confirmar matrícula y registrar estado del pago externo; el historial registra cambios | `ErpV1Service.java`, migraciones V4/V10/V11 |
| RF-05 | Funcional | C | Gestionar período, nivel, grado, sección, catálogo de asignaturas y asociación asignatura–grado; publicar oferta de sección | `oferta/api/OfertaController.java`, `ErpV1Service.java` |
| RF-06 | Funcional | C | Registrar docente, enlazar cuenta de Identidad, asignar ofertas y retirar asignación | `asignaciones/api/AsignacionesController.java`, `ErpV1Service.java` |
| RF-07 | Integración | C | Publicar cambios en outbox con secuencia y versión para que Aula sincronice | `Institucional.java`, `eventos_pendientes`, `versiones_eventos` |
| RF-08 | Funcional | C parcial | Existe una tabla de referencias documentales vinculable a estudiante o matrícula; no hay todavía flujo HTTP de documentos completo | migración V4, `documentos/package-info.java` |
| RNF-01 | Seguridad | C | JWT para API y OIDC para Web; permisos aplicados por ruta y operación; sesiones ERP con vencimiento de inactividad y absoluto configurables | `Seguridad.java`, `SesionAbsolutaFiltro.java`, `application.yaml` |
| RNF-02 | Arquitectura | C parcial | ERP monolito modular con cinco paquetes y `ApplicationModules.verify()`; persistencia y reglas aún concentradas en clases raíz | `ArquitecturaModularTest.java`, `ErpV1Service.java`, `Institucional.java` |
| RNF-03 | Datos | C | Tres bases lógicas; PostgreSQL, Flyway, UUID y outbox | `compose.yaml`, migraciones |
| RES-01 | Restricción | C | No desplegar ni publicar; conservar datos, volúmenes, endpoints y migraciones | `compose.yaml`, prompt de reestructuración |

## Casos específicos y criterios de aceptación

- **Alumno existente:** consulta por documento devuelve identidad e historial antes de elegirla. Si ya tiene código, el flujo de borrador exige usar estudiante registrado. Evidencia: `BorradoresMatricula.crearORecuperarSolicitante` y `ErpV1Service.verificarEstudiante`.
- **Matrícula cancelada:** se conserva el historial; un proceso pendiente se puede cancelar por operación dedicada. Evidencia: V10 y `ErpV1Service.cancelar`.
- **Borrador ajeno:** lectura y edición filtran por `actor`; un UUID de otro operador no debe revelar datos. Evidencia: `BorradoresMatricula.ver` y `bloquear`. Falta prueba automatizada con PostgreSQL.
- **Pago externo:** se registra medio y monto positivo sin pasarela de pagos. Evidencia: V11 y `registrarPagoExterno`. No confundir con Tesorería legada.
- **Asignación docente:** asociación a oferta concreta de sección, no sólo al nombre de asignatura; se conserva estado y fecha. Evidencia: `asignaciones_docentes` y controlador.
- **Fallo de sincronización:** ERP conserva el evento en outbox; Aula debe reanudar desde secuencia. La recuperación completa de fallos parciales requiere prueba de integración automatizada y revisión de límites de reintento.

## Matriz de trazabilidad

| Requisito | Pantalla/API | Datos | Prueba actual | Pendiente |
|---|---|---|---|---|
| RF-01/02 | Personas, `/api/erp/v1/estudiantes`, `/personas` | `personas`, `estudiantes`, `apoderados`, `vinculos_estudiante_apoderado` | compilación y arquitectura | controlador, autorización por recurso, PostgreSQL |
| RF-03/04 | Matrículas, `/api/erp/v1/matriculas/**` | `borradores_matricula`, `matriculas`, `historial_matriculas` | compilación, arquitectura y migraciones V1–V11 en Testcontainers; scripts legados no ejecutados en esta fase | regresión de flujo y permisos por recurso |
| RF-05 | Oferta, `/api/erp/v1/asignaturas/**` | `periodos_academicos`, `grados`, `secciones`, `asignaturas`, `asignaturas_grado`, `ofertas_academicas` | compilación y arquitectura | integración SQL y permisos |
| RF-06 | Docentes, `/api/erp/v1/docentes/**` | `docentes`, `asignaciones_docentes` | compilación y arquitectura | contratos con Identidad y recurso |
| RF-07 | `/api/erp/eventos`, sincronizador Aula | `eventos_pendientes`, `versiones_eventos` | migraciones restauradas en copia; arranque local verificado | fallo/reintento end-to-end |
| RF-08 | Sin pantalla nueva | `referencias_documentales` | inventario de migración | requisitos y diseño aprobado |

## Decisiones abiertas y límites

**D:** documentos exigibles, tipos, visibilidad, estados, versiones y retención; duración de sesión aprobada y política productiva; permisos detallados por recurso/alumno; recuperación operacional de pagos; reglas institucionales de importación. **P:** ampliar pruebas Testcontainers y contrato OpenAPI generado desde tipos, en lugar de mantener esquemas manuales. El hecho de aparecer aquí no los convierte en entregas implementadas.

Documentación histórica útil: [matrículas](../historico/erp-matriculas-v1.md), [asignaturas](../historico/logica-asignaturas-erp.md), [integración](../historico/integracion-erp-aula.md), [método E³](../historico/metodo-e3.md). Contrastarla con el código antes de modificar reglas.
