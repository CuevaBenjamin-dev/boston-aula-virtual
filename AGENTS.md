# Sistema BBS

- Tres servicios: `services/identidad`, `apps/erp/api` y `apps/aula/api`. Interfaces: `apps/erp/web` y `apps/aula/web`. Infraestructura en `infra`, documentación vigente en `docs` e historia inmutable en `docs/historico`.
- Java 25 y Maven para los backends; React, TypeScript y Vite para las interfaces. `docker compose config --quiet` valida la configuración; `docker compose up -d --build` inicia el entorno local.
- Pruebas focalizadas: `mvn test` desde el backend afectado; `npm ci && npm run build` desde el frontend afectado. En ERP, ejecutar siempre `ArquitecturaModularTest` o la suite completa tras cambiar dependencias entre módulos.
- Conserva rutas HTTP, UUID institucionales, migraciones Flyway aplicadas, contratos OIDC y outbox ERP–Aula. Una integración entre servicios no comparte transacción ni base de datos.
- Las credenciales de `.env` y `cuentas-iniciales.local.txt` son locales e ignoradas por Git. Nunca copiarlas a documentación, pruebas, logs o commits.
- Antes de cambios de esquema o de contenedores, validar copias y volúmenes. No borrar bases, archivos, volúmenes ni historial sin autorización explícita. No usar `docker compose down -v`.
- Distingue estado confirmado por código, documentación histórica, propuesta y decisión institucional pendiente. No modificar el contenido de `docs/historico/ESTADO_MAESTRO_BOSTON.md`.
- Para tareas ERP, comienza en `docs/erp/ingenieria/README.md` y consulta selectivamente el [manual](docs/erp/ingenieria/MANUAL_DESARROLLO_MODULAR.md) y el índice del módulo. Las cinco verificaciones obligatorias son Requisitos, Análisis funcional, Arquitectura, Implementación y Verificación y pruebas. No inventes reglas institucionales; distingue aprobación, propuesta e implementación y reporta resultados, fallos y bloqueos reales.
