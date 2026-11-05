# ERP institucional — base limpia

El ERP se reconstruye como monolito modular. La estructura técnica está en preparación; los casos de uso se implementarán progresivamente tras aprobar sus requisitos. Ningún módulo de negocio está operativo todavía.

## Tablero de módulos

| Módulo | Requisitos | Análisis funcional | Arquitectura | Implementación | Verificación y pruebas |
|---|---|---|---|---|---|
| [Personas](modulos/personas/README.md) | [En progreso](modulos/personas/ESTADO.md) | En progreso | En progreso | Pendiente | Pendiente |
| [Matrículas](modulos/matriculas/README.md) | [Pendiente](modulos/matriculas/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente |
| [Oferta académica](modulos/oferta/README.md) | [Pendiente](modulos/oferta/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente |
| [Asignaciones docentes](modulos/asignaciones/README.md) | [Pendiente](modulos/asignaciones/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente |
| [Documentos](modulos/documentos/README.md) | [Pendiente](modulos/documentos/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente |

Cada fila enlaza a su documentación y a la tabla de cinco verificaciones. [Políticas transversales](POLITICAS_TRANSVERSALES.md).

## Estructura y convenciones

- Backend: Java 25, Spring Boot 4.1.2, Spring Modulith, PostgreSQL y Flyway. Cinco paquetes funcionales anotados; `plataforma` es soporte técnico y no un módulo funcional.
- Web: React, TypeScript y Vite. `app` contiene rutas y layout; `modules` contiene futuras vistas por dominio; `shared` contiene piezas transversales de interfaz, API y constantes.
- API: solo `/api/erp/v1`; actualmente responde a `GET /api/erp/v1/sesion` y a `/actuator/health` como soporte técnico.
- Base de desarrollo: `erp_boston_nuevo`, aislada de la base ERP original. La migración V1 crea solo tablas de sesión y cliente OAuth2.
- Pruebas: `cd apps/erp/api; mvn clean test` y `cd apps/erp/web; npm run build`. Para un módulo futuro: `mvn -Dtest=NombreTest test`.

## Recuperación

El estado anterior está en el tag local `backup/erp-before-restart-20261104` (commit `7b6589ac92e90aebc51fa39ebabc653c8045cb76`). La base original `erp_boston` y su volumen permanecen intactos. Se creó un dump fuera del repositorio en `C:\Valana-Pruebas\Aula Virtual Boston\erp-backups\erp_boston_before_rebuild_20261104.dump`, restaurado de prueba en `erp_verificacion_20261104` (30 tablas en origen y restauración). El dump no se debe subir a Git.

La integración de negocio con Aula puede fallar mientras se reconstruyen los módulos. Esta condición es temporal y explícita.
