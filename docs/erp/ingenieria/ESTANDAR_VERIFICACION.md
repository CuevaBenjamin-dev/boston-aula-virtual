# Estándar de verificación y evidencia

Cada módulo registra estas **cinco** verificaciones en `ESTADO.md` con estado, enlace de evidencia, pendiente y dependencia/bloqueo. `PRUEBAS.md` detalla ejecuciones. Una prueba aislada aprobada no cierra una verificación si falta su cobertura.

| Verificación | Objetivo y evidencia requerida | `Completada` solo si… | Incumplimiento frecuente / pendiente a registrar |
|---|---|---|---|
| 1. Requisitos | Alcance, actores, RF, RNF medibles, RI, restricciones y decisiones aprobadas/pendientes trazables. | El alcance implementable tiene reglas consistentes y aprobación institucional identificable. | Código histórico tomado como política; anotar catálogo/facultad sin resolver. |
| 2. Análisis funcional | CU, flujos normales y alternos, excepciones, permisos, aceptación e interacciones. | Cada caso cubre requisitos, errores y ámbito, con criterios verificables. | Solo camino feliz; anotar flujo o dependencia sin definir. |
| 3. Arquitectura | Componentes, datos, restricciones, contratos, seguridad, UX, transacciones y estrategia de pruebas revisados. | Riesgos críticos e integridad resueltos para el alcance y decisiones técnicas explícitas. | Diseño verbal sin SQL/contratos; anotar aprobación física pendiente. |
| 4. Implementación | Código y migraciones nuevas trazables, pruebas junto al código, revisión de dependencias, resultado de build. | Alcance comprometido existe y sus pruebas focalizadas pasan; no quedan funciones declaradas sin implementar. | Build tratado como aceptación funcional; anotar contratos/roles aún sin integrar. |
| 5. Verificación y pruebas | Matriz RF/RI/CU a unitarias, PostgreSQL real, integración, HTTP, seguridad, concurrencia, arquitectura, frontend, rendimiento, accesibilidad y navegador **según riesgo**; comandos, entorno, fecha/commit y resultados. | Los criterios se comprueban con evidencia suficiente y los defectos bloqueantes están resueltos. | Solo tests unitarios, datos no representativos o sin sesión real; anotar cobertura faltante y razón. |

Estados: **Pendiente** (sin trabajo demostrable), **En progreso** (evidencia parcial), **Bloqueada** (impedimento concreto que detiene la verificación), **Completada** (criterio íntegro y evidencia enlazada). Un bloqueo parcial se explica sin borrar avances; no obliga automáticamente a marcar toda la verificación `Bloqueada` si continúa trabajo útil. Fecha y versión de evidencia deben distinguirse del estado de hoy.

Resultados de cada prueba: **aprobada** (ejecutada y cumple), **fallida** (ejecutada y no cumple), **no ejecutada**, **bloqueada** (impedimento identificado), **evidencia insuficiente** (ejecución o alcance no demuestra el criterio). Compilación y build prueban construcción, no cobertura de seguridad, integración o WCAG. Un informe anterior se cita como histórico y no se presenta como nueva ejecución.
