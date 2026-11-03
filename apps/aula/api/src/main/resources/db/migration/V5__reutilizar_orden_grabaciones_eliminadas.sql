ALTER TABLE grabaciones DROP CONSTRAINT grabaciones_clase_id_orden_key;
CREATE UNIQUE INDEX grabaciones_clase_orden_activo ON grabaciones(clase_id, orden) WHERE estado <> 'ELIMINADA';
