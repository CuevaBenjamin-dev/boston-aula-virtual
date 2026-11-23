# Matrículas — índice del módulo

Proceso de matrícula y sus estados, sujetos a definición institucional. No posee identidad personal ni catálogo académico. **Estado:** API y Web tienen implementación parcial; consultar [ESTADO.md](ESTADO.md) para evidencias y pendientes.

**Dependencias implementadas:** Personas verifica persona, condición y apoderados; Oferta identifica año y sección. Su formalización documental sigue pendiente. `plataforma` aporta soporte técnico e integración externa.

| Necesidad | Documento |
|---|---|
| Alcance, reglas y decisiones | [Especificación](ESPECIFICACION.md) |
| Componentes y contratos propuestos | [Arquitectura](ARQUITECTURA.md) |
| Diseño de datos y vacíos | [Modelo de datos](MODELO_DATOS.md) |
| Cobertura y evidencia | [Pruebas](PRUEBAS.md) |
| Cinco verificaciones | [Estado](ESTADO.md) |

Código vigente: [API](../../../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/api/MatriculasController.java), [ERP Web](../../../../apps/erp/web/src/modules/matriculas/MatriculasModulo.tsx) y [pruebas HTTP](../../../../apps/erp/api/src/test/java/pe/boston/erp/matriculas/MatriculasHttpTest.java). Abrir el [índice de ingeniería](../../ingenieria/README.md) y solo los estándares relevantes.
