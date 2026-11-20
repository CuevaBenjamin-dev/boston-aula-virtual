# Aula Virtual — estado local

La implementación de Aula conserva su API y Web independientes, la base `aula_boston` y las migraciones Flyway V1–V25. Identidad autentica las cuentas y el ERP es la fuente de verdad institucional. El informe verificable de este corte está en [Aula 01](INCREMENTO_AULA_01_2026-11-20.md).

## Organización actual

- API: `pe.boston.aula.integraciones` contiene el cliente ERP heredado y su consumidor de eventos. Las demás clases de plataforma y funciones académicas permanecen en `pe.boston.aula` y se extraerán gradualmente.
- Web: `src/app/navegacion.ts` contiene los menús por perfil, títulos y el inventario de rutas. Las páginas, `api.ts` y `style.scss` conservan su estructura y comportamiento.
- CI: Java ejecuta `mvn verify`; Aula Web ejecuta `npm ci`, `npm run test` y `npm run build` con Node 24.

## Diseño objetivo y deuda

El objetivo gradual para la API es `plataforma`, `integraciones`, `academico`, `actividades`, `recursos`, `clases`, `comunicacion` y `perfil`; para Web es `app`, `features`, `shared` y `styles`. Son paquetes internos, no servicios o bases nuevos. Solo `integraciones` y `app/navegacion.ts` se extrajeron en Aula 01.

La sincronización heredada consulta `/api/erp/v1/eventos`, ruta ausente en el ERP nuevo revisado. Las cuentas y cursos solicitados por el ERP siguen pendientes de un contrato operativo. También falta la lectura histórica del estudiante con matrícula finalizada y la conciliación de administración heredada. Véase el informe Aula 01 para riesgos, límites y siguientes incrementos. La [documentación histórica de Aula](../historico/mapa-funcional-y-casos-pendientes.md) aporta contexto, pero debe contrastarse con el código vigente.
