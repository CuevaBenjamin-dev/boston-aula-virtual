# Operación local y recuperación

## Instalar y arrancar

Requisitos: Docker Desktop con Compose, Git; para pruebas fuera de Docker, JDK 25, Maven 3.9+ y Node compatible con Vite 8. Trabajar en la raíz `sistema-bbs`. Crear `.env` desde `.env.example` y mantenerlo fuera de Git. El montaje de cuentas iniciales usa `cuentas-iniciales.local.txt` ignorado. No copiar secretos a tickets o documentación.

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Accesos locales: ERP `http://127.0.0.1:8089`, Aula `http://localhost:8088`, Identidad `http://localhost:9000`, S3 `http://127.0.0.1:8333`. El proyecto Compose es `boston-aula`, aunque la carpeta se llama `sistema-bbs`.

## Verificación

```powershell
docker compose ps
docker compose logs --tail=100 api-erp api-aula identidad
docker compose exec -T api-erp java -version
docker compose exec -T api-aula java -version
docker compose exec -T identidad java -version
docker compose exec -T postgres pg_isready -U boston -d postgres
```

Comprobar además HTTP 200 del ERP y Aula, y `/actuator/health` de Identidad. El S3 local exige autenticación; un GET anónimo a la raíz devolvió 403 durante esta validación, mientras su healthcheck de Compose estaba sano. Una página de login accesible no demuestra todos los flujos de negocio. Los healthchecks de API corren dentro de Compose.

## Pruebas focalizadas

```powershell
mvn -q -f apps/erp/api/pom.xml test
mvn -q -f apps/aula/api/pom.xml test
mvn -q -f services/identidad/pom.xml test
npm ci --prefix apps/erp/web
npm run build --prefix apps/erp/web
docker compose config --quiet
```

Para un cambio de módulo ERP, ejecutar sus pruebas y siempre `ArquitecturaModularTest`. Las pruebas transversales heredadas están en `tests/legacy`; varias preparan datos y deben ejecutarse sólo contra una copia aislada. El workflow `.github/workflows/ci.yaml` automatiza pruebas Maven, compilación Web y configuración Compose, sin despliegue remoto.

## Detener y revertir código

`docker compose stop` conserva bases y objetos. Tras un fallo de imagen, volver a la versión de código anterior y ejecutar `docker compose up -d --build`. No usar `docker compose down -v`, `git clean` ni borrar los volúmenes `boston-aula_pg_data` o `boston-aula_s3_data`. Revertir código no deshace migraciones SQL ya ejecutadas; restaurar datos sólo después de revisar compatibilidad y con copia adicional.

## Copias y restauración

Antes de esta reestructuración se generó una copia externa en `C:\Valana-Pruebas\Aula Virtual Boston\respaldo-sistema-bbs-20261103`: tres dumps personalizados de PostgreSQL y copia del directorio S3. Se verificó `pg_restore --list` y se restauraron los tres dumps en un contenedor PostgreSQL 18.6 temporal separado; se comprobaron las cuentas de Flyway 6/11/25. Los volúmenes originales no se modificaron en la prueba de restauración. La copia es un punto de recuperación local, no un esquema de backup automático. No copiar esta carpeta a Git.

Procedimiento para un backup nuevo: detener escrituras, exportar cada base con `pg_dump -Fc` hacia una carpeta externa, copiar el volumen S3 con un contenedor auxiliar de solo lectura, registrar checksum y comprobar los dumps con `pg_restore --list`. Para restaurar, **crear primero un proyecto Compose aislado con volúmenes nuevos**, usar `pg_restore --clean --if-exists` sólo allí, verificar Flyway y el acceso a objetos; cambiar el entorno principal únicamente tras validar. No ejecutar una restauración directamente sobre volúmenes originales en uso.

## Incidencias frecuentes

| Síntoma | Comprobación | Acción segura |
|---|---|---|
| ERP muestra «servicio no disponible» | `docker compose ps`, logs `api-erp`, `identidad`, PostgreSQL | Esperar healthchecks; corregir causa de logs y reconstruir sin borrar volúmenes |
| Login vuelve a inicio | Comprobar cookies `ERP_SESSION`, emisor OIDC y URLs `127.0.0.1`/`localhost` | Usar las URLs configuradas; comprobar ambos servicios, sin compartir secretos |
| Falló Flyway | Logs de servicio y `flyway_schema_history` | Detener cambios; comparar versión y checksum; restaurar primero en entorno aislado |
| Puerto ocupado | `docker compose ps`, proceso local | Liberar puerto o cambiarlo de forma coordinada en Compose y OIDC |
| Aula no sincroniza | Consultar salud ERP/Aula y logs de sincronización | Revisar secuencia outbox y credenciales de servicio; no editar tablas a mano |

## Producción futura

No existe VPS ni despliegue público. Antes de producción deben definirse TLS, secretos externos, política de sesión, backups periódicos con pruebas de restauración, retención, monitoreo, protección de datos de alumnos y procedimientos de recuperación. Cambiar nombre Compose o puertos forma parte de esa planificación futura.
