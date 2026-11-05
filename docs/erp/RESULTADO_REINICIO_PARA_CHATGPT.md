# Resultado del reinicio limpio del ERP

Fecha: 4 de noviembre de 2026 (Lima). Rama de trabajo: `rebuild/erp-clean-20261104`. Sin commit, push ni fusión.

## Resumen ejecutivo

Se retiró del árbol activo el ERP de negocio anterior y se creó una base técnica nueva. El ERP local arranca y su Web navega entre cinco módulos marcados **Pendiente de implementación**. No se implementaron matrículas, personas, oferta, asignaciones ni documentos. El estado anterior es recuperable en Git y en un respaldo de la base ERP original.

## Estado anterior y recuperación

- Punto Git anterior: tag local `backup/erp-before-restart-20261104` en `7b6589ac92e90aebc51fa39ebabc653c8045cb76`. La rama de origen estaba limpia antes de iniciar.
- Código previo: incluía `ErpV1Service`, `Institucional`, controladores `/api/erp` y `/api/erp/v1`, Tesorería, fachadas, pantallas de matrícula y catálogos. Este código ya no se usa en la rama actual; se puede consultar en el tag.
- Base previa: `erp_boston` conserva 30 tablas en el volumen PostgreSQL original. Dump local fuera del repositorio: `C:\Valana-Pruebas\Aula Virtual Boston\erp-backups\erp_boston_before_rebuild_20261104.dump`, SHA-256 `DC7573B5095B2F75C9D9FADC95FAD8DF9080B83BF28676D286646F29C595CFF1`. Se restauró con éxito en `erp_verificacion_20261104`, que también tiene 30 tablas. El archivo y las bases originales no se suben a Git.

## Estructura final activa

```text
apps/erp/
  api/src/main/java/pe/boston/erp/
    ErpApplication.java
    personas/ matriculas/ oferta/ asignaciones/ documentos/
      package-info.java + api/ application/ domain/ infrastructure/
    plataforma/  (seguridad, sesión y límite absoluto)
  api/src/main/resources/db/migration/V1__infraestructura_sesiones.sql
  api/src/test/java/pe/boston/erp/
    arquitectura/ + una carpeta por módulo
  web/src/
    app/router/ app/layouts/ app/providers/
    modules/personas/ modules/matriculas/ modules/oferta/
    modules/asignaciones/ modules/documentos/
    shared/api/ shared/ui/ shared/lib/ main.tsx
docs/erp/
  README.md  POLITICAS_TRANSVERSALES.md
  modulos/<modulo>/{README,ESPECIFICACION,ESTADO}.md
```

Las carpetas aún vacías contienen únicamente `.gitkeep`. `plataforma` no es un sexto módulo funcional. La navegación Web usa estados pendientes y no muestra formularios de negocio ficticios.

## Archivos heredados retirados

Se retiraron los controladores y servicios raíz `ErpV1Service`, `ErpV1Controller`, `Institucional`, `InstitucionalController`, `PersonasController` y `Tesoreria*`; las fachadas y adaptadores antiguos de Personas, Matrículas, Oferta y Asignaciones; las migraciones ERP V1–V11; las pruebas de caracterización del código eliminado; las pantallas y el cliente HTTP ERP Web anteriores; y los documentos ERP contradictorios que antes estaban en `docs/erp`. La lista exacta está en `git diff --name-status backup/erp-before-restart-20261104` y todos los archivos pueden recuperarse desde ese tag.

## Tecnología y base de datos

- Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2, PostgreSQL 18.6, Flyway, Spring Security/OAuth2 y sesión JDBC.
- React 19, TypeScript 7 y Vite 8 para ERP Web; sin librería de diseño adicional.
- Nueva base aislada `erp_boston_nuevo`: Flyway V1 aplicado. Contiene solo `flyway_schema_history`, `spring_session`, `spring_session_attributes` y `oauth2_authorized_client`. No hay tablas institucionales ni datos de negocio.
- API técnica: `GET /api/erp/v1/sesion`. Los endpoints de negocio heredados no existen. `/actuator/health` se sirve desde el API también a través del proxy ERP.

## Cinco módulos y cinco controles

| Módulo | Requisitos | Análisis funcional | Arquitectura | Implementación | Verificación y pruebas |
|---|---|---|---|---|---|
| [Personas](modulos/personas/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Matrículas](modulos/matriculas/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Oferta académica](modulos/oferta/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Asignaciones docentes](modulos/asignaciones/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Documentos](modulos/documentos/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |

La estructura técnica compartida y la prueba de arquitectura están listas, pero eso no equivale a completar la arquitectura de cada módulo de negocio. Los detalles y pendientes se encuentran en cada `ESTADO.md` y `ESPECIFICACION.md`.

## Verificaciones realizadas

| Verificación | Resultado |
|---|---|
| `mvn clean test -q` | Correcto tras limpiar artefactos de las pruebas retiradas. |
| `mvn test -q` con la nueva prueba de arquitectura | 1 prueba, 0 fallos: cinco módulos exactos y límites válidos. |
| `npm run build` en ERP Web | Correcto; Vite emitió advertencias no bloqueantes sobre directivas `use client` de React Router. |
| `docker compose up -d --build api-erp erp-web erp-proxy` | Contenedores ERP iniciados; API y dependencias saludables. |
| `GET http://127.0.0.1:8089/actuator/health` | HTTP 200, `status: UP`. |
| `GET http://127.0.0.1:8089/api/erp/v1/sesion` sin sesión | HTTP 200, `autenticado: false`. |
| Inicio OIDC desde `/oauth2/authorization/boston-erp` | HTTP 302 hacia Boston Identidad con retorno ERP. No se verificó el ingreso completo de un usuario. |
| Base nueva y original | Nueva base: 4 tablas técnicas; base original: 30 tablas. |
| Aula e Identidad | Archivos fuente sin cambios; contenedores saludables. |

## Compatibilidad y límites reales

La integración anterior ERP–Aula no está disponible porque se retiraron sus endpoints de negocio. Aula e Identidad mantienen sus fuentes y bases intactas, pero las funciones de Aula que dependan de respuestas ERP pueden fallar hasta definir e implementar nuevos contratos. Identidad se reinició durante `docker compose up`, sin cambiar su fuente ni su esquema.

ERP Web está accesible en `http://127.0.0.1:8089/`. La navegación entre módulos requiere autenticación institucional. Se comprobó la redirección OIDC, no un inicio de sesión completo. El ERP no debe presentarse como funcional para operaciones escolares todavía.

## Decisiones pendientes y siguiente módulo

Definir y aprobar requisitos, reglas, permisos, modelos de datos, contratos e integraciones por módulo antes de desarrollarlos. **Personas** es el primer módulo propuesto para comenzar porque los demás podrán necesitar su identidad institucional; el orden definitivo depende de los requisitos aprobados. No hay bloqueos técnicos actuales para trabajar en su especificación. La integración con Aula permanece pendiente de un contrato nuevo.
