# Grabaciones y almacenamiento

Las grabaciones nuevas son referencias de videos externos de YouTube. El docente sube el MP4 por el procedimiento externo del colegio y registra en el aula una o varias partes de una clase académica. Cada parte tiene número, ID de video, URL canónica y estado `PUBLICADA` u `OCULTA`. Puede corregir el enlace y cambiar la publicación. El estudiante solo ve partes publicadas de sus cursos activos y las abre con **Ver en YouTube** en otra pestaña. El aula no reproduce ni descarga videos y no consulta la API de YouTube. La disponibilidad y privacidad del video se administran fuera del aula.

Se validan dominios oficiales, HTTPS e identificadores de video en frontend y backend. Se aceptan enlaces `youtube.com/watch`, `youtu.be`, `shorts`, `live` y `embed`; todos se normalizan a `https://www.youtube.com/watch?v=...`. Se rechazan dominios parecidos, URL malformadas y formatos no admitidos. El administrador puede editar y publicar u ocultar referencias; esa acción no borra el video en YouTube. Developer mantiene por separado el inventario y borrado técnico de MP4 históricos.

La migración V7 **no elimina** la tabla `grabaciones` ni sus objetos S3 existentes. Los MP4 históricos no aparecen como grabaciones de YouTube; su eventual migración requiere una decisión operativa y enlaces externos válidos. No se crean nuevas cargas MP4 desde Aula. YouTube sustituye el almacenamiento previsto en R2 y el S3 local para **videos de clases**.

SeaweedFS y la capa S3 local siguen activos para archivos de tareas y entregas. Sus cargas multipart, URLs temporales y descargas permanecen sin cambios. Los registros S3 históricos de grabaciones se conservan para inventario técnico hasta decidir su migración o eliminación autorizada.
