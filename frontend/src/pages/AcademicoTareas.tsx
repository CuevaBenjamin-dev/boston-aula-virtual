import {useEffect,useState} from 'react';
import {Link,useNavigate,useParams,useSearchParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useForm} from 'react-hook-form';
import {z} from 'zod';
import {zodResolver} from '@hookform/resolvers/zod';
import {api,del,post,put,subirArchivo,type Archivo,type Clase,type Curso,type Entrega,type Material,type Nota,type Perfil,type Seguimiento,type Tarea} from '../api';

const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short',timeZone:'America/Lima'});
function fechaLocalLima(s:string){const piezas=Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(s)).map(x=>[x.type,x.value]));return `${piezas.year}-${piezas.month}-${piezas.day}T${piezas.hour}:${piezas.minute}`}
function aIsoLima(s:string){return new Date(`${s}:00-05:00`).toISOString()}
const esquema=z.object({titulo:z.string().trim().min(3),instrucciones:z.string().trim().min(3),fechaLimite:z.string().min(1)});
type FormTarea=z.infer<typeof esquema>;
type NotaDetalle=Nota&{curso_id:string;instrucciones:string};
const archivoPermitido='.pdf,.png,.jpg,.jpeg,.docx,.pptx';

function estadoTarea(t:Tarea,perfil:Perfil){
  if(t.estado==='CANCELADA')return 'CANCELADA';
  if(t.estado==='CERRADA')return 'CERRADA';
  if(perfil.tipo!=='ESTUDIANTE')return t.estado==='FALTA_COMPLETAR'?'FALTA COMPLETAR':new Date(t.fecha_limite).getTime()<=Date.now()?'VENCIDA ABIERTA':'PUBLICADO';
  if(t.presentada)return t.ultima_entrega_estado==='TARDIA'?'ENTREGADA TARDÍA':'ENTREGADA';
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
  const estado=estadoTarea(t,perfil);return <span className={'badge '+estado.toLowerCase().replaceAll(' ','_')}>{estado}</span>;
}

export function Cursos({perfil,pagina}:{perfil:Perfil;pagina:string}){
  const {data:cursos,isLoading,error}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});
  const {data:sincronizacion}=useQuery<{sincronizadoEn:string|null;pendiente:boolean}>({queryKey:['sincronizacion'],queryFn:()=>api('/sincronizacion/estado'),enabled:pagina==='cursos',refetchInterval:30000});
  if(isLoading)return <p className="muted">Cargando cursos…</p>;
  if(error)return <div className="alert">No fue posible consultar los cursos. {String(error)}</div>;
  if(cursos?.length&&['tareas','notas'].includes(pagina))return <VistaGlobal cursos={cursos} pagina={pagina} perfil={perfil}/>;
  const titulo=pagina==='inicio'?'Bienvenido al Aula Virtual':pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):pagina==='notas'?(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones'):'Mis cursos';
  return <><div className="page-head"><div><span className="eyebrow">CURSOS INSTITUCIONALES</span><h1>{titulo}</h1><p>Selecciona un curso para consultar su resumen.</p></div></div>{sincronizacion?.pendiente&&<div className="alert">{`El ERP está temporalmente inaccesible. Se muestran los cursos de la última sincronización${sincronizacion.sincronizadoEn?` (${fecha(sincronizacion.sincronizadoEn)})`:''}; los cambios pendientes se reintentarán automáticamente.`}</div>}{!cursos?.length?<div className="empty">Aún no hay cursos asignados. La sincronización con el ERP se ejecuta automáticamente.</div>:<div className="cards">{cursos.map(c=><Link className="course-card" key={c.id} to={'/cursos/'+c.id}><span className="course-icon">{c.asignatura[0]}</span><span className="eyebrow">{c.nivel} · {c.grado} · {c.seccion}</span><h2>{c.asignatura}</h2><p>Periodo {c.periodo}</p><span className="text-link">Abrir curso →</span></Link>)}</div>}</>;
}

type Fila={id:string;curso:Curso;tarea?:Tarea;nota?:Nota};
function VistaGlobal({cursos,pagina,perfil}:{cursos:Curso[];pagina:string;perfil:Perfil}){
  const [params,setParams]=useSearchParams();
  const mostrarHistorial=params.get('historial')==='1';
  const cambiar=(clave:string,valor:string)=>{const siguiente=new URLSearchParams(params);if(valor)siguiente.set(clave,valor);else siguiente.delete(clave);siguiente.delete('resaltar');setParams(siguiente)};
  const ids=cursos.map(c=>c.id).join(',');
  const {data:filas=[],isLoading,error}=useQuery<Fila[]>({queryKey:['vista-global',pagina,ids,perfil.tipo],queryFn:async()=>{
    if(pagina==='notas'&&perfil.tipo==='ESTUDIANTE')return (await Promise.all(cursos.map(async curso=>(await api<Nota[]>(`/cursos/${curso.id}/notas`)).map(n=>({id:n.id,curso,nota:n}))))).flat().sort((a,b)=>new Date(b.nota!.actualizado_en).getTime()-new Date(a.nota!.actualizado_en).getTime());
    return (await Promise.all(cursos.map(async curso=>(await api<Tarea[]>(`/cursos/${curso.id}/tareas`)).map(t=>({id:t.id,curso,tarea:t}))))).flat().sort((a,b)=>new Date(b.tarea!.creado_en).getTime()-new Date(a.tarea!.creado_en).getTime());
  }});
  const visibles=filas.filter(f=>{
    const texto=(params.get('q')||'').toLocaleLowerCase('es');
    const valorFecha=f.nota?.actualizado_en||f.tarea?.fecha_limite||'';
    const dia=fechaLocalLima(valorFecha).slice(0,10);
    return (!params.get('curso')||f.curso.id===params.get('curso'))
      &&(pagina!=='tareas'||perfil.tipo!=='DOCENTE'||Boolean(f.tarea?.es_historica)===mostrarHistorial)
      &&(!texto||`${f.nota?.titulo||f.tarea?.titulo} ${f.curso.asignatura} ${f.nota?.comentario||''}`.toLocaleLowerCase('es').includes(texto))
      &&(pagina!=='tareas'||!params.get('estado')||estadoTarea(f.tarea!,perfil)===params.get('estado'))
      &&(!params.get('desde')||dia>=params.get('desde')!)
      &&(!params.get('hasta')||dia<=params.get('hasta')!);
  });
  useEffect(()=>{const id=params.get('resaltar');if(id&&visibles.some(f=>f.id===id))requestAnimationFrame(()=>document.getElementById(`tarea-${id}`)?.scrollIntoView({behavior:'smooth',block:'center'}))},[params,visibles.length]);
  const titulo=pagina==='tareas'?(perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'):(perfil.tipo==='ESTUDIANTE'?'Mis notas':'Calificaciones');
  const descripcion=pagina==='notas'&&perfil.tipo==='DOCENTE'?'Selecciona una tarea para revisar entregas y calificar.':'Consulta la información de tus cursos.';
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>{titulo}</h1><p>{descripcion}</p></div>{pagina==='tareas'&&perfil.tipo==='DOCENTE'&&<CrearTarea cursos={cursos}/>}</div>
    <div className="panel form filters"><div className="filter-grid">
      <label>Buscar<input value={params.get('q')||''} onChange={e=>cambiar('q',e.target.value)} placeholder={pagina==='notas'?'Actividad o comentario':'Tarea o curso'}/></label>
      <label>Curso<select value={params.get('curso')||''} onChange={e=>cambiar('curso',e.target.value)}><option value="">Todos los cursos</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label>
      {pagina==='tareas'&&<label>Estado<select value={params.get('estado')||''} onChange={e=>cambiar('estado',e.target.value)}><option value="">Todos</option>{(perfil.tipo==='ESTUDIANTE'?['PENDIENTE','URGENTE','VENCIDA ABIERTA','ENTREGADA','ENTREGADA TARDÍA','CERRADA','CANCELADA']:['PUBLICADO','VENCIDA ABIERTA','FALTA COMPLETAR','CERRADA','CANCELADA']).map(e=><option key={e}>{e}</option>)}</select></label>}
      <label>Desde<input type="date" value={params.get('desde')||''} onChange={e=>cambiar('desde',e.target.value)}/></label>
      <label>Hasta<input type="date" value={params.get('hasta')||''} onChange={e=>cambiar('hasta',e.target.value)}/></label>
    </div></div>
    {pagina==='tareas'&&perfil.tipo==='DOCENTE'&&<button className="text-link" onClick={()=>cambiar('historial',mostrarHistorial?'':'1')}>{mostrarHistorial?'← Ver tareas vigentes':'Ver versiones anteriores →'}</button>}
    {isLoading?<p className="muted">Cargando…</p>:error?<div className="alert">No fue posible consultar {titulo.toLowerCase()}. {String(error)}</div>:!visibles.length?<div className="empty">No hay resultados para estos filtros.</div>:<div className="list">{visibles.map(f=>f.nota?<Link className="list-row" key={f.id} to={`/notas/${f.id}`}><div><strong>{f.nota.titulo}</strong><span>{f.curso.asignatura} · {f.curso.nivel} {f.curso.grado} {f.curso.seccion} · Calificada {fecha(f.nota.actualizado_en)}</span><p>{f.nota.comentario||'Sin comentario del docente'}</p></div><strong>{f.nota.valor}/{f.nota.escala_maxima}</strong><span>Ver nota →</span></Link>:<Link id={`tarea-${f.id}`} className={'list-row'+(params.get('resaltar')===f.id?' task-return':'')} key={f.id} to={`/tareas/${f.id}?return=${encodeURIComponent('/tareas?'+new URLSearchParams({...Object.fromEntries(params),resaltar:f.id}).toString())}`}><div><strong>{f.tarea!.titulo}</strong><span>{f.curso.asignatura} · {f.curso.nivel} {f.curso.grado} {f.curso.seccion} · Entrega hasta {fecha(f.tarea!.fecha_limite)}</span>{perfil.tipo==='DOCENTE'&&!mostrarHistorial&&<small>{f.tarea!.entregados}/{f.tarea!.total_estudiantes} entregaron · {f.tarea!.tardias} tardías · {f.tarea!.pendientes} pendientes · {f.tarea!.pendientes_revision} por revisar</small>}</div>{pagina==='tareas'&&<EtiquetaTarea t={f.tarea!} perfil={perfil}/>}<span>{pagina==='notas'?'Revisar →':'Ver tarea →'}</span></Link>)}</div>}
  </>;
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

function MaterialesCurso({curso,perfil,cursos}:{curso:string;perfil:Perfil;cursos:Curso[]}){
  const qc=useQueryClient(),[nuevo,setNuevo]=useState(false);
  const {data:materiales=[],error}=useQuery<Material[]>({queryKey:['materiales',curso],queryFn:()=>api(`/cursos/${curso}/materiales`)});
  const ocultar=useMutation({mutationFn:(id:string)=>post(`/materiales/${id}/ocultar`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['materiales']})});
  return <section className="section"><div className="section-head"><h2>Materiales del curso</h2>{perfil.tipo==='DOCENTE'&&<button className="button secondary" onClick={()=>setNuevo(!nuevo)}>+ Añadir material</button>}</div>
    {nuevo&&<FormularioMaterial curso={curso} cursos={cursos} cerrar={()=>setNuevo(false)}/>}
    {error?<div className="alert">No se pudieron consultar los materiales. {String(error)}</div>:!materiales.length?<div className="empty">Todavía no hay materiales.</div>:<div className="list">{materiales.map(m=><MaterialFila key={m.id} material={m} curso={curso} perfil={perfil} cursos={cursos} ocultar={()=>ocultar.mutate(m.id)}/>)}</div>}
    {ocultar.error&&<p className="error">{String(ocultar.error)}</p>}
  </section>;
}

function MaterialFila({material,curso,perfil,cursos,ocultar}:{material:Material;curso:string;perfil:Perfil;cursos:Curso[];ocultar:()=>void}){
  const [editar,setEditar]=useState(false);
  return <div className="panel" ><div className="section-head"><div><strong>{material.nombre}</strong><p className="muted">{material.estado==='BORRADOR'?'Falta completar':material.estado==='OCULTO'?'Oculto':'Publicado'} · {material.total_archivos||0} archivos</p></div>{perfil.tipo==='DOCENTE'&&<div className="row-actions"><button className="button secondary" onClick={()=>setEditar(!editar)}>Editar</button>{material.estado==='PUBLICADO'&&<button className="button secondary" onClick={ocultar}>Ocultar</button>}</div>}</div>
    {material.descripcion&&<p className="prewrap">{material.descripcion}</p>}{material.enlace&&<a className="text-link" href={material.enlace} target="_blank" rel="noopener noreferrer">Abrir enlace →</a>}
    <ArchivosDe padre="materiales" id={material.id} editable={perfil.tipo==='DOCENTE'}/>
    {editar&&<FormularioMaterial curso={curso} cursos={cursos} material={material} cerrar={()=>setEditar(false)}/>}
  </div>;
}

function FormularioMaterial({curso,cursos,material,cerrar}:{curso:string;cursos:Curso[];material?:Material;cerrar:()=>void}){
  const qc=useQueryClient(),[nombre,setNombre]=useState(material?.nombre||''),[descripcion,setDescripcion]=useState(material?.descripcion||''),[enlace,setEnlace]=useState(material?.enlace||''),[destinos,setDestinos]=useState<string[]>(material?.curso_ids||[curso]),[archivos,setArchivos]=useState<File[]>([]),[progreso,setProgreso]=useState(0);
  const {data:detalle}=useQuery<Material>({queryKey:['material',material?.id],queryFn:()=>api(`/materiales/${material?.id}`),enabled:!!material?.id});
  useEffect(()=>{if(detalle?.curso_ids)setDestinos(detalle.curso_ids)},[detalle?.id]);
  const guardar=useMutation({mutationFn:async(publicar:boolean)=>{
    if(!destinos.length)throw new Error('Selecciona al menos una sección.');
    const datos={nombre,descripcion,enlace,cursoIds:destinos};
    const id=material?.id||(await post<{id:string}>('/materiales',datos)).id;
    if(material)await put(`/materiales/${id}`,datos);
    for(const archivo of archivos)await subirArchivo('materiales',id,archivo,setProgreso);
    if(publicar)await post(`/materiales/${id}/publicar`,{});
    return id;
  },onSuccess:()=>{qc.invalidateQueries({queryKey:['materiales']});qc.invalidateQueries({queryKey:['archivos','materiales']});cerrar()}});
  return <form className="form compact" onSubmit={e=>{e.preventDefault();guardar.mutate(true)}}><h3>{material?'Editar material':'Nuevo material'}</h3><label>Nombre<input value={nombre} maxLength={200} onChange={e=>setNombre(e.target.value)} required/></label><label>Descripción<textarea value={descripcion} rows={3} onChange={e=>setDescripcion(e.target.value)}/></label><label>Enlace (opcional)<input type="url" value={enlace} onChange={e=>setEnlace(e.target.value)} placeholder="https://..."/></label>
    <strong>Secciones donde estará disponible</strong><div className="recipient-list">{cursos.map(c=><label className="recipient" key={c.id}><input type="checkbox" checked={destinos.includes(c.id)} onChange={e=>setDestinos(x=>e.target.checked?[...x,c.id]:x.filter(id=>id!==c.id))}/>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</label>)}</div>
    <label>Adjuntar archivos<input type="file" multiple accept={archivoPermitido} onChange={e=>setArchivos(Array.from(e.target.files||[]))}/></label>{archivos.length>0&&<small>{archivos.map(a=>a.name).join(', ')}</small>}{progreso>0&&<progress value={progreso} max="100"/>}{guardar.error&&<p className="error">{String(guardar.error)}</p>}
    <div className="row-actions"><button className="button primary" disabled={guardar.isPending}>Publicar</button><button type="button" className="button secondary" disabled={guardar.isPending} onClick={()=>guardar.mutate(false)}>Guardar como borrador</button><button type="button" className="text-link" onClick={cerrar}>Cancelar</button></div>
  </form>;
}

export function CursoPage({perfil,modulos}:{perfil:Perfil;modulos?:{clasesYGrabaciones:boolean}}){
  const {cursoId}=useParams();
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===cursoId);
  const {data:tareas=[]}=useQuery<Tarea[]>({queryKey:['tareas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/tareas`),enabled:!!cursoId,refetchInterval:60000});
  const {data:clases=[]}=useQuery<Clase[]>({queryKey:['clases',cursoId],queryFn:()=>api(`/cursos/${cursoId}/clases`),enabled:!!cursoId&&modulos?.clasesYGrabaciones!==false,refetchInterval:30000});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',cursoId],queryFn:()=>api(`/cursos/${cursoId}/notas`),enabled:!!cursoId&&['ESTUDIANTE','DOCENTE'].includes(perfil.tipo)});
  const resumenTareas=perfil.tipo==='ESTUDIANTE'?tareas.filter(t=>semanaActual(t.publicado_en||t.creado_en)&&!t.presentada&&t.estado==='PUBLICADA'):tareas.filter(t=>!t.es_historica).slice(0,5);
  const resumenClases=[...clases].sort((a,b)=>Math.abs(new Date(a.inicio).getTime()-Date.now())-Math.abs(new Date(b.inicio).getTime()-Date.now())).slice(0,3);
  return <><div className="page-head"><div><Link className="back" to="/cursos">← Mis cursos</Link><span className="eyebrow">{curso?.nivel} · {curso?.grado} · Sección {curso?.seccion}</span><h1>{curso?.asignatura||'Curso'}</h1><p><strong>Docente:</strong> {curso?.docentes||'Sin docente asignado'}</p><p>Resumen de tareas, clases y {perfil.tipo==='ESTUDIANTE'?'notas':'entregas'} del curso.</p></div></div>
    <section className="section"><div className="section-head"><h2>{perfil.tipo==='ESTUDIANTE'?'Tareas pendientes de esta semana':'Tareas'}</h2>{perfil.tipo==='DOCENTE'&&cursoId&&<CrearTarea curso={cursoId} cursos={cursos}/>}</div>{!resumenTareas.length?<div className="empty">{perfil.tipo==='ESTUDIANTE'?'No hay tareas pendientes esta semana.':'Todavía no hay tareas.'}</div>:<div className="list">{resumenTareas.map(t=>{const regreso=`/tareas?curso=${cursoId}&q=${encodeURIComponent(t.titulo)}&resaltar=${t.id}`;return <Link className="list-row" to={`/tareas/${t.id}?return=${encodeURIComponent(regreso)}`} key={t.id}><div><strong>{t.titulo}</strong><span>Entrega hasta {fecha(t.fecha_limite)}</span></div>{perfil.tipo==='DOCENTE'&&<strong>{t.entregados}/{t.total_estudiantes}</strong>}<EtiquetaTarea t={t} perfil={perfil}/><span>Ver tarea →</span></Link>})}</div>}<Link className="text-link section-more" to={`/tareas?curso=${cursoId}`}>Ver todas las tareas →</Link></section>
    {cursoId&&<MaterialesCurso curso={cursoId} perfil={perfil} cursos={cursos}/>}
    {modulos?.clasesYGrabaciones!==false&&<section className="section"><div className="section-head"><h2>Clases virtuales</h2>{perfil.tipo==='DOCENTE'&&<Link className="button secondary" to={`/clases?asignatura=${encodeURIComponent(curso?.asignatura||'')}&grado=${encodeURIComponent(curso?.grado||'')}&seccion=${encodeURIComponent(curso?.seccion||'')}`}>Programar clase</Link>}</div>{!resumenClases.length?<div className="empty">No hay clases programadas.</div>:<div className="list">{resumenClases.map(c=><Link className="list-row" key={c.id} to={`/clases/${c.id}?return=${encodeURIComponent('/cursos/'+cursoId)}`}><div><strong>{c.tema}</strong><span>{fecha(c.inicio)}</span></div><span className={'badge '+c.estado.toLowerCase()}>{c.estado==='CANCELADA'?'CANCELADA':'PROGRAMADA'}</span><span>Ver clase →</span></Link>)}</div>}<Link className="text-link section-more" to="/clases">Ver todas las clases →</Link></section>}
    {perfil.tipo==='ESTUDIANTE'&&<section className="section"><h2>Mis notas recientes</h2>{!notas.length?<div className="empty">Aún no tienes notas registradas.</div>:<div className="list">{notas.slice(0,3).map(n=><Link className="list-row" key={n.id} to={`/notas/${n.id}`}><div><strong>{n.titulo}</strong><span>{n.comentario||'Sin comentario del docente'}</span></div><strong>{n.valor}/{n.escala_maxima}</strong><span>Ver nota →</span></Link>)}</div>}<Link className="text-link section-more" to={`/notas?curso=${cursoId}`}>Ver todas mis notas →</Link></section>}
    {perfil.tipo==='DOCENTE'&&<section className="section"><h2>Calificaciones</h2><div className="panel">{notas.length} calificaciones registradas en este curso.</div><Link className="text-link section-more" to={`/notas?curso=${cursoId}`}>Revisar actividades y calificar →</Link></section>}
  </>;
}

function ArchivosDe({padre,id,editable=false}:{padre:'tareas'|'entregas'|'materiales';id:string;editable?:boolean}){
  const qc=useQueryClient();
  const {data=[]}=useQuery<Archivo[]>({queryKey:['archivos',padre,id],queryFn:()=>api(`/${padre}/${id}/archivos`),enabled:!!id});const [error,setError]=useState('');
  async function abrir(archivo:Archivo){try{const {url}=await api<{url:string}>(`/archivos/${archivo.id}/url`);window.open(url,'_blank','noopener,noreferrer')}catch(e){setError(String(e))}}
  async function retirar(archivo:Archivo){try{await del(`/archivos/${archivo.id}`);qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  async function reemplazar(archivo:Archivo,nuevo:File){try{await subirArchivo(padre,id,nuevo,()=>{},archivo.id);qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  async function agregar(nuevos:FileList|null){if(!nuevos)return;try{for(const nuevo of Array.from(nuevos))await subirArchivo(padre,id,nuevo,()=>{});qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  return <>{data.map(a=><div className="row-actions" key={a.id}><button className="text-link" onClick={()=>abrir(a)}>↓ {a.nombre}</button>{editable&&<><label className="text-link">Sustituir<input type="file" accept={archivoPermitido} style={{display:'none'}} onChange={e=>{const f=e.target.files?.[0];if(f)reemplazar(a,f)}}/></label><button className="text-link" onClick={()=>retirar(a)}>Retirar</button></>}</div>)}{editable&&<label className="text-link">+ Agregar archivos<input type="file" multiple accept={archivoPermitido} style={{display:'none'}} onChange={e=>agregar(e.target.files)}/></label>}{error&&<small className="error">{error}</small>}</>;
}
function CargarEntrega({id}:{id:string}){
  const qc=useQueryClient(),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0);
  const {data:archivos=[]}=useQuery<Archivo[]>({queryKey:['archivos','entregas',id],queryFn:()=>api(`/entregas/${id}/archivos`)});
  const mut=useMutation({mutationFn:()=>subirArchivo('entregas',id,archivo!,setProgreso),onSuccess:()=>{qc.invalidateQueries({queryKey:['archivos','entregas',id]});setArchivo(null);setProgreso(0)}});
  if(archivos.length)return null;
  return <div className="form compact"><label>Adjuntar archivo a esta entrega<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label><button className="button secondary" disabled={!archivo||mut.isPending} onClick={()=>mut.mutate()}>Subir archivo</button>{progreso>0&&<progress value={progreso} max="100"/>}{mut.error&&<small className="error">{String(mut.error)}</small>}</div>;
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
function Calificar({tarea,estudiante}:{tarea:string;estudiante:string}){
  const [abierto,setAbierto]=useState(false),[valor,setValor]=useState(''),[comentario,setComentario]=useState(''),qc=useQueryClient();
  const {data:t}=useQuery<Tarea>({queryKey:['tarea',tarea],queryFn:()=>api(`/tareas/${tarea}`)});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',t?.curso_id],queryFn:()=>api(`/cursos/${t?.curso_id}/notas`),enabled:!!t?.curso_id});
  const actual=notas.find(n=>n.tarea_id===tarea&&n.estudiante_erp_id===estudiante);
  const mut=useMutation({mutationFn:()=>put(`/tareas/${tarea}/calificacion`,{estudianteId:estudiante,valor:Number(valor),comentario,version:actual?.version||0}),onSuccess:()=>{qc.invalidateQueries({queryKey:['notas',t?.curso_id]});setValor('');setAbierto(false)}});
  return <div><button className="button secondary" onClick={()=>setAbierto(!abierto)}>{actual?`Nota ${actual.valor}/${t?.escala_maxima} · Editar`:'Calificar'}</button>{abierto&&<form className="form compact" onSubmit={e=>{e.preventDefault();mut.mutate()}}><label>Nota sobre {t?.escala_maxima}<input type="number" min="0" max={t?.escala_maxima} step="0.01" value={valor} onChange={e=>setValor(e.target.value)} required/></label><label>Comentario<input value={comentario} onChange={e=>setComentario(e.target.value)}/></label>{mut.error&&<small className="error">{String(mut.error)}</small>}<button className="button primary" disabled={mut.isPending}>Guardar nota</button></form>}</div>;
}

function SeguimientoTarea({tarea}:{tarea:Tarea}){
  const [grupo,setGrupo]=useState<'TODOS'|'ENTREGARON'|'TARDIAS'|'PENDIENTES'|'REVISION'>('TODOS');
  const {data:filas=[],error}=useQuery<Seguimiento[]>({queryKey:['seguimiento',tarea.id,grupo],queryFn:()=>api(`/tareas/${tarea.id}/seguimiento?grupo=${grupo}`)});
  const opciones:[typeof grupo,string,number|undefined][]=[['TODOS','Total',tarea.total_estudiantes],['ENTREGARON','Entregaron',tarea.entregados],['TARDIAS','Tardías',tarea.tardias],['PENDIENTES','Pendientes',tarea.pendientes],['REVISION','Por revisar',tarea.pendientes_revision]];
  return <section className="section"><h2>Seguimiento de esta versión</h2><div className="row-actions">{opciones.map(([clave,etiqueta,total])=><button key={clave} className={'button '+(grupo===clave?'primary':'secondary')} onClick={()=>setGrupo(clave)}>{etiqueta} {total??0}</button>)}</div>
    {error?<p className="error">No se pudo consultar el seguimiento. {String(error)}</p>:!filas.length?<div className="empty">No hay estudiantes en este filtro.</div>:<div className="list">{filas.map(e=><div className="list-row" key={e.estudiante_erp_id}><div><strong>{e.estudiante}</strong><span>{e.estado_entrega==='TARDIA'?'Entregada tardía':e.entrega_id?'Entregada':'Pendiente'}{e.entregado_en?` · ${fecha(e.entregado_en)}`:''}</span></div>{e.entrega_id&&<a className="text-link" href={`#entrega-${e.entrega_id}`}>Ver entrega ↓</a>}</div>)}</div>}
  </section>;
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
  const {data:entregas=[]}=useQuery<Entrega[]>({queryKey:['entregas',tareaId],queryFn:()=>api(`/tareas/${tareaId}/entregas`),enabled:!!tareaId&&perfil.tipo!=='COORDINACION'});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',tarea?.curso_id],queryFn:()=>api(`/cursos/${tarea?.curso_id}/notas`),enabled:perfil.tipo==='ESTUDIANTE'&&!!tarea?.curso_id});
  const [respuesta,setRespuesta]=useState(''),[archivo,setArchivo]=useState<File|null>(null),[progreso,setProgreso]=useState(0),[pendiente,setPendiente]=useState<string|null>(null),[confirmacion,setConfirmacion]=useState<string|null>(null);
  const estadoMut=useMutation({mutationFn:(accion:'publicar'|'cerrar'|'cancelar')=>post(`/tareas/${tareaId}/${accion}`,{}),onSuccess:()=>{qc.invalidateQueries({queryKey:['tarea',tareaId]});qc.invalidateQueries({queryKey:['tareas',tarea?.curso_id]});qc.invalidateQueries({queryKey:['vista-global']})}});
  const cambiarEstado=(accion:'publicar'|'cerrar'|'cancelar')=>{
    const mensaje=accion==='cerrar'?'Cerrar esta tarea impedirá nuevas entregas. Las recibidas y sus notas se conservarán. ¿Continuar?':accion==='cancelar'?'Cancelar esta tarea impedirá nuevas entregas y no se podrá reabrir. Las entregas y notas anteriores se conservarán. ¿Continuar?':tarea?.estado==='CERRADA'?'Reabrir esta tarea permitirá nuevas entregas; si está vencida serán tardías. ¿Continuar?':null;
    if(!mensaje||window.confirm(mensaje))estadoMut.mutate(accion);
  };
  const entregar=useMutation({mutationFn:async()=>{const id=pendiente||(await post<{id:string}>(`/tareas/${tareaId}/entregas`,{respuesta})).id;setPendiente(id);if(archivo)await subirArchivo('entregas',id,archivo,setProgreso);return id},onSuccess:(id)=>{setConfirmacion(id);setPendiente(null);setRespuesta('');setArchivo(null);setProgreso(0);qc.invalidateQueries({queryKey:['entregas',tareaId]});qc.invalidateQueries({queryKey:['tareas',tarea?.curso_id]});qc.invalidateQueries({queryKey:['vista-global']})}});
  if(isLoading)return <p className="muted">Cargando tarea…</p>;
  if(error||!tarea)return <div className="alert">No fue posible abrir la tarea. {String(error)}</div>;
  const presentada=entregas.length>0,plazoVencido=new Date(tarea.fecha_limite).getTime()<Date.now(),calificada=notas.some(n=>n.tarea_id===tareaId);
  const recibo=entregas.find(e=>e.id===confirmacion);
  const pedido=params.get('return');
  const regreso=pedido&&!pedido.startsWith('//')&&(/^\/tareas\?/.test(pedido)||pedido==='/notificaciones?tab=notificaciones')?pedido:`/tareas?curso=${tarea.curso_id}&q=${encodeURIComponent(tarea.titulo)}&resaltar=${tarea.id}${tarea.tarea_sucesora_id?'&historial=1':''}`;
  return <><div className="page-head"><div><Link className="back" to={regreso}>← {regreso.startsWith('/notificaciones')?'Notificaciones':perfil.tipo==='ESTUDIANTE'?'Mis tareas':'Tareas y entregas'}</Link><span className="eyebrow">ACTIVIDAD DEL AULA</span><h1>{tarea.titulo}</h1><p>Fecha y hora límite: {fecha(tarea.fecha_limite)} (Lima)</p><p>{tarea.estado==='PUBLICADA'?(plazoVencido?'Vencida, pero abierta para entregas tardías.':'Abierta para entregas.'):tarea.estado==='CERRADA'?'Cerrada manualmente: no recibe entregas.':tarea.estado==='CANCELADA'?'Cancelada: no recibe entregas.':'Borrador: solo visible para el docente.'}</p></div>{perfil.tipo==='DOCENTE'&&<div className="row-actions">{tarea.estado==='FALTA_COMPLETAR'&&<button className="button primary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('publicar')}>Publicar tarea</button>}{tarea.estado==='PUBLICADA'&&<><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cerrar')}>Cerrar tarea</button><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cancelar')}>Cancelar tarea</button></>}{tarea.estado==='CERRADA'&&!tarea.tarea_sucesora_id&&<><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('publicar')}>Reabrir tarea</button><button className="button secondary" disabled={estadoMut.isPending} onClick={()=>cambiarEstado('cancelar')}>Cancelar tarea</button></>}</div>}</div>
    {estadoMut.error&&<div className="alert">{String(estadoMut.error)}</div>}
    <div className="panel"><h2>Instrucciones</h2><p className="prewrap">{tarea.instrucciones}</p><ArchivosDe padre="tareas" id={tarea.id} editable={perfil.tipo==='DOCENTE'&&!entregas.length&&['FALTA_COMPLETAR','PUBLICADA'].includes(tarea.estado)}/>{perfil.tipo==='DOCENTE'&&<div className="task-actions"><EtiquetaTarea t={tarea} perfil={perfil}/>{!tarea.tarea_sucesora_id&&tarea.estado!=='CANCELADA'&&<EditarTarea tarea={tarea} hayEntregas={entregas.length>0}/>}</div>}</div>
    {perfil.tipo==='DOCENTE'&&tarea.tarea_anterior_id&&<div className="panel"><strong>Versión actual de la actividad.</strong> Las entregas y notas anteriores permanecen en el historial. <Link className="text-link" to={`/tareas/${tarea.tarea_anterior_id}`}>Ver versión anterior →</Link></div>}
    {perfil.tipo==='DOCENTE'&&tarea.tarea_sucesora_id&&<div className="panel"><strong>Versión anterior.</strong> Estas entregas y notas se conservan como historial. <Link className="text-link" to={`/tareas/${tarea.tarea_sucesora_id}`}>Abrir versión vigente →</Link></div>}
    {perfil.tipo==='DOCENTE'&&tarea.lote_id&&<TareasDelLote lote={tarea.lote_id} actual={tarea.id}/>}
    {perfil.tipo==='DOCENTE'&&!tarea.tarea_sucesora_id&&<SeguimientoTarea tarea={tarea}/>}
    {perfil.tipo==='ESTUDIANTE'&&<section className="section"><h2>Mi entrega</h2>{recibo&&<div className="alert">Entrega recibida el {fecha(recibo.entregado_en)} (Lima). {recibo.estado==='TARDIA'?'Registrada como tardía.':'Registrada dentro del plazo.'}</div>}{presentada?<div className="panel">{entregas[0].estado==='TARDIA'?'Entregada tardía':'Entregada'} el {fecha(entregas[0].entregado_en)}. Consulta el registro más abajo.</div>:tarea.estado==='PUBLICADA'?<form className="panel form" onSubmit={e=>{e.preventDefault();entregar.mutate()}}>{plazoVencido&&<p className="alert">El plazo venció, pero la tarea sigue abierta. Tu entrega se registrará como tardía.</p>}<label>Respuesta (opcional si adjuntas un archivo)<textarea rows={5} value={respuesta} onChange={e=>setRespuesta(e.target.value)} required={!archivo}/></label><label>Archivo opcional (PDF, PNG, JPG, DOCX o PPTX, máximo 100 MB)<input type="file" accept={archivoPermitido} onChange={e=>setArchivo(e.target.files?.[0]||null)}/></label>{progreso>0&&<progress value={progreso} max="100"/>}{entregar.error&&<p className="error">{String(entregar.error)} {pendiente&&'La respuesta quedó guardada; puedes completar el archivo en el historial.'}</p>}<button className="button primary" disabled={entregar.isPending}>Presentar tarea</button></form>:<div className="panel">{tarea.estado==='CANCELADA'?'La tarea fue cancelada.':tarea.estado==='CERRADA'?'La tarea está cerrada.':'La tarea aún no se publicó.'} No se reciben nuevas entregas.</div>}</section>}
    {perfil.tipo!=='COORDINACION'&&<section className="section"><h2>{perfil.tipo==='ESTUDIANTE'?'Historial de entregas':'Entregas recibidas'}</h2>{!entregas.length?<div className="empty">Aún no hay entregas.</div>:<div className="list">{entregas.map(e=><div className="list-row stack-mobile" id={`entrega-${e.id}`} key={e.id}><div><strong>{perfil.tipo==='DOCENTE'?e.estudiante||'Estudiante no identificado':`Entrega ${e.version}`}</strong><span>{e.estado==='TARDIA'?'Entregada tardía':'Entregada'} el {fecha(e.entregado_en)} (Lima) · Plazo al recibir: {fecha(e.fecha_limite_al_entregar)}</span><p>{e.respuesta}</p><ArchivosDe padre="entregas" id={e.id}/>{perfil.tipo==='ESTUDIANTE'&&!calificada&&e.id===entregas[0].id&&tarea.estado==='PUBLICADA'&&<CargarEntrega id={e.id}/>}</div>{perfil.tipo==='DOCENTE'&&tareaId&&e.version===Math.max(...entregas.filter(x=>x.estudiante_erp_id===e.estudiante_erp_id).map(x=>x.version))&&<Calificar tarea={tareaId} estudiante={e.estudiante_erp_id}/>}</div>)}</div>}</section>}
  </>;
}

export function NotaPage(){
  const {notaId}=useParams();const {data:nota,isLoading,error}=useQuery<NotaDetalle>({queryKey:['nota',notaId],queryFn:()=>api(`/notas/${notaId}`),enabled:!!notaId});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===nota?.curso_id);
  if(isLoading)return <p className="muted">Cargando nota…</p>;
  if(error||!nota)return <div className="alert">No fue posible consultar la nota. {String(error)}</div>;
  return <><div className="page-head"><div><Link className="back" to="/notas">← Mis notas</Link><span className="eyebrow">CALIFICACIÓN DE ACTIVIDAD</span><h1>{nota.titulo}</h1><p>{curso?.asignatura} · {curso?.grado} {curso?.seccion} · Registrada {fecha(nota.actualizado_en)}</p></div></div><div className="panel"><h2>Resultado</h2><p className="grade-result">{nota.valor} <small>/ {nota.escala_maxima}</small></p><h2>Comentario del docente</h2><p className="prewrap">{nota.comentario||'No se registró un comentario.'}</p><p className="muted">Corresponde a la entrega {nota.entrega_version} · Fecha límite: {fecha(nota.fecha_limite)}</p></div></>;
}
