-- Los materiales anteriores permanecen sin clasificación hasta que el docente los actualice.
ALTER TABLE materiales_curso ADD COLUMN mes SMALLINT CHECK (mes BETWEEN 1 AND 12);
ALTER TABLE materiales_curso ADD COLUMN semana SMALLINT CHECK (semana BETWEEN 1 AND 5);
