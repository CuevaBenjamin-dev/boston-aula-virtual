# Políticas transversales del ERP

Estas decisiones están aprobadas para la nueva base. Las reglas particulares de cada caso de uso se definirán en su módulo.

| Tema | Política |
|---|---|
| API | El prefijo oficial es `/api/erp/v1`. No se conservan los contratos anteriores. |
| Módulos | Personas, Matrículas, Oferta académica, Asignaciones docentes y Documentos. |
| Permisos | Dirección y Administración gestionan permisos únicamente con facultad expresa, verificable y sin elevación indebida; el cargo por sí solo no autoriza operaciones sensibles. |
| Solicitud de cuentas | Coordinación Académica, Dirección y Administración pueden solicitar cuentas según sus facultades. |
| Persona y cuenta | Una persona puede existir institucionalmente sin una cuenta de acceso. |
| Acceso al ERP | El ERP será utilizado por docentes y personal institucional. Estudiantes y apoderados no tendrán acceso directo al ERP. |
| Condiciones y roles | Las condiciones institucionales de una persona no equivalen a roles de seguridad ni conceden por sí mismas permisos de acceso. |
| Suspensión | Una cuenta suspendida no tiene acceso operativo y conserva su historial. |
| Matriz de permisos | Su futura interfaz será clara, accesible y enfocada en la experiencia del usuario. |

La política funcional de Personas está en su [especificación](modulos/personas/ESPECIFICACION.md). Su ejecución depende de controles y contratos técnicos todavía no implementados; los otros cuatro módulos aún no publican operaciones de negocio. No se presume que todas las facultades institucionales estén habilitadas.

**Protección de datos aprobada:** conservar conforme a finalidad y obligación aplicable, proteger especialmente los datos de menores, preservar historiales justificados y evidencias de autorizaciones/revocaciones, restringir el acceso cuando cese la necesidad ordinaria y admitir rectificación, supresión o anonimización cuando corresponda. No borrar automáticamente expedientes al concluir un curso ni conservar indiscriminadamente a perpetuidad. Dirección designará responsable operativo; no se crea por ello otro módulo o departamento. Antes de tratar datos reales deberán aprobarse tabla de conservación por categoría y finalidad, plazos, responsable, procedimientos de derechos y rectificación/supresión, archivo y acceso, documento de seguridad, respaldos/restauración, registro de bancos de datos, evaluación de obligaciones del Oficial de Datos Personales y revisión legal/contable aplicable. El [estándar de seguridad](ingenieria/ESTANDAR_SEGURIDAD_PRIVACIDAD.md) detalla controles transversales; el cierre funcional de Requisitos no acredita cumplimiento legal ni autoriza operación real.

La integración previa con Aula está temporalmente no disponible. Aula e Identidad conservan su código y sus bases; ningún contrato de negocio ERP–Aula se considera vigente.
