# Personas

## Responsabilidad y límites

Personas mantiene el registro único de personas físicas, identificación, condiciones institucionales, parentescos, apoderamientos e historial técnico mínimo. No determina matrícula vigente, credenciales, oferta, asignación docente ni expedientes documentales. La condición Estudiante no equivale a una matrícula confirmada.

## Estructura

| Ubicación | Contenido |
|---|---|
| [api](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/api/PersonasController.java) | Endpoints `/api/erp/v1/personas`, DTO y errores HTTP. |
| [application](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasServicio.java) | Siete casos de uso y autorización por recurso. |
| [domain](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/domain/ReglasPersonas.java) | Validación de invariantes de entrada. |
| [infrastructure](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/infrastructure/PersonasRepositorio.java) | Consultas y mutaciones PostgreSQL. |
| [PersonasParaMatriculas.java](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/PersonasParaMatriculas.java) | Contrato público previsto para Matrículas. |
| [V2__personas.sql](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql) | Modelo físico PostgreSQL 18 y restricciones. |
| [ERP Web](../../../../apps/erp/web/src/modules/personas/PersonasModulo.tsx) | Directorio, alta contextual y ficha. |
| [Pruebas](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java) | Esquema, HTTP y rendimiento. |

La infraestructura compartida está en `plataforma` y no contiene reglas del módulo. Spring Modulith comprueba los límites entre los cinco paquetes funcionales. Identidad solo aporta una sesión OIDC; no hay llamadas HTTP a Identidad dentro de transacciones de Personas.

## Ejecución y pruebas locales

Desde `apps/erp/api`, con Java 25, Maven y Docker Desktop:

```powershell
mvn test
mvn '-Dtest=PersonasEsquemaTest,PersonasHttpTest,PersonasRendimientoTest' test
```

Desde `apps/erp/web`, con Node y dependencias instaladas:

```powershell
npm run build
npm test
```

Flyway aplica la migración al arrancar la API contra la base nueva `erp_boston_nuevo`; nunca contra `erp_boston`. Las pruebas crean PostgreSQL 18.6 aislado con Testcontainers. OpenAPI: `/api/erp/v1/openapi`, accesible solo para Dirección/Administración autenticadas. La interfaz: `/personas` en ERP Web.

## Estado y decisiones

El acceso de Coordinación a fichas existentes exige un ámbito que Identidad todavía no emite; el acceso docente a estudiantes exige el contrato futuro de Asignaciones. Se deniegan ambos mientras no haya prueba de alcance. Ver [Especificación](ESPECIFICACION.md), [Estado](ESTADO.md), [Informe de transferencia](../../RESULTADO_PERSONAS_PARA_CHATGPT.md) y [Políticas transversales](../../POLITICAS_TRANSVERSALES.md).
