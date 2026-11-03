ALTER TABLE notificaciones_tarea ADD COLUMN curso_id UUID REFERENCES cursos(id);
UPDATE notificaciones_tarea n SET curso_id=t.curso_id FROM tareas t WHERE t.id=n.tarea_id;
