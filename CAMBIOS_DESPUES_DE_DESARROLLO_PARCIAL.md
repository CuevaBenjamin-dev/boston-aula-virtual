# Cambios después del desarrollo parcial

Este registro documenta los cambios realizados a partir del 19 de noviembre de 2026, con su motivo y comprobación.

## 2026-11-19 — Error 403 al crear el año escolar 2027

- **Problema:** la operación «Crear año» de Oferta académica respondía `403`, aunque la consulta del año 2026 funcionaba.
- **Causa:** `apps/erp/web/src/shared/api/erp.ts` enviaba en `X-XSRF-TOKEN` el valor `csrf` de `/api/erp/v1/sesion`. Con la configuración `csrf.spa()` del ERP, ese valor está enmascarado; el servidor espera el valor sin enmascarar que entrega en la cookie legible `XSRF-TOKEN`. Se comprobó que ambos valores eran distintos en la respuesta local de sesión.
- **Cambio:** las operaciones de escritura realizadas mediante el cliente compartido `erp()` leen ahora el token actual de `XSRF-TOKEN` y lo envían en `X-XSRF-TOKEN`. Si falta la cookie, se muestra un error que indica recargar la página.
- **Justificación:** usar el token que espera la protección CSRF permite crear años sin desactivar esa protección ni modificar la regla de permisos del ERP. Leerlo al enviar la solicitud evita conservar un valor obsoleto en memoria.
- **Comprobación:** `npm run build` del ERP Web terminó correctamente, se reconstruyó únicamente el contenedor `erp-web` y `http://127.0.0.1:8089/oferta` respondió HTTP 200. La operación autenticada de creación no se ejecutó durante esta corrección; se debe comprobar en la sesión del usuario al volver a pulsar «Crear año».
- **Alcance:** solo se modificó el cliente HTTP del ERP Web. No se cambiaron el backend, los datos ni el esquema.

## 2026-11-19 — El año 2026 desaparecía del selector tras crear 2027

- **Problema:** antes de crear 2027, el selector de Oferta académica mostraba 2026; después mostraba únicamente 2027.
- **Causa verificada:** `ofe_anios` de la base actual `erp_boston_nuevo` contenía solo 2027. El formulario mostraba el año actual como opción provisional cuando la consulta no devolvía registros; 2026 nunca había sido guardado en esa base. `POST /oferta/anios` usa `INSERT` y no elimina otros años. La base anterior `erp_boston` conserva un período 2026 separado.
- **Cambio:** cuando no existen años guardados, el selector ahora indica «Aún no hay años registrados» y queda deshabilitado. Se insertó de forma idempotente el año 2026 en `ofe_anios` de la base actual, sin alterar el registro 2027.
- **Justificación:** el selector debe mostrar únicamente años persistidos y permitir consultar tanto el período en curso como la preparación del siguiente. La corrección de datos restablece 2026 sin sustituir ni borrar 2027.
- **Comprobación:** consulta directa de `ofe_anios` después de la corrección: `2026`, `2027`. `npm run build` terminó correctamente, se reconstruyó solo `erp-web` y `/oferta` respondió HTTP 200. La oferta histórica de la base anterior no se trasladó: en la base actual 2026 aún no tiene secciones ni asignaturas configuradas.
- **Alcance:** cambio puntual en el selector y en un registro de año de la base local. Sin cambios de esquema, Aula, Identidad ni ofertas de otros años.

## 2026-11-19 — Encabezado «Identificación» superpuesto en Registrar persona

- **Problema:** el segundo encabezado del formulario se dibujaba encima de los campos de «Condición inicial».
- **Causa:** la regla compartida de `.per-panel-cabecera` aplica un margen superior negativo de 24 px, adecuado para el primer encabezado de un panel, pero también se aplicaba a los encabezados posteriores del mismo formulario.
- **Cambio:** en el formulario de alta de Personas, los encabezados posteriores al primero tienen un margen superior positivo de 24 px.
- **Justificación:** cada bloque comienza después de los campos previos, sin superponer etiquetas ni controles y sin alterar el encabezado inicial ni otros paneles.
- **Comprobación:** `npm run build` del ERP Web terminó correctamente; se reconstruyó solo `erp-web`, `/personas/nueva` respondió HTTP 200 y la hoja CSS publicada contiene la regla corregida.
- **Alcance:** solo CSS del formulario de registro; sin cambios de negocio, API o datos.

## 2026-11-19 — Error 403 al registrar una persona

- **Problema:** el alta de una persona con condición Docente respondía `403` desde la pantalla de Personas.
- **Causa:** el cliente HTTP propio de Personas aún enviaba el valor `csrf` enmascarado de `/sesion` en `X-XSRF-TOKEN`. El permiso de registro sí admite a Coordinación Académica; el rechazo ocurría antes de completar la operación por la diferencia con el token de la cookie `XSRF-TOKEN`.
- **Cambio:** Personas reutiliza la lectura del token actual de la cookie que ya emplea el cliente HTTP general del ERP para las solicitudes de escritura.
- **Justificación:** elimina la discrepancia entre los dos clientes Web sin desactivar la protección CSRF ni ampliar permisos de registro.
- **Comprobación:** `npm run build` del ERP Web terminó correctamente, se reconstruyó solo `erp-web` y `/personas/nueva` respondió HTTP 200. No se ejecutó un alta autenticada ni se creó una persona de prueba durante esta corrección.
- **Alcance:** solo clientes HTTP del ERP Web; sin cambios de negocio, backend o datos.

## 2026-11-19 — Eliminación de una persona registrada por error

- **Problema:** una ficha creada con datos o DNI incorrectos no podía eliminarse; su documento seguía ocupado e impedía registrar a la persona correcta.
- **Cambio:** la ficha de Personas ofrece «Eliminar registro incorrecto» a Dirección y Coordinación. Requiere escribir `ELIMINAR` y confirmar. La API elimina la persona, identificación, condición inicial y evento de alta en una transacción; libera el documento. Conserva una constancia mínima con UUID, actor y fecha, sin nombres ni DNI.
- **Límite de seguridad:** la API rechaza con `409` la eliminación si hay matrículas (incluidos borradores que la referencian), parentescos, apoderamientos, asignaciones docentes, solicitudes externas ya procesadas o eventos posteriores al alta. Se mantienen permisos y CSRF. Para fichas con historial, corresponde corregir los datos mediante las acciones existentes.
- **Justificación:** permite rectificar un alta errónea antes de que el registro se use en procesos institucionales, sin borrar expedientes ni relaciones legítimas.
- **Comprobación:** prueba HTTP focalizada: eliminación autorizada `204`, ausencia de ficha, constancia creada, DNI reutilizable, rechazo `403` sin CSRF o permiso y `409` si existe un vínculo. `PersonasHttpTest` y compilación del ERP Web terminaron correctamente. Migración V5 aplicada en la base local; API saludable y `/personas/...` respondió `200`.
- **Alcance:** solo Personas en ERP API y ERP Web, migración V5 y esta bitácora. Se respaldó `erp_boston_nuevo` antes de la migración. No se eliminó ninguna persona real durante la implantación; la ficha de la captura sigue disponible para que el usuario decida.

## 2026-11-19 — Fichas relacionadas legibles en Personas

- **Problema:** la ficha mostraba UUID en el encabezado, Parentescos y Apoderamientos; no permitía abrir directamente a la persona relacionada. En Parentescos, «Desactivar» se partía y quedaba desalineado.
- **Cambio:** el encabezado presenta el documento principal o la identificación provisional. La API de la ficha entrega nombre y documento de las personas vinculadas; las filas muestran esos datos y enlazan a sus fichas. El botón de desactivación conserva una sola línea y queda centrado en su fila.
- **Justificación:** las personas usuarias pueden reconocer y consultar rápidamente al estudiante o apoderado sin interpretar identificadores técnicos. Los UUID continúan internamente para mantener los vínculos correctos.
- **Comprobación:** compilación del ERP Web y prueba HTTP focalizada de vínculos completadas; la prueba verificó el nombre y documento entregados en las referencias. No se cambiaron datos ni esquema.
- **Alcance:** ficha de Personas en ERP API y ERP Web, CSS de las filas vinculadas y esta bitácora.

## 2026-11-19 — Bloqueo temprano de matrícula anual duplicada

- **Problema:** al seleccionar a una estudiante ya matriculada en 2027, «Siguiente: apoderado» guardaba el borrador y avanzaba. La validación de duplicidad se ejecutaba recién al confirmar.
- **Causa verificada:** la base local contiene una matrícula `VIGENTE` de Sofía Ensayo Rojas en 2027; las rutas de crear y guardar borrador no consultaban matrículas anuales previas.
- **Cambio:** al crear o guardar un borrador con estudiante registrado y año seleccionado, el ERP comprueba si ya existe una matrícula `VIGENTE`, `RETIRADA` o `FINALIZADA` para esa persona y año. En ese caso devuelve un mensaje claro y no guarda ni avanza al apoderado. La comprobación se conserva antes de tratar el apoderado al confirmar. La navegación de pasos ya no permite saltar hacia adelante sin pasar por «Siguiente».
- **Justificación:** una matrícula anual existente debe continuarse en su expediente; el usuario no debe completar datos de apoderado para descubrir la duplicidad al final.
- **Comprobación:** prueba HTTP focalizada: crear y actualizar borrador duplicado devuelven `400` y no modifican los registros; compilación del ERP Web correcta. No se modificaron datos ni esquema.
- **Alcance:** únicamente Matrículas en ERP API y ERP Web, prueba focalizada y esta bitácora.

## 2026-11-20 — Contraseña personal desde cinco caracteres

- **Problema:** el primer ingreso a Aula impedía cambiar la clave temporal por una contraseña de menos de 12 caracteres; el mismo límite aparecía en el cambio posterior y la recuperación.
- **Cambio:** el formulario y la validación de Identidad aceptan contraseñas nuevas desde 5 caracteres en esos tres recorridos. No se exige combinación de mayúsculas, minúsculas ni símbolos. Se conserva la obligación de que la clave nueva del primer ingreso sea distinta de la temporal.
- **Justificación:** aplica la regla solicitada de forma consistente entre interfaz y servidor, evitando que un formulario permita una contraseña que otra ruta rechace. La creación administrativa de cuentas conserva su validación independiente.
- **Comprobación:** pruebas focalizadas de Identidad correctas; compilación de Aula Web correcta; Identidad y Aula Web reconstruidos en Docker. El formulario de primer ingreso respondió en local con `minlength="5"` y el texto actualizado. No se cambiaron contraseñas de cuentas existentes.
- **Alcance:** cambio de contraseña en Identidad y su interfaz de Aula; sin migraciones ni alteración de datos institucionales.

## 2026-11-20 — Restablecimiento inicial de cuentas académicas vigentes

- **Problema:** algunas cuentas docentes y estudiantiles existentes conservaban contraseñas personales anteriores; por ello el DNI ya no funcionaba como contraseña, aunque el usuario documental fuera correcto.
- **Cambio:** a solicitud del usuario se restablecieron, una sola vez, las cuentas activas correspondientes a la condición Docente vigente y a las matrículas Vigentes del ERP actual. Se utilizó el mecanismo de recuperación de Identidad, que revoca autorizaciones anteriores, y se activó el cambio obligatorio de clave para el siguiente ingreso. No se alteraron perfiles administrativos ni cuentas históricas sin persona académica habilitada en el ERP actual.
- **Justificación:** permite el acceso inicial acordado mediante DNI y obliga a elegir una clave personal después, sin hacer que las conciliaciones futuras sobrescriban las contraseñas elegidas por cada usuario.
- **Comprobación:** tres cuentas académicas activas quedaron con cambio obligatorio; el inicio de sesión HTTP con credencial inicial llegó en los tres casos a `/clave-inicial`. Se verificaron sus vínculos de cursos activos en Aula. No se registraron contraseñas ni tokens en esta bitácora.
- **Alcance:** datos de acceso en Identidad y esta bitácora; sin cambios de código, migraciones ni datos escolares del ERP o Aula.

## 2026-11-20 — Nombre institucional en Aula y aviso de sincronización heredada

- **Problema:** la cuenta docente mostraba el DNI `00000011` como nombre y la página de cursos advertía que el ERP estaba inaccesible pese a mostrar cursos actuales.
- **Causa:** «Mi cuenta» consultaba `/api/erp/v1/perfiles`, contrato que ya no existe en el ERP modular. El aviso leía `estado_sincronizacion`, donde quedó un error HTTP 401 del consumidor histórico; ese consumidor está desactivado y la integración vigente recibe proyecciones enviadas por el ERP.
- **Cambio:** el ERP envía el nombre completo de docentes y estudiantes a Aula mediante el contrato de integración autenticado. Aula conserva una proyección mínima por persona y tipo, la usa en «Mi cuenta», encabezado y menú lateral, y admite la longitud máxima de nombres vigente en Personas. Cuando opera la integración por proyecciones, el estado de cursos ya no interpreta el error del sincronizador heredado como un fallo actual.
- **Justificación:** muestra una identidad comprensible incluso si el ERP no responde momentáneamente y evita un aviso falso, sin alterar usuarios, contraseñas, cursos ni el historial académico.
- **Comprobación:** pruebas focalizadas de perfil, proyección, conciliación y estado de sincronización correctas; prueba de arquitectura ERP correcta. En local, la migración de Aula se aplicó, los perfiles de Ana Prueba Lagos, Manuel Docente Miranda, Luis Ensayo Rojas y Sofía Ensayo Rojas quedaron proyectados y las cuatro solicitudes correspondientes constan como `CONFIRMADA` en ERP. ERP, Aula e Identidad respondieron sanos tras la primera reconstrucción.
- **Alcance:** integración ERP→Aula, perfil y estado de sincronización de Aula, migraciones aditivas V26–V27 y esta bitácora. No se cambiaron datos de acceso ni se borraron registros o volúmenes.

## 2026-11-23 — Ficha institucional de Personas organizada en pestañas

- **Problema:** los datos personales, la identificación, las condiciones y los vínculos aparecían en tarjetas separadas; al editar, el formulario se abría al final de la ficha y quedaba alejado del dato correspondiente.
- **Cambio:** la ficha ahora tiene las pestañas «Datos personales» y «Condiciones institucionales», con el patrón visual de navegación del Aula. La primera reúne datos e identificación; la segunda reúne condiciones, parentescos y apoderamientos. Cada pestaña ofrece «Editar datos» según los permisos del usuario. Los formularios de actualización, incorporación, vinculación y desactivación se muestran dentro de su sección, sin abrir otra tarjeta al final de la página.
- **Justificación:** permite consultar y modificar cada grupo de información en el mismo contexto, con menos desplazamientos y sin cambiar las reglas ni los contratos de Personas.
- **Comprobación:** compilación de ERP Web correcta y seis pruebas focalizadas de Personas correctas, incluida la navegación y edición dentro de ambas pestañas. Se actualizó únicamente el contenedor `erp-web`; la página local `/personas` respondió HTTP 200 con el nuevo recurso JavaScript.
- **Alcance:** únicamente interfaz de la ficha de Personas, sus estilos, prueba focalizada y esta bitácora. Sin cambios en API, datos, migraciones, Aula ni Identidad.

## 2026-11-23 — Jerarquía de la ficha y parentescos desde ambas perspectivas

- **Problema:** cada pestaña repetía su título debajo de la navegación, las filas de datos ocupaban todo el ancho y el parentesco «Madre» se mostraba también en la ficha de la madre junto al nombre del estudiante, invirtiendo el sentido aparente de la relación.
- **Cambio:** se retiraron los títulos duplicados y se reorganizó la ficha en una cuadrícula compacta de datos e identificación y, en la segunda pestaña, tres grupos alineados de condiciones, parentescos y apoderamientos. Se añadieron transiciones discretas y diseños adaptables a pantallas estrechas. El parentesco se presenta desde la persona cuya ficha se consulta: en la ficha del estudiante se conserva el tipo registrado; en la ficha del familiar se muestra la relación inversa (hijo/a, nieto/a, hermano/a o sobrino/a según corresponda). Para elegir la forma masculina o femenina, la referencia de la persona relacionada incluye el sexo registrado; si falta, se utiliza una etiqueta neutral como «Hijo/a», sin inferirlo por el nombre. El tipo original permanece visible como contexto en la ficha del familiar.
- **Justificación:** evita atribuir erróneamente la maternidad o paternidad al estudiante, mantiene separados parentesco y apoderamiento y facilita la lectura rápida sin modificar el vínculo institucional guardado.
- **Comprobación:** compilación de ERP Web correcta; siete pruebas focalizadas de Personas Web correctas, incluidos ambos sentidos del parentesco y el caso sin sexo registrado; `PersonasHttpTest` correcto sobre una base aislada. `api-erp` y `erp-web` se actualizaron localmente y quedaron en funcionamiento.
- **Alcance:** presentación de Personas, un campo de lectura adicional en la referencia de vínculos de la API, pruebas focalizadas y esta bitácora. Sin cambios de datos, migraciones ni reglas de autorización.

## 2026-11-23 — Búsqueda flexible y sugerencias en ERP y Aula

- **Problema:** buscar nombres sin tilde, como «Sofia», no encontraba «Sofía». Los filtros de texto de cada pantalla comparaban de forma distinta y varios no sugerían coincidencias al escribir.
- **Cambio:** las consultas de Personas y Matrículas del ERP ahora comparan nombres y documentos sin distinguir mayúsculas ni tildes, admiten fragmentos y priorizan coincidencias por el comienzo del nombre o apellido. Los listados de Personas y Matrículas muestran sugerencias en vivo; los selectores de estudiantes, apoderados y docentes consultan desde el primer carácter. En Aula se aplicó la misma normalización a tareas, notas, clases, grabaciones, destinatarios y referencias de pago, con sugerencias en los filtros de texto. Las listas se reducen conforme avanza la escritura y permiten selección con teclado o ratón.
- **Justificación:** facilita encontrar registros aunque el operador desconozca la acentuación exacta y acelera la elección de resultados sin cambiar datos ni reglas institucionales.
- **Comprobación:** 11 pruebas HTTP focalizadas de Personas y Matrículas ERP correctas en PostgreSQL aislado; 11 pruebas focalizadas de ERP Web correctas, incluida la secuencia «Mar» → «Mari» y «sofia» → «Sofía»; tres pruebas existentes de Aula Web correctas. Ambas interfaces compilaron. Los contenedores locales `api-erp`, `erp-web` y `frontend` se reconstruyeron sin afectar volúmenes; ERP `/personas` y Aula `/clases` respondieron HTTP 200.
- **Alcance:** búsquedas de texto de ERP y Aula, sus pruebas focalizadas y esta bitácora. Sin cambios en Identidad, datos, migraciones ni políticas de acceso.

## 2026-11-23 — Sugerencia única por persona en el directorio

- **Problema:** el buscador de Personas mostraba dos sugerencias para el mismo registro: «Sofía Ensayo Rojas» y «Rojas Sofía Ensayo».
- **Cambio y justificación:** el desplegable usa una sola presentación del nombre por persona. La consulta del servidor sigue aceptando búsquedas por apellido y por el orden inverso, por lo que se conserva la capacidad de encontrar el registro sin duplicar opciones.
- **Comprobación:** siete pruebas focalizadas de Personas Web correctas, incluida una aserción de una sola sugerencia por registro; compilación de ERP Web correcta.
- **Alcance:** únicamente el desplegable del directorio de Personas, su prueba y esta bitácora.

## 2026-11-23 — Resultados de búsqueda en un solo lugar

- **Problema:** al buscar en el directorio de Personas, el mismo registro aparecía en el desplegable del campo y en la lista «Resultados».
- **Cambio y justificación:** los filtros de los listados de Personas y Matrículas muestran las coincidencias únicamente en sus respectivos cuadros de resultados, mientras se escribe. Se retiró el desplegable y su código sin afectar los selectores que requieren elegir explícitamente a una persona.
- **Comprobación:** diez pruebas focalizadas de ERP Web correctas y compilación correcta.
- **Alcance:** filtros de los dos listados, sus pruebas, estilos asociados y esta bitácora. Sin cambios en API, datos ni Aula.

## 2026-11-23 — Eliminar títulos repetidos bajo la cabecera del ERP

- **Problema:** las vistas principales repetían bajo la cabecera el nombre de la sección, como «Personas» y «Oferta del año».
- **Cambio y justificación:** se retiró el título grande inferior en Inicio, Personas, Oferta académica, Matrículas y Asignaciones docentes. Permanecen el título de la barra superior, el contexto breve y las acciones de cada vista. Los títulos de fichas y formularios específicos se conservan porque identifican el registro o trámite abierto.
- **Comprobación:** compilación de ERP Web correcta.
- **Alcance:** únicamente presentación de las vistas principales del ERP y esta bitácora; sin cambios en API, datos, Aula ni Identidad.
