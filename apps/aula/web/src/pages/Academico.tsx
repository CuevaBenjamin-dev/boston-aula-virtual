import {Link,useParams} from 'react-router-dom';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {api,type Perfil} from '../api';
import {AdminGrabaciones,ClaseDetalle,ClasesListado,GrabacionesListado} from './ClasesGrabaciones';
import {Cursos,CursoPage,MaterialesPage,TareaPage,NotaPage} from './AcademicoTareas';
import Bandeja from './Bandeja';
import Historial from './Historial';
import Inicio from './Inicio';

type Props={perfil:Perfil;pagina?:string;modulos?:{clasesYGrabaciones:boolean}};
const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});
type Resumen={id:string;asignatura:string;nivel:string|null;grado:string;seccion:string;periodo:string;estudiantes:number;docentes:number;tareas:number;entregas:number;calificaciones:number;actividades_pendientes:number;pendientes_calificacion:number};
type TareaSupervision={id:string;curso_id:string;titulo:string;estado:string;fecha_limite:string;asignatura:string;nivel:string|null;grado:string;seccion:string;periodo:string;estudiantes:number;entregadas:number;tardias:number;pendientes_calificacion:number};
type EstudianteSupervision={estudiante_erp_id:string;estudiante:string;curso_id:string;asignatura:string;nivel:string|null;grado:string;seccion:string;periodo:string;pendientes:number;entregadas:number;tardias:number;notas_publicadas:number;multiples_pendientes:boolean};
type ClaseSupervision={id:string;curso_id:string;tema:string;inicio:string;estado:string;asignatura:string;nivel:string|null;grado:string;seccion:string;periodo:string};

function CoordinacionVista({pagina}:{pagina:string}){
  const [nivel,setNivel]=useState(''),[grado,setGrado]=useState(''),[seccion,setSeccion]=useState(''),[curso,setCurso]=useState(''),[periodo,setPeriodo]=useState('');
  const {data:resumen=[],error}=useQuery<Resumen[]>({queryKey:['coordinacion','resumen'],queryFn:()=>api('/supervision/coordinacion/resumen')});
  const {data:docentes=[]}=useQuery<Array<{docente_erp_id:string;docente:string|null;cursos:number;asignaciones:string;curso_ids:string;tareas_publicadas:number}>>({queryKey:['coordinacion','docentes'],queryFn:()=>api('/supervision/coordinacion/docentes')});
  const {data:tareas=[]}=useQuery<TareaSupervision[]>({queryKey:['coordinacion','tareas'],queryFn:()=>api('/supervision/coordinacion/tareas'),enabled:pagina==='reportes'});
  const {data:estudiantes=[]}=useQuery<EstudianteSupervision[]>({queryKey:['coordinacion','estudiantes'],queryFn:()=>api('/supervision/coordinacion/estudiantes'),enabled:pagina==='reportes'});
  const {data:clases=[]}=useQuery<ClaseSupervision[]>({queryKey:['coordinacion','clases'],queryFn:()=>api('/supervision/coordinacion/clases'),enabled:pagina==='reportes'});
  const opciones=(clave:keyof Resumen)=>[...new Set(resumen.map(r=>String(r[clave]||'')).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'es'));
  const visibles=resumen.filter(r=>(!nivel||r.nivel===nivel)&&(!grado||r.grado===grado)&&(!seccion||r.seccion===seccion)&&(!curso||r.id===curso)&&(!periodo||r.periodo===periodo));
  const ids=new Set(visibles.map(r=>r.id));
  const docentesVisibles=docentes.filter(d=>d.curso_ids?.split(',').some(id=>ids.has(id)));
  return <><div className="page-head"><div><span className="eyebrow">COORDINACIÓN ACADÉMICA</span><h1>{pagina==='docentes'?'Docentes':'Seguimiento y reportes'}</h1><p>Información académica de las secciones autorizadas.</p></div></div>{error&&<div className="alert">{String(error)}</div>}
    <div className="panel form"><div className="form-grid"><label>Nivel<select value={nivel} onChange={e=>setNivel(e.target.value)}><option value="">Todos</option>{opciones('nivel').map(x=><option key={x}>{x}</option>)}</select></label><label>Grado<select value={grado} onChange={e=>setGrado(e.target.value)}><option value="">Todos</option>{opciones('grado').map(x=><option key={x}>{x}</option>)}</select></label><label>Sección<select value={seccion} onChange={e=>setSeccion(e.target.value)}><option value="">Todas</option>{opciones('seccion').map(x=><option key={x}>{x}</option>)}</select></label><label>Curso<select value={curso} onChange={e=>setCurso(e.target.value)}><option value="">Todos</option>{resumen.map(r=><option key={r.id} value={r.id}>{r.asignatura} · {r.grado} {r.seccion}</option>)}</select></label><label>Periodo<select value={periodo} onChange={e=>setPeriodo(e.target.value)}><option value="">Todos</option>{opciones('periodo').map(x=><option key={x}>{x}</option>)}</select></label></div></div>
    <section className="section"><h2>Docentes y asignaciones</h2>{!docentesVisibles.length?<div className="empty">No hay docentes para estos filtros.</div>:<div className="table-wrap"><table><thead><tr><th>Docente</th><th>Cursos</th><th>Tareas publicadas</th><th>Asignaciones</th></tr></thead><tbody>{docentesVisibles.map(d=><tr key={d.docente_erp_id}><td>{d.docente||d.docente_erp_id}</td><td>{d.cursos}</td><td>{d.tareas_publicadas}</td><td>{d.asignaciones}</td></tr>)}</tbody></table></div>}</section>
    {pagina==='reportes'&&<><section className="section"><h2>Cursos que requieren seguimiento</h2>{!visibles.length?<div className="empty">No hay cursos para estos filtros.</div>:<div className="table-wrap"><table><thead><tr><th>Curso</th><th>Estudiantes</th><th>Tareas</th><th>Actividades pendientes</th><th>Entregas por calificar</th><th>Notas publicadas</th></tr></thead><tbody>{visibles.map(c=><tr key={c.id}><td><Link to={`/cursos/${c.id}`}>{c.asignatura} · {c.grado} {c.seccion}</Link></td><td>{c.estudiantes}</td><td>{c.tareas}</td><td>{c.actividades_pendientes}</td><td>{c.pendientes_calificacion}</td><td>{c.calificaciones}</td></tr>)}</tbody></table></div>}</section>
    <section className="section"><h2>Tareas y entregas</h2>{!tareas.filter(t=>ids.has(t.curso_id)).length?<div className="empty">No hay tareas publicadas.</div>:<div className="table-wrap"><table><thead><tr><th>Tarea</th><th>Curso</th><th>Entregadas</th><th>Tardías</th><th>Pendientes de calificar</th></tr></thead><tbody>{tareas.filter(t=>ids.has(t.curso_id)).map(t=><tr key={t.id}><td><Link to={`/tareas/${t.id}`}>{t.titulo}</Link></td><td>{t.asignatura} · {t.grado} {t.seccion}</td><td>{t.entregadas}/{t.estudiantes}</td><td>{t.tardias}</td><td>{t.pendientes_calificacion}</td></tr>)}</tbody></table></div>}</section>
    <section className="section"><h2>Estudiantes matriculados</h2>{!estudiantes.filter(e=>ids.has(e.curso_id)).length?<div className="empty">No hay estudiantes para estos filtros.</div>:<div className="table-wrap"><table><thead><tr><th>Estudiante</th><th>Curso y sección</th><th>Entregadas</th><th>Pendientes</th><th>Tardías</th><th>Notas publicadas</th></tr></thead><tbody>{estudiantes.filter(e=>ids.has(e.curso_id)).map(e=><tr key={`${e.curso_id}-${e.estudiante_erp_id}`}><td>{e.estudiante}{e.multiples_pendientes?' · Varias pendientes':''}</td><td>{e.asignatura} · {e.grado} {e.seccion}</td><td>{e.entregadas}</td><td>{e.pendientes}</td><td>{e.tardias}</td><td>{e.notas_publicadas}</td></tr>)}</tbody></table></div>}</section>
    <section className="section"><h2>Clases virtuales</h2>{!clases.filter(c=>ids.has(c.curso_id)).length?<div className="empty">No hay clases registradas o el módulo está desactivado.</div>:<div className="table-wrap"><table><thead><tr><th>Clase</th><th>Curso</th><th>Inicio</th><th>Estado</th></tr></thead><tbody>{clases.filter(c=>ids.has(c.curso_id)).map(c=><tr key={c.id}><td><Link to={`/clases/${c.id}`}>{c.tema}</Link></td><td>{c.asignatura} · {c.grado} {c.seccion}</td><td>{fecha(c.inicio)}</td><td>{c.estado}</td></tr>)}</tbody></table></div>}</section><p><Link to="/historial">Consultar traslados, retiros e historial académico →</Link></p></>}
  </>;
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
  if(actual==='inicio')return <Inicio perfil={perfil} clasesHabilitadas={modulos?.clasesYGrabaciones!==false}/>;
  if(actual==='notificaciones')return <Bandeja perfil={perfil}/>;
  if(actual==='materiales'&&perfil.tipo!=='ADMINISTRADOR')return <MaterialesPage perfil={perfil}/>;
  if(actual==='historial'&&['COORDINACION','ADMINISTRADOR'].includes(perfil.tipo))return <Historial/>;
  if(perfil.tipo==='ADMINISTRADOR'&&actual==='grabaciones')return <AdminGrabaciones/>;
  if(perfil.tipo==='ADMINISTRADOR')return <AdministracionVista pagina={actual}/>;
  if(['ESTUDIANTE','DOCENTE','COORDINACION'].includes(perfil.tipo)&&actual==='clases')return <ClasesListado perfil={perfil}/>;
  if(['ESTUDIANTE','DOCENTE','COORDINACION'].includes(perfil.tipo)&&actual==='grabaciones')return <GrabacionesListado perfil={perfil}/>;
  if(perfil.tipo==='COORDINACION'&&['reportes','docentes'].includes(actual))return <CoordinacionVista pagina={actual}/>;
  return <Cursos perfil={perfil} pagina={actual}/>;
}
