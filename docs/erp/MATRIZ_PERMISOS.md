# ERP: matriz dinámica de permisos — especificación UX y funcional

**Estado al 2026-11-04: propuesta de diseño, sin concesiones implementadas.** La autoridad institucional debe aprobar permisos y ámbitos antes de conectar esta pantalla a escrituras. [Reglas RI-02 a RI-05](REGLAS_INSTITUCIONALES.md) son la única base institucional recibida.

## Modelo visible

| Control | Comportamiento esperado | Validación pendiente |
|---|---|---|
| Selector de sujeto | Alternar **Rol** o **Usuario**, buscar por nombre/código y mostrar estado de cuenta. | Identificador estable ERP↔Identidad y quién puede administrar cada sujeto. |
| Tabla | Filas agrupadas por **módulo** y **acción**; columnas «Heredado», «Individual», «Denegado» y «Efectivo». | Catálogo de acciones aprobado y prioridad entre herencia y denegación. |
| Ámbito | Mostrar institución, periodo, grado, sección y recurso cuando apliquen; opciones inválidas deshabilitadas. | Semántica exacta de alcance y herencia. |
| Vista previa | Comparar permisos efectivos antes/después, fuente de cada permiso y conflictos. | Algoritmo institucional de resolución. |
| Confirmación | Resumen de cambios sensibles, actor, motivo y confirmación explícita; rechazar edición sin facultad. | Qué acciones son sensibles y si requieren doble aprobación. |
| Auditoría | Historial por sujeto/acción/ámbito, fecha, actor, antes/después y origen. | Retención y visibilidad del historial. |

## Dirección visual y accesibilidad

1. Cabecera: sujeto y estado de cuenta; segundo nivel: búsqueda y ámbito. Mantener selección visible al desplazarse.
2. Secciones de módulos plegables con contador de acciones modificadas; cada fila muestra acción, descripción breve y permiso efectivo destacado en texto y color.
3. Diferenciar heredado, individual y denegado mediante etiquetas legibles y símbolos, nunca sólo color. La vista previa aparece en un panel junto a la tabla en escritorio y debajo en pantallas estrechas.
4. En móvil, transformar la tabla en tarjetas por acción; controles y resumen permanecen operables sin desplazamiento horizontal. Navegación por teclado, foco visible, etiquetas asociadas, regiones `aria-live` para conflictos y mensajes de error junto al control.
5. Mostrar el estado «Suspendida» como acceso efectivo denegado conservando el historial. La UI no debe permitir reactivar por inferencia de otro permiso.

```mermaid
flowchart LR
  Sujeto[Seleccionar rol o usuario] --> Ambito[Elegir ámbito]
  Ambito --> Editar[Preparar cambios]
  Editar --> Previa[Vista previa efectiva]
  Previa --> Confirmar[Confirmación sensible]
  Confirmar --> Auditar[Registro de auditoría]
```

## Puertas antes de implementar

- Aprobar la matriz institucional de acciones, roles, sujetos, ámbitos, herencia y denegación; registrar versiones de la política.
- Definir quién solicita, aprueba y aplica cambios; RI-03 restringe la modificación a Dirección y Administración.
- Diseñar pruebas de autorización por operación y recurso, elevación indebida, cuenta suspendida, concurrencia y auditoría. Véanse C09/C12 en [inventario](INVENTARIO_PRUEBAS_PENDIENTES.md).
- Mantener el motor de Identidad como autoridad de autenticación. La pantalla no crea permisos efectivos mediante valores locales sin contrato aprobado.

No se agregó interfaz ejecutable en ERP Web: el diseño visual desacoplado sería prematuro sin catálogo de acciones y sin servicio de vista previa.
