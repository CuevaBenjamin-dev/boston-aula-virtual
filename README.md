# Sistema BBS — entorno local

Aplicación local para validar la integración entre Boston Identidad, API ERP institucional y Aula Virtual. Los datos `demo` son ficticios. No conecta Zoom, R2, bancos ni correo real.

## Iniciar

1. Instalar Docker Desktop con WSL2 y tener libres los puertos 8088, 8089, 9000 y 8333.
2. Copiar `.env.example` a `.env` y cambiar las contraseñas de los servicios antes del primer inicio. Para la preparación local de matrículas, crear `cuentas-iniciales.local.txt` según el formato descrito abajo.
3. Ejecutar desde este directorio: `docker compose up -d --build`.
4. Revisar: `docker compose ps` y `docker compose logs -f identidad api-erp api-aula`.
5. Abrir Aula en <http://localhost:8088> y ERP Web en <http://127.0.0.1:8089>. Identidad usa <http://localhost:9000> para Aula y <http://127.0.0.1:9000> para ERP; S3 local usa <http://localhost:8333>.

Para detener sin borrar datos: `docker compose stop`. Para reconstruir: `docker compose up -d --build`. Los volúmenes `pg_data` y `s3_data` conservan los datos. No uses `docker compose down -v`.

## Cuentas de prueba

El archivo local `cuentas-iniciales.local.txt` contiene las claves iniciales de Coordinación Académica y Dirección. Está excluido de Git y se monta solo en el contenedor de Identidad. Tiene encabezado TSV `usuario`, `clave`, `tipo`, `referencia_erp`, `nivel`, `grado`, `seccion`. El ERP inicia sin alumnos ni docentes de prueba; Dirección registra cada docente en ERP y, en la misma ficha, crea y vincula su acceso al Aula por separado. Las claves no se imprimen en registros ni se guardan en el repositorio. Con `DATOS_DEMO=false` no se generan clases, tareas ni materiales ficticios.

La referencia histórica de [malla curricular](docs/historico/malla-curricular-base.md) contiene 11 grados, pero el ERP nuevo configura la oferta por año, grado y sección en su propia base. La confirmación de matrícula solicita la habilitación en Aula; **el acceso no se considera concedido hasta que el contrato remoto quede implementado y confirme el resultado**.

## Servicios

| Servicio | Función | Acceso local |
|---|---|---|
| frontend + Caddy | SPA y BFF | <http://localhost:8088> |
| erp-web + erp-proxy | ERP Web y BFF OIDC de API ERP | <http://127.0.0.1:8089> |
| identidad | OIDC, login, JWKS | <http://localhost:9000/.well-known/openid-configuration> |
| api-erp | personas, oferta, matrículas y asignaciones; demás módulos en desarrollo | solo red interna Docker |
| api-aula | cursos, tareas, clases y grabaciones | vía `/api/aula` del proxy |
| PostgreSQL 18 | tres bases independientes | solo red interna Docker |
| SeaweedFS | objetos S3 de prueba | <http://localhost:8333> |

Las interfaces se comunican con sus BFF usando cookies de sesión HttpOnly y token CSRF. Las credenciales OAuth permanecen en el servidor. La API ERP usa la sesión OIDC de Identidad y restringe las operaciones a Dirección y Coordinación Académica. Los archivos de tareas y entregas del Aula usan S3 local; las grabaciones nuevas son referencias a enlaces externos HTTP(S) elegidos por el docente. El estado ERP actual está en [incremento MVP](docs/erp/INCREMENTO_MVP_2026-11-19.md); el alcance anterior se conserva en [erp-matriculas-v1.md](docs/historico/erp-matriculas-v1.md).

## Estado y documentación

Consultar el [mapa funcional por rol y casos pendientes](docs/historico/mapa-funcional-y-casos-pendientes.md), las [pruebas](docs/historico/pruebas.md), los [casos de uso V1](docs/historico/casos-de-uso.md) y las [brechas detectadas](docs/historico/brechas-logica-negocio.md). Una compilación correcta no acredita un caso de uso: el estado se registra luego de ejecutar el flujo completo en Docker.

La documentación vigente de esta reestructuración está en [arquitectura](docs/arquitectura/README.md), [ERP](docs/erp/REQUISITOS_Y_TRAZABILIDAD.md), [operación](docs/operacion/README.md) y el [informe de entrega](docs/erp/ENTREGA_PARA_CHATGPT.md). `docs/historico` se conserva como referencia de decisiones anteriores.

