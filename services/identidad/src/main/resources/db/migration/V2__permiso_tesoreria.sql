ALTER TABLE cuentas ADD COLUMN tesoreria BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE cuentas SET tesoreria=TRUE WHERE usuario='admin.demo';
