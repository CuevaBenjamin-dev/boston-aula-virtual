# Asignaciones docentes — índice del módulo

Vinculación de docentes con oferta académica, pendiente de requisitos. No mantiene identidad ni la estructura ofertada. **Estado:** las cinco verificaciones están Pendiente; solo existe estructura de paquetes.

**Dependencias a evaluar:** Personas y Oferta son fuentes potenciales; el acceso docente a alumnos en Personas depende de un contrato verificable futuro. No hay dependencias funcionales implementadas entre módulos; `plataforma` solo aporta soporte técnico.

| Necesidad | Documento |
|---|---|
| Alcance, reglas y decisiones | [Especificación](ESPECIFICACION.md) |
| Componentes y contratos propuestos | [Arquitectura](ARQUITECTURA.md) |
| Diseño de datos y vacíos | [Modelo de datos](MODELO_DATOS.md) |
| Cobertura y evidencia | [Pruebas](PRUEBAS.md) |
| Cinco verificaciones | [Estado](ESTADO.md) |

Código preparado: [backend](../../../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/package-info.java), [ERP Web](../../../../apps/erp/web/src/modules/asignaciones/.gitkeep), [pruebas](../../../../apps/erp/api/src/test/java/pe/boston/erp/asignaciones/.gitkeep). Para nueva implementación: `cd apps/erp/api; mvn test` y `cd apps/erp/web; npm run build`, según código afectado. Abrir el [índice de ingeniería](../../ingenieria/README.md) y solo los estándares relevantes.
