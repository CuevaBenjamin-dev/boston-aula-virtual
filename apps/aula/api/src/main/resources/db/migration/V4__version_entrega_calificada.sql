ALTER TABLE calificaciones_tarea ADD COLUMN entrega_version INTEGER NOT NULL DEFAULT 1;
UPDATE calificaciones_tarea c SET entrega_version=COALESCE((SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id),1);
