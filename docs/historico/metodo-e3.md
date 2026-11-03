# Método E³ para revisar casos de uso

E³ separa tres preguntas. Se aplica a cada caso antes de declararlo terminado:

1. **E¹ — Esencial.** ¿El actor puede completar el proceso principal y obtener el resultado obligatorio? Comprobar entrada, reglas mínimas, persistencia y autorización.
2. **E² — Escenarios.** ¿Qué ocurre con variantes, errores, duplicados, cambios de estado, fallos de dependencias y accesos indebidos? Registrar tanto el resultado correcto como el defectuoso.
3. **E³ — Experiencia.** ¿La persona entiende qué pasó, qué puede hacer y dónde está? Revisar mensajes, navegación, estados vacíos, espera y diseño adaptable con acciones reales.

La revisión transversal se limita al riesgo del caso: seguridad, integridad, rendimiento, disponibilidad, recuperación y usabilidad. No sustituye las tres preguntas.

## Ficha de evaluación

La ficha es un registro separado del método. Cada fila debe contener código del caso o escenario, estado (`aprobado`, `defectuoso`, `pendiente` o `requiere decisión`), comportamiento esperado, resultado observado, evidencia reproducible y corrección aplicada. **Aprobado exige evidencia**; una inspección de código sin ejecución se identifica como tal y no equivale a una prueba integral.

## Plantilla reutilizable

```md
## CP-XX — Nombre
Objetivo esencial: ...
E¹: proceso principal y resultado obligatorio.
E²: escenarios positivos, negativos y excepciones.
E³: mensajes, navegación, estados y claridad.
Riesgos transversales pertinentes: ...

| Código | Estado | Esperado | Observado | Evidencia | Corrección |
|---|---|---|---|---|---|
| CP-XX.01 | pendiente | ... | ... | ... | ... |

Decisiones de negocio pendientes: ...
```

La evaluación debe distinguir lo comprobado en pruebas unitarias, integración local y revisión manual. Cuando una regla no ha sido aprobada institucionalmente, registrar `requiere decisión` y conservar el comportamiento provisional explícito.
