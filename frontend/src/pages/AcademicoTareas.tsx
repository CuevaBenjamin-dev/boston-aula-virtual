import {useEffect,useState} from 'react';
import {Link,useParams,useSearchParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useForm} from 'react-hook-form';
import {z} from 'zod';
import {zodResolver} from '@hookform/resolvers/zod';
import {api,post,put,subirArchivo,type Archivo,type Clase,type Curso,type Entrega,type Nota,type Perfil,type Tarea} from '../api';

const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});
const esquema=z.object({titulo:z.string().trim().min(3),instrucciones:z.string().trim().min(3),fechaLimite:z.string().min(1)});
type FormTarea=z.infer<typeof esquema>;
type NotaDetalle=Nota&{curso_id:string;instrucciones:string};
const archivoPermitido='.pdf,.png,.jpg,.jpeg,.docx';

function estadoTarea(t:Tarea,perfil:Perfil){
  if(perfil.tipo!=='ESTUDIANTE')return t.estado==='FALTA_COMPLETAR'?'FALTA COMPLETAR':t.estado==='PUBLICADA'?'PUBLICADO':t.estado;
  if(t.presentada)return 'PRESENTADO';
  const restante=new Date(t.fecha_limite).getTime()-Date.now();
  if(restante<=0||t.estado==='CERRADA')return 'VENCIDA';
  return restante>0&&restante<10*60*60*1000?'URGENTE':'PENDIENTE';
}
function semanaActual(s:string){
  const ahora=new Date(),inicio=new Date(ahora.getFullYear(),ahora.getMonth(),ahora.getDate());
  inicio.setDate(inicio.getDate()-((inicio.getDay()+6)%7));
  const fin=new Date(inicio);fin.setDate(fin.getDate()+7);
  const fecha=new Date(s);return fecha>=inicio&&fecha<fin;
}
function EtiquetaTarea({t,perfil}:{t:Tarea;perfil:Perfil}){
  const estado=estadoTarea(t,perfil);return <span className={'badge '+estado.toLowerCase().replaceAll(' ','_')}>{estado}</span>;
}

export function Cursos({perfil,pagina}:{perfil:Perfil;pagina:string}){
  const {data:cursos,isLoading,error}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});
  const {data:sincronizacion}=useQuery<{sincronizadoEn:string|null;pendiente:boolean}>({queryKey:['sincronizacion'],queryFn:()=>api('/sincronizacion/estado'),enabled:pagina==='cursos',refetchInterval:30000});
  if(isLoading)return <p className="muted">Cargando cursos…</p>;
  if(error)return <div className="alert">No fue posible consultar los cursos. {String(error)}</div>;
  if(cursos?.length&&['tareas','notas'].includes(pagina))return <VistaGlobal cursos={cursos} pagina={pagina} perfil={perfil}/>;
  const titulo=pagina==='inicio'?'Bienvenido al Aula Virtual':pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):pagina==='notas'?(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones'):'Mis cursos';
  return <><div className="page-head"><div><span className="eyebrow">CURSOS INSTITUCIONALES</span><h1>{titulo}</h1><p>Selecciona un curso para consultar su resumen.</p></div></div>{sincronizacion?.pendiente&&<div className="alert">{`El ERP está temporalmente inaccesible. Se muestran los cursos de la última sincronización${sincronizacion.sincronizadoEn?` (${fecha(sincronizacion.sincronizadoEn)})`:''}; los cambios pendientes se reintentarán automáticamente.`}</div>}{!cursos?.length?<div className="empty">Aún no hay cursos asignados. La sincronización con el ERP se ejecuta automáticamente.</div>:<div className="cards">{cursos.map(c=><Link className="course-card" key={c.id} to={'/cursos/'+c.id}><span className="course-icon">{c.asignatura[0]}</span><span className="eyebrow">{c.grado} · {c.seccion}</span><h2>{c.asignatura}</h2><p>Periodo {c.periodo}</p><span className="text-link">Abrir curso →</span></Link>)}</div>}</>;
}

type Fila={id:string;curso:Curso;tarea?:Tarea;nota?:Nota};
function VistaGlobal({cursos,pagina,perfil}:{cursos:Curso[];pagina:string;perfil:Perfil}){
  const [params,setParams]=useSearchParams();
  const cambiar=(clave:string,valor:string)=>{const siguiente=new URLSearchParams(params);if(valor)siguiente.set(clave,valor);else siguiente.delete(clave);siguiente.delete('resaltar');setParams(siguiente)};
  const ids=cursos.map(c=>c.id).join(',');
  const {data:filas=[],isLoading,error}=useQuery<Fila[]>({queryKey:['vista-global',pagina,ids,perfil.tipo],queryFn:async()=>{
    if(pagina==='notas'&&perfil.tipo==='ESTUDIANTE')return (await Promise.all(cursos.map(async curso=>(await api<Nota[]>(`/cursos/${curso.id}/notas`)).map(n=>({id:n.id,curso,nota:n}))))).flat().sort((a,b)=>new Date(b.nota!.actualizado_en).getTime()-new Date(a.nota!.actualizado_en).getTime());
    return (await Promise.all(cursos.map(async curso=>(await api<Tarea[]>(`/cursos/${curso.id}/tareas`)).map(t=>({id:t.id,curso,tarea:t}))))).flat().sort((a,b)=>new Date(b.tarea!.creado_en).getTime()-new Date(a.tarea!.creado_en).getTime());
  }});
  const visibles=filas.filter(f=>{
    const texto=(params.get('q')||'').toLocaleLowerCase('es');
    const valorFecha=f.nota?.actualizado_en||f.tarea?.fecha_limite||'';
    const dia=new Date(valorFecha).toLocaleDateString('sv-SE');
    return (!params.get('curso')||f.curso.id===params.get('curso'))
      &&(!texto||`${f.nota?.titulo||f.tarea?.titulo} ${f.curso.asignatura} ${f.nota?.comentario||''}`.toLocaleLowerCase('es').includes(texto))
      &&(pagina!=='tareas'||!params.get('estado')||estadoTarea(f.tarea!,perfil)===params.get('estado'))
      &&(!params.get('desde')||dia>=params.get('desde')!)
      &&(!params.get('hasta')||dia<=params.get('hasta')!);
  });
  useEffect(()=>{const id=params.get('resaltar');if(id&&visibles.some(f=>f.id===id))requestAnimationFrame(()=>document.getElementById(`tarea-${id}`)?.scrollIntoView({behavior:'smooth',block:'center'}))},[params,visibles.length]);
  const titulo=pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones');
  const descripcion=pagina==='notas'&&perfil.tipo==='DOCENTE'?'Selecciona una tarea para revisar entregas y calificar.':'Consulta la información de tus cursos.';
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>{titulo}</h1><p>{descripcion}</p></div></div>
    <div className="panel form filters"><div className="filter-grid">
      <label>Buscar<input value={params.get('q')||''} onChange={e=>cambiar('q',e.target.value)} placeholder={pagina==='notas'?'Actividad o comentario':'Tarea o curso'}/></label>
      <label>Curso<select value={params.get('curso')||''} onChange={e=>cambiar('curso',e.target.value)}><option value="">Todos los cursos</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.grado} {c.seccion}</option>)}</select></label>
      {pagina==='tareas'&&<label>Estado<select value={params.get('estado')||''} onChange={e=>cambiar('estado',e.target.value)}><option value="">Todos</option>{(perfil.tipo==='ESTUDIANTE'?['PENDIENTE','URGENTE','PRESENTADO','VENCIDA']:['PUBLICADO','FALTA COMPLETAR','CERRADA']).map(e=><option key={e}>{e}</option>)}</select></label>}
      <label>Desde<input type="date" value={params.get('desde')||''} onChange={e=>cambiar('desde',e.target.value)}/></label>
      <label>Hasta<input type="date" value={params.get('hasta')||''} onChange={e=>cambiar('hasta',e.target.value)}/></label>
    </div></div>
    {isLoading?<p className="muted">Cargando…</p>:error?<div className="alert">No fue posible consultar {titulo.toLowerCase()}. {String(error)}</div>:!visibles.length?<div className="empty">No hay resultados para estos filtros.</div>:<div className="list">{visibles.map(f=>f.nota?<Link className="list-row" key={f.id} to={`/notas/${f.id}`}><div><strong>{f.nota.titulo}</strong><span>{f.curso.asignatura} · {f.curso.grado} {f.curso.seccion} · Calificada {fecha(f.nota.actualizado_en)}</span><p>{f.nota.comentario||'Sin comentario del docente'}</p></div><strong>{f.nota.valor}/{f.nota.escala_maxima}</strong><span>Ver nota →</span></Link>:<Link id={`tarea-${f.id}`} className={'list-row'+(params.get('resaltar')===f.id?' task-return':'')} key={f.id} to={`/tareas/${f.id}?return=${encodeURIComponent('/tareas?'+new URLSearchParams({...Object.fromEntries(params),resaltar:f.id}).toString())}`}><div><strong>{f.tarea!.titulo}</strong><span>{f.curso.asignatura} · {f.curso.grado} {f.curso.seccion} · Entrega hasta {fecha(f.tarea!.fecha_limite)}</span></div>{perfil.tipo==='DOCENTE'&&<strong>{f.tarea!.entregados}/{f.tarea!.total_estudiantes}</strong>}{pagina==='tareas'&&<EtiquetaTarea t={f.tarea!} perfil={perfil}/>}<span>{pagina==='notas'?'Revisar →':'Ver tarea →'}</span></Link>)}</div>}
  </>;
}

function CrearTarea({curso}:{curso:string}){
  const qc=useQueryClient(),[abierto,setAbierto]=useState(false),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0),[incompleta,setIncompleta]=useState<string|null>(null);
  const {register,handleSubmit,formState:{errors},reset}=useForm<FormTarea>({resolver:zodResolver(esquema),defaultValues:{titulo:'',instrucciones:'',fechaLimite:''}});
  const crear=useMutation({mutationFn:async(d:FormTarea)=>{
    const {id}=await post<{id:string}>(`/cursos/${curso}/tareas`,{...d,fechaLimite:new Date(d.fechaLimite).toISOString()});
    setIncompleta(id);
    if(archivo)await subirArchivo('tareas',id,archivo,setProgreso);
    await post(`/tareas/${id}/publicar`,{});return id;
  },onSuccess:()=>{qc.invalidateQueries({queryKey:['tareas',curso]});qc.invalidateQueries({queryKey:['vista-global']});reset();setArchivo(null);setIncompleta(null);setProgreso(0);setAbierto(false)},onError:()=>qc.invalidateQueries({queryKey:['tareas',curso]})});
  return <><button className="button primary" onClick={()=>setAbierto(!abierto)}>+ Nueva tarea</button>{abierto&&<form className="panel form" onSubmit={handleSubmit(d=>crear.mutate(d))}><h3>Nueva tarea</h3><label>Título<input {...register('titulo')}/>{errors.titulo&&<small className="error">Escribe al menos 3 caracteres.</small>}</label><label>Instrucciones<textarea rows={4} {...register('instrucciones')}/>{errors.instrucciones&&<small className="error">Las instrucciones son obligatorias.</small>}</label><label>Fecha y hora límite<input type="datetime-local" {...register('fechaLimite')}/></label><label>Material de referencia opcional<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label>{progreso>0&&<progress value={progreso} max="100"/>}{crear.error&&<p className="error">No se completó la publicación. {incompleta?<Link to={`/tareas/${incompleta}`}>Abrir tarea para completarla →</Link>:'Revisa los datos e inténtalo otra vez.'}</p>}<button className="button primary" disabled={crear.isPending}>Publicar tarea</button></form>}</>;
}

export function CursoPage({perfil,modulos}:{perfil:Perfil;modulos?:{clasesYGrabaciones:boolean}}){
  const {cursoId}=useParams();
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===cursoId);
  const {data:tareas=[]}=useQuery<Tarea[]>({queryKey:['tareas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/tareas`),enabled:!!cursoId,refetchInterval:60000});
  const {data:clases=[]}=useQuery<Clase[]>({queryKey:['clases',cursoId],queryFn:()=>api(`/cursos/${cursoId}/clases`),enabled:!!cursoId&&modulos?.clasesYGrabaciones!==false,refetchInterval:30000});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/notas`),enabled:!!cursoId&&['ESTUDIANTE','DOCENTE'].includes(perfil.tipo)});
  const resumenTareas=perfil.tipo==='ESTUDIANTE'?tareas.filter(t=>semanaActual(t.publicado_en||t.creado_en)&&!t.presentada&&t.estado==='PUBLICADA'&&new Date(t.fecha_limite).getTime()>Date.now()):tareas.slice(0,5);
  const resumenClases=[...clases].sort((a,b)=>Math.abs(new Date(a.inicio).getTime()-Date.now())-Math.abs(new Date(b.inicio).getTime()-Date.now())).slice(0,3);
  return <><div className="page-head"><div><Link className="back" to="/cursos">← Mis cursos</Link><span className="eyebrow">{curso?.grado} · Sección {curso?.seccion}</span><h1>{curso?.asignatura||'Curso'}</h1><p><strong>Docente:</strong> {curso?.docentes||'Sin docente asignado'}</p><p>Resumen de tareas, clases y {perfil.tipo==='ESTUDIANTE'?'notas':'entregas'} del curso.</p></div></div>
    <section className="section"><div className="section-head"><h2>{perfil.tipo==='ESTUDIANTE'?'Tareas pendientes de esta semana':'Tareas'}</h2>{perfil.tipo==='DOCENTE'&&cursoId&&<CrearTarea curso={cursoId}/>}</div>{!resumenTareas.length?<div className="empty">{perfil.tipo==='ESTUDIANTE'?'No hay tareas pendientes esta semana.':'Todavía no hay tareas.'}</div>:<div className="list">{resumenTareas.map(t=>{const regreso=`/tareas?curso=${cursoId}&q=${encodeURIComponent(t.titulo)}&resaltar=${t.id}`;return <Link className="list-row" to={`/tareas/${t.id}?return=${encodeURIComponent(regreso)}`} key={t.id}><div><strong>{t.titulo}</strong><span>Entrega hasta {fecha(t.fecha_limite)}</span></div>{perfil.tipo==='DOCENTE'&&<strong>{t.entregados}/{t.total_estudiantes}</strong>}<EtiquetaTarea t={t} perfil={perfil}/><span>Ver tarea →</span></Link>})}</div>}<Link className="text-link section-more" to={`/tareas?curso=${cursoId}`}>Ver todas las tareas →</Link></section>
    {modulos?.clasesYGrabaciones!==false&&<section className="section"><div className="section-head"><h2>Clases virtuales</h2>{perfil.tipo==='DOCENTE'&&<Link className="button secondary" to={`/clases?asignatura=${encodeURIComponent(curso?.asignatura||'')}&grado=${encodeURIComponent(curso?.grado||'')}&seccion=${encodeURIComponent(curso?.seccion||'')}`}>Programar clase</Link>}</div>{!resumenClases.length?<div className="empty">No hay clases programadas.</div>:<div className="list">{resumenClases.map(c=><Link className="list-row" key={c.id} to={`/clases/${c.id}?return=${encodeURIComponent('/cursos/'+cursoId)}`}><div><strong>{c.tema}</strong><span>{fecha(c.inicio)}</span></div><span className={'badge '+c.estado.toLowerCase()}>{c.estado==='CANCELADA'?'CANCELADA':'PROGRAMADA'}</span><span>Ver clase →</span></Link>)}</div>}<Link className="text-link section-more" to="/clases">Ver todas las clases →</Link></section>}
    {perfil.tipo==='ESTUDIANTE'&&<section className="section"><h2>Mis notas recientes</h2>{!notas.length?<div className="empty">Aún no tienes notas registradas.</div>:<div className="list">{notas.slice(0,3).map(n=><Link className="list-row" key={n.id} to={`/notas/${n.id}`}><div><strong>{n.titulo}</strong><span>{n.comentario||'Sin comentario del docente'}</span></div><strong>{n.valor}/{n.escala_maxima}</strong><span>Ver nota →</span></Link>)}</div>}<Link className="text-link section-more" to={`/notas?curso=${cursoId}`}>Ver todas mis notas →</Link></section>}
    {perfil.tipo==='DOCENTE'&&<section className="section"><h2>Calificaciones</h2><div className="panel">{notas.length} calificaciones registradas en este curso.</div><Link className="text-link section-more" to={`/notas?curso=${cursoId}`}>Revisar actividades y calificar →</Link></section>}
  </>;
}

function ArchivosDe({padre,id}:{padre:'tareas'|'entregas';id:string}){
  const {data=[]}=useQuery<Archivo[]>({queryKey:['archivos',padre,id],queryFn:()=>api(`/${padre}/${id}/archivos`),enabled:!!id});const [error,setError]=useState('');
  async function abrir(archivo:Archivo){try{const {url}=await api<{url:string}>(`/archivos/${archivo.id}/url`);window.open(url,'_blank','noopener,noreferrer')}catch(e){setError(String(e))}}
  return <>{data.map(a=><button className="text-link" key={a.id} onClick={()=>abrir(a)}>↓ {a.nombre}</button>)}{error&&<small className="error">{error}</small>}</>;
}
function CargarEntrega({id}:{id:string}){
  const qc=useQueryClient(),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0);
  const {data:archivos=[]}=useQuery<Archivo[]>({queryKey:['archivos','entregas',id],queryFn:()=>api(`/entregas/${id}/archivos`)});
  const mut=useMutation({mutationFn:()=>subirArchivo('entregas',id,archivo!,setProgreso),onSuccess:()=>{qc.invalidateQueries({queryKey:['archivos','entregas',id]});setArchivo(null);setProgreso(0)}});
  if(archivos.length)return null;
  return <div className="form compact"><label>Adjuntar archivo a esta entrega<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label><button className="button secondary" disabled={!archivo||mut.isPending} onClick={()=>mut.mutate()}>Subir archivo</button>{progreso>0&&<progress value={progreso} max="100"/>}{mut.error&&<small className="error">{String(mut.error)}</small>}</div>;
}
function EditarTarea({tarea}:{tarea:Tarea}){
  const qc=useQueryClient(),[abierto,setAbierto]=useState(false),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0);
  const local=new Date(new Date(tarea.fecha_limite).getTime()-new Date(tarea.fecha_limite).getTimezoneOffset()*60000).toISOString().slice(0,16);
  const {register,handleSubmit,formState:{errors},reset}=useForm<FormTarea>({resolver:zodResolver(esquema),defaultValues:{titulo:tarea.titulo,instrucciones:tarea.instrucciones,fechaLimite:local}});
  useEffect(()=>reset({titulo:tarea.titulo,instrucciones:tarea.instrucciones,fechaLimite:local}),[tarea.titulo,tarea.instrucciones,tarea.fecha_limite,reset]);
  const mut=useMutation({mutationFn:async(d:FormTarea)=>{await put(`/tareas/${tarea.id}`,{...d,fechaLimite:new Date(d.fechaLimite).toISOString(),version:tarea.version});if(archivo)await subirArchivo('tareas',tarea.id,archivo,setProgreso)},onSuccess:()=>{qc.invalidateQueries({queryKey:['tarea',tarea.id]});qc.invalidateQueries({queryKey:['tareas',tarea.curso_id]});qc.invalidateQueries({queryKey:['archivos','tareas',tarea.id]});qc.invalidateQueries({queryKey:['vista-global']});setArchivo(null);setProgreso(0);setAbierto(false)}});
  return <><button className="button secondary" onClick={()=>setAbierto(!abierto)}>Editar tarea</button>{abierto&&<form className="form compact" onSubmit={handleSubmit(d=>mut.mutate(d))}><label>Título<input {...register('titulo')}/></label><label>Instrucciones<textarea rows={4} {...register('instrucciones')}/></label><label>Fecha y hora límite<input type="datetime-local" {...register('fechaLimite')}/></label><label>Reemplazar material de referencia<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label>{Object.keys(errors).length>0&&<p className="error">Revisa los datos de la tarea.</p>}{mut.error&&<p className="error">No se completaron todos los cambios. Si falló la carga, el material anterior sigue disponible. {String(mut.error)}</p>}{progreso>0&&<progress value={progreso} max="100"/>}<button className="button primary" disabled={mut.isPending}>Guardar cambios</button></form>}</>;
}
function Calificar({tarea,estudiante}:{tarea:string;estudiante:string}){
  const [abierto,setAbierto]=useState(false),[valor,setValor]=useState(''),[comentario,setComentario]=useState(''),qc=useQueryClient();
  const {data:t}=useQuery<Tarea>({queryKey:['tarea',tarea],queryFn:()=>api(`/tareas/${tarea}`)});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',t?.curso_id],queryFn:()=>api(`/cursos/${t?.curso_id}/notas`),enabled:!!t?.curso_id});
  const actual=notas.find(n=>n.tarea_id===tarea&&n.estudiante_erp_id===estudiante);
  const mut=useMutation({mutationFn:()=>put(`/tareas/${tarea}/calificacion`,{estudianteId:estudiante,valor:Number(valor),comentario,version:actual?.version||0}),onSuccess:()=>{qc.invalidateQueries({queryKey:['notas',t?.curso_id]});setValor('');setAbierto(false)}});
  return <div><button className="button secondary" onClick={()=>setAbierto(!abierto)}>{actual?`Nota ${actual.valor}/${t?.escala_maxima} · Editar`:'Calificar'}</button>{abierto&&<form className="form compact" onSubmit={e=>{e.preventDefault();mut.mutate()}}><label>Nota sobre {t?.escala_maxima}<input type="number" min="0" max={t?.escala_maxima} step="0.01" value={valor} onChange={e=>setValor(e.target.value)} required/></label><label>Comentario<input value={comentario} onChange={e=>setComentario(e.target.value)}/></label>{mut.error&&<small className="error">{String(mut.error)}</small>}<button className="button primary" disabled={mut.isPending}>Guardar nota</button></form>}</div>;
}

export function TareaPage({perfil}:{perfil:Perfil}){
  const {tareaId}=useParams(),[params]=useSearchParams(),qc=useQueryClient();
  const {data:tarea,isLoading,error}=useQuery<Tarea>({queryKey:['tarea',tareaId],queryFn:()=>api(`/tareas/${tareaId}`),enabled:!!tareaId});
  const {data:entregas=[]}=useQuery<Entrega[]>({queryKey:['entregas',tareaId],queryFn:()=>api(`/tareas/${tareaId}/entregas`),enabled:!!tareaId&&perfil.tipo!=='COORDINACION'});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',tarea?.curso_id],queryFn:()=>api(`/cursos/${tarea?.curso_id}/notas`),enabled:perfil.tipo==='ESTUDIANTE'&&!!tarea?.curso_id});
  const [respuesta,setRespuesta]=useState(''),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0),[pendiente,setPendiente]=useState<string|null>(null);
  const publicar=useMutation({mutationFn:()=>post(`/tareas/${tareaId}/publicar`,{}),onSuccess:()=>{qc.invalidateQueries({queryKey:['tarea',tareaId]});qc.invalidateQueries({queryKey:['tareas',tarea?.curso_id]})}});
  const entregar=useMutation({mutationFn:async()=>{const id=pendiente||(await post<{id:string}>(`/tareas/${tareaId}/entregas`,{respuesta})).id;setPendiente(id);if(archivo)await subirArchivo('entregas',id,archivo,setProgreso);return id},onSuccess:()=>{setPendiente(null);setRespuesta('');setArchivo(null);setProgreso(0);qc.invalidateQueries({queryKey:['entregas',tareaId]});qc.invalidateQueries({queryKey:['tareas',tarea?.curso_id]});qc.invalidateQueries({queryKey:['vista-global']})}});
  if(isLoading)return <p className="muted">Cargando tarea…</p>;
  if(error||!tarea)return <div className="alert">No fue posible abrir la tarea. {String(error)}</div>;
  const presentada=entregas.length>0,plazoVencido=new Date(tarea.fecha_limite).getTime()<Date.now(),calificada=notas.some(n=>n.tarea_id===tareaId);
  const pedido=params.get('return');
  const regreso=pedido?.startsWith('/tareas?')&&!pedido.startsWith('//')?pedido:`/tareas?curso=${tarea.curso_id}&q=${encodeURIComponent(tarea.titulo)}&resaltar=${tarea.id}`;
  return <><div className="page-head"><div><Link className="back" to={regreso}>← {perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'}</Link><span className="eyebrow">ACTIVIDAD DEL AULA</span><h1>{tarea.titulo}</h1><p>Fecha y hora límite: {fecha(tarea.fecha_limite)}</p></div>{perfil.tipo==='DOCENTE'&&tarea.estado==='FALTA_COMPLETAR'&&<button className="button primary" disabled={publicar.isPending} onClick={()=>publicar.mutate()}>Publicar tarea</button>}{perfil.tipo==='DOCENTE'&&tarea.estado==='CERRADA'&&<button className="button secondary" disabled={publicar.isPending} onClick={()=>publicar.mutate()}>Reabrir tarea</button>}</div>
    {publicar.error&&<div className="alert">{String(publicar.error)}</div>}
    <div className="panel"><h2>Instrucciones</h2><p className="prewrap">{tarea.instrucciones}</p><ArchivosDe padre="tareas" id={tarea.id}/>{perfil.tipo==='DOCENTE'&&<div className="task-actions"><EtiquetaTarea t={tarea} perfil={perfil}/><EditarTarea tarea={tarea}/></div>}</div>
    {perfil.tipo==='ESTUDIANTE'&&tarea.estado==='PUBLICADA'&&<section className="section"><h2>Mi entrega</h2>{presentada?<div className="panel">Tu tarea está presentada. Consulta el registro más abajo.</div>:plazoVencido?<div className="panel">El plazo de entrega terminó.</div>:<form className="panel form" onSubmit={e=>{e.preventDefault();entregar.mutate()}}><label>Respuesta (opcional si adjuntas un archivo)<textarea rows={5} value={respuesta} onChange={e=>setRespuesta(e.target.value)} required={!archivo}/></label><label>Archivo opcional (PDF, PNG, JPG o DOCX, máximo 100 MB)<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label>{progreso>0&&<progress value={progreso} max="100"/>}{entregar.error&&<p className="error">{String(entregar.error)} {pendiente&&'La respuesta quedó guardada; puedes completar el archivo en el historial.'}</p>}<button className="button primary" disabled={entregar.isPending}>Presentar tarea</button></form>}</section>}
    {perfil.tipo!=='COORDINACION'&&<section className="section"><h2>{perfil.tipo==='ESTUDIANTE'?'Historial de entregas':'Entregas recibidas'}</h2>{!entregas.length?<div className="empty">Aún no hay entregas.</div>:<div className="list">{entregas.map(e=><div className="list-row stack-mobile" key={e.id}><div><strong>{perfil.tipo==='DOCENTE'?e.estudiante||'Estudiante no identificado':`Entrega ${e.version}`}</strong><span>Presentada el {fecha(e.entregado_en)}</span><p>{e.respuesta}</p><ArchivosDe padre="entregas" id={e.id}/>{perfil.tipo==='ESTUDIANTE'&&!calificada&&e.id===entregas[0].id&&<CargarEntrega id={e.id}/>}</div>{perfil.tipo==='DOCENTE'&&tareaId&&e.version===Math.max(...entregas.filter(x=>x.estudiante_erp_id===e.estudiante_erp_id).map(x=>x.version))&&<Calificar tarea={tareaId} estudiante={e.estudiante_erp_id}/>}</div>)}</div>}</section>}
  </>;
}

export function NotaPage(){
  const {notaId}=useParams();const {data:nota,isLoading,error}=useQuery<NotaDetalle>({queryKey:['nota',notaId],queryFn:()=>api(`/notas/${notaId}`),enabled:!!notaId});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===nota?.curso_id);
  if(isLoading)return <p className="muted">Cargando nota…</p>;
  if(error||!nota)return <div className="alert">No fue posible consultar la nota. {String(error)}</div>;
  return <><div className="page-head"><div><Link className="back" to="/notas">← Mis notas</Link><span className="eyebrow">CALIFICACIÓN DE ACTIVIDAD</span><h1>{nota.titulo}</h1><p>{curso?.asignatura} · {curso?.grado} {curso?.seccion} · Registrada {fecha(nota.actualizado_en)}</p></div></div><div className="panel"><h2>Resultado</h2><p className="grade-result">{nota.valor} <small>/ {nota.escala_maxima}</small></p><h2>Comentario del docente</h2><p className="prewrap">{nota.comentario||'No se registró un comentario.'}</p><p className="muted">Corresponde a la entrega {nota.entrega_version} · Fecha límite: {fecha(nota.fecha_limite)}</p></div></>;
}
