\set ON_ERROR_STOP on
BEGIN;
UPDATE cuentas SET referencia_erp=md5('cp05:estudiante:1:1')::uuid WHERE usuario='estudiante.demo' AND tipo='ESTUDIANTE';
UPDATE cuentas SET referencia_erp=md5('cp05:estudiante:1:2')::uuid WHERE usuario='otro.demo' AND tipo='ESTUDIANTE';
UPDATE cuentas SET clave_hash=(SELECT clave_hash FROM cuentas WHERE usuario='estudiante.demo') WHERE usuario='otro.demo' AND tipo='ESTUDIANTE';
UPDATE cuentas SET referencia_erp=md5('cp05:docente:1')::uuid WHERE usuario='docente.demo' AND tipo='DOCENTE';
UPDATE cuentas SET activo=TRUE,developer=TRUE WHERE usuario='admin.demo' AND tipo='ADMINISTRADOR';
UPDATE cuentas SET activo=TRUE WHERE usuario='coordinacion.demo' AND tipo='COORDINACION';
INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,activo,developer,tesoreria)
SELECT md5('cp05:cuenta:p5b')::uuid,'estudiante.p5b.demo',clave_hash,'ESTUDIANTE',md5('cp05:estudiante:2:1')::uuid,TRUE,FALSE,FALSE
FROM cuentas WHERE usuario='estudiante.demo' ON CONFLICT(usuario) DO NOTHING;
INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,activo,developer,tesoreria)
SELECT md5('cp05:cuenta:s5a')::uuid,'estudiante.s5a.demo',clave_hash,'ESTUDIANTE',md5('cp05:estudiante:3:1')::uuid,TRUE,FALSE,FALSE
FROM cuentas WHERE usuario='estudiante.demo' ON CONFLICT(usuario) DO NOTHING;
INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,activo,developer,tesoreria)
SELECT md5('cp05:cuenta:p5a3')::uuid,'estudiante.p5a3.demo',clave_hash,'ESTUDIANTE',md5('cp05:estudiante:1:3')::uuid,TRUE,FALSE,FALSE
FROM cuentas WHERE usuario='estudiante.demo' ON CONFLICT(usuario) DO NOTHING;
DELETE FROM recuperaciones WHERE cuenta_id IN (SELECT id FROM cuentas WHERE usuario IN ('estudiante.demo','otro.demo','docente.demo','coordinacion.demo','admin.demo'));
COMMIT;
