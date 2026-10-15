# Verificación local · 14–15 de octubre de 2026

Entorno: Windows, Docker Desktop, PostgreSQL 18.6, Spring Boot 4.1.1, React 19, SeaweedFS 4.06 y Caddy. Las verificaciones API autenticadas se realizaron con sesiones reales de estudiante, docente y administrador en navegadores aislados. Los registros persisten en los volúmenes Docker.

## Comandos ejecutados

| Verificación | Resultado |
|---|---|
| `mvn -q test` en Identidad, ERP y Aula | 8 pruebas unitarias aprobadas en total (1, 4, 3). |
| `npm run build` en frontend | TypeScript y Vite completados. |
| `docker compose build` y `docker compose up -d` | Contenedores levantados; migraciones Flyway aplicadas automáticamente. |
| `docker compose --profile pruebas run --rm k6` | 5 usuarios virtuales durante 30 s; 573 solicitudes, 0 errores HTTP, 572/572 comprobaciones, p95 89,63 ms. |

La carga k6 abarca frontend, healthchecks y lecturas ERP sin sesión académica; **no** mide capacidad de subida MP4, actividad autenticada ni cientos de usuarios. Los valores son una referencia de esta máquina, no un compromiso de producción.

## Evidencia de los casos

| Caso | Comprobación realizada | Estado |
|---|---|---|
| CU-01 | Login OIDC real de cuatro perfiles; credencial incorrecta muestra error sin sesión, logout sale del BFF y de Identidad. Recuperación de clave con código temporal desde formulario: primer uso válido, segundo uso rechazado. Restablecer revocó sesión BFF previa (401) mientras otra cuenta siguió en 200. Suspensión y acceso ajeno comprobados. | Verificado |
| CU-02 | Alta de sección, oferta, docente, estudiante y matrícula por ERP; curso B aparece en Aula tras outbox. Repetir sync no aumentó `eventos_procesados`. | Verificado |
| CU-03 | Cambio A→B y retiro del estudiante de prueba; proyección activa pasó A=false/B=true y luego sin inscripción. | Verificado |
| CU-04 | Borrador con material PDF, publicación, lectura autorizada y HTTP Range 206. Edición de borrador 200, versión obsoleta 409, alumno no ve borrador (403). | Verificado |
| CU-05 | Entrega PDF directa a S3, historial; reentrega creó versión 2 sin reemplazar versión 1. Alumno retirado recibió 403. | Verificado |
| CU-06 | Nota 17, corrección 18, conflicto de versión 409 y reentrega revisada a 19; historial y `entrega_version` confirmados en BD/API. | Verificado |
| CU-07 | Clase con enlace manual y segundo bloque; actualización visible en historial. | Verificado |
| CU-08 | Subida directa de dos MP4 adicionales en una clase, publicación, reproducción por Range 206 (32 bytes), descarga completa (7313 bytes); MP4 falso rechazado 400; ajeno, 403. | Verificado |
| CU-09 | Desactivación del módulo: cursos/tareas 200 y clases/grabaciones 409; reactivación. Eliminación: 4 archivos/27 907 bytes en vista previa, actor sin Developer 403, cantidad desactualizada 409, lote autorizado eliminó 4 sin errores; nuevo MP4 reutilizó orden 1. | Verificado |
| CU-10 | Pagos administrativos 100 y 350 completaron una obligación de 450; duplicado 409, importes inválidos 400. Anulación de 100 devolvió saldo 100; nueva aplicación restauró saldo cero. | Verificado |

## Resiliencia y permisos

- ERP detenido: cursos ya sincronizados respondieron 200 y pagos respondieron 503, sin inventar saldos. La primera implementación había devuelto 500; se corrigió y se repitió la prueba. Al reiniciar ERP, el sincronizador volvió a registrar `ultimo_exito` y limpió `ultimo_error`.
- Coordinación sin ámbito recibió 403 al abrir un curso; al asignar sección A solo vio A en reportes y docentes, y B continuó denegado.
- Docente ajeno a curso B, alumno retirado y estudiante no matriculado recibieron 403 al intentar acceso directo a recursos de ese curso.
- Cambio de credenciales S3 locales: SeaweedFS y Aula usan las variables del mismo `.env`; se recreó S3 y un PDF persistido siguió respondiendo Range 206.
- Separación financiera: se creó `tecnico.demo` como administrador sin permiso `tesoreria`; lectura y registro ERP devolvieron 403. `admin.demo` autorizado consultó saldo 450.
- Fixture académico: la instalación añadió una tarea, clase, bloque Zoom y MP4 de 7313 bytes después de sincronizar el curso demo. Se verificó el marcador `academico_v1` en PostgreSQL.
- Navegación académica: los menús Mis tareas, Mis notas, Clases virtuales y Grabaciones mostraron registros reales de cursos autorizados, con enlaces a detalle.
- Apoderados ERP: alta administrativa 201, vínculo 201 y consulta de dos vínculos; repetir el vínculo mantuvo dos filas. Un administrador sin tesorería consultó los apoderados (200) pero no el saldo financiero (403).
- La cuenta administrativa temporal `tecnico.demo` usada para pruebas de permisos y recuperación quedó suspendida; su sesión obtuvo 401. No forma parte de las cuatro cuentas base del fixture.
- Reinicio local: Docker Desktop detuvo los servicios y `docker compose up -d --build` los recuperó con volúmenes persistidos; tareas, notas, MP4 y saldos seguían disponibles.

## Repetición

Para instalación limpia, copie `.env.example` a `.env`, cambie claves y ejecute `docker compose up -d --build`. La API ERP inserta datos institucionales ficticios; Aula prepara tarea, clase y MP4 de demostración una vez tras la primera sincronización, cuando `DATOS_DEMO=true`. Use las cuentas descritas en `README.md`. `docker compose down` mantiene los datos; `docker compose down -v` los elimina deliberadamente.
