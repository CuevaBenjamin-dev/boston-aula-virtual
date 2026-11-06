# Arquitectura — Oferta académica

**Estado: diseño pendiente.** [Especificación](ESPECIFICACION.md) aún no contiene requisitos aprobados. Diseñar contratos de consulta y cambios para módulos consumidores sin exponer repositorios; no hay API ni vista de negocio.

Los paquetes [api, application, domain e infrastructure](../../../../apps/erp/api/src/main/java/pe/boston/erp/oferta/package-info.java) son estructura preparada, no prueba de caso de uso. Una eventual interfaz pública se expondrá solo con contrato aprobado y dependencia sin ciclos. La API futura debe respetar [estándar API](../../ingenieria/ESTANDAR_API_INTEGRACIONES.md); capas y `@NamedInterface` se rigen por [arquitectura modular](../../ingenieria/ESTANDAR_ARQUITECTURA_MODULAR.md).

**Seguridad/UX/transacciones pendientes:** Aprobar quién crea/modifica oferta, cuándo se activa y cómo impacta matrículas y asignaciones existentes. El servidor deberá comprobar operación, recurso y ámbito; el frontend deberá mostrar estados y conservar datos cuando corresponda. No hay decisión de ruta, DTO, pantalla o transacción concreta que pueda declararse aprobada. Antes de construir, diseñar y registrar en este documento componentes, permisos técnicos, contrato y límites transaccionales.
