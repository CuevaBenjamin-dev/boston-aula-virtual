# Aula Virtual — límite de esta reestructuración

`apps/aula/api` y `apps/aula/web` conservan sus funciones y contratos. En este trabajo sólo se actualizaron rutas de Compose y el backend a Java 25 / Spring Boot 4.1.2; no se rediseñó el Aula. Su API mantiene la base `aula_boston`, las 25 migraciones Flyway y el almacenamiento S3 local. La [documentación histórica de Aula](../historico/mapa-funcional-y-casos-pendientes.md) aporta contexto; verificarla contra el código antes de usarla como requisito vigente.
