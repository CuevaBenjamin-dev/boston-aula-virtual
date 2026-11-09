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

La política general de conservación y protección está aprobada en [políticas transversales](../POLITICAS_TRANSVERSALES.md); plazos y procedimientos por categoría requieren aprobación operativa antes de tratar datos reales. No inventar períodos de retención ni permitir acceso por presunción. Un control legal para un trámite se verifica en su módulo responsable. Pruebas de autorización son parte del [estándar de verificación](ESTANDAR_VERIFICACION.md), no sustituidas por build o por la interfaz.

Como referencia normativa para evaluar aplicabilidad, el [Reglamento de la Ley N.º 29733, DS N.º 016-2024-JUS](https://www.gob.pe/institucion/anpd/normas-legales/6554453-16-2024-jus) dispone en su artículo 46 registros de interacción lógica con conservación mínima de dos años y revisión periódica de privilegios al menos semestral; el artículo 47 exige documento de seguridad; el artículo 51 establece respaldos al menos semanales cuando hubo actualizaciones y verificación de integridad. Corresponde verificar alcance, responsable y medidas concretas antes de operar. Los eventos funcionales de Personas no equivalen a toda la trazabilidad de accesos, sesiones y tratamiento requerida; no se extrapola el plazo de los registros de interacción a todos los expedientes personales.
