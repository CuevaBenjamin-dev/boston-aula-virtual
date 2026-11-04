# ERP: reglas institucionales

**Corte 2026-11-04.** Se distingue entre instrucción aprobada del encargo, comportamiento observado y política pendiente. [SRS](REQUISITOS_SRS.md) enlaza los requisitos; [decisiones](DECISIONES_ARQUITECTURA.md) registra las propuestas técnicas.

| ID | Estado | Regla | Evidencia |
|---|---|---|---|
| RI-01 | Aprobada en encargo | El docente puede existir sin cuenta de acceso. | [ErpV1Service, alta de docente](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java). |
| RI-02 | Aprobada en encargo | Coordinación Académica, Dirección y Administración pueden **solicitar** cuentas dentro de sus facultades. | Encargo Fase 2; la implementación actual de [DirectorioIdentidad](../../apps/erp/api/src/main/java/pe/boston/erp/plataforma/DirectorioIdentidad.java) aún sólo permite Dirección en el alta docente. Solicitar no equivale a conceder permisos. |
| RI-03 | Aprobada en encargo | Sólo Administración y Dirección pueden modificar permisos. | Encargo Fase 2; no existe todavía matriz dinámica aplicada. |
| RI-04 | Aprobada en encargo | Una cuenta suspendida no accede y conserva sus datos. | Encargo Fase 2; requiere contrato y verificación con Identidad antes de implementarse en ERP. |
| RI-05 | Propuesta, no política activa | Estados de cuenta: Sin cuenta, Pendiente, Activa, Suspendida, Error de aprovisionamiento. | Encargo Fase 2 indica explícitamente «propuestos». |
| RI-06 | Comportamiento vigente | El código institucional de alumno se asigna al confirmar matrícula. | [ErpV1Service.confirmar](../../apps/erp/api/src/main/java/pe/boston/erp/ErpV1Service.java), [Migración V11](../../apps/erp/api/src/main/resources/db/migration/V11__borradores_y_pago_externo_matricula.sql). |
| RI-07 | Comportamiento vigente | El borrador es privado del operador y finalizarlo repetidamente devuelve la misma matrícula. | [BorradoresMatricula](../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/BorradoresMatricula.java), prueba C04. |
| RI-08 | Comportamiento vigente | El pago externo guarda medio y monto, sin pasarela. | `ErpV1Service.registrarPagoExterno`, prueba C07 parcial. |
| RI-09 | Comportamiento vigente | La asignación docente se hace sobre una oferta concreta, por sección. | `ErpV1Service.asignarDocente`, tabla `asignaciones_docentes` en migraciones. |

## Validación institucional pendiente

| ID | Pregunta por resolver | Consecuencia técnica |
|---|---|---|
| PI-01 | Facultades exactas para solicitar, aprobar, suspender y reactivar cuentas de cada tipo. | No activar concesiones de permisos ni automatizar estados propuestos. |
| PI-02 | Permisos efectivos por rol, persona, recurso, ámbito, herencia y denegación explícita. | [MATRIZ_PERMISOS.md](MATRIZ_PERMISOS.md) queda como especificación UX, no motor de autorización. |
| PI-03 | Documentos exigibles, clasificación, acceso y retención. | Módulo Documentos permanece estructural. |
| PI-04 | Política de sesiones productivas, revocación y plazos de conciliación de Identidad. | Los valores locales son configuración operativa, no norma institucional. |
| PI-05 | Variantes de matrícula, reglas de pago/importación y autorización por alumno o sección. | No cambiar los criterios actualmente codificados sin aprobación y pruebas de contrato. |

Las fuentes históricas se conservan en [docs/historico](../historico/); sirven de contexto y requieren confirmación antes de convertirse en reglas activas.
