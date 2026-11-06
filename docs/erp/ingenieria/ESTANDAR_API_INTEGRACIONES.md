# Estándar de API e integraciones

La [política aprobada](../POLITICAS_TRANSVERSALES.md) fija `/api/erp/v1` como único prefijo oficial; rutas antiguas no se restauran por compatibilidad supuesta. `ARQUITECTURA.md` de cada módulo define recursos y esquemas concretos una vez aprobado el caso de uso.

| Aspecto | Regla de diseño | Evidencia esperada |
|---|---|---|
| Recurso y verbo | Sustantivos y rutas estables; `GET` lectura, `POST` creación/acción, `PUT` sustitución idempotente, `PATCH` parcial si se define, `DELETE` solo si la política permite eliminación. | Contrato OpenAPI y prueba HTTP por ruta. |
| Solicitud/respuesta | DTO HTTP propios de `api/`, campos validados, `Location` en altas y códigos coherentes. No transportar entidades SQL ni modelos internos. | Ejemplos y pruebas de serialización/validación. |
| Errores | Esquema controlado, código estable y mensaje seguro; 400 entrada, 401 sesión, 403 facultad, 404 recurso, 409 conflicto. No filtrar PII, SQL ni trazas. | Pruebas de error y no divulgación. |
| Colecciones | Filtros definidos, orden determinista, página/tamaño acotados, total o cursor según contrato; límites probados. | Casos vacío, límite y orden repetible. |
| Evolución | Revisar compatibilidad de clientes; cambio incompatible requiere versionado o migración coordinada. | Diff de OpenAPI y pruebas de consumidor. |

OpenAPI describe lo publicado, no aprueba facultades. Proteger el acceso a la especificación cuando la política lo exige. El controlador valida transporte y delega; autorización por operación, recurso y ámbito se ejecuta en backend. Los contratos Java entre módulos se exponen por raíz pública o `@NamedInterface` y son distintos de DTO HTTP; [arquitectura modular](ESTANDAR_ARQUITECTURA_MODULAR.md) fija sus límites.

Una transacción de Spring solo cubre recursos locales del ERP. Entre módulos locales, el caso de uso propietario coordina mediante interfaces públicas sin ciclos. Entre ERP, Identidad y Aula, definir responsabilidad, identidad de correlación, idempotencia, respuesta ante timeout, reintento y estado compensable; no suponer transacción distribuida ni llamar HTTP dentro de una transacción de base. La integración ERP–Aula de negocio está temporalmente no disponible según [README ERP](../README.md); no declarar contratos activos por historia. Probar contrato, fallo remoto y repetición sin duplicados cuando se reactive.
