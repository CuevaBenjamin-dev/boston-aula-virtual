# Decisiones técnicas

1. Spring Boot 4.1.1 y Java 21: versión estable y LTS solicitadas.
2. PostgreSQL 18.6: imagen oficial, tres bases y volumen montado en `/var/lib/postgresql` conforme a su disposición desde v18.
3. SeaweedFS 4.06: almacenamiento S3 local sin licencia comercial. Se encapsula detrás de `AlmacenamientoObjetos` para sustituir endpoint y credenciales posteriormente.
4. Caddy en 8088 y Identidad en 9000: evita DNS público y conserva URLs OIDC válidas para navegador y contenedores.
5. Outbox de snapshots por oferta: simplifica cambios de sección, retiros y docente sin cola externa. Se registra versión 1 y secuencia monotónica.
6. Pagos solo en ERP y lectura viva desde Aula: evita mostrar saldos locales desactualizados.
7. Datos de prueba en migración ERP, creación inicial de cuentas y preparación académica posterior a sincronización: reinicio reproducible con persistencia y sin recrear contenido eliminado por el usuario.
8. Permiso `tesoreria` separado de administración: evita conceder lectura o registro financiero a administradores técnicos nuevos.
9. Índice único parcial para número de parte MP4: el borrado lógico conserva evidencia y permite reemplazar una parte eliminada.
10. `sesion_version` en Identidad: restablecer clave o suspender cuenta incrementa la versión; el BFF compara el claim con el estado actual y revoca sesiones anteriores sin depender del vencimiento del ID token.
11. Recuperación local mediante código de un solo uso y formulario de Identidad: la entrega del código es manual mientras no exista un canal institucional verificado.
12. Apoderados permanecen en ERP: el Aula usa una única cuenta académica por estudiante y no replica datos personales de apoderados que no necesita para operar.

