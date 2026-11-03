import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {api,type Clase,type Curso,type Perfil,type Tarea} from '../api';

type Props={perfil:Perfil;clasesHabilitadas:boolean};
type TareaCurso={tarea:Tarea;curso:Curso};

function Icono({tipo}:{tipo:'calendario'|'tarea'}){
  return tipo==='calendario'
    ?<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3.5" y="5.5" width="17" height="15" rx="1"/><path d="M7.5 3v5M16.5 3v5M3.5 10h17"/></svg>
    :<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M6 2.5h8l4 4V21H6zM14 2.5v4h4M9 11h6M9 15h6"/></svg>;
}

const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{timeZone:'America/Lima',day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'});

export default function Inicio({perfil,clasesHabilitadas}:Props){
  const fechaActual=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'America/Lima',day:'2-digit',month:'2-digit',year:'numeric'}).formatToParts(new Date()).map(p=>[p.type,p.value]));
  const puedeVerClases=clasesHabilitadas&&perfil.tipo!=='ADMINISTRADOR';
  const {data:clases=[],isLoading:cargandoClases,error:errorClases}=useQuery<Clase[]>({queryKey:['clases-global'],queryFn:()=>api('/clases'),enabled:puedeVerClases});
  const consultaTareas=perfil.tipo==='ESTUDIANTE'||perfil.tipo==='DOCENTE';
  const {data:cursos=[],isLoading:cargandoCursos,error:errorCursos}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos'),enabled:consultaTareas});
  const {data:tareas=[],isLoading:cargandoTareas,error:errorTareas}=useQuery<TareaCurso[]>({queryKey:['inicio-tareas',cursos.map(c=>c.id).join(',')],queryFn:async()=>(
    await Promise.all(cursos.map(async curso=>(await api<Tarea[]>(`/cursos/${curso.id}/tareas`)).map(tarea=>({tarea,curso}))))
  ).flat(),enabled:consultaTareas&&cursos.length>0});
  const ahora=Date.now();
  const proximas=(puedeVerClases?clases:[]).filter(c=>c.estado==='PROGRAMADA'&&new Date(c.inicio).getTime()>=ahora)
    .sort((a,b)=>new Date(a.inicio).getTime()-new Date(b.inicio).getTime()).slice(0,3);
  const pendientes=tareas.filter(({tarea})=>perfil.tipo==='ESTUDIANTE'
    ?tarea.estado==='PUBLICADA'&&!tarea.presentada&&new Date(tarea.fecha_limite).getTime()>ahora
    :tarea.estado==='PUBLICADA'&&(tarea.pendientes_revision||0)>0)
    .sort((a,b)=>perfil.tipo==='ESTUDIANTE'
      ?new Date(a.tarea.fecha_limite).getTime()-new Date(b.tarea.fecha_limite).getTime()
      :new Date(b.tarea.creado_en).getTime()-new Date(a.tarea.creado_en).getTime()).slice(0,3);

  return <div className="inicio-editorial">
    <section className="inicio-hero" aria-label="Bienvenida al aula virtual">
      <div className="inicio-hero-principal">
        <div className="inicio-kicker">AULA VIRTUAL — {fechaActual.year}</div>
        <h1><span>APRENDER</span><span>ES CRECER<i>.</i></span></h1>
        <p>TU ESFUERZO HOY<br/>CONSTRUYE MÁS MAÑANA.</p>
      </div>
      <div className="inicio-hero-lateral"><time dateTime={`${fechaActual.year}-${fechaActual.month}-${fechaActual.day}`}>{fechaActual.day} / {fechaActual.month}</time><div className="inicio-raya"/><p>UN<br/>MEJOR<br/>FUTURO<br/>COMIENZA<br/>AQUÍ.</p></div>
    </section>
    <div className="inicio-franja"><strong>BOSTON BILINGUAL SCHOOL</strong><span>DISCIPLINA <b>·</b> CONOCIMIENTO <b>·</b> OPORTUNIDADES</span></div>
    <div className="inicio-tarjetas">
      <section className="inicio-tarjeta" aria-labelledby="inicio-clases-titulo">
        <span className="inicio-icono"><Icono tipo="calendario"/></span><div className="inicio-tarjeta-encabezado"><h2 id="inicio-clases-titulo">PRÓXIMAS CLASES</h2></div>
        {cargandoClases?<p className="inicio-cargando">Cargando clases…</p>:errorClases?<p className="inicio-cargando">No fue posible consultar las clases.</p>:proximas.length?<div className="inicio-items">{proximas.map(c=><Link key={c.id} className="inicio-item" to={`/clases/${c.id}?return=%2Fclases`}><span><strong>{c.tema}</strong><small>{c.asignatura} · {c.grado} {c.seccion} · {fecha(c.inicio)}</small></span><span aria-hidden="true">→</span></Link>)}</div>:<div className="inicio-vacio"><Icono tipo="calendario"/><p>Consulta tus clases programadas.</p><span className="inicio-vacio-raya"/><small>Aquí encontrarás las clases virtuales, fechas y horarios<br className="inicio-break"/> asignados por tus docentes.</small></div>}
      </section>
      <section className="inicio-tarjeta" aria-labelledby="inicio-tareas-titulo">
        <span className="inicio-icono"><Icono tipo="tarea"/></span><div className="inicio-tarjeta-encabezado"><h2 id="inicio-tareas-titulo">{perfil.tipo==='DOCENTE'?'TAREAS POR REVISAR':'MIS TAREAS'}</h2></div>
        {cargandoCursos||cargandoTareas?<p className="inicio-cargando">Cargando tareas…</p>:errorCursos||errorTareas?<p className="inicio-cargando">No fue posible consultar las tareas.</p>:pendientes.length?<div className="inicio-items">{pendientes.map(({tarea,curso})=><Link key={tarea.id} className="inicio-item" to={`/tareas/${tarea.id}?return=%2Ftareas`}><span><strong>{tarea.titulo}</strong><small>{curso.asignatura} · {curso.grado} {curso.seccion} · {perfil.tipo==='ESTUDIANTE'?'Entrega hasta':'Por revisar'} {fecha(tarea.fecha_limite)}</small></span><span aria-hidden="true">→</span></Link>)}</div>:<div className="inicio-vacio"><Icono tipo="tarea"/><p>{perfil.tipo==='DOCENTE'?'No tienes entregas por revisar por el momento.':'No tienes tareas pendientes por el momento.'}</p><span className="inicio-vacio-raya"/><small>{perfil.tipo==='DOCENTE'?'Cuando recibas entregas, se mostrarán aquí.':'Cuando tengas actividades asignadas, se mostrarán aquí.'}</small><Link className="inicio-ver-tareas" to="/tareas">Ver tareas <span aria-hidden="true">→</span></Link></div>}
      </section>
    </div>
  </div>;
}
