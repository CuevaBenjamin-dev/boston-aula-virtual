# Especificación — Personas

## 1. Requisitos funcionales

Los siguientes objetivos funcionales están aprobados. Sus reglas detalladas y criterios de aceptación siguen pendientes; no se heredan automáticamente del ERP anterior.

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

Los objetivos siguientes están aprobados; aún faltan sus criterios medibles de verificación. Se aplican también las [políticas transversales](../../POLITICAS_TRANSVERSALES.md).

| Código | Objetivo aprobado |
|---|---|
| RNF-PER-001 | Seguridad y autorización por operación y recurso. |
| RNF-PER-002 | Integridad y prevención de duplicados. |
| RNF-PER-003 | Trazabilidad técnica automática y proporcional de modificaciones relevantes, sin conservar innecesariamente valores personales ni imponer aprobaciones manuales. |
| RNF-PER-004 | Rendimiento de consultas y búsquedas. |
| RNF-PER-005 | Protección de datos personales. |
| RNF-PER-006 | Accesibilidad y usabilidad. |

## 3. Reglas institucionales

Estas reglas están aprobadas a nivel institucional. Los formatos y validaciones concretas aún no acordados se definirán después. Ninguna está implementada todavía.

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

### Datos personales y obligatoriedad por condición

| Dato | Estudiante | Apoderado | Docente | Personal institucional |
|---|---|---|---|---|
| UUID interno | Generado automáticamente | Generado automáticamente | Generado automáticamente | Generado automáticamente |
| Nombres | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Apellidos | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Tipo y número de documento | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Fecha de nacimiento | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Sexo | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Nacionalidad | Obligatorio | Obligatorio | Obligatorio | Obligatorio |
| Teléfono | Opcional | Obligatorio | Obligatorio | Obligatorio |
| Correo electrónico | Opcional | Opcional | Obligatorio | Obligatorio |
| Dirección domiciliaria | Obligatorio | Obligatorio | Obligatorio | Obligatorio |

El UUID no es un campo que deba proporcionar el usuario. Si se añade otra condición a una persona existente, se completarán los datos adicionales exigidos por esa condición en el mismo registro (RI-PER-011 y RI-PER-014). No se establecen todavía validaciones de formato documental sin justificación oficial. Quedan pendientes los formatos, catálogos y validaciones no resueltos, así como las reglas específicas de privacidad.

## 4. Actores y casos de uso

Están aprobados los siguientes casos de uso como objetivos. Coordinación Académica podrá registrar, consultar y editar Personas dentro de su ámbito autorizado; Dirección y Administración podrán corregir identidad según RI-PER-016. La configuración detallada de permisos por acción y ámbito, los flujos principales, alternativos y las excepciones todavía deben especificarse. RF-PER-009 se aplicará automáticamente a las modificaciones relevantes; no se define un caso de uso adicional ni una aprobación manual en esta etapa.

| Código | Caso de uso aprobado |
|---|---|
| CU-PER-01 | Registrar persona. |
| CU-PER-02 | Buscar persona. |
| CU-PER-03 | Editar persona. |
| CU-PER-04 | Incorporar condición institucional. |
| CU-PER-05 | Vincular familiar o responsable. |
| CU-PER-06 | Consultar ficha personal. |
| CU-PER-07 | Desactivar o reactivar condición institucional. |

## 5. Criterios de aceptación

Pendientes de especificación para los siete casos de uso y los nueve requisitos funcionales aprobados. No se consideran cerrados los requisitos mientras falten reglas detalladas, flujos y criterios verificables.

## 6. Arquitectura y modelo de datos

Personas será el registro central de personas físicas. Una persona podrá tener múltiples condiciones y relaciones institucionales sin duplicar sus datos personales.

Entidades conceptuales iniciales aprobadas: Persona, Documento de identidad, Condición institucional y Vínculo entre personas. No equivalen todavía a tablas ni a un diseño físico PostgreSQL aprobado.

La persona conservará su UUID al corregir su identidad. Las condiciones institucionales son independientes de los roles de seguridad, conforme a las [políticas transversales](../../POLITICAS_TRANSVERSALES.md). El vínculo con un estudiante es distinto tanto de la condición de Apoderado como del parentesco; sus tipos y comprobaciones documentales están pendientes.

El módulo dispone de la estructura backend `api/`, `application/`, `domain/` e `infrastructure/`. No hay entidades ni tablas de negocio implementadas. Personas no es propietario de credenciales, matrículas, oferta académica ni asignaciones docentes.

## 7. Contratos e integraciones

Los contratos públicos y las restricciones concretas están pendientes. La API oficial futura usará el prefijo `/api/erp/v1`; no se publican endpoints de negocio todavía. La propiedad de credenciales y el acceso técnico a Identidad se rigen por los límites de este módulo y las [políticas transversales](../../POLITICAS_TRANSVERSALES.md).

## 8. Decisiones pendientes

1. Normalización apropiada de documentos, validaciones de formato justificadas oficialmente y procedimiento para resolver incidencias de identidad sin fusionar personas automáticamente.
2. Detalles de vigencia y transiciones entre los estados Activa e Inactiva de cada condición, incluidos los casos en que corresponde registrar un motivo de desactivación.
3. Tipos de vínculo y comprobaciones documentales; procedimientos concretos de autorización institucional, facultades y determinación de la vigencia de cada vínculo de apoderado.
4. Permisos detallados de registro, consulta, edición y corrección por acción, ámbito y recurso.
5. Flujos principales, alternativos, excepciones y criterios de aceptación de los siete casos de uso.
6. Arquitectura detallada, modelo de datos PostgreSQL, restricciones y contratos públicos.
7. Criterios medibles de los seis requisitos no funcionales.
8. Formatos, catálogos y validaciones todavía no acordados, reglas específicas de privacidad y detalle técnico de la trazabilidad proporcional.
