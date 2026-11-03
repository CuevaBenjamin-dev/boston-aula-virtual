# CP-10 · Gestionar grabaciones

## E¹ · Resultado esencial

El docente asignado registra referencias de video para clases académicas finalizadas. Cada parte puede quedar oculta o publicarse por separado. Los estudiantes matriculados consultan solamente las publicadas y abren la URL en otra pestaña mediante «Ver en YouTube». Aula no almacena videos, no los reproduce y no usa la API de YouTube.

## E² · Reglas y escenarios aplicados

| Escenario | Regla |
| --- | --- |
| Registro | Solo una clase `FINALIZADA` de un curso asignado. Curso, grado, sección, docente y fecha se derivan de la clase. El orden se asigna automáticamente si se omite, admite corrección y no tiene un máximo fijo. |
| Borrador y publicación | La nueva referencia queda `OCULTA` salvo publicación explícita. El aviso a estudiantes con matrícula activa se emite solo al publicar, una vez por evento y destinatario. |
| Consulta | Estudiante: partes `PUBLICADA` de sus cursos vigentes. Docente y Coordinación: ámbito académico actual. Administración: consulta institucional. Filtros existentes por curso, grado, sección y fecha, más búsqueda por título. |
| Edición | Cambios de título, URL u orden; ocultamiento, republicación y retirada conservan historial, actor y auditoría. Corregir la URL publicada avisa a los estudiantes afectados. |
| Enlaces | Por indicación expresa del usuario se acepta cualquier URL HTTP(S) elegida por el docente, sin exigir dominio ni identificador de YouTube. Se rechazan esquemas inseguros, credenciales incrustadas, caracteres de control y formato inválido. No se comprueba disponibilidad externa. |
| Integridad | Una parte y una URL activas no se duplican dentro de la misma clase. Una URL puede reutilizarse en otra clase. Los identificadores de operación evitan registros y avisos repetidos por reintento. |
| Desactivación | Se reutiliza el interruptor conjunto de CP-09; desactivar impide las operaciones de clases y grabaciones y conserva los datos. |

## Reutilización y brechas corregidas

Se reutilizaron clases finalizadas CP-09, cursos y asignaciones sincronizados con ERP, autorizaciones académicas, notificaciones, auditoría, la página Grabaciones y el interruptor institucional. Se cerraron las brechas de estado por parte, numeración, publicación manual, historial y retirada, notificación de grabación, búsqueda por título y autorización de lectura. El historial de enlaces ocultos queda reservado al personal académico.

## Evidencia técnica

`V24__grabaciones_cp10.sql` añade título, estado `RETIRADA`, historial, clave de operación, unicidad de partes/URL activas y avisos de grabación. `GrabacionesYoutube` valida en backend la clase y el curso vigentes, persiste los cambios en transacción y expone consulta diferenciada por rol. `YoutubeUrl` conserva validación HTTP(S) genérica conforme a la excepción solicitada. `pruebas/cp10-cp11-cp13-verificar-flujos.py` pasó con Carlos, Patricia y Roberto: finalizar, crear, reintentar sin duplicar, publicar, consultar como estudiante y rechazar al docente ajeno. También pasó partes múltiples, edición, ocultamiento, retirada, historial privado y URL insegura. Eliminó las clases temporales al terminar. La compilación de API Aula y frontend pasó; la página Grabaciones cargó en navegador local.

## Pendientes y ficha de evaluación

- En producción, confirmar la política institucional sobre proveedores de video y enlaces externos. La disponibilidad y titularidad del recurso no se verifican desde Aula.
- E³ — Experiencia UX/UI está pendiente; los controles añadidos son funcionales y no constituyen evaluación visual.

| Criterio | Estado |
| --- | --- |
| Fuente académica | ERP sincronizado para docente y matrícula; clase CP-09 para contexto. |
| Seguridad | Permisos comprobados por API y esquema HTTP(S) seguro. |
| Consistencia | Historial y auditoría transaccionales; unicidad de clase/parte/URL y aviso por evento. |
| Alcance | E¹ y E² implementados; E³ pendiente. |
