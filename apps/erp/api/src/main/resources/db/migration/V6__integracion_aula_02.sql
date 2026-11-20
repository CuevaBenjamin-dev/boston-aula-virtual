-- Estados verificables de las solicitudes ya registradas por las operaciones ERP.
ALTER TABLE erp_solicitudes_externas DROP CONSTRAINT erp_solicitudes_externas_estado_check;
ALTER TABLE erp_solicitudes_externas ADD CONSTRAINT erp_solicitudes_externas_estado_check
  CHECK (estado IN ('PENDIENTE_NO_CONECTADO','PROCESANDO','ERROR','INTERVENCION','CONFIRMADA'));
ALTER TABLE erp_solicitudes_externas ADD COLUMN intentos INTEGER NOT NULL DEFAULT 0;
ALTER TABLE erp_solicitudes_externas ADD COLUMN proximo_intento TIMESTAMPTZ;
ALTER TABLE erp_solicitudes_externas ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX erp_solicitudes_pendientes_idx ON erp_solicitudes_externas(estado,proximo_intento,creado_en)
  WHERE estado <> 'CONFIRMADA';
