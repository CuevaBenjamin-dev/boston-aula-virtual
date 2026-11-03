ALTER TABLE matriculas DROP CONSTRAINT matriculas_estado_check;
ALTER TABLE matriculas ADD CONSTRAINT matriculas_estado_check
  CHECK (estado IN ('PROCESO_PENDIENTE','VACANTE_RESERVADA','MATRICULADO','CANCELADA'));

DROP INDEX ux_matricula_estudiante_periodo;
CREATE UNIQUE INDEX ux_matricula_estudiante_periodo
  ON matriculas(estudiante_id,periodo_id) WHERE estado <> 'CANCELADA';
