# Estándar de seguridad y privacidad

Identidad autentica la sesión; el ERP autoriza cada operación. Las [políticas transversales](../POLITICAS_TRANSVERSALES.md) definen quién puede acceder al ERP y separan condición institucional de rol. Este estándar no concede facultades ni crea ámbitos nuevos.

| Control | Aplicación | Verificación |
|---|---|---|
| Denegar por defecto | Endpoint, menú y servicio sin permiso comprobable no operan. El backend decide; ocultar botones solo ayuda a UX. | 401 sin sesión, 403 sin facultad y acceso directo por URL denegado. |
| Operación, recurso y ámbito | Comprobar rol/claim, relación con el recurso y alcance vigente en cada lectura/escritura. Si un servicio externo aún no entrega ámbito, bloquear la operación dependiente. | Pruebas cruzadas de usuarios, recursos ajenos y claims ausentes/falsos. |
| Privilegio mínimo | Usar contratos públicos y consultas limitadas; no inferir permiso desde condición Estudiante/Docente/Apoderado. | Revisión de matriz y datos devueltos. |
| CSRF y entrada | Mantener protección CSRF en escrituras con sesión/cookie cuando aplique; validar formato, tamaño y tipo en servidor. | Escritura sin token rechazada, payload inválido seguro. |
| Datos personales y menores | Minimizar campos en listas, logs, errores y auditoría; evitar datos reales en pruebas. Restringir consultas masivas/exportación y revisar finalidad del dato. | Inspección de respuesta, logs, fixtures y permisos. |
| Trazabilidad | Registrar actor, operación, recurso, instante y resultado proporcional, sin contraseñas, tokens ni valores anteriores innecesarios. | Pruebas de evento y revisión de excepciones. |

La retención, medios de comprobación y facultades legales específicos necesitan decisión institucional en la `ESPECIFICACION.md` propietaria. Hasta entonces no inventar períodos de retención ni permitir acceso por presunción. Un control legal para un trámite se verifica en su módulo responsable. Pruebas de autorización son parte del [estándar de verificación](ESTANDAR_VERIFICACION.md), no sustituidas por build o por la interfaz.
