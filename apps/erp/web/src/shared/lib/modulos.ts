export const modulos = [
  { ruta: 'personas', nombre: 'Personas', resumen: 'Identidad y relaciones institucionales.' },
  { ruta: 'matriculas', nombre: 'Matrículas', resumen: 'Procesos de ingreso y continuidad.' },
  { ruta: 'oferta', nombre: 'Oferta académica', resumen: 'Organización de la oferta educativa.' },
  { ruta: 'asignaciones', nombre: 'Asignaciones docentes', resumen: 'Vínculos docentes con la oferta.' },
  { ruta: 'documentos', nombre: 'Documentos', resumen: 'Gestión documental por definir.' }
] as const

export type Modulo = typeof modulos[number]
