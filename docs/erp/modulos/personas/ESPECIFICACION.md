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
| RNF-PER-001 | Cada operación comprueba rol y recurso; sin sesión responde 401 y sin facultad 403; CSRF protege escrituras. |
| RNF-PER-002 | Ningún commit puede dejar persona sin condición/identificación, documentos vigentes duplicados, principal simultáneo o vigencias superpuestas; la concurrencia no lo elude. |
| RNF-PER-003 | Crear, corregir, activar y desactivar escribe actor, acción, recurso y fecha sin copiar valores personales; la operación y evento comparten transacción. |
| RNF-PER-004 | Con 30 000 personas y 30 usuarios concurrentes, búsquedas p95 ≤ 2 s y guardados habituales p95 ≤ 3 s. |
| RNF-PER-005 | El API solo devuelve datos para recursos autorizados; ninguna contraseña ni valor anterior de identidad se guarda en auditoría; la retención y protección operativa requieren política institucional. |
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
| RI-PER-006 | La obligatoriedad de datos dependerá de las condiciones institucionales de la persona. |
| RI-PER-007 | Toda persona deberá registrarse con documento de identidad. |
| RI-PER-008 | Para estudiantes, teléfono y correo electrónico serán opcionales. Los demás datos de la matriz acordada serán obligatorios, sujetos a la posterior definición de validaciones. |
| RI-PER-009 | Para apoderados, el correo electrónico será opcional y los demás datos definidos serán obligatorios. |
| RI-PER-010 | Para docentes y personal institucional, todos los datos definidos en la matriz serán obligatorios. |
| RI-PER-011 | Si una persona adquiere otra condición institucional, deberá completar los datos adicionales necesarios sin crear otro registro personal. |
| RI-PER-012 | Los tipos de documento inicialmente admitidos serán DNI, carné de extranjería y pasaporte. El catálogo podrá ampliarse mediante configuración administrativa. |
| RI-PER-013 | No podrán existir dos personas con el mismo tipo y número de documento vigente, después de una normalización apropiada. La normalización y las validaciones de formato concretas quedan pendientes de definición. |
| RI-PER-014 | Si el documento ya está registrado, se permitirá consultar la ficha existente según los permisos aplicables y se incorporará cualquier nueva condición a esa persona, sin crear otro registro. |
| RI-PER-015 | La similitud de nombres nunca provocará una fusión automática de personas. |
| RI-PER-016 | Dirección y Administración podrán corregir documentos y datos de identidad dentro de sus facultades autorizadas, sin aprobaciones adicionales y conservando el UUID de la persona. |
| RI-PER-017 | Las condiciones institucionales iniciales serán Estudiante, Docente, Personal institucional y Apoderado. |
| RI-PER-018 | Cada condición tendrá estado Activa o Inactiva, vigencia y motivo de desactivación cuando corresponda. Sus estados y vigencias serán independientes y podrán coexistir varias condiciones por persona. |
| RI-PER-019 | Apoderado será una condición institucional de la persona y requerirá vínculos explícitos con los estudiantes correspondientes. |
| RI-PER-020 | Un estudiante podrá tener varios apoderados simultáneamente, sin categorías de apoderado principal o secundario. |
| RI-PER-021 | La condición de Apoderado es independiente del parentesco; este no otorgará automáticamente facultades legales. |
| RI-PER-022 | Los vínculos entre personas conservarán su historial y vigencia. |
| RI-PER-023 | La autorización o el control operativo de recojo de estudiantes no forman parte de Personas. |
| RI-PER-024 | Coordinación Académica podrá registrar, consultar y editar información de Personas dentro de su ámbito autorizado. El detalle de acciones y ámbitos queda pendiente. |
| RI-PER-025 | El personal autorizado podrá actualizar inmediatamente teléfono, correo y demás datos de contacto cotidianos, sin solicitudes de aprobación. Las correcciones sensibles quedarán reservadas a los perfiles autorizados. |
| RI-PER-026 | Las operaciones sensibles tendrán trazabilidad técnica automática y proporcional, sin conservar innecesariamente valores personales ni requerir una pantalla de auditoría. |
| RI-PER-027 | Una persona podrá ser apoderada de varios estudiantes. Si no tiene parentesco familiar con alguno, el vínculo requerirá autorización institucional válida. El vínculo de apoderado no acreditará por sí solo representación legal para todos los trámites. |
| RI-PER-028 | Cada vínculo estudiante–apoderado tendrá estado Activo o Inactivo y vigencia propia, sin afectar los demás vínculos. |
| RI-PER-029 | La condición institucional Apoderado permanecerá Activa mientras exista al menos un vínculo de apoderado vigente y Activo. Si no existe ninguno, quedará Inactiva y conservará su historial, sin afectar las demás condiciones institucionales de la persona. |
| RI-PER-030 | Coordinación Académica, Dirección y Administración podrán registrar vínculos de apoderados dentro de sus facultades autorizadas. Para una persona sin parentesco familiar, comprobarán la autorización institucional correspondiente y registrarán el vínculo en la misma operación administrativa, sin aprobación adicional. |
| RI-PER-031 | Cuando corresponda comprobar una autorización, el ERP conservará como mínimo quién la verificó, la fecha de verificación, el tipo o referencia del sustento y el vínculo estudiante–apoderado correspondiente, sin almacenar datos adicionales innecesarios. |
| RI-PER-032 | Finalizar o desactivar un vínculo estudiante–apoderado no afectará los demás vínculos vigentes de la persona. |
| RI-PER-033 | El apoderado será reconocido para contacto y gestiones institucionales ordinarias. El módulo responsable de una operación que requiera representación legal deberá comprobar esa facultad de forma independiente; la condición Apoderado no la concede automáticamente para todos los trámites. |
| RI-PER-034 | Se admite autorización verbal para vincular como apoderado a una persona sin parentesco familiar. Coordinación Académica, Dirección o Administración la comprobará directamente con una persona facultada para otorgarla, presencialmente, por teléfono u otro medio verificable. Se registrará quién autorizó, el medio, la fecha y hora, el personal que comprobó la autorización y el vínculo estudiante–apoderado. |
| RI-PER-035 | Toda persona nueva se registra en una operación atómica con al menos una condición institucional válida; un apoderado como única condición inicial requiere un vínculo válido en la misma operación. |
| RI-PER-036 | Un estudiante sin documento oficial puede identificarse provisionalmente con motivo, tipo y referencia del sustento y regularización pendiente; no se inventa un DNI. La posterior regularización conserva el UUID y el historial. Esta excepción no se extiende automáticamente a docentes o personal. |
| RI-PER-037 | Matriz de acceso aprobada: Administración y Dirección gestionan Personas dentro de sus facultades; Coordinación registra, consulta y edita en su ámbito autorizado sin corregir identidad reservada; Docentes solo acceden a información necesaria de estudiantes asignados y, cuando corresponda, a su propio contacto; Estudiantes y Apoderados no acceden directamente al ERP. La condición institucional no concede un rol de seguridad. |

### Datos personales y obligatoriedad por condición

| Dato | Estudiante | Apoderado | Docente | Personal institucional |
|---|---|---|---|---|
| UUID interno | Generado automáticamente | Generado automáticamente | Generado automáticamente | Generado automáticamente |
| Nombres | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Apellidos | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Tipo y número de documento | Obligatorio, salvo identificación provisional sustentada | Obligatorio | Obligatorio | Obligatorio |
| Fecha de nacimiento | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Sexo | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Nacionalidad | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Teléfono | Opcional | Obligatorio | Obligatorio | Obligatorio |
| Correo electrónico | Opcional | Opcional | Obligatorio | Obligatorio |
| Dirección domiciliaria | Obligatorio | Obligatorio | Obligatorio | Obligatorio |

El UUID lo genera el servidor. Si se añade otra condición a una persona existente, se completan los datos exigidos por esa condición en el mismo registro (RI-PER-011 y RI-PER-014). La única excepción documental actual es RI-PER-036. La norma técnica de matrícula 2026 del MINEDU admite sustento provisional y regularización posterior; Personas conserva la identificación, mientras Matrículas decide el trámite. El único formato cerrado en esta versión es el DNI peruano de ocho dígitos; los formatos CE y pasaporte siguen configurables.

## 4. Actores y casos de uso

Coordinación Académica actúa dentro de un ámbito comprobable; Dirección y Administración pueden corregir identidad. La auditoría de RF-PER-009 es automática y no crea un octavo caso de uso.

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
| CU-PER-01 | Elegir condición, completar datos y documento; alternativamente, estudiante con sustento provisional. Apoderado inicial exige vínculo en el mismo commit. | Falta de condición, identificación o contacto exigido: 400/409 y rollback. Documento vigente repetido: 409, sin nueva persona. UUID creado solo al finalizar la transacción. |
| CU-PER-02 | Buscar por nombre, apellido o documento, filtrar condición y vigencia, paginar. | Sin ámbito: 403. No coincidentes: lista vacía. La similitud no fusiona ni altera registros. |
| CU-PER-03 | Corregir datos comunes o contacto; documento nuevo cierra principal anterior, regulariza provisional y conserva UUID. | Solo Dirección/Administración corrige identidad; Docente propio y Coordinación con ámbito pueden editar contacto. Un documento repetido da 409 y conserva el anterior. |
| CU-PER-04 | Añadir condición a persona existente y comprobar datos adicionales; APODERADO se deriva de vínculos. | Condición ya activa: 409. Contacto insuficiente: 400. Histórico anterior permanece al reactivar. |
| CU-PER-05 | Registrar parentesco o apoderamiento; para no familiar, comprobar autorización verbal y guardar autorizador, medio, fecha, verificador y referencia. | Duplicado vigente: 409. Falta de autorización: 400. Se pueden mantener varios apoderados, y cerrar uno no afecta otros. |
| CU-PER-06 | Abrir ficha con identidad, documentos, condiciones, parentescos y apoderamientos históricos. | Sin permiso sobre recurso: 403; UUID inexistente: 404. No se muestra historial de valores personales en eventos. |
| CU-PER-07 | Desactivar condición con motivo o reactivar creando un nuevo período; apoderamiento se cierra de forma independiente. | Períodos no se superponen; cierre con apoderamiento incompatible se rechaza y hace rollback. |

## 5. Criterios de aceptación

Los criterios de cada CU figuran arriba. La integración de Coordinación y el acceso docente a alumnos asignados siguen abiertos porque Identidad/Asignaciones aún no suministran un ámbito comprobable. La interfaz y la auditoría de accesibilidad requieren verificación adicional; por ello no se declara cerrado el módulo.

## 6. Arquitectura y modelo de datos

El diseño técnico, sus brechas y contratos están en [ARQUITECTURA.md](ARQUITECTURA.md); el diseño conceptual, lógico y físico PostgreSQL V2 está en [MODELO_DATOS.md](MODELO_DATOS.md). Son evidencia de implementación técnica, no aprobación institucional automática.

## 7. Contratos, seguridad e interfaz

Las rutas, DTO, matriz técnica de acceso e interfaz están en [ARQUITECTURA.md](ARQUITECTURA.md#contratos-seguridad-e-interfaz). La cobertura y los resultados de pruebas se registran en [PRUEBAS.md](PRUEBAS.md). Los permisos funcionales aprobados permanecen en RI-PER-037 y las decisiones abiertas en la sección 8.

## 8. Decisiones pendientes

1. Autorizar y definir el ámbito efectivo de Coordinación por recurso; Identidad no emite `personas_ambito` y no se concede acceso por suposición. Definir asimismo el contrato de Asignaciones para alumnos asignados a cada docente.
2. Determinar quién puede otorgar autorización no familiar, las comprobaciones aceptadas por medio, la referencia de sustento y los alcances de representación legal por trámite. La modalidad verbal ya está aprobada.
3. Aprobar el modelo físico V2 como arquitectura institucional y concretar catálogos/formatos CE, pasaporte, sexo, nacionalidad y parentesco. El DNI peruano de ocho dígitos ya tiene validación técnica.
4. Precisar vigencias futuras, motivos de cierre que puedan automatizarse y procedimiento de incidencias de identidad sin fusión automática.
5. Aprobar retención y protección operativa de datos, auditoría de accesibilidad WCAG 2.2 AA y validación en navegador. El rendimiento JDBC local cumple el escenario medido, pero aún falta medición extremo a extremo.
