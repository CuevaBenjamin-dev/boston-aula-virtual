# Especificación — Personas

## 1. Requisitos funcionales

Los nueve objetivos funcionales están aprobados. La implementación actual y sus límites se describen en las secciones siguientes; los aspectos institucionales no resueltos permanecen explícitos en la sección 8. Ninguna regla se hereda automáticamente del ERP anterior.

| Código | Objetivo aprobado |
|---|---|
| RF-PER-001 | Registrar una persona con datos identificativos. |
| RF-PER-002 | Buscar personas por nombre o documento. |
| RF-PER-003 | Detectar posibles duplicados antes del registro. |
| RF-PER-004 | Consultar y actualizar información personal. |
| RF-PER-005 | Asignar múltiples condiciones institucionales a una persona. |
| RF-PER-006 | Registrar relaciones entre estudiantes, familiares y responsables. |
| RF-PER-007 | Consultar una ficha personal con condiciones y relaciones. |
| RF-PER-008 | Activar y desactivar condiciones institucionales conservando el historial. |
| RF-PER-009 | Registrar automáticamente la trazabilidad técnica de modificaciones relevantes, sin exigir aprobaciones manuales ni una pantalla de auditoría. |

## 2. Requisitos no funcionales

Se aplican también las [políticas transversales](../../POLITICAS_TRANSVERSALES.md). Los criterios siguientes son verificables; una prueba aislada no cierra por sí sola el objetivo completo.

| Código | Objetivo y criterio verificable |
|---|---|
| RNF-PER-001 | Cada operación comprueba permiso expreso, operación, recurso y ámbito; sin sesión responde 401 y sin facultad 403; CSRF protege escrituras. Se deniega por defecto cuando falta autorización verificable. |
| RNF-PER-002 | Ningún commit puede dejar persona definitiva sin condición/identificación, asignar ordinariamente a otra identidad un documento vigente o histórico, mantener principal simultáneo ni superponer vigencias; la concurrencia no lo elude. La solicitud preliminar incompleta de Matrículas no constituye persona definitiva. |
| RNF-PER-003 | Crear, corregir, activar y desactivar escribe actor, acción, recurso y fecha sin copiar valores personales; la operación y evento comparten transacción. |
| RNF-PER-004 | Con 30 000 personas y 30 usuarios concurrentes, búsquedas p95 ≤ 2 s y guardados habituales p95 ≤ 3 s. |
| RNF-PER-005 | El API solo devuelve datos necesarios para operaciones y recursos autorizados; ninguna contraseña ni valor anterior de identidad se guarda en auditoría. Personas conserva historial y constancias de autorizaciones/revocaciones según finalidad y obligación aplicable, restringe el acceso cuando cesa su necesidad ordinaria y permite rectificación, supresión o anonimización cuando corresponda. Los plazos y procedimientos por categoría requieren aprobación operativa antes de tratar datos reales; no hay borrado automático al terminar un curso ni conservación perpetua indiscriminada. |
| RNF-PER-006 | Objetivo WCAG 2.2 AA: campos etiquetados, foco y teclado utilizables, estados de carga/error/éxito anunciados y contraste verificable. |

## 3. Reglas institucionales

Estas reglas están aprobadas a nivel institucional. Los formatos y validaciones concretas no acordados permanecen pendientes; el estado de implementación se detalla más abajo.

| Código | Regla aprobada |
|---|---|
| RI-PER-001 | Cada persona tendrá un único registro personal institucional. |
| RI-PER-002 | Una persona podrá tener múltiples condiciones institucionales simultáneamente. |
| RI-PER-003 | Cada condición institucional tendrá un estado independiente. |
| RI-PER-004 | Desactivar una condición no desactivará las demás condiciones y relaciones que continúen siendo válidas. |
| RI-PER-005 | La desactivación no eliminará datos ni historiales personales. |
| RI-PER-006 | La obligatoriedad de datos dependerá de la condición institucional y de la operación: una solicitud preliminar incompleta pertenece a Matrículas y no equivale al registro definitivo de una persona en Personas. |
| RI-PER-007 | Toda persona registrada definitivamente deberá contar con documento de identidad, salvo la excepción sustentada para estudiantes de RI-PER-036. |
| RI-PER-008 | Para estudiantes, teléfono y correo electrónico serán opcionales. Los demás datos de la matriz serán obligatorios al crear una persona definitiva, salvo la identificación provisional sustentada de RI-PER-036. Los datos aún pendientes de acreditación permanecen en el proceso preliminar que los admita; nunca se inventarán valores para registrar a la persona. |
| RI-PER-009 | Para apoderados, el correo electrónico será opcional y los demás datos definidos serán obligatorios. |
| RI-PER-010 | Para docentes y personal institucional, todos los datos definidos en la matriz serán obligatorios. |
| RI-PER-011 | Si una persona adquiere otra condición institucional, deberá completar los datos adicionales necesarios sin crear otro registro personal. |
| RI-PER-012 | Los tipos de documento inicialmente admitidos serán DNI peruano, carné de extranjería y pasaporte. El catálogo será extensible mediante administración controlada. DNI peruano: exactamente ocho dígitos, incluidos ceros iniciales; CE: formatos oficiales válidos con validaciones configurables y sin longitud universal arbitraria; pasaporte: número y país emisor con validación conservadora y configurable. Validar formato no acredita autenticidad oficial. |
| RI-PER-013 | La clave documental es tipo, país emisor y número normalizado, independientes de la nacionalidad. Una persona puede conservar varios documentos y su historial bajo UUID estable. No se asignará ordinariamente el mismo documento a identidades distintas aunque una asociación anterior ya no esté vigente. La normalización conservadora no eliminará ceros iniciales ni caracteres internos significativos; las validaciones específicas dependerán del tipo. |
| RI-PER-014 | Si el documento ya está registrado, se permitirá consultar la ficha existente según los permisos aplicables y se incorporará cualquier nueva condición a esa persona, sin crear otro registro. |
| RI-PER-015 | La similitud de nombres nunca provocará una fusión automática de personas. |
| RI-PER-016 | Dirección o Administración con facultad expresa y verificable podrán corregir documentos y datos de identidad conservando UUID e historiales, sin aprobación adicional. La reasociación excepcional de un documento atribuido por error a otra identidad exige responsable, fecha, motivo y evidencia necesaria; no fusiona personas ni altera historiales académicos, financieros o administrativos de la identidad correcta. |
| RI-PER-017 | Las condiciones institucionales iniciales serán Estudiante, Docente, Personal institucional y Apoderado. |
| RI-PER-018 | Cada condición tendrá estado Activa o Inactiva, vigencia y motivo de desactivación cuando corresponda. Sus estados y vigencias serán independientes y podrán coexistir varias condiciones por persona. |
| RI-PER-019 | Apoderado para gestiones ordinarias será una condición institucional de la persona y requerirá vínculos explícitos, alcance autorizado y vigencia por estudiante. El contacto autorizado, limitado a comunicaciones pertinentes según lo permitido, es un alcance independiente; ambos se distinguen de parentesco y representación legal y conservan modificaciones y revocaciones. |
| RI-PER-020 | Un estudiante podrá tener varios apoderados simultáneamente, sin categorías de apoderado principal o secundario. |
| RI-PER-021 | La condición de Apoderado es independiente del parentesco; este no confiere por sí solo contacto autorizado, apoderamiento ni representación legal. |
| RI-PER-022 | Los vínculos entre personas conservarán su historial y vigencia. |
| RI-PER-023 | La autorización o el control operativo de recojo de estudiantes no forman parte de Personas. |
| RI-PER-024 | Coordinación Académica podrá registrar, buscar, consultar, actualizar contactos y gestionar condiciones y vínculos de Personas en toda la institución BBS, actualmente una sola institución, solo con permiso expreso y verificable para cada operación. No podrá corregir identidad oficial ni administrar permisos; el cargo por sí solo no autoriza. |
| RI-PER-025 | El personal autorizado podrá actualizar inmediatamente teléfono, correo y demás datos de contacto cotidianos, sin solicitudes de aprobación. Las correcciones sensibles quedarán reservadas a los perfiles autorizados. |
| RI-PER-026 | Las operaciones sensibles tendrán trazabilidad técnica automática y proporcional, sin conservar innecesariamente valores personales ni requerir una pantalla de auditoría. |
| RI-PER-027 | Una persona podrá ser apoderada de varios estudiantes. Si no tiene parentesco familiar con alguno, el vínculo requerirá autorización institucional válida otorgada por persona facultada e identificada razonablemente; el estudiante mayor de edad podrá autorizar directamente a terceros cuando tenga capacidad legal para ese acto. El vínculo no acredita por sí solo representación legal para todos los trámites. |
| RI-PER-028 | Cada vínculo estudiante–apoderado tendrá estado Activo o Inactivo, facultades expresas y vigencia propia, sin afectar los demás vínculos. Su alcance podrá modificarse o revocarse inmediatamente con historial y trazabilidad. Restricciones judiciales o conflictos de representación conocidos suspenderán únicamente las operaciones afectadas hasta aclarar facultades. |
| RI-PER-029 | La condición institucional Apoderado permanecerá Activa mientras exista al menos un vínculo de apoderado vigente y Activo. Si no existe ninguno, quedará Inactiva y conservará su historial, sin afectar las demás condiciones institucionales de la persona. |
| RI-PER-030 | Coordinación Académica, Dirección y Administración podrán registrar vínculos de apoderados dentro de sus facultades expresamente autorizadas. Para una persona sin parentesco comprobarán la autorización y registrarán el vínculo en la misma operación, sin aprobación adicional. Si no se acredita facultad suficiente, no activarán la autorización. |
| RI-PER-031 | La constancia de autorización registrará estudiante, persona autorizada, otorgante, medio, fecha y hora, trabajador verificador, alcance y vínculo; referencia de sustento cuando corresponda. Se conservarán las constancias de modificación y revocación sin datos adicionales innecesarios. |
| RI-PER-032 | Finalizar o desactivar un vínculo estudiante–apoderado no afectará los demás vínculos vigentes de la persona. |
| RI-PER-033 | Según su alcance expreso, el apoderado podrá recibir comunicaciones, consultar asuntos operativos, coordinar horarios o reprogramaciones y justificar inasistencias. Ni el contacto autorizado ni el apoderamiento conceden automáticamente representación legal, acceso familiar o académico irrestricto, facultades financieras ni firma de actos formales. El módulo dueño de cada trámite comprobará sus facultades adicionales. |
| RI-PER-034 | Se admite autorización verbal comprobable para vincular como apoderado a una persona sin parentesco. Coordinación Académica, Dirección o Administración verificará directamente al otorgante facultado, presencialmente, por teléfono u otro medio verificable. Por teléfono se preferirán contactos ya comprobados; en un primer registro se admiten comprobaciones presenciales o sustentos verificables. No se exigen grabaciones, documentos nuevos por cada gestión ordinaria ni aprobación adicional. |
| RI-PER-035 | Toda persona nueva se registra en una operación atómica con al menos una condición institucional válida; un apoderado como única condición inicial requiere un vínculo válido en la misma operación. |
| RI-PER-036 | Un estudiante real sin documento oficial puede identificarse provisionalmente solo en un supuesto funcional y legal admitido, con motivo, tipo y referencia del sustento y regularización pendiente. Conserva UUID e historial al regularizarse; no se inventarán DNI, fecha de nacimiento, nacionalidad ni otros valores. No habilita automáticamente personas ficticias o incompletas ni se extiende a docentes o personal. |
| RI-PER-037 | Matriz de acceso funcional: Dirección y Administración gestionan Personas solo con facultades expresas; corregir identidad oficial es una facultad reservada y verificable. Coordinación actúa en toda BBS solo con permisos expresos por operación, sin corregir identidad ni administrar permisos. Docentes consultan únicamente datos necesarios de estudiantes efectivamente asignados y contactos autorizados para sus funciones, sin acceso general a fichas completas ni modificación de datos familiares; sus datos propios también requieren autorización expresa. Estudiantes y Apoderados no acceden directamente al ERP. Se comprueban operación, recurso y ámbito, se deniega por defecto y ninguna condición institucional concede un rol de seguridad. |

### Datos personales y obligatoriedad por condición

| Dato | Estudiante | Apoderado | Docente | Personal institucional |
|---|---|---|---|---|
| UUID interno | Generado automáticamente | Generado automáticamente | Generado automáticamente | Generado automáticamente |
| Nombres | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Apellidos | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Tipo y número de documento | Obligatorio, salvo identificación provisional sustentada | Obligatorio | Obligatorio | Obligatorio |
| Fecha de nacimiento | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Sexo documental | Obligatorio y fiel al documento acreditado | Obligatorio y fiel al documento acreditado | Obligatorio y fiel al documento acreditado | Obligatorio y fiel al documento acreditado |
| Nacionalidad | Obligatoria, con estado acreditado diferenciado de desconocida, no determinada o apatridia | Obligatoria, con la misma distinción | Obligatoria, con la misma distinción | Obligatoria, con la misma distinción |
| Teléfono | Opcional | Obligatorio | Obligatorio | Obligatorio |
| Correo electrónico | Opcional | Opcional | Obligatorio | Obligatorio |
| Dirección domiciliaria | Obligatorio | Obligatorio | Obligatorio | Obligatorio |

El UUID lo genera el servidor. Si se añade otra condición a una persona existente, se completan los datos exigidos sin crear otra identidad (RI-PER-011 y RI-PER-014). La excepción documental de RI-PER-036 no vuelve opcionales la fecha de nacimiento ni los demás datos de una persona definitiva: si faltan, la información permanece en la solicitud preliminar de Matrículas hasta reunir lo exigible, sin valores inventados. Matrículas define los requisitos de su matrícula definitiva según el servicio educativo; la norma de Educación Básica no se traslada automáticamente a programas de idiomas BBS. El sexo documental admite Masculino y Femenino cuando así consten en documento peruano, designaciones oficialmente reconocidas de documentos extranjeros e información pendiente de comprobación cuando la operación lo permita; no se inventan categorías legales. Nacionalidad usa referencia normalizada de países y distingue desconocida, no determinada y apatridia sin códigos ISO ficticios; no equivale a país emisor, nacimiento o residencia. Parentescos iniciales: Padre, Madre, Abuelo/a, Hermano/a, Tío/a y Otro parentesco. «Sin parentesco» puede ser opción de interfaz, nunca relación familiar ficticia. Los catálogos son ampliables mediante administración controlada. Los estados pendiente y no aplicable se admiten solo en la operación que los permita expresamente, especialmente procesos preliminares; no satisfacen por sí solos datos obligatorios del registro definitivo.

## 4. Actores y casos de uso

Coordinación Académica actúa dentro de toda BBS solo con permiso comprobable por operación; Dirección y Administración necesitan facultad expresa para corregir identidad. La auditoría de RF-PER-009 es automática y no crea un octavo caso de uso. Matrículas conserva preinscripciones incompletas sin crear una persona ficticia; Personas registra únicamente la persona real con los datos exigibles. Identidad suministra permisos verificables y Asignaciones docentes la relación docente–estudiante; su ausencia no autoriza accesos por presunción.

| Código | Caso de uso aprobado |
|---|---|
| CU-PER-01 | Registrar persona. |
| CU-PER-02 | Buscar persona. |
| CU-PER-03 | Editar persona. |
| CU-PER-04 | Incorporar condición institucional. |
| CU-PER-05 | Vincular familiar o responsable. |
| CU-PER-06 | Consultar ficha personal. |
| CU-PER-07 | Desactivar o reactivar condición institucional. |

### Flujos y criterios de aceptación

| Caso | Flujo principal y alternativos | Error y criterio verificable |
|---|---|---|
| CU-PER-01 | Elegir condición, completar datos exigibles y documento; alternativamente, estudiante real con sustento provisional admitido. Apoderado inicial exige vínculo válido en el mismo commit. Una solicitud preliminar incompleta queda en Matrículas. | Falta de condición, identificación o contacto exigido: error y rollback. Documento asociado vigente o históricamente a otra identidad: conflicto, sin nueva persona. El UUID puede generarse antes; el registro solo queda persistido tras commit correcto. |
| CU-PER-02 | Buscar por nombre, apellido o documento, filtrar condición y vigencia, paginar. | Sin ámbito: 403. No coincidentes: lista vacía. La similitud no fusiona ni altera registros. |
| CU-PER-03 | Corregir datos comunes o contacto; documento nuevo cierra principal anterior, regulariza provisional y conserva UUID. Rectificar excepcionalmente una asociación documental errónea con responsable, fecha, motivo y evidencia, sin fusionar personas. | Solo Dirección/Administración con facultad expresa corrige identidad; Docente propio y Coordinación con permiso pueden editar contacto dentro de su alcance. Un documento asociado a otra identidad se rechaza ordinariamente, incluso si es histórico. |
| CU-PER-04 | Añadir condición a persona existente y comprobar datos adicionales; APODERADO se deriva de vínculos. | Condición ya activa: 409. Contacto insuficiente: 400. Histórico anterior permanece al reactivar. |
| CU-PER-05 | Registrar parentesco, contacto autorizado o apoderamiento según alcance expreso; para no familiar, comprobar autorización verbal o por otro medio verificable y guardar otorgante, medio, fecha/hora, verificador, alcance, vínculo y referencia cuando corresponda. Modificar o revocar cada autorización con historial. | Duplicado vigente: conflicto. Falta de facultad comprobada: no activar. No inferir representación legal ni permitir gestiones fuera del alcance; varios apoderados pueden coexistir y cerrar uno no afecta otros. |
| CU-PER-06 | Abrir ficha con identidad, documentos, condiciones, parentescos y apoderamientos históricos. | Sin permiso sobre recurso: 403; UUID inexistente: 404. No se muestra historial de valores personales en eventos. |
| CU-PER-07 | Desactivar condición con motivo o reactivar creando un nuevo período; apoderamiento se cierra de forma independiente. | Períodos no se superponen; cierre con apoderamiento incompatible se rechaza y hace rollback. |

## 5. Criterios de aceptación

Los criterios de cada CU figuran arriba. La política funcional de permisos está aprobada; la integración de Coordinación y el acceso docente a alumnos asignados siguen cerrados técnicamente porque Identidad/Asignaciones aún no suministran autorización y vínculo comprobables. La interfaz y la accesibilidad requieren verificación posterior; cerrar Requisitos no implica cerrar las otras cuatro verificaciones.

### Trazabilidad compacta de requisitos

| RF | RI principales | RNF aplicables | CU y criterio observable |
|---|---|---|---|
| RF-PER-001, RF-PER-003 | RI-PER-001, 006–010, 012–015, 035–036 | RNF-PER-001–003, 005 | CU-PER-01: persona real, condición e identificación en commit; duplicado vigente o histórico ajeno rechazado; sin persona ficticia por preinscripción. |
| RF-PER-002 | RI-PER-001, 013–015, 024, 037 | RNF-PER-001, 004–006 | CU-PER-02: búsqueda, paginación y ámbito; denegación sin facultad y ninguna fusión por similitud. |
| RF-PER-004 | RI-PER-011, 013–016, 024–025, 036–037 | RNF-PER-001–003, 005–006 | CU-PER-03: contacto según permiso; rectificación de identidad reservada, UUID e historial conservados. |
| RF-PER-005, RF-PER-008 | RI-PER-002–005, 011, 017–019, 029, 035 | RNF-PER-001–003, 005 | CU-PER-04 y CU-PER-07: condiciones independientes, sin superposiciones; Apoderado derivado de vínculos. |
| RF-PER-006 | RI-PER-019–023, 027–034 | RNF-PER-001–003, 005–006 | CU-PER-05 y CU-PER-07: parentesco, contacto y apoderamiento separados; alcance expreso, revocación e historial independiente. |
| RF-PER-007 | RI-PER-001–005, 019, 022, 028–029, 037 | RNF-PER-001, 004–006 | CU-PER-06: ficha e históricos visibles solo según permiso, recurso, ámbito y datos necesarios. |
| RF-PER-009 | RI-PER-005, 016, 019, 022, 026, 028, 031, 034 | RNF-PER-003, 005 | Transversal a CU-PER-01–07: actor, acción, recurso y fecha en transacción; sin datos anteriores innecesarios. |

RI-PER-023 limita el alcance de CU-PER-05; RI-PER-025 rige actualizaciones de contacto; RI-PER-030–034 delimitan comprobación y facultades. RNF-PER-004 y RNF-PER-006 fijan objetivos verificables cuya ejecución corresponde a Verificación 5. El contrato y las pruebas futuras se detallan en [PRUEBAS.md](PRUEBAS.md).

## 6. Arquitectura y modelo de datos

El diseño técnico, sus brechas y contratos están en [ARQUITECTURA.md](ARQUITECTURA.md); el diseño conceptual, lógico y físico PostgreSQL V2 está en [MODELO_DATOS.md](MODELO_DATOS.md). Son evidencia de implementación técnica, no aprobación institucional automática.

## 7. Contratos, seguridad e interfaz

Las rutas, DTO, matriz técnica de acceso e interfaz están en [ARQUITECTURA.md](ARQUITECTURA.md#contratos-seguridad-e-interfaz). La cobertura y los resultados de pruebas se registran en [PRUEBAS.md](PRUEBAS.md). Los permisos funcionales aprobados permanecen en RI-PER-037 y las decisiones abiertas en la sección 8.

## 8. Decisiones pendientes

Las decisiones funcionales D1–D5 y precisiones P1–P6 de esta actualización quedan aprobadas para el alcance descrito; no se reabren por falta de implementación. Permanecen como trabajos de verificaciones posteriores:

1. **Arquitectura:** revisar y aprobar modelo físico V2, estrategia de corrección excepcional del documento histórico, representación de contactos autorizados y alcances/revocaciones de apoderados, catálogos físicos y permisos verificables. No modificar V2 ya aplicada.
2. **Dependencias:** Identidad/plataforma debe asignar y probar permisos expresos; Asignaciones docentes debe exponer relación docente–estudiante; Matrículas debe definir solicitudes incompletas y consumir el contrato público de Personas. Ninguno habilita facultades por presunción.
3. **Implementación y seguridad:** corregir consulta y restricción de documentos históricos entre identidades, alta de Coordinación sin ámbito, corrección de identidad basada solo en rol, campos de datos pendientes y alcances de contactos/apoderados. No ampliar acceso efectivo hasta verificarlo.
4. **Operación y obligaciones legales:** Dirección designará responsable operativo de protección de datos y aprobará tabla por categoría, finalidad/fundamento, plazos, rectificación/supresión, archivo/acceso, documento de seguridad, respaldos/restauración, atención de derechos, registro de bancos, evaluación de Oficial de Datos Personales y revisión legal/contable cuando corresponda **antes de datos personales reales**. La política general aprobada no equivale a cumplimiento operativo.
5. **Verificación posterior:** vigencias futuras y cierres automáticos son ampliaciones no incluidas en el alcance inicial manual; auditar WCAG 2.2 AA, navegador, rendimiento HTTP, seguridad y escenarios nuevos. Los resultados JDBC locales históricos no acreditan esas pruebas.
