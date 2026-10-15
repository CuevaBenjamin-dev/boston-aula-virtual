# Boston Aula Virtual V1 — entorno local

Aplicación local para validar la integración entre Boston Identidad, API ERP institucional y Aula Virtual. Los datos `demo` son ficticios. No conecta Zoom, R2, bancos ni correo real.

## Iniciar

1. Instalar Docker Desktop con WSL2 y tener libres los puertos 8088, 9000 y 8333.
2. Copiar `.env.example` a `.env` y cambiar todas las contraseñas antes del primer inicio. `SEED_PASSWORD` será la clave inicial de las cuatro cuentas de prueba.
3. Ejecutar desde este directorio: `docker compose up -d --build`.
4. Revisar: `docker compose ps` y `docker compose logs -f identidad api-erp api-aula`.
5. Abrir <http://localhost:8088>. Identidad usa <http://localhost:9000>; S3 local usa <http://localhost:8333>.

Para detener sin borrar datos: `docker compose down`. Para reconstruir: `docker compose up -d --build`. Los volúmenes `pg_data` y `s3_data` conservan los datos. `docker compose down -v` **borra los datos locales** y solo debe usarse para reiniciar las pruebas desde cero.

## Cuentas de prueba

La clave es el valor elegido en `SEED_PASSWORD` del archivo local `.env`. No se guarda en el repositorio. Usuarios: `estudiante.demo`, `docente.demo`, `coordinacion.demo`, `admin.demo`. `admin.demo` tiene permisos Developer y tesorería asignados explícitamente para el entorno de prueba. La cuenta académica de ejemplo representa al estudiante y su apoderada. Con `DATOS_DEMO=true`, el primer inicio prepara una tarea, clase, enlace Zoom ficticio y MP4 breve después de sincronizar el curso ERP.

## Servicios

| Servicio | Función | Acceso local |
|---|---|---|
| frontend + Caddy | SPA y BFF | <http://localhost:8088> |
| identidad | OIDC, login, JWKS | <http://localhost:9000/.well-known/openid-configuration> |
| api-erp | datos académicos y tesorería | solo red interna Docker |
| api-aula | cursos, tareas, clases y grabaciones | vía `/api/aula` del proxy |
| PostgreSQL 18 | tres bases independientes | solo red interna Docker |
| SeaweedFS | objetos S3 de prueba | <http://localhost:8333> |

La UI se comunica con el BFF usando cookie de sesión HttpOnly y token CSRF. Las credenciales OAuth permanecen en el servidor. La API ERP exige JWT firmado por Identidad. Los archivos MP4 se cargan directo a S3 mediante URL temporal.

## Estado de la V1

Consultar el [mapa funcional por rol y casos pendientes](documentacion/mapa-funcional-y-casos-pendientes.md), las [pruebas](documentacion/pruebas.md), los [casos de uso V1](documentacion/casos-de-uso.md) y las [brechas detectadas](documentacion/brechas-logica-negocio.md). Una compilación correcta no acredita un caso de uso: el estado se registra luego de ejecutar el flujo completo en Docker.

