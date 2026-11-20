ALTER TABLE inscripciones_curso
  ADD COLUMN estado_matricula VARCHAR(12) NOT NULL DEFAULT 'VIGENTE'
    CHECK (estado_matricula IN ('VIGENTE','FINALIZADA','RETIRADA'));

UPDATE inscripciones_curso SET estado_matricula='RETIRADA' WHERE NOT activo;
