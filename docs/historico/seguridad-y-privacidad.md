# Seguridad y privacidad

Identidad almacena hashes Argon2id, no claves en claro. Las APIs usan OIDC/JWT y verifican roles, inscripción activa, asignación docente y ámbito de coordinación por recurso. La UI no guarda tokens en `localStorage`. El BFF usa cookie HttpOnly SameSite=Lax y CSRF por cookie y cabecera. PostgreSQL no tiene puerto público. MP4 y documentos académicos se guardan en bucket S3 privado. Las URLs temporales se emiten solo después de autorizar el recurso. ERP exige permiso financiero explícito además de rol administrador para pagos. La suspensión y el restablecimiento de contraseña incrementan la versión de sesión; el BFF invalida una sesión anterior con 401. El login muestra un error controlado ante credenciales incorrectas.

El entorno local usa HTTP y valores de ejemplo. No debe publicarse. Producción requiere TLS, cookie Secure, gestores de secretos, rotación de claves, correo de recuperación y políticas de datos de menores. Se documentan las brechas actuales en [auditoría](brechas-logica-negocio.md).

