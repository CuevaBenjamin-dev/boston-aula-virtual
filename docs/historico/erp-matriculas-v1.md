# Boston ERP Web V1 — Matrículas

Estado: implementación y verificación local, 30 de octubre de 2026. Este alcance no equivale a la aprobación integral de los casos primordiales ni a un despliegue productivo.

## Aplicación y límites

`erp-web/` contiene React, TypeScript, Vite y React Router. Se sirve en `http://127.0.0.1:8089` mediante `erp-proxy`; Aula continúa independiente en `http://localhost:8088`. El acceso antiguo a `http://localhost:8089` redirige al nuevo origen. El BFF OIDC reside en la API ERP existente, utiliza Boston Identidad y envía las llamadas autenticadas a `/api/erp/v1`. No se añadió otra API ni base institucional.

El menú de ERP Web contiene Inicio, Estudiantes, Matrículas, Apoderados, Importación Excel, Oferta académica, Asignaturas y Docentes. Dirección y Coordinación pueden consultar y operar matrículas; solo Dirección administra oferta, asignaturas y docentes. `ADMINISTRADOR` técnico no hereda permiso académico. La autorización se comprueba en API ERP.

## Modelo y reglas

- Estudiante permanente con UUID y código `BOS-######` asignado por secuencia PostgreSQL. El documento es opcional y su unicidad se comprueba por tipo y número.
- Apoderados y contactos reutilizan personas por documento. Los vínculos permiten principal, segundo y suplente, contacto de emergencia y datos de contacto; la confirmación exige al menos un responsable identificado. El responsable financiero puede ser otra persona.
- Una matrícula por estudiante y periodo. Procedencia `NUEVO`, `ANTIGUO`, `TRASLADO` o `REINCORPORACION`; estado `PROCESO_PENDIENTE`, `VACANTE_RESERVADA` o `MATRICULADO`. Se conservan periodos anteriores y sus registros. La creación de estudiante nuevo y matrícula pendiente es transaccional. La confirmación exige acción explícita y queda auditada.
- El grado se requiere al confirmar; la sección puede asignarse después. Solo una matrícula confirmada con sección válida produce inscripción de curso. No se crean secciones por cantidad de alumnos ni se presume capacidad.
- Docentes tienen código `DOC-######`, identidad y asignaciones independientes. Un curso admite varios docentes y un docente varios cursos. La cuenta de Identidad debe ser tipo DOCENTE, estar activa y referir al UUID ERP exacto. ERP no guarda claves; el alta de cuenta se realiza en Boston Identidad por su administrador y luego se vincula en ERP.
- Asignaturas son catálogo reutilizable, con relaciones por grado y opcionalmente periodo. Desactivar una relación retira la oferta vigente de la proyección de Aula sin borrar historial de curso o trabajo.

## Importación Excel

`ImportacionMatriculas` ofrece plantilla ficticia `.xlsx`, previsualización de columnas y filas, detección de documentos/códigos repetidos o incompatibles, confirmación autorizada y reporte. El SHA-256 identifica el archivo para que una repetición no cree duplicados; al reintentar un archivo aún no confirmado se vuelve a validar. Los registros entran como pendientes, nunca como matrículas confirmadas sin la acción explícita.

## Integración

ERP publica instantáneas académicas de oferta mediante el outbox existente. Aula conserva su proyección y sus operaciones de tareas, entregas, notas y clases. La cuenta académica ESTUDIANTE se vincula al UUID ERP del alumno y es compartida con el apoderado según la decisión institucional vigente; no hay cuenta separada de apoderado. Las cuentas nuevas se administran en Identidad. Se verificó que Aula sigue consultando cursos sincronizados al detener temporalmente API ERP y que se recupera al reactivarla.

## Migraciones y verificación

- ERP V4: campos y estados de estudiantes, responsables y matrículas; asignaturas por grado; asignaciones docentes múltiples; historial; referencias documentales; importación. La migración conservó las 70 matrículas confirmadas de 2026.
- ERP V5: códigos internos para docentes preexistentes. ERP V6: unicidad de cuenta de Identidad por docente.
- Identidad V5: perfil DIRECCION y cliente OIDC local de ERP Web.
- Identidad V6: URI de retorno de ERP Web en `127.0.0.1`. ERP e Identidad usan este host para el flujo de ERP, mientras que Aula usa `localhost`, permitiendo sesiones de cuentas distintas en el mismo navegador local.

Compilaron `erp-web` y `api-erp` en Docker. `InstitucionalTest` pasó durante la implementación. La revisión de navegador con `direccion.demo` cubrió creación y búsqueda, conservación de periodos, reserva y confirmación, importación, asignaturas, asignaciones múltiples, enlace de cuenta docente, permisos y sincronización. Una cuenta `admin.demo` recibió 403 al intentar confirmar matrícula. Las pruebas usaron datos ficticios locales.

## Pendientes

Definir políticas institucionales de capacidad de secciones, documentos exigidos y cierre de periodo. Crear las cuentas nuevas de estudiante/docente desde el administrador de Identidad según el proceso institucional; ERP no las provisiona por sí solo. Las referencias documentales tienen metadatos, pero no hay gestor completo de anexos. No incluye horarios, SIAGIE, cobros, pasarela, CRM, planillas ni facturación. No hay despliegue VPS o productivo.
