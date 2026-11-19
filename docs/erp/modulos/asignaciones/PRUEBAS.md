# Pruebas — Asignaciones docentes

**Evidencia nueva (19/11/2026):** [PersonasHttpTest](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/PersonasHttpTest.java) registra docente, crea sección/asignatura compatibles, asigna, consulta y cierra vía HTTP con PostgreSQL 18.6. La suite completa tuvo 19 pruebas sin fallos. El [test modular](../../../../apps/erp/api/src/test/java/pe/boston/erp/arquitectura/ArquitecturaModularTest.java) comprueba siete módulos. Quedan horarios estructurados, reemplazos y concurrencia.

**Matriz pendiente:** Probar vigencias, solapes según política, ámbitos de acceso, cambios concurrentes y contrato con Personas cuando se implemente. Una vez aprobados RF/RI/CU, relacionar cada criterio con caso, prueba, comando, entorno, fecha/commit, resultado y defecto. Aplicar [estándar de verificación](../../ingenieria/ESTANDAR_VERIFICACION.md). No declarar aprobadas pruebas aún inexistentes.
