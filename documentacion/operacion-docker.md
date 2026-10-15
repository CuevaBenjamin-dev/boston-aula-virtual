# Operación Docker

Desde `boston-aula/`: copie `.env.example` a `.env`, cambie claves de prueba y ejecute `docker compose up -d --build`. Compruebe `docker compose ps` (Identidad, ERP, Aula, PostgreSQL y SeaweedFS sanos), `docker compose logs -f identidad api-erp api-aula` y abra `http://localhost:8088`. Para detener sin perder datos: `docker compose down`. La primera compilación descarga imágenes y dependencias Maven/npm. Si cambia `.env` después de crear las bases, la contraseña PostgreSQL inicial del volumen no cambia automáticamente: se debe aplicar el cambio en PostgreSQL o reiniciar los volúmenes de prueba deliberadamente. SeaweedFS y Aula deben reiniciarse juntos al cambiar claves S3.

Los healthchecks ordenan Identidad → ERP → Aula → Proxy. La URL principal es <http://localhost:8088>; OIDC usa <http://localhost:9000> y S3 local <http://localhost:8333>. Para probar OIDC, entrar con `estudiante.demo` y el valor local `SEED_PASSWORD`. `DATOS_DEMO=true` prepara tarea, clase y MP4 una sola vez después de sincronizar el curso; si se desactiva antes del primer inicio, solo se cargan los datos ERP e Identidad. No usar contraseñas reales en este entorno.

Pruebas: `mvn -q test` desde cada backend, `npm run build` desde `frontend/` y `docker compose --profile pruebas run --rm k6` desde la raíz. Para reiniciar fixtures **borrando toda la información local**, `docker compose down -v` y después `docker compose up -d --build`; no ejecutar ese comando con datos que se quieran conservar.

