import {Link,useParams} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {api,type Perfil} from '../api';
import {AdminGrabaciones,ClaseDetalle,ClasesListado,GrabacionesListado} from './ClasesGrabaciones';
import {Cursos,CursoPage,TareaPage,NotaPage} from './AcademicoTareas';
import Bandeja from './Bandeja';
import Historial from './Historial';

type Props={perfil:Perfil;pagina?:string;modulos?:{clasesYGrabaciones:boolean}};
const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});
type Resumen={id:string;asignatura:string;grado:string;seccion:string;estudiantes:number;docentes:number;tareas:number;entregas:number;calificaciones:number};

function CoordinacionVista({pagina}:{pagina:string}){
  const {data:resumen,error}=useQuery<Resumen[]>({queryKey:['coordinacion','resumen'],queryFn:()=>api('/supervision/coordinacion/resumen')});
  const {data:docentes}=useQuery<Array<{docente_erp_id:string;cursos:number;asignaciones:string}>>({queryKey:['coordinacion','docentes'],queryFn:()=>api('/supervision/coordinacion/docentes'),enabled:pagina==='docentes'});
  return <><div className="page-head"><div><span className="eyebrow">COORDINACIÓN ACADÉMICA</span><h1>{pagina==='docentes'?'Docentes':'Seguimiento y reportes'}</h1><p>Información de las secciones asignadas a tu cuenta.</p></div></div>{error&&<div className="alert">{String(error)}</div>}{pagina==='docentes'?(!docentes?.length?<div className="empty">No hay docentes en las secciones asignadas.</div>:<div className="table-wrap"><table><thead><tr><th>Docente ERP</th><th>Cursos</th><th>Asignaciones</th></tr></thead><tbody>{docentes.map(d=><tr key={d.docente_erp_id}><td>{d.docente_erp_id}</td><td>{d.cursos}</td><td>{d.asignaciones}</td></tr>)}</tbody></table></div>):(!resumen?.length?<div className="empty">No hay secciones asignadas. Administración puede configurarlas desde Usuarios y accesos.</div>:<div className="table-wrap"><table><thead><tr><th>Curso</th><th>Estudiantes</th><th>Docentes</th><th>Tareas</th><th>Entregas</th><th>Calificaciones</th></tr></thead><tbody>{resumen.map(c=><tr key={c.id}><td><Link to={`/cursos/${c.id}`}>{c.asignatura} · {c.grado} {c.seccion}</Link></td><td>{c.estudiantes}</td><td>{c.docentes}</td><td>{c.tareas}</td><td>{c.entregas}</td><td>{c.calificaciones}</td></tr>)}</tbody></table></div>)}</>;
}
function AdministracionVista({pagina}:{pagina:string}){
  const {data:clases}=useQuery<Array<{id:string;tema:string;inicio:string;asignatura:string;grado:string;seccion:string;bloques:number}>>({queryKey:['admin','clases'],queryFn:()=>api('/supervision/administracion/clases'),enabled:pagina==='clases'});
  if(pagina==='clases')return <><div className="page-head"><div><span className="eyebrow">SUPERVISIÓN</span><h1>Control de clases virtuales</h1><p>Sesiones y bloques Zoom registrados por los docentes.</p></div></div>{!clases?.length?<div className="empty">No hay clases registradas.</div>:<div className="table-wrap"><table><thead><tr><th>Clase</th><th>Curso</th><th>Fecha</th><th>Bloques</th></tr></thead><tbody>{clases.map(c=><tr key={c.id}><td>{c.tema}</td><td>{c.asignatura} · {c.grado} {c.seccion}</td><td>{fecha(c.inicio)}</td><td>{c.bloques}</td></tr>)}</tbody></table></div>}</>;
  return <div className="page-head"><div><span className="eyebrow">GESTIÓN DEL AULA</span><h1>Panel de administración</h1><p>Configura módulos, accesos, almacenamiento e integraciones desde el menú lateral.</p></div></div>;
}
export default function Academico({perfil,pagina='inicio',modulos}:Props){
  const p=useParams();const actual=p.pagina||pagina;
  if(p.cursoId)return <CursoPage perfil={perfil} modulos={modulos}/>;
  if(p.tareaId)return <TareaPage perfil={perfil}/>;
  if(p.notaId)return <NotaPage/>;
  if(p.claseId)return <ClaseDetalle perfil={perfil}/>;
  if(actual==='notificaciones')return <Bandeja perfil={perfil}/>;
  if(actual==='historial'&&['COORDINACION','ADMINISTRADOR'].includes(perfil.tipo))return <Historial/>;
  if(perfil.tipo==='ADMINISTRADOR'&&actual==='grabaciones')return <AdminGrabaciones/>;
  if(perfil.tipo==='ADMINISTRADOR')return <AdministracionVista pagina={actual}/>;
  if(['ESTUDIANTE','DOCENTE','COORDINACION'].includes(perfil.tipo)&&actual==='clases')return <ClasesListado perfil={perfil}/>;
  if(['ESTUDIANTE','DOCENTE','COORDINACION'].includes(perfil.tipo)&&actual==='grabaciones')return <GrabacionesListado perfil={perfil}/>;
  if(perfil.tipo==='COORDINACION'&&['reportes','docentes'].includes(actual))return <CoordinacionVista pagina={actual}/>;
  return <Cursos perfil={perfil} pagina={actual}/>;
}
