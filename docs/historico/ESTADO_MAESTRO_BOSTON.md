# Estado maestro de Boston

**Creación inicial autorizada:** 30 de octubre de 2026. **Ámbito de verificación:** repositorio local `boston-aula` y Docker local. Este documento distingue decisiones indicadas por el usuario, funciones observadas en código y pruebas, y asuntos pendientes. Una función implementada no implica aprobación integral del caso primordial.

> **Regla permanente:** después de esta creación inicial, no modificar este archivo sin autorización explícita del usuario. Ante cambios futuros, informar los apartados afectados, resumir la actualización propuesta y pedir aprobación. Una nueva tarea de desarrollo no autoriza por sí sola a editarlo. No realizar commits automáticos.

## 1. Propósito y alcance

Boston reúne identidad institucional, ERP y Aula Virtual. El ERP conserva el dato oficial y su historia; Aula opera la experiencia académica usando una proyección sincronizada. La primera interfaz de ERP cubre matrículas y sus dependencias académicas, no el ERP completo. El entorno actual es solo local. El usuario aclaró que el despliegue al VPS se realizará más adelante.

## 2. Aplicaciones

| Aplicación | Estado comprobado al crear este documento |
|---|---|
| Boston Web | Mencionada como parte del ecosistema; código, despliegue y alcance no verificados en este repositorio. |
| Valana Repartidor | Mencionada como parte del ecosistema; código, despliegue y alcance no verificados aquí. |
| Boston ERP | API Spring Boot existente ampliada y nueva interfaz ERP Web V1 en `erp-web/`, ejecutadas localmente. |
| Boston Identidad | Servicio OIDC independiente; cuentas, autenticación, roles y enlaces a UUID ERP. |
| Aula Virtual | API y SPA independientes para cursos, materiales, tareas, entregas, notas, clases, grabaciones, avisos, supervisión y consulta de pagos. |

No se ha verificado una integración operativa con Boston Web o Valana Repartidor.

## 3. Arquitectura y stack confirmado

Docker Compose ejecuta `identidad`, `api-erp`, `api-aula`, `erp-web`, `frontend`, dos proxies Caddy, PostgreSQL y SeaweedFS S3 local. Identidad, ERP y Aula tienen bases PostgreSQL separadas (`identidad_boston`, `erp_boston`, `aula_boston`). ERP Web y Aula Web son aplicaciones React y TypeScript separadas. ERP Web usa Vite y React Router. Las API usan Java 21 y Spring Boot; Flyway mantiene migraciones incrementales. La autenticación es OIDC Authorization Code con BFF, cookies de sesión y CSRF; las API validan JWT de Identidad. Los UUID son estables y no hay claves foráneas entre bases de servicios.

Accesos locales comprobados: ERP Web `http://localhost:8089`, Aula `http://localhost:8088`, Identidad `http://localhost:9000`. API ERP no está publicada directamente al navegador. El proxy de ERP enruta a su SPA y BFF. `compose.yaml` define las dependencias; no hay VPS ni conexión de producción en este alcance.

## 4. Responsabilidades

- **API ERP:** fuente institucional de estudiantes, personas responsables, matrículas, periodos, grados, secciones, asignaturas, ofertas, docentes, asignaciones y tesorería existente. Publica instantáneas académicas mediante el outbox.
- **Boston Identidad:** gestiona cuentas, credenciales, perfiles, autenticación, suspensión, recuperación y vínculo `referencia_erp`. No guarda matrículas.
- **API Aula:** consume la proyección académica local y controla propiedad de recursos; conserva tareas, entregas, notas de actividades, materiales, clases, referencias de grabación y mensajes. Consulta tesorería en ERP para Mis pagos.
- **ERP Web:** interfaz de la V1 de matrículas y oferta; utiliza exclusivamente la API ERP existente a través de su BFF.
- **Aula Web:** interfaz académica por perfil; no crea datos oficiales de ERP.

## 5. Modelo institucional

`personas` contiene identidad y contacto; `estudiantes` y `apoderados` representan funciones distintas de personas. El estudiante conserva UUID y código institucional estable. `vinculos_estudiante_apoderado` guarda rol, relación y contacto de emergencia. `matriculas` relaciona estudiante con periodo y, opcionalmente al inicio, grado y sección; incluye procedencia, estado, responsable financiero, instantánea de contactos, versión y auditoría. `periodos_academicos`, `niveles_educativos`, `grados`, `secciones`, `asignaturas`, `asignaturas_grado` y `ofertas_academicas` componen la oferta. `docentes` y `asignaciones_docentes` permiten varios docentes por oferta y varias ofertas por docente. `referencias_documentales` conserva metadatos sin binarios en PostgreSQL; `importaciones_matriculas` registra archivo y reporte.

Los detalles de esquema están en las migraciones ERP y en [modelo-datos.md](modelo-datos.md); el alcance V1 está en [erp-matriculas-v1.md](erp-matriculas-v1.md).

## 6. Reglas vigentes de matrículas y oferta

**Decisiones indicadas en el encargo V1:** un estudiante permanente, código único generado por ERP y documento opcional; no usar DNI como clave. Apoderados reutilizables por tipo y número de documento; basta un responsable identificado para confirmar, y el responsable financiero puede ser otra persona. Una matrícula por estudiante y periodo, con procedencia `NUEVO`, `ANTIGUO`, `TRASLADO` o `REINCORPORACION`. Estados de trámite: `PROCESO_PENDIENTE`, `VACANTE_RESERVADA` y `MATRICULADO`. La confirmación exige revisión y acto explícito. Un periodo futuro no borra ni cierra automáticamente uno anterior. Una matrícula confirmada puede carecer de sección; solo una sección válida habilita cursos específicos.

**Permisos:** Dirección y Coordinación Académica pueden confirmar y corregir matrículas. Dirección administra periodos, secciones, asignaturas y docentes en esta V1. `ADMINISTRADOR` técnico no hereda estos permisos. La API aplica estas reglas, además de la interfaz.

**Oferta y docentes:** secciones A–E son opciones configurables, no se abren ni se llenan automáticamente. No existe aforo oficial inventado. Asignaturas reutilizables se relacionan con grados y periodos; su retiro conserva historial. Varios docentes pueden compartir una oferta. Su cuenta DOCENTE en Identidad debe referir al mismo UUID docente ERP; ERP no almacena contraseñas.

## 7. Integración ERP → Identidad → Aula

ERP emite instantáneas por oferta con secuencia y versión; Aula las aplica de modo idempotente y autoriza mediante su proyección local. Pendiente y reserva no habilitan curso. Confirmada sin sección tampoco. Retirar oferta o relación desactiva acceso vigente sin eliminar tareas ni notas históricas. La caída temporal de ERP no interrumpe el uso de cursos ya sincronizados en Aula; tesorería en vivo sí informa indisponibilidad.

Identidad crea o vincula cuentas por `referencia_erp`. La decisión institucional vigente es una sola cuenta académica ESTUDIANTE compartida por estudiante y apoderado; no se crea un perfil de acceso separado para apoderado. Esto impide distinguir cuál de ambos usó la cuenta. La creación de cuenta no es automática desde la nueva pantalla de matrículas: el administrador de Identidad debe completarla. No se envían correos reales ni se almacenan claves en ERP.

## 8. Casos primordiales CP-01 a CP-14

La columna «evidencia» resume implementación o pruebas existentes; **ninguna fila equivale por sí sola a aprobación completa**. La evaluación E³ citada corresponde a los documentos de revisión, que pueden requerir actualización tras cambios visuales posteriores.

| Caso | Implementación y evidencia localizada | Aprobación o pendiente |
|---|---|---|
| CP-01 Acceso e identidad | Login OIDC, perfiles, recuperación y revocación implementados; `revision-e3-cp01-cp04.md` aprueba escenarios individuales de acceso. | No hay aprobación integral; caducidad, sesiones simultáneas y canal productivo de recuperación pendientes. |
| CP-02 Asignación de cursos/docentes | Outbox ERP y proyección Aula; V1 de matrículas y docentes probó asignaciones y varias secciones con datos ficticios. | Revisión anterior aprobó idempotencia y continuidad ante caída; no consta aprobación integral. |
| CP-03 Cambios académicos | Sincronización de cambios y conservación de historia implementadas; la V1 probó matrícula futura sin sobrescribir la anterior. | Retiro, traslado y acceso histórico requieren decisión y evaluación integral. |
| CP-04 Autorización | Backend controla perfiles y recursos; pruebas de curso ajeno y rechazo de ADMIN para confirmar matrícula. | Matriz completa de IDs y mutaciones ajenas pendiente. |
| CP-05 Cursos, materiales y tareas | Flujos y avisos documentados en `revision-e3-cp05.md`; materiales por mes/semana añadidos localmente. | Políticas de periodo y consulta histórica pendientes; no consta cierre institucional integral. |
| CP-06 Gestión de tareas | Creación, publicación, cambios, varias secciones y seguimiento verificados en `revision-e3-cp06.md`. | Decisiones de cierre/retención aún abiertas. |
| CP-07 Entrega de tareas | Reentregas, intentos e historial técnico en `revision-e3-cp07.md`. | Su documento deja E³ pendiente; no marcar aprobado integralmente. |
| CP-08 Notas de actividades | Calificación por intento, publicación e historial en `revision-e3-cp08.md`. | Cierre académico oficial y excepciones aún sin contrato aprobado; E³ pendiente en esa revisión. |
| CP-09 Clases virtuales | Programación, secciones, cambios y avisos en `revision-e3-cp09.md`. | E³ pendiente según su revisión; horarios institucionales generales fuera del ERP V1. |
| CP-10 Grabaciones | Referencias de videos por clase y controles descritos en `revision-e3-cp10.md`. | E³ pendiente según su revisión; publicación externa del video no la garantiza Aula. |
| CP-11 Mensajes y avisos | Bandeja y notificaciones descritas en `revision-e3-cp11.md`. | E³ pendiente según su revisión. |
| CP-12 Tesorería | API de obligaciones, pagos y anulaciones; consulta de Mis pagos según `tesoreria.md`. | Cobro en línea deshabilitado; no se modificó CP-12 en ERP V1 ni consta aprobación integral. |
| CP-13 Supervisión | Consultas por docente, curso y estudiante en `revision-e3-cp13.md`. | E³ pendiente según su revisión. |
| CP-14 | No se encontró una definición ni una revisión CP-14 verificable en este repositorio. | Alcance, implementación y aprobación por confirmar; no se infieren. |

## 9. Decisiones funcionales y límites

Se mantienen una sola cuenta académica compartida, separación de permisos técnicos y académicos, periodos históricos, sección asignable después, varios docentes por curso y proyección académica local. Los videos de Grabaciones aceptan enlaces externos HTTP(S) elegidos por el docente según corrección expresa del usuario; la validación de URL oficial de YouTube fue retirada. El entorno se mantiene en Docker local y usa únicamente datos ficticios para pruebas. No incluye SIAGIE, pasarela de pagos, Zoom API, correo real, horarios, CRM, planillas, facturación ni gestor documental completo.

## 10. Cambios implementados y referencias

La V1 añadió `erp-web/`, BFF ERP, servicios ERP de estudiantes/matrículas/importación/oferta/docentes, rol DIRECCION en Identidad, migraciones ERP V4–V6 e Identidad V5, y `erp-proxy` en Compose. Se verificaron creación transaccional de estudiante y matrícula, estados, reutilización de responsables, importación sin duplicar, rechazo de ADMIN, asignaciones compartidas, desactivación/reactivación de oferta sincronizada y continuidad de Aula con ERP detenido. Los 70 registros confirmados del periodo 2026 persistieron tras las migraciones.

Referencias principales: [erp-matriculas-v1.md](erp-matriculas-v1.md), [arquitectura-general.md](arquitectura-general.md), [integracion-erp-aula.md](integracion-erp-aula.md), [identidad-institucional.md](identidad-institucional.md), [operacion-docker.md](operacion-docker.md), [tesoreria.md](tesoreria.md) y las revisiones `revision-e3-cp*.md`. `mapa-funcional-y-casos-pendientes.md` es un corte del 15 de octubre de 2026 y puede estar desactualizado.

## 11. Pendientes institucionales y técnicos

- Definir documentación y validaciones oficiales para confirmar matrículas, capacidad de secciones, cierre de periodo, consulta histórica tras retiro o traslado y excepciones autorizadas.
- Establecer el proceso de alta y entrega segura de cuentas desde Identidad. El enlace de cuentas docentes se valida; el alta de cuenta se hace fuera de ERP Web.
- Definir y revisar CP-14; completar las evaluaciones E³ y escenarios pendientes de CP-01–13 donde correspondan.
- Decidir política documental, retención y recuperación; el modelo actual guarda referencias, no un expediente digital completo.
- Preparar seguridad, respaldos, secretos, dominio y operación del VPS únicamente cuando el usuario abra una fase de despliegue. Nada de esto se considera productivo ahora.

## 12. Estado local y siguientes pasos

Al cierre de esta creación, Docker local tiene Identidad, API ERP, API Aula, ERP Web, Aula Web, PostgreSQL, SeaweedFS y proxies activos; las API con healthcheck estaban sanas. ERP Flyway V4, V5 y V6 e Identidad V5 están aplicadas. ERP Web se abre en `http://localhost:8089`; Aula sigue en `http://localhost:8088`. Hay datos ficticios de prueba y cambios del repositorio aún sin commit automático. El siguiente paso institucional es revisar el flujo de matrícula y sus campos obligatorios; el siguiente paso técnico es concluir la vinculación operativa de cuentas nuevas desde Identidad y evaluar los casos pendientes con la institución.
