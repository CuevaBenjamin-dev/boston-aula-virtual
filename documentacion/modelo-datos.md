# Modelo de datos

Las migraciones son la fuente del esquema: `identidad/src/main/resources/db/migration`, `api-erp/src/main/resources/db/migration` y `api-aula/src/main/resources/db/migration`.

```mermaid
erDiagram
  PERSONAS ||--o| ESTUDIANTES : corresponde
  PERSONAS ||--o| DOCENTES : corresponde
  ESTUDIANTES ||--o{ MATRICULAS : tiene
  SECCIONES ||--o{ MATRICULAS : agrupa
  SECCIONES ||--o{ OFERTAS_ACADEMICAS : ofrece
  ASIGNATURAS ||--o{ OFERTAS_ACADEMICAS : define
  OFERTAS_ACADEMICAS ||--o{ ASIGNACIONES_DOCENTES : asigna
  ESTUDIANTES ||--o{ OBLIGACIONES_PAGO : debe
  PAGOS ||--o{ APLICACIONES_PAGO : aplica
  CURSOS ||--o{ TAREAS : contiene
  TAREAS ||--o{ ENTREGAS : recibe
  TAREAS ||--o{ CALIFICACIONES_TAREA : evalua
  CLASES_ACADEMICAS ||--o{ BLOQUES_ZOOM : continua
  CLASES_ACADEMICAS ||--o{ HISTORIAL_CLASES : registra
  CLASES_ACADEMICAS ||--o{ NOTIFICACIONES_CLASE : avisa
  CLASES_ACADEMICAS ||--o{ GRABACIONES_YOUTUBE : referencia
  CLASES_ACADEMICAS ||--o{ GRABACIONES : historico
```

IDs principales: UUID. Fechas operativas: `TIMESTAMPTZ`. Importes: `NUMERIC(12,2)`. Los pagos y obligaciones tienen restricciones de positividad. Matrículas y asignaciones activas poseen índices únicos parciales. `grabaciones_youtube` guarda referencias externas; la tabla histórica `grabaciones` guarda metadatos y claves S3, nunca MP4 en PostgreSQL. `eventos_pendientes.secuencia` ordena el outbox; `eventos_procesados` garantiza idempotencia en Aula.

## Diccionario resumido

| Servicio y tabla | Clave y campos decisivos | Regla de integridad |
|---|---|---|
| Identidad `cuentas` | `id`, `usuario`, `clave_hash`, `tipo`, `referencia_erp`, `developer`, `tesoreria`, `activo`, `sesion_version` | Usuario único; vínculo ERP único por tipo; nuevos administradores sin tesorería; la versión revoca sesiones tras restablecimiento o suspensión. |
| Identidad `recuperaciones` | `cuenta_id`, `token_hash`, `vence_en`, `usado_en` | Hash único y uso único durante 30 minutos. |
| Identidad `claves_firma` y tablas OAuth | Clave pública/privada, clientes, autorizaciones | Firma y sesiones persistentes. |
| ERP `personas`, `estudiantes`, `apoderados`, `docentes` | UUID y relación con persona | La cuenta académica corresponde al estudiante; el vínculo con apoderado se guarda en ERP. |
| ERP `periodos_academicos`, `niveles_educativos`, `grados`, `secciones`, `asignaturas`, `ofertas_academicas` | Catálogos y oferta por sección | Una oferta por sección y asignatura; periodo definido en sección. |
| ERP `matriculas`, `asignaciones_docentes` | Estado y relación con estudiante/oferta | Solo una matrícula activa por estudiante y un docente activo por oferta. |
| ERP `conceptos_cobro`, `obligaciones_pago`, `pagos`, `aplicaciones_pago` | `NUMERIC(12,2)`, vencimiento, referencia, estado | Referencia de pago única, aplicación positiva, recálculo al anular. |
| ERP `eventos_pendientes`, `auditoria_erp` | Secuencia monotónica, JSONB versionado, actor | Outbox e historial de cambios institucionales. |
| Aula `cursos`, `inscripciones_curso`, `docentes_curso`, `coordinaciones_seccion` | UUID ERP proyectados, indicador activo y ámbito | Acceso depende de inscripción/asignación/ámbito vigente. |
| Aula `tareas`, `archivos_tarea`, `entregas`, `archivos_entrega` | Estado, plazo, publicación, versión, clave S3 | `FALTA_COMPLETAR` es privado; `PUBLICADA` es visible al estudiante. El material reemplazado conserva su objeto S3 pero queda inactivo. Versiones de entrega únicas por alumno y tarea. |
| Aula `calificaciones_tarea`, `historial_calificaciones` | Valor, `version`, `entrega_version`, actor | Una nota por alumno y tarea; cada corrección conserva historia. |
| Aula `clases_academicas`, `bloques_zoom` | Tema, horario, estado, docente ERP, enlace y número de bloque | Cancelar conserva clase y bloques; último bloque es enlace vigente. |
| Aula `historial_clases` | Clase, acción, tema, horario, estado y fecha | Cada creación, edición o cancelación conserva una instantánea; V8 registra el estado conocido de las clases anteriores. |
| Aula `notificaciones_clase` | Clase, estudiante ERP, tipo, mensaje, creación, lectura | Avisos persistentes por estudiante matriculado; cada uno se marca leído por separado. |
| Aula `notificaciones_vistas` | Cuenta y evento de clase | Lectura independiente de avisos para docente, coordinación y administración. |
| Aula `mensajes`, `mensajes_destinatarios` | Emisor, asunto, cuerpo, curso opcional, destinatarios y lectura | Cada destinatario tiene su copia de lectura; solo emisor y destinatario consultan el mensaje. |
| Aula `perfiles_aula` | Cuenta, foto, tipo MIME y fecha | La foto de cada cuenta se guarda en el Aula; las contraseñas permanecen en Identidad. |
| Aula `grabaciones_youtube` | Clase, orden, ID y URL canónica, estado | Orden único por clase; solo partes publicadas son visibles al estudiante. |
| Aula `grabaciones` históricas | Orden MP4, clave S3, estado | Se conservan sin migración destructiva y no se mezclan con referencias de YouTube. |
| Aula `configuraciones_modulos`, `estado_sincronizacion`, `eventos_procesados`, `registros_auditoria` | Flags, secuencia ERP, actor/acción | La desactivación no borra datos; evento aplicado una sola vez. |
| Aula `eliminaciones_grabaciones`, `preparacion_demo` | Lote, resultado, marcador `academico_v1` | Borrado auditado y fixtures insertados una sola vez. |

Cada servicio mantiene su propio esquema lógico dentro de una base independiente. Los UUID que cruzan servicios son referencias externas sin FK entre bases. La migración V7 del Aula añade estado de clases, avisos y referencias de YouTube sin borrar MP4 históricos. V8 crea el historial de clases y registra una instantánea del estado conocido de cada clase anterior; no puede reconstruir modificaciones anteriores a la migración. En clases anteriores, el docente ERP se reconstruye desde la asignación actualmente activa cuando existe; podría no corresponder al docente que impartió una clase histórica si hubo reasignación.

