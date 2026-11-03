import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {api} from '../api';

type Curso={id:string;asignatura:string;nivel:string|null;grado:string;seccion:string;periodo:string;estudiantes_historicos:number;tareas:number};
type Estudiante={estudiante_erp_id:string;estudiante:string;activo:boolean;entregas:number;calificaciones:number};
type Actividad={tarea_id:string;titulo:string;entrega_id:string|null;entregado_en:string|null;valor:number|null;comentario:string|null};
const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});

export default function Historial(){
  const [curso,setCurso]=useState(''),[estudiante,setEstudiante]=useState('');
  const {data:cursos=[],error}=useQuery<Curso[]>({queryKey:['historial','cursos'],queryFn:()=>api('/historial/cursos')});
  const {data:estudiantes=[]}=useQuery<Estudiante[]>({queryKey:['historial','estudiantes',curso],queryFn:()=>api(`/historial/cursos/${curso}/estudiantes`),enabled:!!curso});
  const {data:actividades=[]}=useQuery<Actividad[]>({queryKey:['historial','actividad',curso,estudiante],queryFn:()=>api(`/historial/cursos/${curso}/estudiantes/${estudiante}`),enabled:!!curso&&!!estudiante});
  return <><div className="page-head"><div><span className="eyebrow">CONSULTA Y AUDITORÍA</span><h1>Historial académico</h1><p>Cursos de periodos anteriores y estudiantes que ya no están matriculados en la sección.</p></div></div>
    {error&&<div className="alert">{String(error)}</div>}
    <div className="panel form"><label>Curso<select value={curso} onChange={e=>{setCurso(e.target.value);setEstudiante('')}}><option value="">Seleccionar curso</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.periodo} · {c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label></div>
    {curso&&<section className="section"><h2>Estudiantes del curso</h2>{!estudiantes.length?<div className="empty">No hay estudiantes registrados.</div>:<div className="list">{estudiantes.map(e=><button className="list-row message-row" key={e.estudiante_erp_id} onClick={()=>setEstudiante(e.estudiante_erp_id)}><div><strong>{e.estudiante}</strong><span>{e.activo?'Matrícula vigente':'Matrícula histórica'} · {e.entregas} entregas · {e.calificaciones} notas</span></div><span>Consultar →</span></button>)}</div>}</section>}
    {estudiante&&<section className="section"><h2>Entregas y notas conservadas</h2>{!actividades.length?<div className="empty">No hay actividad registrada para este estudiante.</div>:<div className="list">{actividades.map((a,i)=><div className="list-row" key={`${a.tarea_id}-${a.entrega_id||i}`}><div><strong>{a.titulo}</strong><span>{a.entregado_en?`Entregada ${fecha(a.entregado_en)}`:'Sin entrega registrada'}</span>{a.comentario&&<p>{a.comentario}</p>}</div>{a.valor!=null&&<strong>Nota {a.valor}/20</strong>}</div>)}</div>}</section>}
  </>;
}
