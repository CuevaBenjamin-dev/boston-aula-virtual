# Estándar UX/UI del ERP

Cada pantalla debe corresponder a una tarea y un módulo; el diseño concreto queda en su `ARQUITECTURA.md`. Usar la navegación y componentes compartidos del ERP cuando resuelvan la misma interacción. No crear formularios, confirmaciones o aprobaciones que la regla institucional no requiere.

| Decisión | Aplicación | Comprobación |
|---|---|---|
| Contexto | Mostrar ubicación, estado y siguiente acción; pedir solo datos necesarios para el caso actual. | Recorrido del flujo principal y alternativo. |
| Formularios | Etiquetas, ayudas breves, validación junto al campo, errores que explican corrección; preservar lo ingresado ante errores o retroceso. | Prueba de validación y pérdida de datos. |
| Listas | Búsqueda, filtros y paginación cuando volumen lo amerite; encabezado separado visualmente de resultados. | Estado vacío y filtros combinados. |
| Estados | Carga, vacío, error, éxito y falta de permiso distinguibles, con acción de recuperación cuando exista. | Tests de componentes y recorrido manual. |
| Responsive | Evitar desbordes y pérdida funcional en tamaños previstos; no asumir solo escritorio. | Revisión de anchos relevantes del producto. |
| Accesibilidad | **Objetivo WCAG 2.2 AA**: teclado, foco visible, orden lógico, etiquetas, contraste, mensajes anunciados, errores identificados. | Automatización y revisión manual con teclado/lector; nunca declarar conformidad por una sola prueba axe. |

El frontend muestra acciones según capacidades para evitar confusión, pero el servidor sigue siendo autoridad. Para conformidad AA se evalúa la página y sus estados completos según [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/). Registrar cobertura y pendientes en `PRUEBAS.md`.
