# Oferta académica — índice del módulo

Definición de períodos y estructura académica ofertada, aún sin modelo institucional aprobado en esta base. No matricula ni asigna docentes. **Estado:** las cinco verificaciones están Pendiente; solo existe estructura de paquetes.

**Dependencias a evaluar:** Matrículas y Asignaciones serán consumidores potenciales mediante contratos por definir. No hay dependencias funcionales implementadas entre módulos; `plataforma` solo aporta soporte técnico.

| Necesidad | Documento |
|---|---|
| Alcance, reglas y decisiones | [Especificación](ESPECIFICACION.md) |
| Componentes y contratos propuestos | [Arquitectura](ARQUITECTURA.md) |
| Diseño de datos y vacíos | [Modelo de datos](MODELO_DATOS.md) |
| Cobertura y evidencia | [Pruebas](PRUEBAS.md) |
| Cinco verificaciones | [Estado](ESTADO.md) |

Código preparado: [backend](../../../../apps/erp/api/src/main/java/pe/boston/erp/oferta/package-info.java), [ERP Web](../../../../apps/erp/web/src/modules/oferta/.gitkeep), [pruebas](../../../../apps/erp/api/src/test/java/pe/boston/erp/oferta/.gitkeep). Para nueva implementación: `cd apps/erp/api; mvn test` y `cd apps/erp/web; npm run build`, según código afectado. Abrir el [índice de ingeniería](../../ingenieria/README.md) y solo los estándares relevantes.
