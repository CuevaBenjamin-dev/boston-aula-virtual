UPDATE docentes
SET codigo = 'DOC-' || lpad(nextval('codigo_docente_seq')::text, 6, '0')
WHERE codigo IS NULL;
ALTER TABLE docentes ALTER COLUMN codigo SET NOT NULL;
