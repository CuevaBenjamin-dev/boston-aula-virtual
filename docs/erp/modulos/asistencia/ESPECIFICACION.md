# Asistencia — especificación aplicada

Fuente institucional: `ENCARGO_CODEX_ASISTENCIA_BBS.md`, `MAPA_MAESTRO_DESARROLLO_BBS.md` y `MEMORIA_BBS_ERP.md` entregados el 26/11/2026. Las decisiones AST-Q01–19 y AF-Q01–17 indicadas allí están aprobadas. La exportación MODULA revisión 532 no está disponible para verificar trazabilidad de identificadores originales.

| Caso | Regla aplicada | Código |
|---|---|---|
| Padrón y calendario | La matrícula y sus tramos efectivos determinan elegibilidad; sin calendario lectivo ni incorporación efectiva no se crean pendientes. | [Flyway V9](../../../../apps/erp/api/src/main/resources/db/migration/V9__asistencia.sql), [servicio](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaService.java) |
| Registro | Docente activo registra solo hoy en Lima, cualquier sección; avance privado, publicación parcial y confirmación completa son actos distintos. Dirección/Coordinación regularizan fechas anteriores con motivo. | [ERP API](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaService.java) |
| Cierre | Tras medianoche recupera avances; completa faltas por sistema solo cuando hay alguna marca, conserva jornada sin marcas como pendiente. | [Tarea](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaProgramada.java) |
| Justificación | Falta y tardanza se solicitan con matrícula vigente; solo Coordinación resuelve; se conserva hecho original y comprobantes privados. | [ERP API](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaService.java) |
| Salida | Autorizada o no autorizada, con motivo, autor y hora; no cambia la asistencia ni sustituye actuaciones físicas. | [ERP Web](../../../../apps/erp/web/src/modules/asistencia/AsistenciaModulo.tsx) |
| Avisos | Recordatorio por sección/docente vigente y resultado de justificación en la bandeja común de Aula, sin motivo sensible. | [Aula API](../../../../apps/aula/api/src/main/java/pe/boston/aula/AsistenciaAvisos.java) |
| Consulta | Presencia física y cobertura usan denominadores explícitos; no lectivos e invalidados se excluyen. El cierre anual informa pendientes y no los bloquea. | [ERP API](../../../../apps/erp/api/src/main/java/pe/boston/erp/asistencia/AsistenciaService.java), [Matrículas Web](../../../../apps/erp/web/src/modules/matriculas/MatriculasModulo.tsx) |

No se implementan sanciones automáticas, control de retorno, llamadas a familiares ni la gestión completa de consecuencias de salidas.
