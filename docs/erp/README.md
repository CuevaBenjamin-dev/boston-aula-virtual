# ERP institucional — estado operativo

El ERP continúa como monolito modular con siete responsabilidades: Personas, Oferta académica, Matrículas, Asignaciones docentes, Asistencia, Documentos y Atención institucional. `plataforma` contiene soporte técnico. Solo Dirección y Coordinación Académica inician sesión en el ERP. Las condiciones de Persona no conceden acceso.

Esta fase implementa una primera vertical con persistencia real y muestra explícitamente lo que aún carece de integración. Las decisiones posteriores a MODULA revisión 532 provienen del paquete funcional recibido el 19/11/2026; **no constan como importadas o reaprobadas en MODULA**. La implementación y las cinco verificaciones documentales no se consideran completas por el solo hecho de compilar.

## Tablero

| Módulo | Situación ejecutable | Documento |
|---|---|---|
| Personas | Directorio, ficha, identificación, condiciones y vínculos; cambios de cuenta pendientes de integración | [Estado](modulos/personas/ESTADO.md) |
| Oferta académica | Alta/consulta de año, secciones y asignaturas por año/grado | [Estado](modulos/oferta/ESTADO.md) |
| Matrículas | Borrador, confirmación validada, retirada, reincorporación y cierre anual API; solicitudes remotas pendientes | [Estado](modulos/matriculas/ESTADO.md) |
| Asignaciones docentes | Alta/consulta/cierre de asignación y alerta de cobertura por docente inactivo; Aula pendiente | [Estado](modulos/asignaciones/ESTADO.md) |
| Asistencia | En desarrollo | [Incremento](INCREMENTO_MVP_2026-11-19.md) |
| Documentos | En desarrollo | [Estado](modulos/documentos/ESTADO.md) |
| Atención institucional | En desarrollo | [Incremento](INCREMENTO_MVP_2026-11-19.md) |

El [incremento MVP](INCREMENTO_MVP_2026-11-19.md) identifica rutas de código, límites y pasos de prueba. El [índice de ingeniería](ingenieria/README.md) conduce al proceso y a los estándares. Las [políticas transversales](POLITICAS_TRANSVERSALES.md) distinguen decisiones actuales de contratos pendientes.

## Ejecución local

Desde la raíz: `docker compose up -d --build`. ERP Web: <http://127.0.0.1:8089>; Aula: <http://localhost:8088>; Identidad: <http://127.0.0.1:9000>. No se eliminan volúmenes. La cuenta local de Dirección o Coordinación se configura según el [README principal](../../README.md), sin publicar claves. La base ERP es `erp_boston_nuevo`, separada de las bases de Aula e Identidad.

## Contexto histórico

El estado anterior está en el tag local `backup/erp-before-restart-20261104` (commit `7b6589ac92e90aebc51fa39ebabc653c8045cb76`). La base ERP anterior y su volumen se conservan. `docs/historico/` registra la evolución, no los contratos nuevos de negocio.
