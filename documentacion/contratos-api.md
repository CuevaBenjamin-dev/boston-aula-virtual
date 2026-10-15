# Contratos API local

Las rutas bajo `/api/aula` requieren sesión BFF y CSRF para cambios. Las rutas ERP requieren JWT de Identidad. Los cuerpos y errores de API son JSON. Estado HTTP 403 significa falta de permiso o propiedad. El inventario completo de 94 operaciones se genera desde controladores en [openapi.yaml](openapi.yaml) con `python scripts/generar-openapi.py`; este documento detalla las operaciones principales y sus reglas.

| Método y ruta | Función |
|---|---|
| `GET /api/sesion` | Perfil y emisión de cookie CSRF |
| `GET /api/aula/cursos` | Cursos visibles para el usuario |
| `GET/POST /api/aula/cursos/{id}/tareas` | Listar o crear borrador |
| `POST /api/aula/tareas/{id}/publicar` | Publicar tarea |
| `PUT /api/aula/tareas/{id}` | Editar borrador con `version` optimista |
| `POST/GET /api/aula/tareas/{id}/entregas` | Entregar o revisar |
| `PUT /api/aula/tareas/{id}/calificacion` | Nota con versión optimista |
| `GET /api/aula/cursos/{id}/notas` | Notas y versión de entrega calificada |
| `POST /api/aula/tareas/{id}/archivos` | Iniciar material privado multipart |
| `POST /api/aula/entregas/{id}/archivos` | Iniciar adjunto privado multipart |
| `GET /api/aula/archivos/{id}/partes/{n}` | URL de subida directa al almacenamiento |
| `POST /api/aula/archivos/{id}/completar` | Verificar firma y tamaño del documento |
| `GET/POST /api/aula/cursos/{id}/clases` | Clases académicas |
| `POST /api/aula/clases/{id}/enlaces` | Nuevo bloque Zoom |
| `POST /api/aula/grabaciones` | Iniciar multipart |
| `GET /api/aula/grabaciones/{id}/partes/{n}` | URL temporal de parte |
| `POST /api/aula/grabaciones/{id}/completar` | Completar y verificar tamaño/cabecera MP4 |
| `GET /api/aula/grabaciones/{id}/url` | URL temporal autorizada |
| `GET /api/aula/grabaciones/{id}/descargar` | Redirección autorizada a descarga temporal |
| `GET /api/aula/grabaciones/eliminacion/vista-previa?periodo=2026` | Archivos publicados y bytes a eliminar |
| `POST /api/aula/grabaciones/eliminacion` | Borrado por lote con `periodo` y `cantidadConfirmada` |
| `GET /api/aula/pagos` | Tesorería actual del ERP |
| `GET /api/erp/eventos?despues=n` | Eventos versionados para Aula |
| `POST /api/erp/matriculas` | Matrícula administrativa |
| `POST /api/erp/apoderados` | Crear apoderado y persona vinculada |
| `POST /api/erp/estudiantes/{id}/apoderados` | Vincular apoderado a estudiante con parentesco |
| `GET /api/erp/estudiantes/{id}/apoderados` | Consultar vínculos con autorización académica |
| `PUT /api/erp/estudiantes/{id}/seccion` | Cambio de sección |
| `POST /api/erp/tesoreria/pagos` | Registro administrativo de pago |
| `POST /api/erp/tesoreria/pagos/{id}/anular` | Anulación auditada con motivo |
| `GET/POST /restablecer` en Identidad | Formulario y cambio de clave con código temporal y CSRF |

Las mutaciones financieras requieren `tipo=ADMINISTRADOR` **y** el claim `tesoreria=true`, asignado explícitamente en Identidad. Cuentas administrativas nuevas no obtienen ese permiso. `/api/aula/pagos` solo acepta estudiante y devuelve 503 si ERP no está disponible. El contrato futuro de pasarela aceptará referencia externa única, importe, moneda, estado y firma verificable; su ejecución no forma parte de esta etapa.

La especificación OpenAPI sirve como inventario verificable de rutas y seguridad básica. Los cuerpos genéricos de operaciones aún requieren esquemas JSON detallados antes de publicar un SDK; las reglas normativas están en los servicios y en los documentos de este directorio.

