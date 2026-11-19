# Pruebas — Oferta académica

**Evidencia nueva (19/11/2026):** [PersonasHttpTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java) crea año, sección y asignatura vía HTTP y continúa hasta una matrícula en PostgreSQL 18.6. La suite completa tuvo 19 pruebas sin fallos. El [test modular](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java) comprueba siete módulos. Quedan escenarios de calendario, edición y años paralelos.

**Matriz pendiente:** Probar integridad de catálogos, publicaciones, cambios con consumidores, permisos y paginación cuando existan casos aprobados. Una vez aprobados RF/RI/CU, relacionar cada criterio con caso, prueba, comando, entorno, fecha/commit, resultado y defecto. Aplicar [estándar de verificación](../../ingenieria/ESTANDAR_VERIFICACION.md). No declarar aprobadas pruebas aún inexistentes.
