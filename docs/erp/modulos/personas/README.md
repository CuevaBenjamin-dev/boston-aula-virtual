# Personas — índice del módulo

Registro único de personas físicas, documentos, condiciones institucionales, parentescos, apoderamientos e historial técnico mínimo. No decide matrícula vigente, credenciales, oferta, asignación docente ni expedientes. Su implementación inicial está **En progreso** en las cinco verificaciones; ámbitos de autorización y revisión técnica siguen abiertos.

| Si necesita… | Consulte |
|---|---|
| RF, RNF, RI-PER-001–037, siete CU y decisiones | [Especificación](ESPECIFICACION.md) |
| Capas, rutas, contratos, seguridad y UX | [Arquitectura](ARQUITECTURA.md) |
| V2, tablas, restricciones, índices y vigencias | [Modelo de datos](MODELO_DATOS.md) |
| Cobertura, resultados históricos y pendientes | [Pruebas](PRUEBAS.md) |
| Cinco verificaciones y bloqueos | [Estado](ESTADO.md) |

Código: [backend Personas](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/package-info.java), [contrato público actual](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/PersonasParaMatriculas.java), [migración V2](../../../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql), [ERP Web](../../../../apps/erp/web/src/modules/personas/PersonasModulo.tsx). Matrículas podrá consumir contrato público, pero aún no lo hace. Identidad aporta OIDC; el ámbito de Coordinación no se emite hoy. Plataforma provee soporte técnico sin reglas de Personas.

Pruebas relevantes: `cd apps/erp/api; mvn test` y `cd apps/erp/web; npm test; npm run build`; no se ejecutaron durante la reorganización documental. Consulte [proceso](../../ingenieria/README.md), [políticas](../../POLITICAS_TRANSVERSALES.md) e [informe histórico](../../RESULTADO_PERSONAS_PARA_CHATGPT.md) solo si la tarea lo exige.
