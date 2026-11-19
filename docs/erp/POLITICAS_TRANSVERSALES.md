# Políticas transversales del ERP

Estas políticas operativas reflejan decisiones del responsable funcional recibidas el 19/11/2026. Sus cambios posteriores a la revisión 532 todavía requieren el trámite formal correspondiente en MODULA. Las reglas particulares se precisan en cada módulo.

| Tema | Política |
|---|---|
| API | El prefijo oficial es `/api/erp/v1`. No se conservan los contratos anteriores. |
| Módulos | Personas, Oferta académica, Matrículas, Asignaciones docentes, Asistencia, Documentos y Atención institucional. |
| Permisos | Solo Dirección y Coordinación Académica tienen rol ERP. La API verifica el rol obtenido de Identidad; una condición personal no eleva permisos. No existe rol Administración ERP. |
| Solicitud de cuentas | Dirección y Coordinación Académica pueden solicitar cuentas de Aula mediante contratos de Identidad; la emisión remota sigue pendiente de integración. |
| Persona y cuenta | Una persona puede existir institucionalmente sin una cuenta de acceso. |
| Acceso al ERP | Solo Dirección y Coordinación Académica. Docentes y estudiantes usan Aula; apoderados no tienen cuenta autónoma en el MVP. |
| Condiciones y roles | Las condiciones institucionales de una persona no equivalen a roles de seguridad ni conceden por sí mismas permisos de acceso. |
| Suspensión | Una cuenta suspendida no tiene acceso operativo y conserva su historial. |
| Matriz de permisos | Su futura interfaz será clara, accesible y enfocada en la experiencia del usuario. |

La política funcional de Personas está en su [especificación](modulos/personas/ESPECIFICACION.md). La primera vertical de Oferta, Matrículas y Asignaciones está implementada parcialmente; Asistencia, Documentos y Atención institucional siguen en desarrollo. Las solicitudes de integración se registran como pendientes, sin presumir acceso externo concedido.

**Protección de datos aprobada:** conservar conforme a finalidad y obligación aplicable, proteger especialmente los datos de menores, preservar historiales justificados y evidencias de autorizaciones/revocaciones, restringir el acceso cuando cese la necesidad ordinaria y admitir rectificación, supresión o anonimización cuando corresponda. No borrar automáticamente expedientes al concluir un curso ni conservar indiscriminadamente a perpetuidad. Dirección designará responsable operativo; no se crea por ello otro módulo o departamento. Antes de tratar datos reales deberán aprobarse tabla de conservación por categoría y finalidad, plazos, responsable, procedimientos de derechos y rectificación/supresión, archivo y acceso, documento de seguridad, respaldos/restauración, registro de bancos de datos, evaluación de obligaciones del Oficial de Datos Personales y revisión legal/contable aplicable. El [estándar de seguridad](ingenieria/ESTANDAR_SEGURIDAD_PRIVACIDAD.md) detalla controles transversales; el cierre funcional de Requisitos no acredita cumplimiento legal ni autoriza operación real.

La integración previa con Aula está temporalmente no disponible. Aula e Identidad conservan su código y sus bases; ningún contrato de negocio ERP–Aula se considera vigente.
