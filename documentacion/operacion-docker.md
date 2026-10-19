# Operación Docker

Desde `boston-aula/`: copie `.env.example` a `.env`, cambie claves de prueba y ejecute `docker compose up -d --build`. Compruebe `docker compose ps` (Identidad, ERP, Aula, PostgreSQL y SeaweedFS sanos), `docker compose logs -f identidad api-erp api-aula` y abra `http://localhost:8088`. Para detener sin perder datos: `docker compose down`. La primera compilación descarga imágenes y dependencias Maven/npm. Si cambia `.env` después de crear las bases, la contraseña PostgreSQL inicial del volumen no cambia automáticamente: se debe aplicar el cambio en PostgreSQL o reiniciar los volúmenes de prueba deliberadamente. SeaweedFS y Aula deben reiniciarse juntos al cambiar claves S3.

Los healthchecks ordenan Identidad → ERP → Aula → Proxy. La URL principal es <http://localhost:8088>; OIDC usa <http://localhost:9000> y S3 local <http://localhost:8333>. Para probar OIDC, entrar con `estudiante.demo` y el valor local `SEED_PASSWORD`. `DATOS_DEMO=true` prepara tarea, clase y MP4 una sola vez después de sincronizar el curso; si se desactiva antes del primer inicio, solo se cargan los datos ERP e Identidad. No usar contraseñas reales en este entorno.

Pruebas: `mvn -q test` desde cada backend y `npm run build` desde `frontend/`. El perfil k6 existe para pruebas de carga separadas; no forma parte de la verificación focalizada CP-05. Para detener y volver a iniciar sin perder datos use `docker compose down` y `docker compose up -d`.

Para el conjunto ficticio CP-05, use `./pruebas/cp05-datos-locales.ps1` en PowerShell. Repetir la carga normal mantiene los IDs y los registros existentes; el reinicio inicial de esos datos se ejecutó una sola vez con copias previas en `pruebas/.cp05-backup/`. El entorno CP-05 debe conservar sus volúmenes: **no** use la instrucción general `docker compose down -v` sobre él. La comprobación focalizada está en `python pruebas/cp05-verificar-flujos.py`; detalles y cuentas en [revisión CP-05](revision-e3-cp05.md).

Para CP-06, la prueba focalizada es `python pruebas/cp06-verificar-flujos.py`. Crea tareas temporales, verifica entrega tardía, cierre, reapertura, cancelación, ampliación, versiones, seguimiento y varias secciones, y limpia sus registros al finalizar. No requiere reiniciar los datos CP-05. Consulte [revisión CP-06](revision-e3-cp06.md).

