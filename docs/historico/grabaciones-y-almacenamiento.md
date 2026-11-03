# Grabaciones y almacenamiento

Las grabaciones nuevas son referencias a videos externos. El docente publica el video mediante el procedimiento externo que elija y registra en el aula una o varias partes de una clase académica. Cada parte tiene número, URL y estado `PUBLICADA` u `OCULTA`. Puede corregir el enlace y cambiar la publicación. El estudiante solo ve partes publicadas de sus cursos activos y las abre en otra pestaña. El aula no reproduce ni descarga videos y no consulta al proveedor externo. La disponibilidad y privacidad del video se administran fuera del aula.

Se acepta cualquier URL HTTP(S) válida que el docente elija; no se exige un dominio oficial de YouTube ni un identificador de video. Se rechazan esquemas inseguros, credenciales incrustadas y caracteres de control. El administrador puede editar y publicar u ocultar referencias; esa acción no borra el video de su proveedor. Developer mantiene por separado el inventario y borrado técnico de MP4 históricos.

La migración V7 **no elimina** la tabla `grabaciones` ni sus objetos S3 existentes. Los MP4 históricos no aparecen en el listado de enlaces externos; su eventual migración requiere una decisión operativa y enlaces válidos. No se crean nuevas cargas MP4 desde Aula. Los enlaces externos sustituyen el almacenamiento previsto en R2 y el S3 local para **videos de clases**.

SeaweedFS y la capa S3 local siguen activos para archivos de tareas y entregas. Sus cargas multipart, URLs temporales y descargas permanecen sin cambios. Los registros S3 históricos de grabaciones se conservan para inventario técnico hasta decidir su migración o eliminación autorizada.
