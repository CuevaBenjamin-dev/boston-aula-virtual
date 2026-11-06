# Resultado de implantación del proceso de ingeniería ERP

## 1. Fecha y punto de partida

**6 de noviembre de 2026 (Lima).** Rama `main`; HEAD inicial `0c8357091394c0a176bac308bd63718ac764e497`. `git status --short` inicial limpio. La implantación se preparó sin commit ni push, conforme a la instrucción original. La publicación posterior fue solicitada expresamente por el usuario; el commit de publicación se consulta en `git log`.

## 2. Diagnóstico inicial

Se revisaron [AGENTS.md](../../AGENTS.md), [ERP AGENTS.md](../../apps/erp/AGENTS.md), [índice ERP](README.md), [políticas](POLITICAS_TRANSVERSALES.md), los quince documentos de los cinco módulos, código y pruebas de Personas, [V1](../../apps/erp/api/src/main/resources/db/migration/V1__infraestructura_sesiones.sql), [V2](../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql) y [CI](../../.github/workflows/ci.yaml). No existía `ingenieria/`; cada módulo tenía solo README, ESPECIFICACION y ESTADO. Personas contenía diseño SQL, contratos y resultados de pruebas mezclados con reglas funcionales; los otros cuatro tenían estructura preparada y cinco estados Pendiente. [Resultado previo de Personas](RESULTADO_PERSONAS_PARA_CHATGPT.md) y [resultado histórico del reinicio](RESULTADO_REINICIO_PARA_CHATGPT.md) se consultaron como evidencia de su momento, no como política vigente.

## 3. Estructura documental final

```text
docs/erp/
  README.md  POLITICAS_TRANSVERSALES.md
  RESULTADO_IMPLANTACION_PROCESO_PARA_CHATGPT.md
  ingenieria/
    README.md  MANUAL_DESARROLLO_MODULAR.md
    ESTANDAR_ARQUITECTURA_MODULAR.md  ESTANDAR_POSTGRESQL.md
    ESTANDAR_API_INTEGRACIONES.md  ESTANDAR_SEGURIDAD_PRIVACIDAD.md
    ESTANDAR_UX_UI.md  GUIA_IMPLEMENTACION.md
    ESTANDAR_VERIFICACION.md  LECCIONES_APRENDIDAS.md
  modulos/{personas,matriculas,oferta,asignaciones,documentos}/
    README.md  ESPECIFICACION.md  ARQUITECTURA.md
    MODELO_DATOS.md  PRUEBAS.md  ESTADO.md
```

No se creó `ingenieria/decisiones/` porque no se adoptó una decisión arquitectónica nueva con alternativas evaluadas que justificara un ADR. El formato queda descrito en el [manual](ingenieria/MANUAL_DESARROLLO_MODULAR.md); se abre la carpeta cuando exista una decisión sustentada.

## 4. Estándares globales y responsabilidad

| Documento | Responsabilidad |
|---|---|
| [Índice](ingenieria/README.md) | Matriz de enrutamiento, lectura selectiva y precedencia. |
| [Manual](ingenieria/MANUAL_DESARROLLO_MODULAR.md) | Cuatro actividades, cinco verificaciones, roles, inicio, cambios y retrospectiva. |
| [Arquitectura modular](ingenieria/ESTANDAR_ARQUITECTURA_MODULAR.md) | Cinco módulos, capas, contratos, Spring Modulith, frontend y brecha real de Personas. |
| [PostgreSQL](ingenieria/ESTANDAR_POSTGRESQL.md) | Modelos, integridad, vigencias, transacciones, rendimiento, Flyway y Testcontainers. |
| [API e integraciones](ingenieria/ESTANDAR_API_INTEGRACIONES.md) | `/api/erp/v1`, DTO, errores, paginación, OpenAPI, contratos internos y servicios. |
| [Seguridad y privacidad](ingenieria/ESTANDAR_SEGURIDAD_PRIVACIDAD.md) | Identidad, autorización por recurso/ámbito, CSRF, PII, menores y auditoría. |
| [UX/UI](ingenieria/ESTANDAR_UX_UI.md) | Tareas, formularios, estados, responsive y objetivo WCAG 2.2 AA. |
| [Guía](ingenieria/GUIA_IMPLEMENTACION.md) | Secuencia operativa de quince pasos para Codex y desarrolladores. |
| [Verificación](ingenieria/ESTANDAR_VERIFICACION.md) | Criterios, estados y vocabulario de resultados para cinco verificaciones. |
| [Lecciones](ingenieria/LECCIONES_APRENDIDAS.md) | Nueve aprendizajes de Personas con evidencia y estado de incorporación. |

Fuentes técnicas primarias enlazadas dentro de los estándares: documentación oficial de PostgreSQL 18, Spring Modulith, Flyway y W3C WCAG 2.2. Las [políticas transversales](POLITICAS_TRANSVERSALES.md) siguen siendo su propia fuente autorizada y no se copiaron dentro de cada estándar.

## 5. Mapa por módulo

| Módulo | Entrada | Definición | Diseño | Datos | Pruebas | Estado |
|---|---|---|---|---|---|---|
| Personas | [README](modulos/personas/README.md) | [Especificación](modulos/personas/ESPECIFICACION.md) | [Arquitectura](modulos/personas/ARQUITECTURA.md) | [Modelo](modulos/personas/MODELO_DATOS.md) | [Pruebas](modulos/personas/PRUEBAS.md) | [Estado](modulos/personas/ESTADO.md) |
| Matrículas | [README](modulos/matriculas/README.md) | [Especificación](modulos/matriculas/ESPECIFICACION.md) | [Arquitectura](modulos/matriculas/ARQUITECTURA.md) | [Modelo](modulos/matriculas/MODELO_DATOS.md) | [Pruebas](modulos/matriculas/PRUEBAS.md) | [Estado](modulos/matriculas/ESTADO.md) |
| Oferta académica | [README](modulos/oferta/README.md) | [Especificación](modulos/oferta/ESPECIFICACION.md) | [Arquitectura](modulos/oferta/ARQUITECTURA.md) | [Modelo](modulos/oferta/MODELO_DATOS.md) | [Pruebas](modulos/oferta/PRUEBAS.md) | [Estado](modulos/oferta/ESTADO.md) |
| Asignaciones docentes | [README](modulos/asignaciones/README.md) | [Especificación](modulos/asignaciones/ESPECIFICACION.md) | [Arquitectura](modulos/asignaciones/ARQUITECTURA.md) | [Modelo](modulos/asignaciones/MODELO_DATOS.md) | [Pruebas](modulos/asignaciones/PRUEBAS.md) | [Estado](modulos/asignaciones/ESTADO.md) |
| Documentos | [README](modulos/documentos/README.md) | [Especificación](modulos/documentos/ESPECIFICACION.md) | [Arquitectura](modulos/documentos/ARQUITECTURA.md) | [Modelo](modulos/documentos/MODELO_DATOS.md) | [Pruebas](modulos/documentos/PRUEBAS.md) | [Estado](modulos/documentos/ESTADO.md) |

## 6. AGENTS.md y lectura selectiva

[AGENTS.md](../../AGENTS.md) y [ERP AGENTS.md](../../apps/erp/AGENTS.md) remiten al índice/manual, preservan límites y exigen distinguir aprobación, propuesta, implementación y resultados. La ruta de consulta es **AGENTS → ingeniería/README → módulo/README → documento pertinente → código/prueba**. No se agregó RAG ni infraestructura vectorial. Se actualizaron solo las instrucciones ERP aplicables; Aula e Identidad no se modificaron.

## 7. Reorganización de Personas y lecciones

[ESPECIFICACION.md](modulos/personas/ESPECIFICACION.md) conserva RF-PER-001–009, RNF-PER-001–006, RI-PER-001–037, CU-PER-01–07, matriz de datos, criterios y decisiones pendientes. El detalle de capas, contratos HTTP, seguridad e interfaz pasó a [ARQUITECTURA.md](modulos/personas/ARQUITECTURA.md); V2, diccionario, diagrama ER, restricciones y consultas a [MODELO_DATOS.md](modulos/personas/MODELO_DATOS.md); cobertura y resultados históricos a [PRUEBAS.md](modulos/personas/PRUEBAS.md). [README](modulos/personas/README.md) es índice; [ESTADO](modulos/personas/ESTADO.md) conserva cinco `En progreso`.

La [retrospectiva](ingenieria/LECCIONES_APRENDIDAS.md) registra diseño de datos e invariantes, dependencia de DTO HTTP en capas internas, contrato público, ámbitos externos, verificación incompleta, consolidación de prompts, estado de Git y duplicación documental. Causas históricas no demostradas se declaran como tales. La separación de `contratos/`/`@NamedInterface` y de DTO HTTP **no se ejecutó**; [PersonasServicio](../../apps/erp/api/src/main/java/pe/boston/erp/personas/application/PersonasServicio.java) y [PersonasRepositorio](../../apps/erp/api/src/main/java/pe/boston/erp/personas/infrastructure/PersonasRepositorio.java) todavía importan `api.PersonaDto`.

## 8. Limpieza y conservación

Se retiró de la especificación funcional la duplicación de SQL, rutas y resultados; se corrigieron las referencias de `ESTADO.md`. Se corrigió en [políticas transversales](POLITICAS_TRANSVERSALES.md) la afirmación obsoleta de que ninguna operación ERP de negocio estaba habilitada: Personas sí tiene implementación inicial. Las cifras de una suite anterior (183/218 ms y cuatro pruebas React) dejaron de figurar como evidencia actual en la especificación; [PRUEBAS.md](modulos/personas/PRUEBAS.md) cita **283/214 ms y cinco pruebas React** del informe previo, con su límite temporal. Los resultados `EXPLAIN` antiguos dejaron el modelo y quedaron remitidos a la evidencia.

Se conservaron [POLITICAS_TRANSVERSALES.md](POLITICAS_TRANSVERSALES.md), [README ERP](README.md), [informe Personas](RESULTADO_PERSONAS_PARA_CHATGPT.md), [informe del reinicio](RESULTADO_REINICIO_PARA_CHATGPT.md), `docs/historico/`, [V1](../../apps/erp/api/src/main/resources/db/migration/V1__infraestructura_sesiones.sql), [V2](../../apps/erp/api/src/main/resources/db/migration/V2__personas.sql) y todas las pruebas: contienen política, índice, evidencia histórica, esquema aplicado o verificación; eliminarlos alteraría trazabilidad. No se detectaron enlaces relativos rotos en los documentos revisados tras la reorganización.

## 9. Validaciones ejecutadas

| Comprobación | Resultado en esta tarea | Límite |
|---|---|---|
| `python scripts/validar-docs-erp.py` | **Aprobada**: 44 archivos obligatorios, 47 documentos revisados, cinco verificaciones por módulo, RI-PER-001–037 y enlaces relativos/anclas existentes. | Validación documental; no comprueba semántica institucional. |
| `git diff --check` | **Aprobada**, sin errores de formato en archivos seguidos. | No incluye archivos no seguidos hasta que Git los incorpore; se revisan aparte. |
| Comparación `git show HEAD:.../ESPECIFICACION.md` con documento actual | **Aprobada**: 9 RF, 6 RNF, 37 RI y 7 CU conservados; ningún identificador faltante o nuevo. | Verifica identificadores, no interpretación institucional. |
| Revisión de 47 archivos locales cambiados | **Aprobada**: sin espacios finales ni rutas fuera del alcance documental/CI/AGENTS/validador. | Inspección de archivos, no ejecución de negocio. |
| Inspección de `git status` y rutas | Solo documentación ERP, dos AGENTS, CI y [validador](../../scripts/validar-docs-erp.py). | No se ejecutaron Maven, React, Docker ni pruebas de negocio porque no cambió código. |

El validador se integró como trabajo `docs-erp` en [CI](../../.github/workflows/ci.yaml), con Python estándar y sin nuevas dependencias. Se ejecutará en CI en el próximo evento; **la CI remota no se ejecutó aquí**.

## 10. Contradicciones, decisiones y riesgos pendientes

| Hallazgo | Clasificación y tratamiento |
|---|---|
| Políticas decían que no había negocio ERP; Personas sí tiene API/Web V2. | Contradicción documental demostrable; frase corregida sin ampliar facultades. |
| Personas importó DTO HTTP en `application`/`infrastructure` pese a la nueva regla. | Brecha técnica vigente, documentada; refactorización Java pendiente. El test modular actual no la detecta. |
| Evidencia RNF de pruebas en especificación no coincidía con último informe. | Diferencia temporal/documental; evidencia centralizada en `PRUEBAS.md`. No se inventó ejecución nueva. |
| Identidad no emite `personas_ambito`; Asignaciones no ofrece relación docente–alumno. | Dependencia externa: accesos correspondientes siguen denegados hasta contrato verificable. |
| V2 implementada, pero aprobación institucional del diseño físico y catálogos incompleta. | Aprobación pendiente; no inferirla de SQL existente. |

Siguen pendientes facultad y comprobación del autorizador no familiar, representación legal por trámite, formatos/catálogos, vigencias futuras, retención, protección operativa y accesibilidad; ver [decisiones Personas](modulos/personas/ESPECIFICACION.md#8-decisiones-pendientes). Los demás módulos necesitan requisitos y análisis institucional antes de construcción. No se elevaron recomendaciones técnicas a políticas institucionales.

## 11. Cinco verificaciones por módulo

| Módulo | Requisitos | Análisis funcional | Arquitectura | Implementación | Verificación y pruebas |
|---|---|---|---|---|---|
| [Personas](modulos/personas/ESTADO.md) | En progreso | En progreso | En progreso | En progreso | En progreso |
| [Matrículas](modulos/matriculas/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Oferta](modulos/oferta/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Asignaciones](modulos/asignaciones/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| [Documentos](modulos/documentos/ESTADO.md) | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |

## 12. Siguientes pasos concretos para Personas

1. Aprobar alcance institucional de Coordinación y contrato de estudiantes asignados de Asignaciones/Identidad; mantener denegación mientras tanto.
2. Acordar aprobación del modelo físico V2, catálogos, retención y sustento de autorizaciones.
3. Aprobar refactorización incremental de modelos internos, DTO HTTP y contrato `contratos/`/`@NamedInterface`, añadiendo pruebas de capas y manteniendo comportamiento.
4. Completar pruebas de eventos, navegador autenticado, accesibilidad WCAG 2.2 AA y rendimiento HTTP; registrar comandos, entorno y defectos en [PRUEBAS.md](modulos/personas/PRUEBAS.md).
5. Revisar evidencia frente a los cinco criterios antes de cerrar cualquier verificación.

## 13. Alcance y Git final

No se modificaron Java, React, código de negocio, migraciones, bases `erp_boston`/`erp_boston_nuevo`, datos, volúmenes, respaldos, Aula, Identidad ni `docs/historico/`. Archivos modificados: [CI](../../.github/workflows/ci.yaml), [AGENTS raíz](../../AGENTS.md), [AGENTS ERP](../../apps/erp/AGENTS.md), [README ERP](README.md), [políticas](POLITICAS_TRANSVERSALES.md) y README/ESPECIFICACION/ESTADO de los cinco módulos. Archivos nuevos: diez en [ingeniería](ingenieria/README.md), tres por módulo (ARQUITECTURA, MODELO_DATOS, PRUEBAS), [validador](../../scripts/validar-docs-erp.py) y este informe. Al redactarse inicialmente, el árbol tenía cambios locales sin confirmar y HEAD seguía siendo el inicial; una solicitud posterior autorizó commit y push. La referencia exacta de publicación corresponde a `git log -1` y `git status --short` tras subir a GitHub.
