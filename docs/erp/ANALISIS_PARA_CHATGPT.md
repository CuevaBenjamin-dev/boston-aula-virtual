# Sistema BBS — análisis ERP Fase 1 para revisión

## Resumen ejecutivo

Se inventariaron todos los métodos declarados de `ErpV1Service` e `Institucional`, sus consumidores, reglas, SQL, transacciones y permisos. La organización en cinco paquetes existe, pero los dos servicios raíz aún concentran las operaciones principales. Los controladores V1 ya se agrupan por módulo y la API heredada `/api/erp` sigue activa; ambas presentan diferencias de validación, permisos y flujo que deben caracterizarse antes de extraer código. Evidencia: [matriz método por método](MATRIZ_RESPONSABILIDADES.md) y [`ArquitecturaModularTest`](../../apps/erp/api/src/test/java/pe/boston/erp/ArquitecturaModularTest.java#L11).

La mayor incertidumbre técnica es la creación de cuenta docente: el ERP hace POST a Identidad dentro de una transacción local y puede dejar una cuenta remota creada si el commit local falla. La publicación al Aula usa outbox en el mismo PostgreSQL y depende de la transacción del llamador. Los tests actuales verifican módulos, migraciones, dos rechazos de matrícula, sesión/BFF y Tesorería, pero no caracterizan la mayoría de los flujos SQL/HTTP. Evidencia y escenarios concretos: [inventario de pruebas](INVENTARIO_PRUEBAS_PENDIENTES.md).

El [plan incremental](PLAN_MODULARIZACION.md) propone congelar primero contratos y pruebas, extraer infraestructura técnica, Personas, Oferta, Matrículas por subfases y Asignaciones; conservar las rutas heredadas mediante adaptadores. Documentos permanece delimitado sin inventar flujos: sólo hay tabla de metadatos y paquete. El diagrama y las propuestas de puertos evitan dependencias circulares Java; la consulta de snapshot del outbox que lee varias tablas queda como excepción técnica temporal **por aprobar**. Tesorería heredada queda fuera de esta extracción.

## Para decidir antes de la fase 2

1. Aprobar o ajustar las interfaces públicas y la excepción de lectura del outbox propuestas en el [plan](PLAN_MODULARIZACION.md#dependencias-y-contratos-publicos-propuestos).
2. Decidir cómo conservar o converger las reglas distintas de `/api/erp` y `/api/erp/v1`, especialmente matrícula directa, secciones/ofertas y autorización, sin cambiar consumidores de Aula ni ERP Web inadvertidamente. Ver [diferencias](PLAN_MODULARIZACION.md#diferencias-de-contrato-que-se-deben-congelar-antes-de-extraer).
3. Definir manejo de creación remota de cuentas docentes frente a fallo, timeout o reintento; y aprobar reglas institucionales aún abiertas (permisos por recurso, documentos, importación y pagos). Ver [riesgos y decisiones](PLAN_MODULARIZACION.md#decisiones-y-puertas-de-aprobacion).
4. Implementar y ejecutar la caracterización requerida para cada tarea antes de mover código. Ver [C01–C15](INVENTARIO_PRUEBAS_PENDIENTES.md#paquete-minimo-de-caracterizacion-antes-de-mover-codigo).

## Estado de esta entrega

Sólo se añadieron estos cuatro documentos locales. No se modificaron clases productivas, controladores, datos, migraciones ni infraestructura. No se hizo commit ni push. No se ejecutó refactorización ni se afirma que haya pasado una suite de regresión.

- [Plan de modularización](PLAN_MODULARIZACION.md)
- [Matriz de responsabilidades](MATRIZ_RESPONSABILIDADES.md)
- [Inventario de pruebas pendientes](INVENTARIO_PRUEBAS_PENDIENTES.md)
