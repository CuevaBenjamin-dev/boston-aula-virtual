# ERP: trazabilidad de requisitos, implementación y verificación

**Corte 2026-11-04.** Requisitos canónicos en [REQUISITOS_SRS.md](REQUISITOS_SRS.md); reglas en [REGLAS_INSTITUCIONALES.md](REGLAS_INSTITUCIONALES.md); pruebas faltantes en [INVENTARIO_PRUEBAS_PENDIENTES.md](INVENTARIO_PRUEBAS_PENDIENTES.md). «Parcial» no equivale a prueba de extremo a extremo.

| Requisito / decisión | Código o API principal | Datos | Prueba ejecutada en esta fase | Vacío verificable |
|---|---|---|---|---|
| RF-01, RI-06 | `PersonasController`, `PersonasService.crear/editar`, `ErpV1Service.confirmar` | `personas`, `estudiantes`, `matriculas` | C01 alta, edición y duplicado; C05–C06 parciales en PostgreSQL | HTTP, concurrencia y autorización por recurso. |
| RF-02 | `PersonasService`, `ErpV1Service.verificarEstudiante` | `personas`, `apoderados`, `vinculos_estudiante_apoderado` | C02–C03 parciales | Reloj fijo, historial mixto, snapshot y casos de error HTTP. |
| RF-03, RI-07 | `BorradoresMatricula` | `borradores_matricula`, `matriculas` | C04 con actor ajeno, guardado y finalizar repetido | Race de finalización y contrato MVC. |
| RF-04/05, RI-08 | `MatriculasController`, `ErpV1Service`, `ImportacionMatriculas` | `matriculas`, `historial_matriculas`, `eventos_pendientes` | C05–C07 parciales y C13 previsualización/confirmación/reintento | Estados completos, pago concurrente, contrato y rollback de cada transición/importación. |
| RF-06 | `OfertaController`, `ErpV1Service` | `asignaturas`, `asignaturas_grado`, `secciones`, `ofertas_academicas` | C08 propagación y versión | CRUD completo, estados y restricciones por HTTP. |
| RF-07, RI-01/02 | `AsignacionesController`, `ErpV1Service`, `DirectorioIdentidad` | `docentes`, `asignaciones_docentes`, cuenta remota | C09 lote/rol parcial; se corrigió `List.of(...).contains(null)` | Fallo remoto simulado, idempotencia/reconciliación y baja. |
| RF-08, DA-05 | `Institucional`, `IntegracionV1Controller`, `Sincronizacion` | `eventos_pendientes`, `versiones_eventos` | C11 rollback, secuencia y versión | Snapshot por módulo, HTTP scope y recepción ERP–Aula. |
| RF-09, DA-09 | `documentos/package-info.java` | `referencias_documentales` | Ninguna funcional | Requisitos institucionales y C15. |
| RNF-01, RI-03/04 | `Seguridad`, `AccesoAcademico`, `ErpWebProxy` | Sesión y tokens | Tests existentes de sesión/proxy; C12 de rol parcial | IDOR, CSRF real, suspensión y matriz aprobada. |
| RNF-02 | Paquetes ERP | N/A | `ApplicationModules.verify()` pasa | Propiedad efectiva de tablas y eliminación de servicios raíz. |
| RNF-03, DA-02 | `InstitucionalController`, `IntegracionV1Controller`, controladores V1 | N/A | Compilación ERP/Aula y [comparación MockMvc de cinco lecturas](../../apps/erp/api/src/test/java/pe/boston/erp/IntegracionV1ContratoTest.java) | Comparación HTTP de escrituras, seguridad y retiro seguro de `/api/erp`. |

Evidencia ejecutable: [caracterización](../../apps/erp/api/src/test/java/pe/boston/erp/ContratosErpCaracterizacionTest.java), [arquitectura](../../apps/erp/api/src/test/java/pe/boston/erp/ArquitecturaModularTest.java), [migraciones](../../apps/erp/api/src/test/java/pe/boston/erp/MigracionesPostgresTest.java). La matriz de métodos de Fase 1 permanece en [MATRIZ_RESPONSABILIDADES.md](MATRIZ_RESPONSABILIDADES.md); es inventario del origen, no descripción de propiedad ya lograda.
