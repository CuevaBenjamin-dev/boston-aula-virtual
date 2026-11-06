# Documentos — índice del módulo

Custodia y acceso a documentos institucionales del ERP, aún sin catálogo ni flujo aprobado. No modifica Personas, Matrículas u Oferta. **Estado:** las cinco verificaciones están Pendiente; solo existe estructura de paquetes.

**Dependencias a evaluar:** Los módulos propietarios podrán referenciar documentos mediante contratos aún no definidos; no se presupone almacenamiento. No hay dependencias funcionales implementadas entre módulos; `plataforma` solo aporta soporte técnico.

| Necesidad | Documento |
|---|---|
| Alcance, reglas y decisiones | [Especificación](ESPECIFICACION.md) |
| Componentes y contratos propuestos | [Arquitectura](ARQUITECTURA.md) |
| Diseño de datos y vacíos | [Modelo de datos](MODELO_DATOS.md) |
| Cobertura y evidencia | [Pruebas](PRUEBAS.md) |
| Cinco verificaciones | [Estado](ESTADO.md) |

Código preparado: [backend](../../../../apps/erp/api/src/main/java/pe/boston/erp/documentos/package-info.java), [ERP Web](../../../../apps/erp/web/src/modules/documentos/.gitkeep), [pruebas](../../../../apps/erp/api/src/test/java/pe/boston/erp/documentos/.gitkeep). Para nueva implementación: `cd apps/erp/api; mvn test` y `cd apps/erp/web; npm run build`, según código afectado. Abrir el [índice de ingeniería](../../ingenieria/README.md) y solo los estándares relevantes.
