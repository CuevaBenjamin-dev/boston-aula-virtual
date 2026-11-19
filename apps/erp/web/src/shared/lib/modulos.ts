export const modulos = [
  { ruta: 'personas', nombre: 'Personas', resumen: 'Identidad y relaciones institucionales.' },
  { ruta: 'oferta', nombre: 'Oferta académica', resumen: 'Organización de la oferta educativa.' },
  { ruta: 'matriculas', nombre: 'Matrículas', resumen: 'Procesos de ingreso y continuidad.' },
  { ruta: 'asignaciones', nombre: 'Asignaciones docentes', resumen: 'Vínculos docentes con la oferta.' },
  { ruta: 'asistencia', nombre: 'Asistencia', resumen: 'Registro institucional de asistencia.' },
  { ruta: 'documentos', nombre: 'Documentos', resumen: 'Archivos de expedientes personales.' },
  { ruta: 'atencion', nombre: 'Atención institucional', resumen: 'Consultas recibidas desde Aula Virtual.' }
] as const

export type Modulo = typeof modulos[number]
