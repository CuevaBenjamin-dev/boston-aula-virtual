# Modelo de datos — Matrículas

**Estado: sin modelo conceptual/lógico/físico aprobado ni tablas de negocio.** Identificar entidad matrícula, relación con persona y oferta, vigencias, unicidad por período y reglas de concurrencia solo tras aprobar el proceso. No existen tablas de negocio.

Antes de SQL, documentar entidades y cardinalidades, propietario de cada dato, identificadores, vigencias e invariantes; después claves, restricciones, índices, migración Flyway nueva, concurrencia y consultas. Aplicar [estándar PostgreSQL](../../ingenieria/ESTANDAR_POSTGRESQL.md) de forma proporcional. No modificar [V1 de plataforma](../../../../apps/erp/api/src/main/resources/db/migration/V1__infraestructura_sesiones.sql) ni [V2 de Personas](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql) para introducir este módulo.
