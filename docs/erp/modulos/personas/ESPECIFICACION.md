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
| RF-PER-009 | Auditar modificaciones relevantes. |

## 2. Requisitos no funcionales

Los objetivos siguientes están aprobados; aún faltan sus criterios medibles de verificación. Se aplican también las [políticas transversales](../../POLITICAS_TRANSVERSALES.md).

| Código | Objetivo aprobado |
|---|---|
| RNF-PER-001 | Seguridad y autorización por operación y recurso. |
| RNF-PER-002 | Integridad y prevención de duplicados. |
| RNF-PER-003 | Auditoría de modificaciones relevantes. |
| RNF-PER-004 | Rendimiento de consultas y búsquedas. |
| RNF-PER-005 | Protección de datos personales. |
| RNF-PER-006 | Accesibilidad y usabilidad. |

## 3. Reglas institucionales

Estas reglas están aprobadas a nivel institucional. Los formatos, catálogos y validaciones concretas se definirán después. Ninguna está implementada todavía.

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

El UUID no es un campo que deba proporcionar el usuario. Si se añade otra condición a una persona existente, se completarán los datos adicionales exigidos por esa condición en el mismo registro (RI-PER-011). Quedan pendientes formatos, catálogos, validaciones y reglas específicas de privacidad.

## 4. Actores y casos de uso

Están aprobados los siguientes casos de uso como objetivos. Sus actores autorizados, flujos principales, alternativos y excepciones todavía deben especificarse. RF-PER-009 (auditoría) se aplicará a las modificaciones relevantes; no se define un caso de uso adicional en esta etapa.

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

El módulo dispone de la estructura backend `api/`, `application/`, `domain/` e `infrastructure/`. No hay entidades ni tablas de negocio implementadas. Personas no es propietario de credenciales, matrículas, oferta académica ni asignaciones docentes.

## 7. Contratos e integraciones

Los contratos públicos y las restricciones concretas están pendientes. La API oficial futura usará el prefijo `/api/erp/v1`; no se publican endpoints de negocio todavía. La propiedad de credenciales y el acceso técnico a Identidad se rigen por los límites de este módulo y las [políticas transversales](../../POLITICAS_TRANSVERSALES.md).

## 8. Decisiones pendientes

1. Tipos de documentos admitidos y reglas de detección y resolución de duplicados.
2. Estados, vigencias y transiciones de las condiciones institucionales.
3. Parentesco, apoderados y responsabilidades de los vínculos entre personas.
4. Permisos detallados de consulta y modificación por operación y recurso.
5. Flujos principales, alternativos, excepciones y criterios de aceptación de los siete casos de uso.
6. Arquitectura detallada, modelo de datos PostgreSQL, restricciones y contratos públicos.
7. Criterios medibles de los seis requisitos no funcionales.
8. Formatos, catálogos, validaciones y reglas específicas de privacidad para los datos personales.
