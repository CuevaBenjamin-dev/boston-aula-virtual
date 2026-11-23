import {useEffect,useState} from 'react';
import {Link,useNavigate,useParams,useSearchParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useForm} from 'react-hook-form';
import {z} from 'zod';
import {zodResolver} from '@hookform/resolvers/zod';
import {api,post,put,subirArchivo,type Archivo,type Clase,type Curso,type Entrega,type Nota,type Perfil,type Tarea} from '../api';
import {PageHeading} from '../PageHeading';
import {BusquedaConSugerencias,coincideBusqueda} from '../components/BusquedaConSugerencias';
import {fecha} from './AcademicoComun';
import {PrepararEntrega} from './EntregasAcademicas';
import {SeguimientoTarea} from './SeguimientoAcademico';
import {ArchivosDe,archivoPermitido} from './AdjuntosAcademicos';
import {MaterialesCurso} from './MaterialesAcademicos';
export {MaterialesPage} from './MaterialesAcademicos';

function fechaLocalLima(s:string){const piezas=Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(s)).map(x=>[x.type,x.value]));return `${piezas.year}-${piezas.month}-${piezas.day}T${piezas.hour}:${piezas.minute}`}
function aIsoLima(s:string){return new Date(`${s}:00-05:00`).toISOString()}
const esquema=z.object({titulo:z.string().trim().min(3),instrucciones:z.string().trim().min(3),fechaLimite:z.string().min(1)});
type FormTarea=z.infer<typeof esquema>;
type NotaDetalle=Nota&{curso_id:string;instrucciones:string};
function estadoTarea(t:Tarea,perfil:Perfil){
  if(perfil.tipo==='ESTUDIANTE'&&t.presentada)return t.evaluacion_publicada?'REVISADA':t.ultima_entrega_estado==='TARDIA'?'ENTREGADA TARDÍA':'ENTREGADA';
  if(t.estado==='CANCELADA')return 'CANCELADA';
  if(t.estado==='CERRADA')return 'CERRADA';
  if(perfil.tipo!=='ESTUDIANTE')return t.estado==='FALTA_COMPLETAR'?'FALTA COMPLETAR':new Date(t.fecha_limite).getTime()<=Date.now()?'VENCIDA ABIERTA':'PUBLICADO';
  const restante=new Date(t.fecha_limite).getTime()-Date.now();
  if(restante<=0)return 'VENCIDA ABIERTA';
  return restante>0&&restante<10*60*60*1000?'URGENTE':'PENDIENTE';
}
function semanaActual(s:string){
  const hoy=new Date(fechaLocalLima(new Date().toISOString()).slice(0,10)+'T00:00:00Z');
  hoy.setUTCDate(hoy.getUTCDate()-((hoy.getUTCDay()+6)%7));
  const fin=new Date(hoy);fin.setUTCDate(fin.getUTCDate()+7);
  const dia=fechaLocalLima(s).slice(0,10);
  return dia>=hoy.toISOString().slice(0,10)&&dia<fin.toISOString().slice(0,10);
}
function EtiquetaTarea({t,perfil}:{t:Tarea;perfil:Perfil}){
  const estado=estadoTarea(t,perfil);return <div className="task-status"><span className={'badge '+estado.toLowerCase().replaceAll(' ','_')}>{estado}</span>{estado==='REVISADA'&&t.ultima_entrega_estado==='TARDIA'&&<small>Entrega tardía</small>}</div>;
}

export function Cursos({perfil,pagina}:{perfil:Perfil;pagina:string}){
  const {data:cursos,isLoading,error}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});
  const [anioHistorial,setAnioHistorial]=useState('');
  const {data:sincronizacion}=useQuery<{sincronizadoEn:string|null;pendiente:boolean}>({queryKey:['sincronizacion'],queryFn:()=>api('/sincronizacion/estado'),enabled:pagina==='cursos',refetchInterval:30000});
  if(isLoading)return <p className="muted">Cargando cursos…</p>;
  if(error)return <div className="alert">No fue posible consultar los cursos. {String(error)}</div>;
  if(pagina==='tareas'||pagina==='notas')return <VistaGlobal cursos={pagina==='tareas'&&perfil.tipo==='ESTUDIANTE'?(cursos||[]).filter(c=>c.estado_matricula==='VIGENTE'):cursos||[]} pagina={pagina} perfil={perfil}/>;
  const titulo=pagina==='historial'?'Historial académico':pagina==='inicio'?'Bienvenido al Aula Virtual':pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):pagina==='notas'?(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones'):'Mis cursos';
  if(pagina==='cursos'||pagina==='historial'){
    const historico=pagina==='historial';
    const visibles=(cursos||[]).filter(c=>perfil.tipo!=='ESTUDIANTE'||(historico?c.estado_matricula==='FINALIZADA':c.estado_matricula==='VIGENTE'));
    const filtrados=historico&&anioHistorial?visibles.filter(c=>c.periodo===anioHistorial):visibles;
    const nombre=historico?'Historial académico':perfil.tipo==='COORDINACION'?'Cursos y secciones':'Mis cursos actuales';
    const anio=new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric'}).format(new Date());
    return <main className="cursos-editorial"><PageHeading title={nombre} metricLabel={historico?'CURSOS FINALIZADOS':'CURSOS INSTITUCIONALES'} metricValue={String(visibles.length).padStart(2,'0')} metricCaption={historico?'SOLO LECTURA':visibles.length===1?'CURSO DISPONIBLE':'CURSOS DISPONIBLES'}/>
      <header className="cursos-portada">
        <div className="cursos-portada-principal"><span className="cursos-sobretitulo">AULA VIRTUAL — {anio}</span><h1>{nombre}<i>.</i></h1><p>{historico?'Consulta cursos y actividades de años finalizados. No se admiten nuevas entregas.':'Selecciona un curso para consultar su resumen.'}</p></div>
        <div className="cursos-portada-lateral"><span>{historico?'HISTORIAL ACADÉMICO':'CURSOS INSTITUCIONALES'}</span><strong>{String(visibles.length).padStart(2,'0')}</strong><small>{historico?'SOLO LECTURA':visibles.length===1?'CURSO DISPONIBLE':'CURSOS DISPONIBLES'}</small></div>
      </header>
      {sincronizacion?.pendiente&&<div className="alert">{`El ERP está temporalmente inaccesible. Se muestran los cursos de la última sincronización${sincronizacion.sincronizadoEn?` (${fecha(sincronizacion.sincronizadoEn)})`:''}; los cambios pendientes se reintentarán automáticamente.`}</div>}
      {historico&&<label className="form">Año escolar <select value={anioHistorial} onChange={e=>setAnioHistorial(e.target.value)}><option value="">Todos</option>{[...new Set(visibles.map(c=>c.periodo))].sort().reverse().map(y=><option key={y} value={y}>{y}</option>)}</select></label>}
      {!filtrados.length?<div className="cursos-vacio">{historico?'No hay cursos finalizados para este año.':'Aún no hay cursos actuales asignados. La sincronización con el ERP se ejecuta automáticamente.'}</div>:<section className="cursos-cuadricula" aria-label={historico?'Cursos finalizados':'Cursos actuales'}>{filtrados.map(c=><Link className="cursos-ficha" key={c.id} to={'/cursos/'+c.id}><div className="cursos-ficha-cuerpo"><span>{c.periodo} · {c.nivel} · {c.grado} · SECCIÓN {c.seccion}</span><h2>{c.asignatura}</h2><small className="cursos-ficha-docente">{historico?'FINALIZADO · SOLO LECTURA':`DOCENTE: ${c.docentes||'Sin docente asignado'}`}</small></div><div className="cursos-ficha-pie"><span>ABRIR CURSO</span><span aria-hidden="true">→</span></div></Link>)}</section>}
    </main>;
  }
  return <><PageHeading title={titulo} metricLabel="CURSOS INSTITUCIONALES" metricValue={String(cursos?.length||0).padStart(2,'0')} metricCaption="CURSOS DISPONIBLES"/><div className="page-head"><div><span className="eyebrow">CURSOS INSTITUCIONALES</span><h1>{titulo}</h1><p>Selecciona un curso para consultar su resumen.</p></div></div>{sincronizacion?.pendiente&&<div className="alert">{`El ERP está temporalmente inaccesible. Se muestran los cursos de la última sincronización${sincronizacion.sincronizadoEn?` (${fecha(sincronizacion.sincronizadoEn)})`:''}; los cambios pendientes se reintentarán automáticamente.`}</div>}{!cursos?.length?<div className="empty">Aún no hay cursos asignados. La sincronización con el ERP se ejecuta automáticamente.</div>:<div className="cards">{cursos.map(c=><Link className="course-card" key={c.id} to={'/cursos/'+c.id}><span className="course-icon">{c.asignatura[0]}</span><span className="eyebrow">{c.nivel} · {c.grado} · {c.seccion}</span><h2>{c.asignatura}</h2><p>Periodo {c.periodo}</p><span className="text-link">Abrir curso →</span></Link>)}</div>}</>;
}

type Fila={id:string;curso:Curso;tarea?:Tarea;nota?:Nota};
function VistaGlobal({cursos,pagina,perfil}:{cursos:Curso[];pagina:string;perfil:Perfil}){
  const [params,setParams]=useSearchParams();
  const mostrarHistorial=params.get('historial')==='1';
  const cambiar=(clave:string,valor:string)=>{const siguiente=new URLSearchParams(params);if(valor)siguiente.set(clave,valor);else siguiente.delete(clave);siguiente.delete('resaltar');setParams(siguiente)};
  const ids=cursos.map(c=>c.id).join(',');
  const {data:filas=[],isLoading,error}=useQuery<Fila[]>({queryKey:['vista-global',pagina,ids,perfil.tipo],queryFn:async()=>{
    if(pagina==='notas'&&perfil.tipo==='ESTUDIANTE')return (await Promise.all(cursos.map(async curso=>{
      const [notas,tareas]=await Promise.all([api<Nota[]>(`/cursos/${curso.id}/notas`),api<Tarea[]>(`/cursos/${curso.id}/tareas`)]);
      const pendientes=tareas.filter(t=>t.estado!=='CANCELADA'&&!notas.some(n=>n.tarea_id===t.id&&n.entrega_version===t.ultima_entrega_version)).map(t=>({id:t.id,curso,tarea:t}));
      return [...notas.map(n=>({id:n.id,curso,nota:n})),...pendientes] as Fila[];
    }))).flat().sort((a,b)=>new Date(b.nota?.publicada_en||b.tarea?.creado_en||'').getTime()-new Date(a.nota?.publicada_en||a.tarea?.creado_en||'').getTime());
    return (await Promise.all(cursos.map(async curso=>(await api<Tarea[]>(`/cursos/${curso.id}/tareas`)).map(t=>({id:t.id,curso,tarea:t}))))).flat().sort((a,b)=>new Date(b.tarea!.creado_en).getTime()-new Date(a.tarea!.creado_en).getTime());
  }});
  const visibles=filas.filter(f=>{
    const texto=params.get('q')||'';
    const valorFecha=f.nota?.actualizado_en||f.tarea?.fecha_limite||'';
    const dia=fechaLocalLima(valorFecha).slice(0,10);
    return (!params.get('curso')||f.curso.id===params.get('curso'))
      &&(pagina!=='tareas'||perfil.tipo!=='DOCENTE'||Boolean(f.tarea?.es_historica)===mostrarHistorial)
      &&(!texto||coincideBusqueda(`${f.nota?.titulo||f.tarea?.titulo} ${f.curso.asignatura} ${f.nota?.comentario||''}`,texto))
      &&(pagina!=='tareas'||!params.get('estado')||estadoTarea(f.tarea!,perfil)===params.get('estado'))
      &&(!params.get('desde')||dia>=params.get('desde')!)
      &&(!params.get('hasta')||dia<=params.get('hasta')!);
  });
  useEffect(()=>{const id=params.get('resaltar');if(id&&visibles.some(f=>f.id===id))requestAnimationFrame(()=>document.getElementById(`tarea-${id}`)?.scrollIntoView({behavior:'smooth',block:'center'}))},[params,visibles.length]);
  const titulo=pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones');
  const descripcion=pagina==='notas'&&perfil.tipo==='DOCENTE'?'Selecciona una tarea para revisar entregas y calificar.':'Consulta la información de tus cursos.';
  if(pagina==='tareas'){
    const anio=new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric'}).format(new Date());
    const enlace=(f:Fila)=>`/tareas/${f.id}?return=${encodeURIComponent('/tareas?'+new URLSearchParams({...Object.fromEntries(params),resaltar:f.id}).toString())}`;
    return <main className="tareas-editorial"><PageHeading title={titulo} metricLabel="TAREAS EN EL LISTADO" metricValue={String(visibles.length).padStart(2,'0')} metricCaption={visibles.length===1?'RESULTADO VISIBLE':'RESULTADOS VISIBLES'}/>
      <header className="cursos-portada tareas-portada">
        <div className="cursos-portada-principal"><span className="cursos-sobretitulo">AULA VIRTUAL — {anio}</span><h1>{titulo}<i>.</i></h1><p>{perfil.tipo==='ESTUDIANTE'?'Organiza tus actividades, revisa los plazos y continúa tus entregas.':'Organiza las actividades de tus cursos y revisa sus entregas.'}</p></div>
        <div className="cursos-portada-lateral"><span>TAREAS EN EL LISTADO</span><strong>{String(visibles.length).padStart(2,'0')}</strong><small>{visibles.length===1?'RESULTADO VISIBLE':'RESULTADOS VISIBLES'}</small></div>
      </header>
      {perfil.tipo==='DOCENTE'&&cursos.length>0&&<div className="tareas-crear"><CrearTarea cursos={cursos}/></div>}
      <section className="tareas-busqueda" aria-labelledby="tareas-busqueda-titulo">
        <div className="tareas-seccion-cabecera"><div><span>01 / EXPLORAR</span><h2 id="tareas-busqueda-titulo">Encuentra una tarea</h2><p>Filtra por curso, estado o fecha de entrega.</p></div></div>
        <div className="panel form filters"><div className="filter-grid">
          <BusquedaConSugerencias etiqueta="Buscar" valor={params.get('q')||''} alCambiar={v=>cambiar('q',v)} candidatos={filas.flatMap(f=>[f.tarea?.titulo||'',f.curso.asignatura])} placeholder="Tarea o curso"/>
          <label>Curso<select value={params.get('curso')||''} onChange={e=>cambiar('curso',e.target.value)}><option value="">Todos los cursos</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label>
          <label>Estado<select value={params.get('estado')||''} onChange={e=>cambiar('estado',e.target.value)}><option value="">Todos</option>{(perfil.tipo==='ESTUDIANTE'?['PENDIENTE','URGENTE','VENCIDA ABIERTA','ENTREGADA','ENTREGADA TARDÍA','REVISADA','CERRADA','CANCELADA']:['PUBLICADO','VENCIDA ABIERTA','FALTA COMPLETAR','CERRADA','CANCELADA']).map(e=><option key={e}>{e}</option>)}</select></label>
          <label>Desde<input type="date" value={params.get('desde')||''} onChange={e=>cambiar('desde',e.target.value)}/></label>
          <label>Hasta<input type="date" value={params.get('hasta')||''} onChange={e=>cambiar('hasta',e.target.value)}/></label>
        </div><button type="button" className="button secondary" onClick={()=>setParams(new URLSearchParams())}>Limpiar filtros</button></div>
      </section>
      <section className="tareas-resultados" aria-labelledby="tareas-resultados-titulo"><div className="tareas-seccion-cabecera"><div><span>02 / ACTIVIDADES</span><h2 id="tareas-resultados-titulo">{perfil.tipo==='DOCENTE'&&mostrarHistorial?'Versiones anteriores':'Tareas de tus cursos'}</h2><p>{visibles.length} {visibles.length===1?'tarea encontrada':'tareas encontradas'}</p></div>{perfil.tipo==='DOCENTE'&&<button className="text-link" onClick={()=>cambiar('historial',mostrarHistorial?'':'1')}>{mostrarHistorial?'← Ver tareas vigentes':'Ver versiones anteriores →'}</button>}</div>
        {isLoading?<p className="muted">Cargando tareas…</p>:error?<div className="alert">No fue posible consultar las tareas. {String(error)}</div>:!visibles.length?<div className="empty">{cursos.length?'No hay tareas para estos filtros.':'Aún no tienes cursos asignados.'}</div>:<div className="tareas-lista">{visibles.map(f=>{
          const tarea=f.tarea!;
          const limite=new Date(tarea.fecha_limite);
          const dia=new Intl.DateTimeFormat('es-PE',{day:'2-digit',timeZone:'America/Lima'}).format(limite);
          const mes=new Intl.DateTimeFormat('es-PE',{month:'short',timeZone:'America/Lima'}).format(limite).replace('.','');
          return <Link id={`tarea-${f.id}`} className={'tareas-tarjeta'+(params.get('resaltar')===f.id?' task-return':'')} key={f.id} to={enlace(f)}><div className="tareas-tarjeta-fecha"><strong>{dia}</strong><span>{mes}</span></div><div className="tareas-tarjeta-info"><span className="tareas-tarjeta-curso">{f.curso.asignatura} · {f.curso.nivel} {f.curso.grado} {f.curso.seccion}</span><h3>{tarea.titulo}</h3><p>Entrega hasta {fecha(tarea.fecha_limite)} · Lima</p>{perfil.tipo==='DOCENTE'&&!mostrarHistorial&&<small>{tarea.entregados}/{tarea.total_estudiantes} entregaron · {tarea.tardias} tardías · {tarea.pendientes_revision} por revisar</small>}</div><EtiquetaTarea t={tarea} perfil={perfil}/><span className="tareas-tarjeta-abrir">VER TAREA <b aria-hidden="true">→</b></span></Link>;
        })}</div>}
      </section>
    </main>;
  }
  const anio=new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric'}).format(new Date());
  const volver=(id:string)=>encodeURIComponent('/notas?'+new URLSearchParams({...Object.fromEntries(params),resaltar:id}).toString());
  return <main className="notas-editorial"><PageHeading title={titulo} metricLabel="RESULTADOS EN EL LISTADO" metricValue={String(visibles.length).padStart(2,'0')} metricCaption={visibles.length===1?'RESULTADO VISIBLE':'RESULTADOS VISIBLES'}/>
    <header className="cursos-portada notas-portada"><div className="cursos-portada-principal"><span className="cursos-sobretitulo">AULA VIRTUAL — {anio}</span><h1>{titulo}<i>.</i></h1><p>{perfil.tipo==='ESTUDIANTE'?'Revisa tus calificaciones y el estado de tus actividades.':descripcion}</p></div><div className="cursos-portada-lateral"><span>RESULTADOS EN EL LISTADO</span><strong>{String(visibles.length).padStart(2,'0')}</strong><small>{visibles.length===1?'RESULTADO VISIBLE':'RESULTADOS VISIBLES'}</small></div></header>
<section className="notas-busqueda" aria-labelledby="notas-busqueda-titulo"><div className="tareas-seccion-cabecera"><div><span>01 / EXPLORAR</span><h2 id="notas-busqueda-titulo">Encuentra una actividad</h2><p>Busca una actividad o acota los resultados por curso y fecha.</p></div></div><div className="panel form filters"><div className="filter-grid"><BusquedaConSugerencias etiqueta="Buscar" valor={params.get('q')||''} alCambiar={v=>cambiar('q',v)} candidatos={filas.flatMap(f=>[f.nota?.titulo||f.tarea?.titulo||'',f.curso.asignatura,f.nota?.comentario||''])} placeholder="Actividad o comentario"/><label>Curso<select value={params.get('curso')||''} onChange={e=>cambiar('curso',e.target.value)}><option value="">Todos los cursos</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label><label>Desde<input type="date" value={params.get('desde')||''} onChange={e=>cambiar('desde',e.target.value)}/></label><label>Hasta<input type="date" value={params.get('hasta')||''} onChange={e=>cambiar('hasta',e.target.value)}/></label></div><button type="button" className="button secondary" onClick={()=>setParams(new URLSearchParams())}>Limpiar filtros</button></div></section>
    <section className="notas-resultados" aria-labelledby="notas-resultados-titulo"><div className="tareas-seccion-cabecera"><div><span>02 / RESULTADOS</span><h2 id="notas-resultados-titulo">{perfil.tipo==='ESTUDIANTE'?'Calificaciones y actividades':'Actividades para evaluar'}</h2><p>{visibles.length} {visibles.length===1?'resultado encontrado':'resultados encontrados'}</p></div></div>
      {isLoading?<p className="muted">Cargando calificaciones…</p>:error?<div className="alert">No fue posible consultar las calificaciones. {String(error)}</div>:!visibles.length?<div className="empty">{cursos.length?'No hay resultados para estos filtros.':'Aún no tienes cursos asignados.'}</div>:<div className="notas-lista">{visibles.map(f=>{
        const nota=f.nota;
        const esAnterior=nota&&nota.entrega_version<nota.ultima_entrega_version;
        const fechaReferencia=nota?(nota.publicada_en||nota.actualizado_en):f.tarea!.fecha_limite;
        const dia=new Intl.DateTimeFormat('es-PE',{day:'2-digit',timeZone:'America/Lima'}).format(new Date(fechaReferencia));
        const mes=new Intl.DateTimeFormat('es-PE',{month:'short',timeZone:'America/Lima'}).format(new Date(fechaReferencia)).replace('.','');
        const estadoPendiente=nota?'':perfil.tipo==='ESTUDIANTE'?(f.tarea!.presentada?'PENDIENTE DE EVALUACIÓN':'SIN ENTREGAR'):`${f.tarea!.pendientes_revision} POR REVISAR`;
        const enlace=nota?`/notas/${f.id}?return=${volver(f.id)}`:`/tareas/${f.id}?return=${volver(f.id)}`;
        return <Link id={`tarea-${f.id}`} className={'notas-tarjeta'+(!nota?' notas-tarjeta-pendiente':'')+(params.get('resaltar')===f.id?' task-return':'')} key={f.id} to={enlace}><div className="notas-tarjeta-fecha"><strong>{dia}</strong><span>{mes}</span></div><div className="notas-tarjeta-info"><span>{f.curso.asignatura} · {f.curso.nivel} {f.curso.grado} {f.curso.seccion}</span><h3>{nota?.titulo||f.tarea!.titulo}</h3><p>{nota?(esAnterior?'Intento anterior':'Publicada')+' '+fecha(fechaReferencia):'Entrega hasta '+fecha(fechaReferencia)}</p>{nota?.comentario&&<small>{nota.comentario}</small>}</div><div className="notas-tarjeta-resultado">{nota?<><strong>{nota.valor}<small> / {nota.escala_maxima}</small></strong><span>{esAnterior?'INTENTO ANTERIOR':'NOTA PUBLICADA'}</span></>:<><strong aria-hidden="true">—</strong><span>{estadoPendiente}</span></>}</div><span className="notas-tarjeta-abrir">{nota?'VER NOTA':'VER TAREA'} <b aria-hidden="true">→</b></span></Link>;
      })}</div>}
    </section>
  </main>;

}

function CrearTarea({curso,cursos}:{curso?:string;cursos:Curso[]}){
  const qc=useQueryClient(),[abierto,setAbierto]=useState(false),[archivos,setArchivos]=useState<File[]>([]),[progreso,setProgreso]=useState(0),[incompleta,setIncompleta]=useState<string|null>(null),[operacion,setOperacion]=useState(()=>crypto.randomUUID());
  const inicial=cursos.find(c=>c.id===curso);
  const [nivel,setNivel]=useState(inicial?.nivel||''),[grado,setGrado]=useState(inicial?.grado||''),[seccion,setSeccion]=useState(inicial?.seccion||''),[cursoElegido,setCursoElegido]=useState(curso||''),[destinos,setDestinos]=useState<string[]>(curso?[curso]:[]);
  useEffect(()=>{const c=cursos.find(x=>x.id===curso);if(c){setNivel(c.nivel||'Sin nivel');setGrado(c.grado);setSeccion(c.seccion);setCursoElegido(c.id);setDestinos([c.id])}},[curso,cursos.length]);
  const niveles=[...new Set(cursos.map(c=>c.nivel||'Sin nivel'))];
  const grados=[...new Set(cursos.filter(c=>!nivel||(c.nivel||'Sin nivel')===nivel).map(c=>c.grado))];
  const secciones=[...new Set(cursos.filter(c=>(!nivel||(c.nivel||'Sin nivel')===nivel)&&(!grado||c.grado===grado)).map(c=>c.seccion))];
  const opciones=cursos.filter(c=>(!nivel||(c.nivel||'Sin nivel')===nivel)&&(!grado||c.grado===grado)&&(!seccion||c.seccion===seccion));
  const base=cursos.find(c=>c.id===cursoElegido);
  const seccionesCompatibles=base?cursos.filter(c=>c.asignatura===base.asignatura&&c.nivel===base.nivel&&c.grado===base.grado):[];
  const {register,handleSubmit,formState:{errors},reset}=useForm<FormTarea>({resolver:zodResolver(esquema),defaultValues:{titulo:'',instrucciones:'',fechaLimite:''}});
  const enviar=(d:FormTarea,publicar:boolean)=>{
    const limite=aIsoLima(d.fechaLimite);
    if(new Date(limite).getTime()<=Date.now()&&!window.confirm(`La fecha límite ${fecha(limite)} ya pasó. La tarea aparecerá como vencida y las entregas serán tardías. ¿Continuar?`))return;
    crear.mutate({d,publicar});
  };
  const crear=useMutation({mutationFn:async({d,publicar}:{d:FormTarea;publicar:boolean})=>{
    if(!cursoElegido||!opciones.some(c=>c.id===cursoElegido))throw new Error('Selecciona un curso asignado.');
    const elegidos=destinos.filter(id=>seccionesCompatibles.some(c=>c.id===id));
    if(!elegidos.length)throw new Error('Selecciona al menos una sección.');
    const limite=aIsoLima(d.fechaLimite);
    if(elegidos.length>1){
      const lote=await post<{operacionId:string;tareas:Tarea[]}>('/tareas/lotes',{cursoIds:elegidos,titulo:d.titulo,instrucciones:d.instrucciones,fechaLimite:limite,operacionId:operacion});
      const fuente=lote.tareas[0]?.id;if(!fuente)throw new Error('No se crearon las tareas del lote.');
      setIncompleta(fuente);
      const existentes=await api<Archivo[]>(`/tareas/${fuente}/archivos`);
      for(const archivo of archivos)if(!existentes.some(x=>x.nombre===archivo.name&&x.tamano===archivo.size))await subirArchivo('tareas',fuente,archivo,setProgreso);
      await post(`/tareas/lotes/${operacion}/compartir-archivos`,{});
      if(publicar)await post(`/tareas/lotes/${operacion}/publicar`,{});
      return fuente;
    }
    let id=incompleta;
    if(id) {
      const anterior=await api<Tarea>(`/tareas/${id}`);
      if(anterior.estado==='PUBLICADA')return id;
      await put(`/tareas/${id}`,{...d,fechaLimite:limite,version:anterior.version,cursoId:elegidos[0]});
    } else id=(await post<{id:string}>(`/cursos/${elegidos[0]}/tareas`,{...d,fechaLimite:limite})).id;
    setIncompleta(id);
    const existentes=await api<Archivo[]>(`/tareas/${id}/archivos`);
    for(const archivo of archivos)if(!existentes.some(x=>x.nombre===archivo.name&&x.tamano===archivo.size))await subirArchivo('tareas',id,archivo,setProgreso);
    if(publicar)await post(`/tareas/${id}/publicar`,{});return id;
  },onSuccess:()=>{qc.invalidateQueries({queryKey:['tareas']});qc.invalidateQueries({queryKey:['vista-global']});reset();setArchivos([]);setIncompleta(null);setOperacion(crypto.randomUUID());setProgreso(0);setAbierto(false)}});
  return <><button className="button primary" onClick={()=>setAbierto(!abierto)}>+ Nueva tarea</button>{abierto&&<form className="panel form" onSubmit={handleSubmit(d=>enviar(d,true))}><h3>Nueva tarea</h3>
    <div className="filter-grid"><label>Nivel<select value={nivel} onChange={e=>{setNivel(e.target.value);setGrado('');setSeccion('');setCursoElegido('');setDestinos([])}}><option value="">Seleccionar nivel</option>{niveles.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Grado<select value={grado} onChange={e=>{setGrado(e.target.value);setSeccion('');setCursoElegido('');setDestinos([])}}><option value="">Seleccionar grado</option>{grados.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Sección<select value={seccion} onChange={e=>{setSeccion(e.target.value);setCursoElegido('');setDestinos([])}}><option value="">Seleccionar sección</option>{secciones.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Curso<select value={cursoElegido} required onChange={e=>{setCursoElegido(e.target.value);setDestinos(e.target.value?[e.target.value]:[])}}><option value="">Seleccionar asignatura</option>{opciones.map(c=><option value={c.id} key={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label></div>
    {seccionesCompatibles.length>1&&<div className="form compact"><strong>Secciones destinatarias</strong><div className="recipient-list">{seccionesCompatibles.map(c=><label className="recipient" key={c.id}><input type="checkbox" checked={destinos.includes(c.id)} onChange={e=>setDestinos(actual=>e.target.checked?[...actual,c.id]:actual.filter(id=>id!==c.id))}/>{c.nivel} {c.grado} {c.seccion}</label>)}</div><small>Se creará una tarea independiente para cada sección seleccionada.</small></div>}
    <label>Título<input {...register('titulo')}/>{errors.titulo&&<small className="error">Escribe al menos 3 caracteres.</small>}</label><label>Instrucciones<textarea rows={4} {...register('instrucciones')}/>{errors.instrucciones&&<small className="error">Las instrucciones son obligatorias.</small>}</label><label>Fecha y hora límite (Lima)<input type="datetime-local" {...register('fechaLimite')}/></label><label>Archivos de referencia (opcional)<input type="file" multiple accept={archivoPermitido} onChange={e=>setArchivos(Array.from(e.target.files||[]))}/></label>{archivos.length>0&&<small>{archivos.map(a=>a.name).join(', ')}</small>}{progreso>0&&<progress value={progreso} max="100"/>}{crear.error&&<p className="error">No se completó la tarea. {incompleta?<Link to={`/tareas/${incompleta}`}>Abrir tarea para completarla →</Link>:String(crear.error)}</p>}<div className="row-actions"><button className="button primary" disabled={crear.isPending}>Publicar {destinos.length>1?'tareas':'tarea'}</button><button type="button" className="button secondary" disabled={crear.isPending} onClick={handleSubmit(d=>enviar(d,false))}>Guardar como borrador</button></div></form>}</>;
}

export function CursoPage({perfil,modulos}:{perfil:Perfil;modulos?:{clasesYGrabaciones:boolean}}){
  const {cursoId}=useParams();
  const [parametros,setParametros]=useSearchParams();
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===cursoId);
  const historico=perfil.tipo==='ESTUDIANTE'&&curso?.estado_matricula==='FINALIZADA';
  const {data:tareas=[]}=useQuery<Tarea[]>({queryKey:['tareas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/tareas`),enabled:!!cursoId,refetchInterval:60000});
  const {data:clases=[]}=useQuery<Clase[]>({queryKey:['clases',cursoId],queryFn:()=>api(`/cursos/${cursoId}/clases`),enabled:!!cursoId&&modulos?.clasesYGrabaciones!==false,refetchInterval:30000});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/notas`),enabled:!!cursoId&&['ESTUDIANTE','DOCENTE'].includes(perfil.tipo)});
  const resumenTareas=historico?tareas:perfil.tipo==='ESTUDIANTE'?tareas.filter(t=>semanaActual(t.publicado_en||t.creado_en)&&!t.presentada&&t.estado==='PUBLICADA'):tareas.filter(t=>!t.es_historica).slice(0,5);
  const resumenClases=[...clases].sort((a,b)=>Math.abs(new Date(a.inicio).getTime()-Date.now())-Math.abs(new Date(b.inicio).getTime()-Date.now())).slice(0,3);
  const numeroNotas=modulos?.clasesYGrabaciones===false?'03':'04';
  const vistas=['tareas','materiales',...(modulos?.clasesYGrabaciones===false||historico?[]:['clases']),...(['ESTUDIANTE','DOCENTE'].includes(perfil.tipo)?['notas']:[])];
  const vista=vistas.includes(parametros.get('vista')||'')?parametros.get('vista'):null;
  const seleccionarVista=(siguiente:string|null)=>{const nuevos=new URLSearchParams(parametros);if(siguiente)nuevos.set('vista',siguiente);else nuevos.delete('vista');setParametros(nuevos)};
  return <main className="curso-editorial-detalle"><PageHeading title={curso?.asignatura||'Curso'} metricLabel={curso?.nivel||'CURSO'} metricValue={`${curso?.grado||'Grado'} · ${curso?.seccion||'Sección'}`} metricCaption={curso?.docentes||'Sin docente asignado'}/>
    <header className="curso-detalle-cabecera"><Link className="curso-detalle-volver" to={historico?'/historial':'/cursos'}>← {historico?'HISTORIAL ACADÉMICO':'MIS CURSOS'}</Link><div className="curso-detalle-portada"><div><span className="cursos-sobretitulo">AULA VIRTUAL — {curso?.periodo||'CURSO'}</span><h1>{curso?.asignatura||'Curso'}<i>.</i></h1><p>{historico?'Matrícula finalizada · Solo lectura. Consulta tus materiales, tareas, entregas y notas.':modulos?.clasesYGrabaciones===false?'Resumen de tareas y materiales del curso.':`Resumen de tareas, clases y ${perfil.tipo==='ESTUDIANTE'?'notas':'entregas'} del curso.`}</p></div><div className="curso-detalle-contexto"><span>{curso?.nivel||'NIVEL'}</span><strong>{curso?.grado||'Grado'} · {curso?.seccion||'Sección'}</strong><small>DOCENTE<br/>{curso?.docentes||'Sin docente asignado'}</small></div></div></header>
    <nav className="curso-indice" aria-label="Filtrar contenido del curso">
      <button type="button" aria-pressed={vista==='tareas'} onClick={()=>seleccionarVista('tareas')}><span>01</span>Tareas</button>
      <button type="button" aria-pressed={vista==='materiales'} onClick={()=>seleccionarVista('materiales')}><span>02</span>Materiales</button>
      {modulos?.clasesYGrabaciones!==false&&!historico&&<button type="button" aria-pressed={vista==='clases'} onClick={()=>seleccionarVista('clases')}><span>03</span>Clases virtuales</button>}
      {['ESTUDIANTE','DOCENTE'].includes(perfil.tipo)&&<button type="button" aria-pressed={vista==='notas'} onClick={()=>seleccionarVista('notas')}><span>{numeroNotas}</span>{perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones'}</button>}
    </nav>
    {vista&&<div className="curso-filtro-retorno"><button type="button" onClick={()=>seleccionarVista(null)}>Mostrar todas las secciones</button></div>}
    {(!vista||vista==='tareas')&&<section className="section curso-resumen-seccion" id="curso-tareas">
      <div className="curso-seccion-cabecera"><div className="curso-seccion-titulos"><span className="curso-seccion-numero">01 / ACTIVIDADES</span><h2>{historico?'Tareas del año':perfil.tipo==='ESTUDIANTE'?'Tareas pendientes de esta semana':'Tareas'}</h2><p>{historico?'Actividades conservadas para consulta.':perfil.tipo==='ESTUDIANTE'?'Consulta las actividades que tienes por entregar.':'Consulta las actividades recientes y sus entregas.'}</p></div>{perfil.tipo==='DOCENTE'&&cursoId&&<CrearTarea curso={cursoId} cursos={cursos}/>}</div>
      {!resumenTareas.length?<div className="empty">{historico?'No hay tareas registradas en este curso.':perfil.tipo==='ESTUDIANTE'?'No hay tareas pendientes esta semana.':'Todavía no hay tareas.'}</div>:<div className="curso-resumen-lista">{resumenTareas.map(t=>{const regreso=`/tareas?curso=${cursoId}&q=${encodeURIComponent(t.titulo)}&resaltar=${t.id}`;return <Link className={'curso-resumen-fila'+(perfil.tipo==='DOCENTE'?' curso-fila-docente':'')} to={`/tareas/${t.id}?return=${encodeURIComponent(regreso)}`} key={t.id}><div className="curso-fila-principal"><strong>{t.titulo}</strong><span>Entrega hasta {fecha(t.fecha_limite)}</span></div>{perfil.tipo==='DOCENTE'&&<div className="curso-fila-metrica"><small>ENTREGAS</small><strong>{t.entregados}/{t.total_estudiantes}</strong></div>}{!historico&&<EtiquetaTarea t={t} perfil={perfil}/>}<span className="curso-fila-accion">Ver tarea <b aria-hidden="true">→</b></span></Link>})}</div>}
      <Link className="curso-seccion-pie" to={`/tareas?curso=${cursoId}`}>Ver todas las tareas <span aria-hidden="true">→</span></Link>
    </section>}
    {cursoId&&(!vista||vista==='materiales')&&<MaterialesCurso curso={cursoId} perfil={perfil}/>}
    {modulos?.clasesYGrabaciones!==false&&!historico&&(!vista||vista==='clases')&&<section className="section curso-resumen-seccion" id="curso-clases">
      <div className="curso-seccion-cabecera"><div className="curso-seccion-titulos"><span className="curso-seccion-numero">03 / SESIONES</span><h2>Clases virtuales</h2><p>Revisa las sesiones recientes y su estado.</p></div>{perfil.tipo==='DOCENTE'&&<Link className="button secondary" to={`/clases?asignatura=${encodeURIComponent(curso?.asignatura||'')}&grado=${encodeURIComponent(curso?.grado||'')}&seccion=${encodeURIComponent(curso?.seccion||'')}`}>Programar clase</Link>}</div>
      {!resumenClases.length?<div className="empty">No hay clases programadas.</div>:<div className="curso-resumen-lista">{resumenClases.map(c=><Link className="curso-resumen-fila" key={c.id} to={`/clases/${c.id}?return=${encodeURIComponent('/cursos/'+cursoId)}`}><div className="curso-fila-principal"><strong>{c.tema}</strong><span>{fecha(c.inicio)}</span></div><span className={'badge '+(c.estado_visible||c.estado).toLowerCase()}>{c.estado_visible||c.estado}</span><span className="curso-fila-accion">Ver clase <b aria-hidden="true">→</b></span></Link>)}</div>}
      <Link className="curso-seccion-pie" to="/clases">Ver todas las clases <span aria-hidden="true">→</span></Link>
    </section>}
    {perfil.tipo==='ESTUDIANTE'&&(!vista||vista==='notas')&&<section className="section curso-resumen-seccion" id="curso-notas"><div className="curso-seccion-cabecera"><div className="curso-seccion-titulos"><span className="curso-seccion-numero">{numeroNotas} / EVALUACIONES</span><h2>Mis notas recientes</h2><p>Consulta las evaluaciones publicadas de este curso.</p></div></div>{!notas.some(n=>n.entrega_version===n.ultima_entrega_version)?<div className="empty">Aún no tienes notas publicadas para los intentos actuales.</div>:<div className="curso-resumen-lista">{notas.filter(n=>n.entrega_version===n.ultima_entrega_version).slice(0,3).map(n=><Link className="curso-resumen-fila" key={n.id} to={`/notas/${n.id}`}><div className="curso-fila-principal"><strong>{n.titulo}</strong><span>{n.comentario||'Sin observaciones del docente'}</span></div><strong className="curso-nota-valor">{n.valor}/{n.escala_maxima}</strong><span className="curso-fila-accion">Ver nota <b aria-hidden="true">→</b></span></Link>)}</div>}<Link className="curso-seccion-pie" to={`/notas?curso=${cursoId}`}>Ver todas mis notas <span aria-hidden="true">→</span></Link></section>}
    {perfil.tipo==='DOCENTE'&&(!vista||vista==='notas')&&<section className="section curso-resumen-seccion" id="curso-notas"><div className="curso-seccion-cabecera"><div className="curso-seccion-titulos"><span className="curso-seccion-numero">{numeroNotas} / EVALUACIONES</span><h2>Calificaciones</h2><p>Estado de las evaluaciones publicadas en este curso.</p></div></div><div className="curso-notas-resumen"><strong>{notas.filter(n=>n.estado==='PUBLICADA').length}</strong><span>calificaciones publicadas en este curso</span></div><Link className="curso-seccion-pie" to={`/notas?curso=${cursoId}`}>Revisar actividades y calificar <span aria-hidden="true">→</span></Link></section>}
  </main>;
}

function EditarTarea({tarea,hayEntregas}:{tarea:Tarea;hayEntregas:boolean}){
  const qc=useQueryClient(),navegar=useNavigate(),[abierto,setAbierto]=useState(false),[archivos,setArchivos]=useState<File[]>([]),[progreso,setProgreso]=useState(0),[curso,setCurso]=useState(tarea.curso_id),[operacion,setOperacion]=useState(crypto.randomUUID()),[tipoCambio,setTipoCambio]=useState<'MENOR'|'SUSTANCIAL'>('MENOR');
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});
  const local=fechaLocalLima(tarea.fecha_limite);
  const {register,handleSubmit,formState:{errors},reset}=useForm<FormTarea>({resolver:zodResolver(esquema),defaultValues:{titulo:tarea.titulo,instrucciones:tarea.instrucciones,fechaLimite:local}});
  useEffect(()=>reset({titulo:tarea.titulo,instrucciones:tarea.instrucciones,fechaLimite:local}),[tarea.titulo,tarea.instrucciones,tarea.fecha_limite,reset]);
  const mut=useMutation({mutationFn:async(d:FormTarea)=>{
    if(hayEntregas&&tipoCambio==='MENOR'&&archivos.length)throw new Error('Añadir materiales con entregas requiere un cambio sustancial.');
    if(hayEntregas&&tipoCambio==='SUSTANCIAL'&&!window.confirm('Se creará una nueva versión. Las entregas y notas anteriores quedarán en el historial y los estudiantes deberán entregar de nuevo. ¿Continuar?'))return null;
    if(hayEntregas&&tipoCambio==='MENOR'&&(d.titulo!==tarea.titulo||d.instrucciones!==tarea.instrucciones)&&!window.confirm('Confirma que la corrección no cambia el trabajo solicitado. Las entregas recibidas se conservarán.'))return null;
    const resultado=await put<Tarea>(`/tareas/${tarea.id}`,{...d,fechaLimite:aIsoLima(d.fechaLimite),version:tarea.version,cursoId:curso,confirmarReinicio:hayEntregas&&tipoCambio==='SUSTANCIAL',operacionId:operacion,tipoCambio});
    const existentes=await api<Archivo[]>(`/tareas/${resultado.id}/archivos`);
    for(const archivo of archivos)if(!existentes.some(x=>x.nombre===archivo.name&&x.tamano===archivo.size))await subirArchivo('tareas',resultado.id,archivo,setProgreso);
    return resultado;
  },onSuccess:(resultado)=>{if(!resultado)return;qc.invalidateQueries({queryKey:['tarea']});qc.invalidateQueries({queryKey:['tareas']});qc.invalidateQueries({queryKey:['archivos']});qc.invalidateQueries({queryKey:['vista-global']});setArchivos([]);setProgreso(0);setOperacion(crypto.randomUUID());setAbierto(false);if(resultado.id!==tarea.id)navegar(`/tareas/${resultado.id}`)}});
  return <><button className="button secondary" onClick={()=>setAbierto(!abierto)}>Editar tarea</button>{abierto&&<form className="form compact" onSubmit={handleSubmit(d=>mut.mutate(d))}><label>Tipo de cambio<select value={tipoCambio} onChange={e=>setTipoCambio(e.target.value as 'MENOR'|'SUSTANCIAL')}><option value="MENOR">Ampliación o corrección menor</option><option value="SUSTANCIAL">Cambio del trabajo solicitado</option></select></label><label>Curso y sección<select value={curso} onChange={e=>{setCurso(e.target.value);if(e.target.value!==tarea.curso_id)setTipoCambio('SUSTANCIAL')}}>{cursos.map(c=><option value={c.id} key={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label><label>Título<input {...register('titulo')}/></label><label>Instrucciones<textarea rows={4} {...register('instrucciones')}/></label><label>Fecha y hora límite (Lima)<input type="datetime-local" {...register('fechaLimite')}/></label><label>Agregar archivos<input type="file" multiple accept={archivoPermitido} onChange={e=>setArchivos(Array.from(e.target.files||[]))}/></label>{hayEntregas&&<p className="muted">Una ampliación o corrección menor conserva las entregas. Cambiar el trabajo, curso o material esencial crea una nueva versión.</p>}{Object.keys(errors).length>0&&<p className="error">Revisa los datos de la tarea.</p>}{mut.error&&<p className="error">No se completaron todos los cambios. {String(mut.error)}</p>}{progreso>0&&<progress value={progreso} max="100"/>}<button className="button primary" disabled={mut.isPending}>Guardar cambios</button></form>}</>;
}
function TareasDelLote({lote,actual}:{lote:string;actual:string}){
  const qc=useQueryClient();
  const {data:tareas=[]}=useQuery<(Tarea&{asignatura:string;grado:string;seccion:string})[]>({queryKey:['lote',lote],queryFn:()=>api(`/tareas/lotes/${lote}`)});
  const publicar=useMutation({mutationFn:()=>post(`/tareas/lotes/${lote}/publicar`,{}),onSuccess:()=>{qc.invalidateQueries({queryKey:['lote',lote]});qc.invalidateQueries({queryKey:['tarea']});qc.invalidateQueries({queryKey:['tareas']});qc.invalidateQueries({queryKey:['vista-global']})}});
  if(!tareas.length)return null;
  return <div className="panel"><strong>Tareas independientes de esta actividad</strong><div className="row-actions">{tareas.map(t=><Link key={t.id} className="text-link" to={`/tareas/${t.id}`}>{t.asignatura} {t.grado} {t.seccion}{t.id===actual?' (actual)':''}</Link>)}</div>{tareas.every(t=>t.estado==='FALTA_COMPLETAR')&&<button className="button secondary" disabled={publicar.isPending} onClick={()=>publicar.mutate()}>Publicar todas las tareas del lote</button>}{publicar.error&&<p className="error">{String(publicar.error)}</p>}</div>;
}

export function TareaPage({perfil}:{perfil:Perfil}){
  const {tareaId}=useParams(),[params]=useSearchParams(),qc=useQueryClient();
  const {data:tarea,isLoading,error}=useQuery<Tarea>({queryKey:['tarea',tareaId],queryFn:()=>api(`/tareas/${tareaId}`),enabled:!!tareaId});
  const {data:cursos=[],isLoading:cargandoCursos,error:errorCursos}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos'),enabled:perfil.tipo==='ESTUDIANTE'});
  const historico=perfil.tipo==='ESTUDIANTE'&&cursos.some(c=>c.id===tarea?.curso_id&&c.estado_matricula==='FINALIZADA');
  const {data:entregas=[],isLoading:cargandoEntregas,error:errorEntregas}=useQuery<Entrega[]>({queryKey:['entregas',tareaId],queryFn:()=>api(`/tareas/${tareaId}/entregas`),enabled:!!tareaId&&perfil.tipo!=='COORDINACION'});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',tarea?.curso_id],queryFn:()=>api(`/cursos/${tarea?.curso_id}/notas`),enabled:perfil.tipo==='ESTUDIANTE'&&!!tarea?.curso_id});
  const [confirmacion,setConfirmacion]=useState<string|null>(null);
  const estadoMut=useMutation({mutationFn:(accion:'publicar'|'cerrar'|'cancelar')=>post(`/tareas/${tareaId}/${accion}`,{}),onSuccess:()=>{qc.invalidateQueries({queryKey:['tarea',tareaId]});qc.invalidateQueries({queryKey:['tareas',tarea?.curso_id]});qc.invalidateQueries({queryKey:['vista-global']})}});
  const cambiarEstado=(accion:'publicar'|'cerrar'|'cancelar')=>{
    const mensaje=accion==='cerrar'?'Cerrar esta tarea impedirá nuevas entregas. Las recibidas y sus notas se conservarán. ¿Continuar?':accion==='cancelar'?'Cancelar esta tarea impedirá nuevas entregas y no se podrá reabrir. Las entregas y notas anteriores se conservarán. ¿Continuar?':tarea?.estado==='CERRADA'?'Reabrir esta tarea permitirá nuevas entregas; si está vencida serán tardías. ¿Continuar?':null;
    if(!mensaje||window.confirm(mensaje))estadoMut.mutate(accion);
  };
  if(isLoading)return <p className="muted">Cargando tarea…</p>;
  if(error||!tarea)return <div className="alert">No fue posible abrir la tarea. {String(error)}</div>;
  const presentada=entregas.length>0,plazoVencido=new Date(tarea.fecha_limite).getTime()<Date.now();
  const recibo=entregas.find(e=>e.id===confirmacion);
  const pedido=params.get('return');
  const regreso=pedido&&!pedido.startsWith('//')&&(/^\/(tareas|notas)\?/.test(pedido)||pedido==='/notificaciones?tab=notificaciones')?pedido:`/tareas?curso=${tarea.curso_id}&q=${encodeURIComponent(tarea.titulo)}&resaltar=${tarea.id}${tarea.tarea_sucesora_id?'&historial=1':''}`;
  return <main className="tarea-detalle-editorial"><PageHeading title={tarea.titulo} metricLabel="FECHA LÍMITE · LIMA" metricValue={new Date(tarea.fecha_limite).toLocaleDateString('es-PE',{day:'2-digit',month:'short',timeZone:'America/Lima'})} metricCaption={`${new Date(tarea.fecha_limite).toLocaleTimeString('es-PE',{timeStyle:'short',timeZone:'America/Lima'})} · ${tarea.estado}`}/><div className="page-head"><div><Link className="back" to={regreso}>← {regreso.startsWith('/notificaciones')?'Notificaciones':regreso.startsWith('/notas')?'Mis notas':perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'}</Link><span className="eyebrow">ACTIVIDAD DEL AULA</span><h1>{tarea.titulo}</h1><p>Fecha y hora límite: <strong>{fecha(tarea.fecha_limite)}</strong> (Lima)</p><p>{tarea.estado==='PUBLICADA'?(plazoVencido?'Vencida, pero abierta para entregas tardías.':'Abierta para entregas.'):tarea.estado==='CERRADA'?'Cerrada manualmente: no recibe entregas.':tarea.estado==='CANCELADA'?'Cancelada: no recibe entregas.':'Borrador: solo visible para el docente.'}</p></div>{perfil.tipo==='DOCENTE'&&<div className="row-actions">{tarea.estado==='FALTA_COMPLETAR'&&<button className="button primary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('publicar')}>Publicar tarea</button>}{tarea.estado==='PUBLICADA'&&<><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cerrar')}>Cerrar tarea</button><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cancelar')}>Cancelar tarea</button></>}{tarea.estado==='CERRADA'&&!tarea.tarea_sucesora_id&&<><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('publicar')}>Reabrir tarea</button><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cancelar')}>Cancelar tarea</button></>}</div>}</div>
    {estadoMut.error&&<div className="alert">{String(estadoMut.error)}</div>}
    <div className="panel"><h2>Instrucciones</h2><p className="prewrap">{tarea.instrucciones}</p><ArchivosDe padre="tareas" id={tarea.id} editable={perfil.tipo==='DOCENTE'&&!entregas.length&&['FALTA_COMPLETAR','PUBLICADA'].includes(tarea.estado)}/>{perfil.tipo==='DOCENTE'&&<div className="task-actions"><EtiquetaTarea t={tarea} perfil={perfil}/>{!tarea.tarea_sucesora_id&&tarea.estado!=='CANCELADA'&&<EditarTarea tarea={tarea} hayEntregas={entregas.length>0}/>}</div>}</div>
    {perfil.tipo==='DOCENTE'&&tarea.tarea_anterior_id&&<div className="panel"><strong>Versión actual de la actividad.</strong> Las entregas y notas anteriores permanecen en el historial. <Link className="text-link" to={`/tareas/${tarea.tarea_anterior_id}`}>Ver versión anterior →</Link></div>}
    {perfil.tipo==='DOCENTE'&&tarea.tarea_sucesora_id&&<div className="panel"><strong>Versión anterior.</strong> Estas entregas y notas se conservan como historial. <Link className="text-link" to={`/tareas/${tarea.tarea_sucesora_id}`}>Abrir versión vigente →</Link></div>}
    {perfil.tipo==='DOCENTE'&&tarea.lote_id&&<TareasDelLote lote={tarea.lote_id} actual={tarea.id}/>}
    {perfil.tipo==='DOCENTE'&&<SeguimientoTarea tarea={tarea} entregas={entregas} cargandoEntregas={cargandoEntregas} errorEntregas={errorEntregas}/>}
    {perfil.tipo==='ESTUDIANTE'&&<section className="section tarea-entrega-seccion"><h2>Mi entrega</h2>{cargandoCursos?<p className="muted">Consultando matrícula…</p>:errorCursos?<p className="alert">No se pudo comprobar el estado de la matrícula. Vuelve a cargar la página.</p>:historico?<p className="muted">Año escolar finalizado · Solo lectura. Puedes consultar tus entregas anteriores.</p>:<>{recibo&&<div className="alert">Entrega recibida el {fecha(recibo.entregado_en)} (Lima). Intento {recibo.version}. {recibo.estado==='TARDIA'?'Registrada como tardía.':'Registrada dentro del plazo.'}</div>}{plazoVencido&&tarea.estado==='PUBLICADA'&&!presentada&&<p className="alert">El plazo venció, pero la tarea sigue abierta. Tu entrega se registrará como tardía.</p>}<PrepararEntrega tarea={tarea} presentada={presentada} confirmada={setConfirmacion}/></>}</section>}
    {perfil.tipo==='ESTUDIANTE'&&<section className="section tarea-historial-seccion"><h2>Historial de entregas</h2>{!entregas.length?<div className="empty">Aún no hay entregas.</div>:<div className="list">{entregas.map(e=>{const nota=notas.find(n=>n.tarea_id===tarea.id&&n.entrega_version===e.version);return <div className="list-row stack-mobile" key={e.id}><div><strong>Entrega {e.version} · {nota?'REVISADA':e.estado==='TARDIA'?'ENTREGADA TARDÍA':'ENTREGADA'}</strong><span>{e.estado==='TARDIA'?'Entregada tardía':'Entregada'} el {fecha(e.entregado_en)} (Lima) · Plazo al recibir: {fecha(e.fecha_limite_al_entregar)}</span><p>{e.respuesta}</p><ArchivosDe padre="entregas" id={e.id}/><p>{nota?<Link className="text-link" to={`/notas/${nota.id}`}>Nota {nota.valor}/{nota.escala_maxima} · Ver retroalimentación →</Link>:'Pendiente de evaluación'}</p></div></div>})}</div>}</section>}
  </main>;
}

export function NotaPage(){
  const {notaId}=useParams(),[params]=useSearchParams();const {data:nota,isLoading,error}=useQuery<NotaDetalle>({queryKey:['nota',notaId],queryFn:()=>api(`/notas/${notaId}`),enabled:!!notaId});
  const {data:historial=[]}=useQuery<{id:string;valor_anterior:number|null;valor_nuevo:number;comentario:string|null;instante:string}[]>({queryKey:['historial-nota',notaId],queryFn:()=>api(`/notas/${notaId}/historial`),enabled:!!notaId});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===nota?.curso_id);
  if(isLoading)return <p className="muted">Cargando nota…</p>;
  if(error||!nota)return <div className="alert">No fue posible consultar la nota. {String(error)}</div>;
  const pedido=params.get('return');
  const regreso=pedido&&!pedido.startsWith('//')&&/^\/notas\?/.test(pedido)?pedido:'/notas';
  return <main className="nota-detalle-editorial"><PageHeading title={nota.titulo} metricLabel="RESULTADO PUBLICADO" metricValue={`${nota.valor} / ${nota.escala_maxima}`} metricCaption={curso?.asignatura||'Curso'}/>
    <Link className="back" to={regreso}>← MIS NOTAS</Link>
    <header className="nota-detalle-portada"><div className="nota-detalle-principal"><span className="cursos-sobretitulo">CALIFICACIÓN DE ACTIVIDAD</span><h1>{nota.titulo}<i>.</i></h1><p>{curso?.asignatura||'Curso'} · {curso?.nivel} {curso?.grado} {curso?.seccion}</p></div><div className="nota-detalle-resultado"><span>RESULTADO PUBLICADO</span><strong>{nota.valor}<small> / {nota.escala_maxima}</small></strong><time>Publicado el {fecha(nota.publicada_en||nota.actualizado_en)}</time></div></header>
    <div className="nota-detalle-grid"><section className="nota-detalle-panel nota-detalle-observaciones"><span>01 / RETROALIMENTACIÓN</span><h2>Observaciones del docente</h2><p className="prewrap">{nota.comentario||'No se registraron observaciones.'}</p></section><section className="nota-detalle-panel nota-detalle-datos"><span>02 / ENTREGA</span><h2>Sobre esta calificación</h2><dl><div><dt>Entrega evaluada</dt><dd>Intento {nota.entrega_version}</dd></div><div><dt>Fecha límite</dt><dd>{fecha(nota.fecha_limite)} · Lima</dd></div><div><dt>Curso</dt><dd>{curso?.asignatura||'Curso'} · {curso?.grado} {curso?.seccion}</dd></div></dl></section></div>
    {historial.length>1&&<section className="nota-detalle-historial" aria-label="Historial de esta nota"><div className="tareas-seccion-cabecera"><div><span>03 / REGISTRO</span><h2>Historial de esta nota</h2><p>Cambios publicados en esta calificación.</p></div></div><ol>{historial.map(h=><li key={h.id}><time>{fecha(h.instante)}</time><strong>{h.valor_anterior==null?'Nota publicada':'Nota corregida'}</strong><p>{h.valor_anterior!=null&&<del>{h.valor_anterior}/{nota.escala_maxima}</del>} <b>{h.valor_nuevo}/{nota.escala_maxima}</b>{h.comentario&&<span> · {h.comentario}</span>}</p></li>)}</ol></section>}
  </main>;
}
