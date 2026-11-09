# Personas — transferencia de Verificación 1: Requisitos

## Resultado ejecutivo

**Rama y commit inicial:** `main`, `5a6773320e8f68a675db09259370a755e3407fa2`. El árbol estaba limpio antes de esta tarea. La Verificación 1 se declara **Completada para el alcance funcional definido**: las decisiones institucionales recibidas D1–D5 y P1–P6 se incorporaron y se trazaron contra los requisitos y casos de uso. No equivale a aprobar la arquitectura física, terminar la implementación, superar pruebas ni autorizar tratamiento de datos personales reales.

Se modificaron [ESPECIFICACION.md](modulos/personas/ESPECIFICACION.md), [ESTADO.md](modulos/personas/ESTADO.md), [POLITICAS_TRANSVERSALES.md](POLITICAS_TRANSVERSALES.md), [ESTANDAR_SEGURIDAD_PRIVACIDAD.md](ingenieria/ESTANDAR_SEGURIDAD_PRIVACIDAD.md), [ARQUITECTURA.md](modulos/personas/ARQUITECTURA.md), [MODELO_DATOS.md](modulos/personas/MODELO_DATOS.md) y [PRUEBAS.md](modulos/personas/PRUEBAS.md), además de este informe. No se modificaron Java, React, SQL, migraciones ni datos.

## Decisiones institucionales incorporadas

| Decisión | Resultado funcional y requisito principal |
|---|---|
| D1/P1 — Identidad y documentos | UUID independiente y estable, varios documentos históricos, clave tipo + país emisor + número normalizado, sin reasignación ordinaria a otra identidad incluso tras cerrar un documento; rectificación excepcional con facultad expresa, motivo y evidencia. RI-PER-012–016 y RNF-PER-002. |
| D1/P2 — Preinscripción | La solicitud preliminar incompleta pertenece a Matrículas y no crea persona ficticia; Personas registra una persona real con condición e identificación exigibles. El estudiante provisional solo opera en supuesto admitido y sin datos inventados. RI-PER-006–008, 035–036. |
| D2/P3 — Permisos | Coordinación puede operar en toda BBS solo con permisos expresos por operación; Dirección/Administración requieren facultad reservada para corregir identidad; el docente ve únicamente información necesaria del alumno efectivamente asignado. Denegar por defecto. RI-PER-024, 037 y RNF-PER-001/005. |
| D3/P4 — Contactos/apoderados | Contacto autorizado, parentesco, apoderado ordinario y representante legal son distintos; facultades explícitas y limitadas, comprobación verbal admitida sin nuevas aprobaciones, modificación y revocación con historial. RI-PER-019–034. |
| D4/P5 — Catálogos | DNI peruano de ocho dígitos conservando ceros; CE y pasaporte con validación conservadora configurable; sexo documental y nacionalidad acreditados sin valores ficticios; parentescos iniciales definidos. RI-PER-006–012. |
| D5/P6 — Privacidad | Conservación por finalidad y obligación, acceso restringido al historial, evidencias de autorizaciones/revocaciones, posibilidad de derechos y prohibición de borrado automático o conservación perpetua indiscriminada. Políticas operativas detalladas pendientes antes de datos reales. RNF-PER-005. |

No se crearon identificadores nuevos: permanecen RF-PER-001–009, RNF-PER-001–006, RI-PER-001–037 y CU-PER-01–07. La independencia del contacto autorizado se integró en RI-PER-019 y RI-PER-033 para evitar duplicar reglas. La [matriz compacta de trazabilidad](modulos/personas/ESPECIFICACION.md#trazabilidad-compacta-de-requisitos) enlaza cada RF con RI, RNF, CU y criterio observable; RF-PER-009 se mantiene transversal.

## Auditoría de consistencia

Se corrigieron tres tensiones documentales: RI-PER-013 ya incluye país emisor y el documento histórico ajeno; RI-PER-007/035/036 distingue registro definitivo de solicitud preliminar sin rebajar la exigencia de condición e identificación; CU-PER-01 distingue generación de UUID de persistencia tras commit. No se detectó contradicción funcional material restante dentro del alcance inicial: las vigencias futuras y cierres automáticos se dejan como ampliaciones posteriores, no como decisiones silenciosas.

Los datos obligatorios de una persona definitiva permanecen exigibles. Si faltan datos como fecha de nacimiento, el proceso preliminar queda en Matrículas hasta contar con información suficiente; no se inventan valores. Los estados documentales pendientes se admiten únicamente donde la operación los autorice. La política de acceso aprobada no habilita hoy claims o relaciones que Identidad y Asignaciones todavía no suministran.

## Brechas clasificadas y trabajo posterior

| Tipo | Diferencia verificable | Siguiente verificación |
|---|---|---|
| Brecha de Arquitectura e Implementación | `V2__personas.sql` usa `UNIQUE (tipo_codigo,pais_emisor,numero_normalizado,periodo WITHOUT OVERLAPS)`: evita solapes, pero permite que dos identidades compartan ese documento en períodos distintos. RI-PER-013 lo prohíbe ordinariamente incluso con asociación histórica cerrada. Se requiere diseñar una migración correctiva futura y probar duplicado histórico, concurrencia y rectificación excepcional; no se eligió el mecanismo SQL en esta tarea. [Modelo](modulos/personas/MODELO_DATOS.md#pendientes), [SQL](../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql). | 3, 4 y 5 |
| Brecha de Seguridad | `AutorizacionPersonas.registrar` acepta el rol Coordinación sin comprobar el ámbito que sí exige para otras operaciones. Dirección/Administración también necesitan facultad verificable específica para corregir identidad, no únicamente rol. [Arquitectura](modulos/personas/ARQUITECTURA.md#decisiones-técnicas-pendientes), [Java](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/AutorizacionPersonas.java). | 3, 4 y 5 |
| Brecha de Arquitectura e Implementación | `per_apoderamientos` y DTO/UI actuales no representan por sí solos contacto autorizado separado, alcance detallado, modificación/revocación y restricciones conocidas. Los catálogos, sexo/nacionalidad pendientes y rectificación excepcional tampoco tienen aún modelo completo. [Modelo](modulos/personas/MODELO_DATOS.md), [ERP Web](../../apps/erp/web/src/modules/personas/PersonasModulo.tsx). | 2, 3, 4 y 5 |
| Dependencia externa | Identidad/plataforma: permisos expresos y verificables; Asignaciones: relación docente–estudiante; Matrículas: solicitud preliminar y consumo autorizado del contrato de Personas. No habilitar operaciones por presunción. [Arquitectura](modulos/personas/ARQUITECTURA.md). | 2–5 |
| Obligación legal u operativa pendiente | Tabla de conservación por categoría, finalidad y plazo, responsable designado, documento de seguridad, derechos, registro de bancos, revisión de privilegios, trazabilidad lógica, respaldos/restauración y evaluación de Oficial de Datos Personales. `per_eventos` no cubre necesariamente accesos/sesiones/tratamiento. [Política](POLITICAS_TRANSVERSALES.md), [estándar](ingenieria/ESTANDAR_SEGURIDAD_PRIVACIDAD.md). | Antes de datos reales y operación |

La norma de referencia es el [DS N.º 016-2024-JUS publicado por la ANPD](https://www.gob.pe/institucion/anpd/normas-legales/6554453-16-2024-jus). Su [texto oficial](https://cdn.www.gob.pe/uploads/document/file/7568330/6426760-decreto-supremo-n-016-2024-jus-reglamento-de-la-ley-n-29733-ley-de-proteccion-de-datos-personales-publicado-nov-2024.pdf) contiene en el artículo 46 conservación mínima de dos años para los registros de interacción lógica allí descritos y revisión semestral de privilegios; el 47 exige documento de seguridad; el 51, respaldos semanales si hubo actualizaciones y comprobación de integridad. Su aplicabilidad y ejecución concreta requieren revisión institucional; ese mínimo de logs no se traslada automáticamente a cada expediente.

## Escenarios y estado de las cinco verificaciones

Los escenarios futuros, **no implementados ni ejecutados**, están enumerados por área en [PRUEBAS.md](modulos/personas/PRUEBAS.md#escenarios-derivados-de-la-verificación-1-consolidada--no-implementados-ni-ejecutados): documentos, preliminares, permisos, apoderados, catálogos y privacidad. Incluyen los casos de duplicado histórico, distinta jurisdicción, autorización docente, revocación y restricción conocida.

| Verificación | Estado final | Razón |
|---|---|---|
| 1. Requisitos | **Completada** | Alcance funcional consistente, decisiones institucionales explícitas y trazabilidad [RF/RI/RNF/CU](modulos/personas/ESPECIFICACION.md#trazabilidad-compacta-de-requisitos). |
| 2. Análisis funcional | En progreso | Detallar alternativas, errores, autorizaciones y dependencias de los flujos nuevos. |
| 3. Arquitectura | En progreso | Diseñar integridad histórica, modelo de alcances/catálogos y autorización verificable. |
| 4. Implementación | En progreso | Código y datos actuales no realizan todas las reglas aprobadas; no hubo cambios funcionales en esta tarea. |
| 5. Verificación y pruebas | En progreso | Escenarios nuevos y auditorías aún no ejecutados; la evidencia previa es histórica. |

**Siguiente paso mínimo:** ejecutar Verificación 2 sobre CU-PER-01, 03 y 05, especialmente rectificación documental excepcional, identidad preliminar, alcance/revocación de contactos y apoderados, y matriz de permisos por operación. Mantener cerradas las funciones sin autorización comprobable mientras se diseñan los contratos externos.

## Validaciones de esta entrega

`python scripts/validar-docs-erp.py`: **OK** (44 archivos obligatorios, 48 documentos, cinco verificaciones por módulo, RI-PER-001–037 y enlaces relativos). `git diff --check`: **sin errores de espacios o parche**; Git informó solo conversión futura de LF a CRLF en dos archivos. El recuento de identificadores confirmó 6 RNF y 37 RI únicos y siete CU únicos; los RF aparecen tanto en la tabla normativa como en la nueva matriz de trazabilidad, por lo que deben contarse en la primera. No se corrieron pruebas de negocio, Java, React o PostgreSQL por ser una tarea documental. Durante esa auditoría no se hizo commit ni push; la publicación posterior se realiza por solicitud independiente y consta en el historial de Git.

**Observación de índice:** [README.md](modulos/personas/README.md) aún resume históricamente las cinco verificaciones como «En progreso». No se editó porque no figura entre los documentos modificables de esta tarea; [ESTADO.md](modulos/personas/ESTADO.md) es la fuente vigente del estado y deja solo la Verificación 1 como «Completada». Esta referencia de índice debe sincronizarse cuando se autorice editarlo; no altera los requisitos funcionales aprobados.

**Git al cerrar la auditoría documental original:** rama `main`, HEAD `5a6773320e8f68a675db09259370a755e3407fa2`; siete documentos existentes modificados y este informe nuevo sin seguimiento. La publicación posterior se identifica por su propio commit de Git.
