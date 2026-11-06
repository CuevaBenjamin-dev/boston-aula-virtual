# Arquitectura — Documentos

**Estado: diseño pendiente.** [Especificación](ESPECIFICACION.md) aún no contiene requisitos aprobados. Diseñar contrato de referencia y autorización, sin acoplar otros módulos a detalles de almacenamiento. No hay API ni vista de negocio.

Los paquetes [api, application, domain e infrastructure](../../../../apps/erp/api/src/main/java/pe/boston/erp/documentos/package-info.java) son estructura preparada, no prueba de caso de uso. Una eventual interfaz pública se expondrá solo con contrato aprobado y dependencia sin ciclos. La API futura debe respetar [estándar API](../../ingenieria/ESTANDAR_API_INTEGRACIONES.md); capas y `@NamedInterface` se rigen por [arquitectura modular](../../ingenieria/ESTANDAR_ARQUITECTURA_MODULAR.md).

**Seguridad/UX/transacciones pendientes:** Aprobar quién carga, consulta, sustituye o invalida documentos y qué sustento exige cada caso. El servidor deberá comprobar operación, recurso y ámbito; el frontend deberá mostrar estados y conservar datos cuando corresponda. No hay decisión de ruta, DTO, pantalla o transacción concreta que pueda declararse aprobada. Antes de construir, diseñar y registrar en este documento componentes, permisos técnicos, contrato y límites transaccionales.
