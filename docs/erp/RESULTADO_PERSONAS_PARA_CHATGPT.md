# Resultado de implementación — Personas

Fecha: 5 de noviembre de 2026. Alcance: únicamente Personas del ERP nuevo. Este informe distingue implementación verificada de decisiones institucionales aún abiertas.

## 1. Punto de partida y protección de datos

- Rama inicial `main`, commit `da008cf`; el código de Personas no estaba implementado.
- Base de trabajo `erp_boston_nuevo`, con V1 de infraestructura. Antes de aplicar cambios se creó respaldo local fuera del repositorio: `C:\Valana-Pruebas\Aula Virtual Boston\erp-backups\erp_boston_nuevo_before_personas_20261105.dump`.
- La base original `erp_boston`, sus respaldos, Aula e Identidad no se modificaron. No hubo commit ni push.
- Flyway V2 se ejecutó en PostgreSQL 18.6 aislado mediante Testcontainers y en la base local nueva a través de Docker Compose. La verificación local devolvió versiones V1 y V2 y ocho tablas `per_*`.

## 2. Implementación y estructura

| Capa | Ubicación | Resultado |
|---|---|---|
| PostgreSQL | [`V2__personas.sql`](../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql) | Ocho tablas, restricciones temporales y diferidas, índices focalizados. |
| Dominio | [`ReglasPersonas.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/domain/ReglasPersonas.java) | Identidad, contacto, DNI y condición inicial. |
| Aplicación | [`PersonasServicio.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasServicio.java) | Siete CU transaccionales y trazabilidad. |
| Seguridad | [`AutorizacionPersonas.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/AutorizacionPersonas.java) | Rol OIDC, recurso y denegación de ámbitos no comprobables. |
| Infraestructura | [`PersonasRepositorio.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/infrastructure/PersonasRepositorio.java) | SQL y consultas paginadas. |
| HTTP | [`PersonasController.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasController.java), [`PersonaDto.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonaDto.java), [`PersonasErrores.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasErrores.java) | API v1, DTO tipados, errores controlados y OpenAPI. |
| Contrato futuro | [`PersonasParaMatriculas.java`](../../apps/erp/api/src/main/java/pe/boston/erp/personas/PersonasParaMatriculas.java) | Interfaz para Matrículas sin dependencias inversas ni implementación de Matrículas. |
| Web | [`PersonasModulo.tsx`](../../apps/erp/web/src/modules/personas/PersonasModulo.tsx), [`api.ts`](../../apps/erp/web/src/modules/personas/api.ts) | Directorio, registro y ficha con selector de personas. |

La [especificación](modulos/personas/ESPECIFICACION.md) conserva RI-PER-001 a RI-PER-034 y añade RI-PER-035 a 037 sin reutilizar identificadores. El [estado](modulos/personas/ESTADO.md) no marca ninguna verificación como Completada.

## 3. Modelo físico PostgreSQL

`per_personas` es la fuente única de identidad UUID. `per_tipos_documento` contiene DNI, CE y PASAPORTE; `per_documentos` guarda el historial y el principal. `per_identificaciones_provisionales` conserva el sustento de estudiantes sin documento hasta su regularización. `per_condiciones` tiene períodos independientes de Estudiante, Docente y Personal institucional. La condición Apoderado se deriva de `per_apoderamientos`, sin un segundo estado persistente. `per_parentescos` expresa relación familiar separada. `per_eventos` guarda solo actor, acción, recurso y fecha.

Los rangos temporales son `[inicio, fin)` con columnas generadas `TSTZRANGE`. `WITHOUT OVERLAPS` evita documentos duplicados y períodos incompatibles. La exclusión GiST y el índice único parcial impiden dos documentos principales vigentes. Índices adicionales sirven la búsqueda exacta de documento, nombre mediante `pg_trgm`, condiciones vigentes y ambos sentidos del apoderamiento. Triggers diferidos comprueban integridad entre tablas al commit: persona con condición e identificación, contacto exigido, principal documental y vínculo con estudiante/parentesco válido. Las escrituras sensibles bloquean filas relevantes y usan transacciones.

La normalización documental recorta únicamente espacios exteriores; conserva guiones, letras y espacios internos. DNI peruano exige ocho dígitos. CE, pasaporte y tipos administrativamente ampliados pueden usar patrón configurable. No se fusionan personas por similitud de nombres ni se inventan DNI provisionales. Se prohíbe la eliminación física ordinaria desde la API.

## 4. Casos de uso

| Caso | Implementado | Evidencia |
|---|---|---|
| CU-PER-01 Registrar | Alta atómica con documento o provisional; apoderado inicial exige vínculo. | HTTP y esquema. |
| CU-PER-02 Buscar | Texto, documento, condición, vigencia y paginación. | HTTP, esquema, React y carga. |
| CU-PER-03 Editar | Datos comunes/contacto; documento histórico y regularización sin cambiar UUID. | HTTP y esquema. |
| CU-PER-04 Incorporar condición | Datos adicionales, duplicado activo y reactivación histórica. | HTTP y esquema. |
| CU-PER-05 Vincular | Parentescos, varios apoderados y autorización sin parentesco. | HTTP y esquema. |
| CU-PER-06 Ficha | Datos y períodos históricos, identificación y vínculos. | HTTP y React. |
| CU-PER-07 Desactivar/reactivar | Cierre motivado, período nuevo y vínculos independientes. | HTTP y esquema. |

## 5. API, permisos y errores

Todos los endpoints nuevos usan `/api/erp/v1/personas`. Las rutas de lectura son `GET /`, `GET /{id}`, `GET /capacidades`, `GET /tipos-documento`, `GET /duplicado-documental`; las escrituras son `POST /`, `PUT /{id}`, `PUT /{id}/contacto`, `POST /{id}/documentos`, `POST /{id}/condiciones`, `POST /{id}/condiciones/{condicion}/desactivar`, `POST /parentescos`, `POST /{id}/parentescos/{vinculo}/desactivar`, `POST /apoderamientos`, `POST /{id}/apoderamientos/{vinculo}/desactivar` y `POST /tipos-documento`. DTO y campos se documentan en la [especificación](modulos/personas/ESPECIFICACION.md#7-contratos-seguridad-e-interfaz). OpenAPI tipado está en `/api/erp/v1/openapi`, protegido para Dirección/Administración.

Errores controlados: 400 datos/formato, 401 ausencia de sesión, 403 facultad insuficiente, 404 recurso inexistente, 409 conflicto. La seguridad web conserva denegación por defecto y exige CSRF para escrituras. Dirección y Administración tienen gestión; Coordinación puede registrar, pero la consulta/edición de recursos existentes exige un ámbito verificable que Identidad todavía no emite. Docente puede consultar/editar su contacto propio si el UUID coincide; el acceso a alumnos asignados queda cerrado hasta la implementación del contrato de Asignaciones. Estudiantes y apoderados no acceden al ERP. Ninguna condición institucional concede por sí sola privilegios de sesión.

## 6. Interfaz ERP

Tres vistas: directorio con búsqueda/filtros/paginación; registro con condición contextual, verificación de documento e identificación provisional; ficha con datos, documentos, condiciones, parentescos, apoderamientos y acciones permitidas. Los vínculos seleccionan personas por nombre o documento; no exigen escribir UUID. La interfaz conserva datos del formulario tras errores y muestra estados de carga, vacío, éxito y error. El catálogo de tipos documentales se lee también en el alta permitida a Coordinación. El Docente con referencia personal verificable recibe un enlace directo a su ficha, sin acceso al directorio.

## 7. Pruebas y mediciones

- `mvn -q test`: 18 pruebas aprobadas: 1 de límites Spring Modulith, 11 PostgreSQL con Testcontainers, 3 HTTP/seguridad, 1 rendimiento y 2 de dominio. V1 y V2 migraron desde un esquema vacío en PostgreSQL 18.6.
- `npm test`: 5 pruebas React aprobadas: restricción por ámbito, acceso propio del Docente, búsqueda/paginación, campos contextuales y revisión automatizada de etiquetas/atributos ARIA seleccionados.
- `npm run build`: TypeScript y Vite compilan. `git diff --check` sin errores.
- PostgreSQL local: `flyway_schema_history` muestra V1 y V2, con ocho tablas `per_*`; `GET http://127.0.0.1:8089/personas` y `/actuator/health` respondieron 200. Las pruebas HTTP usaron MockMvc y sesión OIDC controlada, no un recorrido manual autenticado de navegador.

**Carga medida:** Docker Desktop local (PostgreSQL 18.6, 30 000 personas sintéticas, 30 clientes JDBC concurrentes); 300 búsquedas p95 **283 ms** frente a objetivo ≤ 2 s; 30 guardados p95 **214 ms** frente a ≤ 3 s. `EXPLAIN (ANALYZE, BUFFERS)` de nombre: `Seq Scan`, 15,829 ms y 492 buffers compartidos en la última suite; documento exacto: `Index Scan` por restricción temporal, 0,082 ms. El plan de nombre debe reevaluarse con datos representativos de producción; el conjunto sintético y la consulta medida no justifican por sí solos un índice adicional. Es una medición de base/JDBC; no certifica latencia HTTP, red o navegador. El escenario sintético evita repetir cargas reales de usuarios y no contiene datos personales.

## 8. Estado de verificaciones y pendientes

| Verificación | Estado | Razón abierta |
|---|---|
| Requisitos | En progreso | Catálogos, autorizaciones y protección operativa pendientes. |
| Análisis funcional | En progreso | Ámbito efectivo de Coordinación y alumnos asignados a Docentes. |
| Arquitectura | En progreso | Aprobación del modelo físico y contrato de ámbito. |
| Implementación | En progreso | Acceso de roles con ámbito no disponible y prueba de navegador autenticado. |
| Verificación y pruebas | En progreso | Auditoría WCAG 2.2 AA completa y carga HTTP extremo a extremo. |

La autorización no familiar registra quién autorizó, verificador, medio, momento, tipo y referencia del sustento; no hay aprobación adicional. La institución debe definir cómo se comprueba que el autorizador está facultado, qué otros sustentos son válidos y la representación legal por trámite. La prueba automatizada de accesibilidad cubre un subconjunto de reglas; no equivale a conformidad WCAG 2.2 AA.

## 9. Repetición local

```powershell
cd apps/erp/api
mvn test
cd ../web
npm test
npm run build
cd ../../..
docker compose up -d --build --no-deps api-erp erp-web erp-proxy
docker exec boston-aula-postgres-1 psql -U boston -d erp_boston_nuevo -Atc "SELECT version,description FROM flyway_schema_history ORDER BY installed_rank"
```

No ejecutar el proceso contra `erp_boston`. No hay despliegue externo, commit ni push asociado a esta entrega.
