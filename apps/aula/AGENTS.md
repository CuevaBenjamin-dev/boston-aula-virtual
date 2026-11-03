# Aula Virtual

- Aula es un servicio independiente, con base `aula_boston`, API y Web propios. ERP es propietario del dato institucional y lo sincroniza mediante outbox.
- Durante tareas exclusivamente ERP no modificar lógica, endpoints, migraciones ni interfaz de Aula. Las actualizaciones transversales de Java o rutas requieren pruebas de regresión de Aula.
- API: `cd apps/aula/api && mvn test`. Web: `cd apps/aula/web && npm ci && npm run build`. Consultar `../../docs/historico` para contratos históricos.
