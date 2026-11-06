# Estándar PostgreSQL 18 y Flyway

Aplica a cada módulo propietario de datos. Una técnica indicada como **obligatoria** debe justificarse y verificarse; una indicada como **evaluar** se usa solo si el caso lo necesita. El diseño concreto y su evidencia pertenecen a `MODELO_DATOS.md` y `PRUEBAS.md` del módulo. Referencias técnicas: [restricciones PostgreSQL 18](https://www.postgresql.org/docs/18/ddl-constraints.html), [CREATE TABLE y `WITHOUT OVERLAPS`](https://www.postgresql.org/docs/18/sql-createtable.html), [migraciones Flyway](https://documentation.red-gate.com/fd/migrations-271585107.html).

## 1. Modelo antes de SQL

| Nivel | Decisión obligatoria | Verificación |
|---|---|---|
| Conceptual | Entidades, hechos, actores, propiedad por módulo, cardinalidades, vigencias e identidad. | Diagrama o tabla que cubra cada regla institucional aprobada; conflictos y pendientes explícitos. |
| Lógico | Atributos, claves, catálogos, relaciones, nulabilidad y normalización. | Trazar RF/RI/CU a invariantes; evitar duplicar datos derivados. Desnormalizar solo con consulta y costo medidos. |
| Físico | Tipos PostgreSQL, restricciones, índices, migración y límites transaccionales. | SQL reproducible desde esquema vacío y pruebas reales de integridad. |

Cada entidad tiene identificador estable; elegir UUID si debe sobrevivir correcciones o integraciones, y claves naturales solo si su estabilidad está aprobada. Una FK identifica al propietario, no transfiere propiedad de la tabla. Definir `ON DELETE` deliberadamente, especialmente con datos históricos. Los catálogos configurables no se sustituyen con `CHECK` rígido sin decisión. Para historial, definir inicio, fin, semántica `[inicio, fin)`, zona horaria y si se permiten fechas futuras antes de implementar. No usar un `activo` ambiguo si su vigencia se deriva de períodos, salvo necesidad aprobada.

## 2. Integridad y duplicados

| Técnica | Regla de uso | Prueba mínima |
|---|---|---|
| `PRIMARY KEY`, `FOREIGN KEY`, `NOT NULL` | **Obligatorias** para identidad, relaciones y campos exigidos por fila. | Inserción válida; FK huérfana y nulo requerido rechazados. |
| `CHECK` | **Obligatorio cuando** la regla es local e inmutable en una fila. No consultar otras tablas desde `CHECK`. | Límites válidos y fuera de rango. |
| `UNIQUE` | **Obligatorio cuando** exista unicidad institucional o técnica. Documentar semántica de `NULL` y normalización. | Dos escrituras concurrentes del mismo valor no crean duplicado. |
| Índice único parcial | **Evaluar** para unicidad de un subconjunto, por ejemplo solo filas abiertas. | Duplicado del subconjunto rechazado; histórico válido permitido. |
| Exclusión GiST / `WITHOUT OVERLAPS` | **Evaluar y aplicar cuando** períodos de una clave no puedan solaparse. En PostgreSQL 18 `WITHOUT OVERLAPS` exige rango/multirango no vacío y soporte GiST para otras claves (`btree_gist` si corresponde). | Intervalos contiguos permitidos, solapados rechazados incluso con concurrencia. |
| Trigger diferido | **Último recurso justificado** para invariantes entre tablas que FK/CHECK/UNIQUE no expresan; documentar costo, orden, transacción y errores. | Probar commit válido, commit inválido, rollback y concurrentes. |

Normalizar identificadores antes de comparar, sin destruir información significativa. La preconsulta de duplicado mejora UX pero **no sustituye** restricción en BD. No crear triggers, particionamiento, GIN/GiST, caché ni columnas derivadas en todos los módulos por defecto. En `MODELO_DATOS.md` registrar cada invariante, mecanismo SQL, motivo y prueba.

## 3. Transacciones y concurrencia

La operación de negocio que debe ser indivisible tiene una frontera transaccional local documentada. Las validaciones y mutaciones relacionadas, incluida auditoría técnica exigida, se confirman o revierten juntas. Nunca mantener una transacción de BD abierta durante llamadas HTTP a Identidad o Aula; coordinar entre servicios por contrato, idempotencia y mensajería/outbox cuando la operación lo requiera. Probar error a mitad del caso y ausencia de estado parcial.

`READ COMMITTED` es el punto de partida, no una garantía de invariantes: considerar bloqueos de filas, restricciones, versión optimista o aislamiento más fuerte según la carrera observada. Definir orden de bloqueos para reducir interbloqueos, tratar `serialization_failure`/conflicto de unicidad con respuesta controlada y reintento solo si la operación es idempotente. Evitar pérdida de actualización con versión o condición `UPDATE ... WHERE version = ?`, y verificar dos escritores simultáneos. La estrategia de borrado/historial debe preservar referencias y responsabilidades institucionales.

## 4. Consultas y rendimiento

Diseñar índices desde filtros, uniones, orden y cardinalidad reales. Usar parámetros, límites explícitos y paginación estable; decidir `OFFSET` o cursor según volumen y navegación. Separar búsqueda exacta de flexible; `pg_trgm` es opcional si los datos y consulta lo justifican. Evaluar `EXPLAIN (ANALYZE, BUFFERS)` con datos sintéticos representativos, estadísticas actualizadas, selectividad y concurrencia. Un `Seq Scan` no demuestra por sí solo un defecto; comparar latencia, buffers y plan de alternativas. Registrar tamaño, consultas, hardware, p95/p99 y alcance (JDBC, HTTP o navegador). El coste de escrituras y mantenimiento de índices cuenta. No optimizar prematuramente.

## 5. Evolución y recuperación

Cada cambio físico usa nueva migración Flyway versionada y revisada. **No editar V1/V2 ya aplicadas** ni restablecer checksums para ocultar diferencias; una corrección va en una migración posterior. Probar aplicación desde base vacía y actualización de una base de prueba con esquema anterior. Cuidar compatibilidad de despliegue: ampliar, migrar datos, cambiar lectores, retirar solo cuando sea seguro. Antes de cambios destructivos, verificar copia, volumen, restauración ensayada y alcance; proteger `erp_boston` histórica y `erp_boston_nuevo`. Probar integridad en PostgreSQL 18 real con Testcontainers; un mock de repositorio no valida restricciones SQL. Documentar plan de reversión o recuperación y registrar la versión de esquema evaluada.
