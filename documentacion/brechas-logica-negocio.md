# Auditoría de brechas de lógica de negocio · V1 local

Revisión realizada sobre flujos, permisos y datos de las tres APIs y la SPA el 15 de octubre de 2026. Esta lista distingue correcciones ya aplicadas de límites que conviene revisar con Boston antes de usar datos reales.

## Hallazgos corregidos durante la revisión

| Área | Hallazgo | Corrección y evidencia |
|---|---|---|
| Calificaciones | Una reentrega posterior a una nota podía parecer revisada. | `entrega_version` identifica la versión calificada y la vista señala la última entrega pendiente; se verificó entrega 2 frente a nota de entrega 1 y luego revisión de entrega 2. |
| Borradores | No había edición controlada tras crear una tarea. | `PUT /tareas/{id}` solo en borrador, con versión optimista; petición obsoleta respondió 409. |
| Grabaciones | El borrado lógico reservaba el orden de parte para siempre. | Índice único parcial sobre grabaciones no eliminadas; se conserva el historial y puede volver a usarse el orden. |
| MP4 | Extensión y tamaño no bastan para publicar un archivo. | Al completar se verifica tamaño real y cabecera `ftyp`; un archivo falso fue rechazado y retirado de S3. |
| Tesorería | Un administrador técnico tenía acceso financiero implícito. | Permiso de tesorería separado en Identidad y validado en ERP; cuentas nuevas carecen del permiso por defecto. `admin.demo` lo recibe explícitamente como fixture local. |
| Caída del ERP | La consulta de pagos devolvía 500 genérico. | Respuesta 503 sin mostrar información inventada; cursos sincronizados siguen consultables. |
| Credenciales S3 | Las claves personalizadas del `.env` no llegaban a SeaweedFS. | Ambos contenedores reciben el mismo par de claves; se comprobó lectura de objeto persistido tras recrear S3. |
| Login | Una contraseña incorrecta terminaba en `/login?error` sin página y devolvía 404. | Pantalla institucional con aviso accesible; el rechazo y el ingreso correcto se probaron en navegador. |
| Restablecimiento | Revocar autorizaciones OAuth no invalidaba una sesión BFF ya abierta. | `sesion_version` en Identidad y filtro del Aula; tras cambiar la clave la sesión anterior devolvió 401 y otra cuenta conservó 200. |
| Navegación académica | Los menús de tareas, notas, clases y grabaciones repetían solo tarjetas de cursos. | Vistas globales con registros reales de los cursos autorizados y enlaces a detalle; comprobadas como estudiante. |
| Apoderados ERP | Existían tablas y fixture, pero no operaciones para crear o vincular apoderados. | Se añadieron endpoints administrativos y lectura limitada al estudiante, operador autorizado o cliente ERP. |

## Pendientes funcionales para la segunda revisión

| Prioridad | Escenario | Impacto y decisión pendiente |
|---|---|---|
| Alta | La recuperación de contraseña muestra al administrador un token local de un solo uso; no hay canal institucional de entrega al titular. | Antes de uso real se necesita proceso de identidad/verificación y canal seguro. El token dura 30 min, se almacena hasheado y revoca autorizaciones al restablecer. |
| Alta | Un enlace S3 ya emitido permanece válido hasta cinco minutos aunque se retire la matrícula o se suspenda la cuenta. | Decidir si la ventana es aceptable o si se requiere entrega mediante proxy autorizado/URLs de vida más corta. La API ya impide emitir enlaces nuevos. |
| Alta | La asignación de permiso financiero requiere operación administrativa fuera de la pantalla normal; no hay flujo de aprobación de doble control. | Definir responsables y segregación de funciones antes de conectar datos financieros reales. |
| Media | Una carga multipart iniciada y abandonada puede dejar partes temporales en S3. | Añadir limpieza de cargas inconclusas por antigüedad, con registro y sin afectar publicaciones. |
| Media | El flujo de entrega guarda primero el texto y luego el archivo; si falla la carga, queda una respuesta sin adjunto y el usuario debe reintentar. | Se informa en UI y se conserva el identificador para reintento. Evaluar estado explícito «pendiente de archivo» y cierre transaccional de la entrega. |
| Media | Listados de tareas, entregas y pagos no tienen paginación completa; cuentas se limita a 200 y auditoría a 50. | Incorporar cursores/páginas y mediciones con volumen representativo antes de crecer. |
| Media | Las vistas globales de Grabaciones y la validación de sesión consultan varias APIs por curso/clase y por petición respectivamente. | Medir con cursos y usuarios representativos; incorporar agregación o caché breve sin relajar la revocación cuando el volumen lo requiera. |
| Media | La carga de archivos académicos inspecciona firma básica y tamaño, pero no analiza malware ni contenido activo de documentos. | Definir política de inspección para datos reales; mantener el bucket privado y permitir tipos cerrados. |
| Media | La UI muestra UUID ERP del estudiante en revisión y de docentes en supervisión. | Resolver nombres mínimos desde una proyección autorizada del ERP, respetando privacidad de menores. |
| Media | La eliminación por periodo incluye grabaciones publicadas; las cargas multipart inconclusas quedan para limpieza separada. | Documentar procedimiento de limpieza y conservación; el borrado informa el resultado por lote. |
| Baja | El reproductor HTML5 depende del MP4/códec original de Zoom. | Verificar en dispositivos objetivo; no hay HLS ni transcodificación V1. |
| Baja | No hay notificaciones por nueva tarea, nota o cambio de enlace. | Confirmar si son necesarias para operación escolar; la información se consulta al entrar y el enlace de clase se refresca periódicamente. |

## Límites deliberados de V1

No se implementan notas oficiales del ERP, cobro bancario, cuenta separada para apoderado, integración Zoom API, transcodificación de video ni eliminación automática. Los pagos se registran únicamente por operadores con permiso financiero y referencia administrativa única. Los archivos y datos demo son ficticios.

La prueba k6 disponible aún no cubre sesiones autenticadas simultáneas ni carga multipart grande. Por eso el proyecto no afirma capacidad para un número determinado de usuarios concurrentes. La revisión siguiente debe combinar pruebas de volumen, evaluación de seguridad y validación operativa con los responsables de Boston.
