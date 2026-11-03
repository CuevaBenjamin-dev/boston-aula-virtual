CREATE UNIQUE INDEX ux_docente_cuenta_identidad
  ON docentes(cuenta_identidad_id)
  WHERE cuenta_identidad_id IS NOT NULL;
