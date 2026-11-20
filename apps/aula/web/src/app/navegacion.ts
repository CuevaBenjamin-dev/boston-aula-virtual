import type {Perfil} from '../api';

export type ItemMenu = [ruta: string, etiqueta: string];

export const menu: Record<Perfil['tipo'], ItemMenu[]> = {
  ESTUDIANTE:[['/','Inicio'],['/cursos','Mis cursos actuales'],['/historial','Historial académico'],['/materiales','Mis materiales'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/tareas','Mis tareas'],['/notas','Mis notas'],['/pagos','Mis pagos'],['/cuenta','Mi cuenta']],
  DOCENTE:[['/','Inicio'],['/cursos','Mis cursos'],['/materiales','Mis materiales'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/tareas','Tareas y entregas'],['/notas','Calificaciones'],['/cuenta','Mi cuenta']],
  COORDINACION:[['/','Inicio'],['/cursos','Cursos y secciones'],['/materiales','Mis materiales'],['/historial','Historial académico'],['/docentes','Docentes'],['/reportes','Seguimiento y reportes'],['/tareas','Tareas y evaluaciones'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/cuenta','Mi cuenta']],
  ADMINISTRADOR:[['/','Inicio'],['/usuarios','Usuarios y accesos'],['/historial','Historial académico'],['/modulos','Módulos del aula'],['/clases','Control de clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/cuenta','Mi cuenta']]
};

export function menuVisible(perfil: Perfil, modulos?: {clasesYGrabaciones:boolean}, developer=false): ItemMenu[] {
  const items=menu[perfil.tipo].filter(([,label])=>modulos?.clasesYGrabaciones!==false||!['Clases virtuales','Grabaciones','Control de clases virtuales'].includes(label));
  if(perfil.tipo==='ADMINISTRADOR'&&perfil.developer&&developer)items.push(['/almacenamiento','Almacenamiento'],['/integraciones','Integraciones'],['/seguridad','Seguridad y auditoría'],['/estado','Estado del sistema']);
  return items;
}

export function tituloRuta(path: string, tipo: Perfil['tipo']): string {
  if(path.startsWith('/cursos/'))return 'Curso';
  if(path.startsWith('/clases/'))return 'Clase virtual';
  if(path.startsWith('/tareas/'))return 'Tarea';
  if(path.startsWith('/notas/'))return 'Nota';
  return menu[tipo].find(([ruta])=>ruta===path)?.[1]||'Aula Virtual';
}

export type RutaAplicacion =
  | {path:string; vista:'academico'; pagina?:string}
  | {path:string; vista:'operacion'; pagina:string}
  | {path:string; vista:'redireccion'};

// Contratos de URL existentes: el acceso efectivo permanece en la API y las páginas.
export const rutasAplicacion: RutaAplicacion[] = [
  {path:'/',vista:'academico',pagina:'inicio'},
  {path:'/cuenta',vista:'operacion',pagina:'cuenta'},
  {path:'/pagos',vista:'operacion',pagina:'pagos'},
  {path:'/modulos',vista:'operacion',pagina:'modulos'},
  {path:'/integraciones',vista:'operacion',pagina:'integraciones'},
  {path:'/almacenamiento',vista:'operacion',pagina:'almacenamiento'},
  {path:'/seguridad',vista:'operacion',pagina:'seguridad'},
  {path:'/estado',vista:'operacion',pagina:'estado'},
  {path:'/usuarios',vista:'operacion',pagina:'usuarios'},
  {path:'/docentes',vista:'academico',pagina:'docentes'},
  {path:'/reportes',vista:'academico',pagina:'reportes'},
  {path:'/cursos/:cursoId',vista:'academico',pagina:'curso'},
  {path:'/tareas/:tareaId',vista:'academico',pagina:'tarea'},
  {path:'/notas/:notaId',vista:'academico',pagina:'nota'},
  {path:'/clases/:claseId',vista:'academico',pagina:'clase'},
  {path:'/:pagina',vista:'academico'},
  {path:'*',vista:'redireccion'}
];
