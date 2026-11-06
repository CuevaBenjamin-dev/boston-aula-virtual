# Políticas transversales del ERP

Estas decisiones están aprobadas para la nueva base. Las reglas particulares de cada caso de uso se definirán en su módulo.

| Tema | Política |
|---|---|
| API | El prefijo oficial es `/api/erp/v1`. No se conservan los contratos anteriores. |
| Módulos | Personas, Matrículas, Oferta académica, Asignaciones docentes y Documentos. |
| Permisos | Administración y Dirección pueden gestionar permisos con controles de autorización y sin elevación indebida. |
| Solicitud de cuentas | Coordinación Académica, Dirección y Administración pueden solicitar cuentas según sus facultades. |
| Persona y cuenta | Una persona puede existir institucionalmente sin una cuenta de acceso. |
| Acceso al ERP | El ERP será utilizado por docentes y personal institucional. Estudiantes y apoderados no tendrán acceso directo al ERP. |
| Condiciones y roles | Las condiciones institucionales de una persona no equivalen a roles de seguridad ni conceden por sí mismas permisos de acceso. |
| Suspensión | Una cuenta suspendida no tiene acceso operativo y conserva su historial. |
| Matriz de permisos | Su futura interfaz será clara, accesible y enfocada en la experiencia del usuario. |

La implementación de estas políticas depende de requisitos y controles todavía por aprobar. [Personas](modulos/personas/README.md) dispone de operaciones iniciales de negocio bajo permisos y límites documentados; los otros cuatro módulos aún no publican operaciones de negocio. No se presume que todas las facultades institucionales estén habilitadas.

La integración previa con Aula está temporalmente no disponible. Aula e Identidad conservan su código y sus bases; ningún contrato de negocio ERP–Aula se considera vigente.
