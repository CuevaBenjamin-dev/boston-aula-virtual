# Grabaciones y almacenamiento

SeaweedFS proporciona S3 compatible local. `AlmacenamientoObjetos` abstrae iniciar multipart, presignar partes, completar, consultar tamaño, leer y eliminar. La carga ocurre navegador → S3 con URLs temporales; Spring no transporta MP4. El frontend acepta varios archivos, los ordena en una clase, divide cada archivo en partes de 8 MiB y reintenta cada PUT hasta tres veces. La publicación ocurre tras verificar tamaño real y cabecera MP4 `ftyp`; el máximo por archivo es 5 GB. La lectura exige curso autorizado y devuelve URL de cinco minutos; S3 sirve las peticiones HTTP Range. La descarga usa otra URL temporal con nombre de archivo seguro.

La operación de eliminación muestra archivos publicados y bytes por periodo, exige permiso Developer y cantidad confirmada, procesa cada objeto, conserva resultado del lote y audita. Se retiene la fila como `ELIMINADA`; el índice único parcial permite volver a usar el número de parte. No hay borrado automático ni eliminación del bucket. Las cargas multipart abandonadas requieren limpieza separada.

CORS y `ETag` se validaron desde navegador; dos MP4 se subieron directamente y un alumno autorizado obtuvo Range 206 y descarga completa. El fixture de 7313 bytes se instala una sola vez con `DATOS_DEMO=true` después de la primera sincronización ERP. Queda pendiente medir archivos grandes y concurrencia representativa; ver [pruebas](pruebas.md).

