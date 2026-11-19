# Pruebas — Matrículas

**Evidencia nueva (19/11/2026):** [PersonasHttpTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java) cubre el flujo de borrador, rechazo de menor sin apoderado, confirmación, solicitudes externas, retirada y reincorporación sobre PostgreSQL 18.6. La suite completa tuvo 19 pruebas sin fallos. El [test modular](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java) comprueba siete módulos. Quedan las variantes del [incremento MVP](../../INCREMENTO_MVP_2026-11-19.md).

**Matriz pendiente:** Probar transiciones válidas/inválidas, duplicados por período, autorizaciones, concurrencia y rollback cuando se apruebe la especificación. Una vez aprobados RF/RI/CU, relacionar cada criterio con caso, prueba, comando, entorno, fecha/commit, resultado y defecto. Aplicar [estándar de verificación](../../ingenieria/ESTANDAR_VERIFICACION.md). No declarar aprobadas pruebas aún inexistentes.
