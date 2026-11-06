# Ingeniería del ERP — índice de consulta

Este índice dirige al [proceso obligatorio](MANUAL_DESARROLLO_MODULAR.md). Los cinco módulos funcionales y las decisiones institucionales vigentes se localizan en el [tablero ERP](../README.md) y las [políticas transversales](../POLITICAS_TRANSVERSALES.md). No sustituye las reglas de negocio del módulo.

## Ruta de lectura selectiva

1. Leer las instrucciones breves de [AGENTS.md](../../../AGENTS.md) y [ERP AGENTS.md](../../../apps/erp/AGENTS.md).
2. Identificar el módulo en el [tablero](../README.md) y abrir solo su `README.md`.
3. Leer el documento especializado que corresponda a la tarea; ampliar a otro módulo solo ante una dependencia concreta.
4. Verificar el estado declarado contra código, migraciones y pruebas antes de afirmar implementación o aprobación.

| Tarea | Fuente del módulo | Estándar reutilizable |
|---|---|---|
| Reglas, actores, permisos funcionales | `ESPECIFICACION.md` | [Manual](MANUAL_DESARROLLO_MODULAR.md) y [políticas](../POLITICAS_TRANSVERSALES.md) |
| PostgreSQL, vigencia, migraciones | `MODELO_DATOS.md` | [PostgreSQL](ESTANDAR_POSTGRESQL.md) |
| Capas y dependencias | `ARQUITECTURA.md` | [Arquitectura modular](ESTANDAR_ARQUITECTURA_MODULAR.md) |
| HTTP y comunicación | `ARQUITECTURA.md` | [API e integraciones](ESTANDAR_API_INTEGRACIONES.md) |
| Seguridad y datos personales | `ESPECIFICACION.md` y `ARQUITECTURA.md` | [Seguridad y privacidad](ESTANDAR_SEGURIDAD_PRIVACIDAD.md) |
| Pantallas y formularios | `ARQUITECTURA.md` | [UX/UI](ESTANDAR_UX_UI.md) |
| Implementar una tarea | `README.md`, `ESTADO.md` | [Guía de implementación](GUIA_IMPLEMENTACION.md) |
| Validar y reportar | `PRUEBAS.md`, `ESTADO.md` | [Verificación](ESTANDAR_VERIFICACION.md) |
| Evitar retrabajo | Documento pertinente | [Lecciones aprendidas](LECCIONES_APRENDIDAS.md) |

Los estándares y el proceso son reglas de trabajo; las fichas de módulo contienen decisiones y evidencia específica. Busque encabezados o códigos estables con `rg` antes de leer documentos largos. Un resumen nunca reemplaza una regla original.

## Precedencia y estados

Una **política institucional aprobada** prevalece sobre una propuesta técnica. La `ESPECIFICACION.md` del módulo es fuente de reglas funcionales y decisiones pendientes; los estándares gobiernan el diseño reutilizable. `ARQUITECTURA.md` y `MODELO_DATOS.md` describen diseño y estado técnico, sin aprobar políticas nuevas. `PRUEBAS.md` guarda resultados; el código y las pruebas prueban lo implementado, no lo aprobado institucionalmente. `ESTADO.md` resume las cinco verificaciones. Un ADR, cuando exista, registra una decisión arquitectónica sustentada. `docs/historico/` conserva contexto, no define la arquitectura vigente.

Use **Pendiente**, **En progreso**, **Bloqueada** o **Completada** solo con el significado del [estándar de verificación](ESTANDAR_VERIFICACION.md). Ante contradicción, cite ambas secciones, clasifique el conflicto y corrija únicamente lo demostrable; una decisión institucional no resuelta queda pendiente.
