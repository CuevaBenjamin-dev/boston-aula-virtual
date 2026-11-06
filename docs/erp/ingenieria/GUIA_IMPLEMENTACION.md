# Guía operativa para Codex y desarrolladores

Recibir un único prompt consolidado con objetivo, alcance, decisiones aprobadas y restricciones. Dividirlo **internamente** en incrementos verificables sin obligar al usuario a repetir instrucciones. Usar el [índice](README.md) para lectura selectiva. Las pruebas se escriben junto al cambio; la secuencia siguiente ordena decisiones, no impide iterar.

| Paso | Acción y salida verificable |
|---|---|
| 1 | Inspeccionar rama, HEAD, `git status`, AGENTS, código y datos afectados; preservar cambios ajenos. |
| 2 | Identificar módulo, caso de uso y verificaciones afectadas. |
| 3 | Abrir índice y secciones pertinentes, incluida política transversal aplicable. |
| 4 | Separar decisiones aprobadas de propuestas y pendientes; no inferir política desde código histórico. |
| 5 | Identificar dependencias, contratos, riesgos de integridad/seguridad y pruebas necesarias. |
| 6 | Diseñar cambio mínimo, criterios de aceptación y plan de reversión cuando aplique. |
| 7 | Diseñar primero integridad de datos y migración nueva **si hay cambio físico**. |
| 8 | Desarrollar dominio e invariantes con pruebas cercanas. |
| 9 | Implementar persistencia y contratos públicos sin importar detalles internos ajenos. |
| 10 | Implementar API, validación y autorización por operación/recurso/ámbito. |
| 11 | Implementar frontend y estados de interacción cuando el caso lo requiera. |
| 12 | Ejecutar pruebas focalizadas durante el desarrollo; registrar fallos y correcciones. |
| 13 | Comprobar integración, límites modulares, regresiones, seguridad y UX según alcance. |
| 14 | Actualizar seis documentos del módulo y enlaces/evidencia; no elevar estados sin criterio. |
| 15 | Entregar informe con archivos, comportamiento, pruebas aprobadas/fallidas/no ejecutadas/bloqueadas, pendientes, riesgos y Git final. |

Si falta decisión institucional, avanzar solo en trabajo independiente y anotar la operación dependiente como pendiente o bloqueada. Una falla de prueba se investiga y se informa; no se reetiqueta como éxito. Antes de borrar datos, volúmenes o respaldos, comprobar autorización explícita y objetivo. No modificar migraciones aplicadas ni otros servicios por conveniencia. `git diff --check` y `git status` cierran la revisión. Para compartir con ChatGPT, enlazar fuentes originales y el informe, evitando secretos o informes que presenten resultados antiguos como actuales.
