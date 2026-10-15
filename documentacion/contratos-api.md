# Contratos API local

Las rutas bajo `/api/aula` requieren sesión BFF y CSRF para cambios. Las rutas ERP requieren JWT de Identidad. Los cuerpos y errores de API son JSON. Estado HTTP 403 significa falta de permiso o propiedad. El inventario de rutas se genera desde controladores en [openapi.yaml](openapi.yaml) con `python scripts/generar-openapi.py`; este documento detalla las operaciones principales y sus reglas.

| Método y ruta | Función |
|---|---|
| `GET /api/sesion` | Perfil y emisión de cookie CSRF |
| `GET /api/aula/cursos` | Cursos visibles para el usuario |
| `GET/POST /api/aula/cursos/{id}/tareas` | Listar o registrar tarea como `FALTA_COMPLETAR` durante su carga |
| `POST /api/aula/tareas/{id}/publicar` | Publicar tarea completada o reabrir tarea cerrada |
| `PUT /api/aula/tareas/{id}` | Editar título, instrucciones y plazo con `version` optimista, incluso tras publicar |
| `POST/GET /api/aula/tareas/{id}/entregas` | Entregar o revisar |
| `PUT /api/aula/tareas/{id}/calificacion` | Nota con versión optimista |
| `GET /api/aula/cursos/{id}/notas` | Notas y versión de entrega calificada |
| `GET /api/aula/notas/{id}` | Detalle de nota del estudiante autorizado |
| `GET /api/erp/cursos/{id}/estudiantes/resumen` | Nombres de matriculados para Aula, solo cliente de servicio |
| `POST /api/aula/tareas/{id}/archivos` | Iniciar material privado multipart |
| `POST /api/aula/entregas/{id}/archivos` | Iniciar adjunto privado multipart |
| `GET /api/aula/archivos/{id}/partes/{n}` | URL de subida directa al almacenamiento |
| `POST /api/aula/archivos/{id}/completar` | Verificar firma y tamaño del documento |
| `GET/POST /api/aula/cursos/{id}/clases` | Clases del curso autorizado; programación por docente asignado |
| `GET /api/aula/clases` y `GET /api/aula/clases/{id}` | Listado global autorizado y detalle |
| `PUT /api/aula/clases/{id}` y `POST /api/aula/clases/{id}/cancelar` | Edición y cancelación por docente asignado |
| `GET /api/aula/clases/{id}/historial` | Cambios de tema, horario y estado |
| `GET/POST /api/aula/clases/{id}/enlaces` | Bloques Zoom; último enlace vigente |
| `GET /api/aula/notificaciones` y `POST /api/aula/notificaciones/{id}/leer` | Avisos del ámbito de cada rol y lectura individual |
| `GET /api/aula/mensajes/directorio` | Cuentas activas disponibles para enviar mensajes, sin vínculo ERP expuesto |
| `GET/POST /api/aula/mensajes` | Recibidos, enviados y envío directo o masivo a un curso docente |
| `GET /api/aula/mensajes/{id}` y `POST /api/aula/mensajes/{id}/leer` | Detalle autorizado y lectura del destinatario |
| `GET /api/aula/perfil`, `GET/POST /api/aula/perfil/foto` | Información del usuario y foto personal |
| `POST /api/aula/cuentas/mi-clave` | Cambio de contraseña propia con contraseña actual |
| `GET /api/erp/perfiles/{tipo}/{id}` | Datos institucionales; solo servicio Aula con `erp.read` |
| `GET/POST /api/aula/grabaciones` | Listar referencias autorizadas o registrar parte YouTube |
| `GET /api/aula/grabaciones/clase/{id}` | Partes de una clase; solo publicadas para estudiante |
| `PUT /api/aula/grabaciones/{id}` y `PUT /api/aula/grabaciones/{id}/estado` | Editar enlace o publicar/ocultar |
| `GET /api/aula/grabaciones/administracion` | Referencias YouTube para administrador |
| `GET /api/aula/grabaciones/eliminacion/vista-previa?periodo=2026` | Inventario técnico de MP4 históricos; Developer |
| `POST /api/aula/grabaciones/eliminacion` | Borrado técnico histórico por lote; Developer |
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

