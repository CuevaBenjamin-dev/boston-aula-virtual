CREATE TABLE perfiles_institucionales (
  persona_erp_id UUID NOT NULL,
  tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('DOCENTE','ESTUDIANTE')),
  nombre_completo VARCHAR(240) NOT NULL CHECK (length(btrim(nombre_completo)) > 0),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (persona_erp_id,tipo)
);
