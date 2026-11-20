-- Solo las cuentas creadas por la integración exigirán el cambio de clave inicial.
ALTER TABLE cuentas ADD COLUMN cambio_clave_obligatorio BOOLEAN NOT NULL DEFAULT FALSE;
