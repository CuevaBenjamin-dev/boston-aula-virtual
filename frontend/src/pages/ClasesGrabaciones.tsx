import {useEffect, useMemo, useState} from 'react';
import {Link, useParams, useSearchParams} from 'react-router-dom';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api, post, put, type Clase, type Curso, type Grabacion, type NotificacionClase, type Perfil} from '../api';

const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});
const local=(s:string)=>{const d=new Date(s);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16)};
const paginaTamano=10;
const unico=(xs:string[])=>[...new Set(xs)].sort((a,b)=>a.localeCompare(b,'es'));
const errorDe=(error:unknown)=>error instanceof Error?error.message:String(error);

export function normalizarYoutube(valor:string):string|null {
  try {
    const u=new URL(valor.trim());
    if(u.protocol!=='https:'||u.username||u.password||u.port||u.hash)return null;
    const host=u.hostname.toLowerCase();let id='';
    if(['youtu.be','www.youtu.be'].includes(host))id=u.pathname.replace(/^\//,'').replace(/\/$/,'');
    else if(['youtube.com','www.youtube.com','m.youtube.com','youtube-nocookie.com','www.youtube-nocookie.com'].includes(host)){
      if(u.pathname==='/watch')id=u.searchParams.get('v')||'';
      else if(/^\/(shorts|live|embed|v)\/[A-Za-z0-9_-]{11}\/?$/.test(u.pathname))id=u.pathname.split('/')[2]||'';
      if(host.endsWith('youtube-nocookie.com')&&!/^\/embed\//.test(u.pathname))id='';
    }
    return /^[A-Za-z0-9_-]{11}$/.test(id)?`https://www.youtube.com/watch?v=${id}`:null;
  } catch {return null}
}

function useFiltros(){
  const [params,setParams]=useSearchParams();
  function cambiar(clave:string,valor:string){const p=new URLSearchParams(params);if(valor)p.set(clave,valor);else p.delete(clave);if(clave!=='p')p.delete('p');setParams(p)}
  return {params,cambiar};
}
function Filtros({params,cambiar,asignaturas,grados,secciones,estados,conTitulo=false}:{params:URLSearchParams;cambiar:(k:string,v:string)=>void;asignaturas:string[];grados:string[];secciones:string[];estados?:string[];conTitulo?:boolean}){
  return <div className="panel form filters"><div className="filter-grid">
    <label>{conTitulo?'Buscar título':'Buscar tema'}<input value={params.get('q')||''} onChange={e=>cambiar('q',e.target.value)} placeholder="Escribe para buscar"/></label>
    <label>Curso<select value={params.get('asignatura')||''} onChange={e=>cambiar('asignatura',e.target.value)}><option value="">Todos</option>{asignaturas.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Grado<select value={params.get('grado')||''} onChange={e=>cambiar('grado',e.target.value)}><option value="">Todos</option>{grados.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Sección<select value={params.get('seccion')||''} onChange={e=>cambiar('seccion',e.target.value)}><option value="">Todas</option>{secciones.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Fecha<input type="date" value={params.get('fecha')||''} onChange={e=>cambiar('fecha',e.target.value)}/></label>
    {estados&&<label>Estado<select value={params.get('estado')||''} onChange={e=>cambiar('estado',e.target.value)}><option value="">Todos</option>{estados.map(x=><option key={x} value={x}>{x}</option>)}</select></label>}
  </div></div>
}
function Paginacion({total,params,cambiar}:{total:number;params:URLSearchParams;cambiar:(k:string,v:string)=>void}){
  const max=Math.max(1,Math.ceil(total/paginaTamano)),actual=Math.min(max,Math.max(1,Number(params.get('p'))||1));
  if(max<2)return null;
  return <div className="pagination"><button className="button secondary" disabled={actual<=1} onClick={()=>cambiar('p',String(actual-1))}>← Anterior</button><span>Página {actual} de {max}</span><button className="button secondary" disabled={actual>=max} onClick={()=>cambiar('p',String(actual+1))}>Siguiente →</button></div>
}
function paginaFilas<T>(filas:T[],params:URLSearchParams){const max=Math.max(1,Math.ceil(filas.length/paginaTamano));const p=Math.min(max,Math.max(1,Number(params.get('p'))||1));return filas.slice((p-1)*paginaTamano,p*paginaTamano)}
function rutaRegreso(params:URLSearchParams,porDefecto:string){const r=params.get('return');return r&&r.startsWith('/')&&!r.startsWith('//')&&(/^\/(clases|grabaciones|notificaciones)(\?|$)/.test(r)||/^\/cursos\/[a-f\d-]+(\?|$)/i.test(r))?r:porDefecto}
function estadoClase(c:Clase){return c.estado==='CANCELADA'?'Cancelada':c.modificado_en?'Modificada':'Programada'}

function CrearClase({cursos}:{cursos:Curso[]}){
  const qc=useQueryClient();const [asignatura,setAsignatura]=useState('');const [grado,setGrado]=useState('');const [seccion,setSeccion]=useState('');
  const [tema,setTema]=useState('');const [inicio,setInicio]=useState('');const [fin,setFin]=useState('');const [enlace,setEnlace]=useState('');const [hecho,setHecho]=useState(false);
  const grados=unico(cursos.filter(c=>c.asignatura===asignatura).map(c=>c.grado));
  const secciones=unico(cursos.filter(c=>c.asignatura===asignatura&&c.grado===grado).map(c=>c.seccion));
  const curso=cursos.find(c=>c.asignatura===asignatura&&c.grado===grado&&c.seccion===seccion);
  const mut=useMutation({mutationFn:()=>post(`/cursos/${curso!.id}/clases`,{tema,inicio:new Date(inicio).toISOString(),fin:new Date(fin).toISOString(),enlace}),onSuccess:()=>{qc.invalidateQueries({queryKey:['clases-global']});setTema('');setEnlace('');setHecho(true)}});
  return <form className="panel form" onSubmit={e=>{e.preventDefault();setHecho(false);mut.mutate()}}><h2>Programar clase</h2><div className="filter-grid">
    <label>Curso<select required value={asignatura} onChange={e=>{setAsignatura(e.target.value);setGrado('');setSeccion('')}}><option value="">Seleccionar curso</option>{unico(cursos.map(c=>c.asignatura)).map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Grado<select required value={grado} disabled={!asignatura} onChange={e=>{setGrado(e.target.value);setSeccion('')}}><option value="">Seleccionar grado</option>{grados.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Sección<select required value={seccion} disabled={!grado} onChange={e=>setSeccion(e.target.value)}><option value="">Seleccionar sección</option>{secciones.map(x=><option key={x}>{x}</option>)}</select></label>
  </div><div className="form-grid"><label>Tema<input required maxLength={200} value={tema} onChange={e=>setTema(e.target.value)}/></label><label>Enlace Zoom<input required type="url" placeholder="https://zoom.us/j/…" value={enlace} onChange={e=>setEnlace(e.target.value)}/></label><label>Inicio<input required type="datetime-local" value={inicio} onChange={e=>setInicio(e.target.value)}/></label><label>Fin<input required type="datetime-local" value={fin} onChange={e=>setFin(e.target.value)}/></label></div>
    {inicio&&fin&&new Date(fin)<=new Date(inicio)&&<p className="error">El fin debe ser posterior al inicio.</p>}{mut.error&&<p className="error">{errorDe(mut.error)}</p>}{hecho&&<p className="success">Clase programada y estudiantes notificados.</p>}<button className="button primary" disabled={!curso||!tema||!inicio||!fin||new Date(fin)<=new Date(inicio)||mut.isPending}>Crear clase</button>
  </form>
}

export function ClasesListado({perfil}:{perfil:Perfil}){
  const {params,cambiar}=useFiltros();const {data:clases=[],isLoading,error}=useQuery<Clase[]>({queryKey:['clases-global'],queryFn:()=>api('/clases'),refetchInterval:30000});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos'),enabled:perfil.tipo==='DOCENTE'});
  const filas=useMemo(()=>clases.filter(c=>(!params.get('q')||c.tema.toLowerCase().includes(params.get('q')!.toLowerCase()))&&(!params.get('asignatura')||c.asignatura===params.get('asignatura'))&&(!params.get('grado')||c.grado===params.get('grado'))&&(!params.get('seccion')||c.seccion===params.get('seccion'))&&(!params.get('fecha')||local(c.inicio).slice(0,10)===params.get('fecha'))&&(!params.get('estado')||c.estado===params.get('estado'))),[clases,params]);
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>Clases virtuales</h1><p>{perfil.tipo==='DOCENTE'?'Programa y administra tus clases asignadas.':'Consulta tus clases y el enlace vigente de Zoom.'}</p></div></div>
    {perfil.tipo==='DOCENTE'&&<CrearClase cursos={cursos}/>}
    <Filtros params={params} cambiar={cambiar} asignaturas={unico(clases.map(c=>c.asignatura||''))} grados={unico(clases.map(c=>c.grado||''))} secciones={unico(clases.map(c=>c.seccion||''))} estados={['PROGRAMADA','CANCELADA']}/>
    {isLoading?<p className="muted">Cargando clases…</p>:error?<div className="alert">{errorDe(error)}</div>:!filas.length?<div className="empty">No hay clases para estos filtros.</div>:<div className="list">{paginaFilas(filas,params).map(c=><div className="list-row stack-mobile" key={c.id}><div><strong>{c.tema}</strong><span>{c.asignatura} · {c.grado} {c.seccion} · {fecha(c.inicio)}</span></div><span className={'badge '+c.estado.toLowerCase()}>{estadoClase(c)}</span><div className="row-actions">{perfil.tipo==='ESTUDIANTE'&&c.estado==='PROGRAMADA'&&c.enlace_actual&&<a className="button secondary" href={c.enlace_actual} target="_blank" rel="noopener noreferrer">Abrir Zoom ↗</a>}<Link className="button secondary" to={`/clases/${c.id}?return=${encodeURIComponent('/clases'+window.location.search)}`}>Ver detalle</Link></div></div>)}</div>}
    <Paginacion total={filas.length} params={params} cambiar={cambiar}/>
  </>
}

function EditarClase({clase}:{clase:Clase}){
  const qc=useQueryClient();const [tema,setTema]=useState(clase.tema);const [inicio,setInicio]=useState(local(clase.inicio));const [fin,setFin]=useState(local(clase.fin));const [enlace,setEnlace]=useState(clase.enlace_actual||'');
  useEffect(()=>{setTema(clase.tema);setInicio(local(clase.inicio));setFin(local(clase.fin));setEnlace(clase.enlace_actual||'')},[clase]);
  const mut=useMutation({mutationFn:()=>put(`/clases/${clase.id}`,{tema,inicio:new Date(inicio).toISOString(),fin:new Date(fin).toISOString(),enlace}),onSuccess:()=>{qc.invalidateQueries({queryKey:['clase',clase.id]});qc.invalidateQueries({queryKey:['clases-global']});qc.invalidateQueries({queryKey:['historial-clase',clase.id]});qc.invalidateQueries({queryKey:['enlaces',clase.id]})}});
  return <form className="panel form" onSubmit={e=>{e.preventDefault();mut.mutate()}}><h2>Editar clase</h2><div className="form-grid"><label>Tema<input required maxLength={200} value={tema} onChange={e=>setTema(e.target.value)}/></label><label>Enlace Zoom<input required type="url" value={enlace} onChange={e=>setEnlace(e.target.value)}/></label><label>Inicio<input required type="datetime-local" value={inicio} onChange={e=>setInicio(e.target.value)}/></label><label>Fin<input required type="datetime-local" value={fin} onChange={e=>setFin(e.target.value)}/></label></div>{mut.error&&<p className="error">{errorDe(mut.error)}</p>}<button className="button primary" disabled={mut.isPending||new Date(fin)<=new Date(inicio)}>Guardar cambios</button></form>
}
function EnlaceZoom({clase}:{clase:Clase}){
  const qc=useQueryClient();const [enlace,setEnlace]=useState('');const mut=useMutation({mutationFn:()=>post(`/clases/${clase.id}/enlaces`,{enlace}),onSuccess:()=>{qc.invalidateQueries({queryKey:['clase',clase.id]});qc.invalidateQueries({queryKey:['enlaces',clase.id]});setEnlace('')}});
  return <form className="panel form" onSubmit={e=>{e.preventDefault();mut.mutate()}}><h2>Continuar con otra reunión Zoom</h2><p>Se mantiene la misma clase académica y se conserva el historial de enlaces.</p><label>Nuevo enlace<input required type="url" placeholder="https://zoom.us/j/…" value={enlace} onChange={e=>setEnlace(e.target.value)}/></label>{mut.error&&<p className="error">{errorDe(mut.error)}</p>}<button className="button secondary" disabled={mut.isPending}>Actualizar enlace vigente</button></form>
}

function RegistrarVideo({clases,claseFija}:{clases:Clase[];claseFija?:string}){
  const qc=useQueryClient();const [claseId,setClaseId]=useState(claseFija||'');const [orden,setOrden]=useState(1);const [url,setUrl]=useState('');const [publicar,setPublicar]=useState(true);const [hecho,setHecho]=useState(false);
  const clase=clases.find(c=>c.id===(claseFija||claseId));const canon=normalizarYoutube(url);
  const mut=useMutation({mutationFn:()=>post('/grabaciones',{claseId:clase!.id,orden,url:canon,publicar}),onSuccess:()=>{qc.invalidateQueries({queryKey:['grabaciones-global']});qc.invalidateQueries({queryKey:['grabaciones-clase',clase?.id]});setUrl('');setOrden(x=>x+1);setHecho(true)}});
  return <form className="panel form" onSubmit={e=>{e.preventDefault();setHecho(false);mut.mutate()}}><h2>Registrar video de YouTube</h2>{!claseFija&&<label>Clase académica<select required value={claseId} onChange={e=>{setClaseId(e.target.value);setOrden(1)}}><option value="">Seleccionar clase</option>{clases.filter(c=>c.estado==='PROGRAMADA').map(c=><option key={c.id} value={c.id}>{c.tema} · {c.asignatura} · {c.grado} {c.seccion} · {fecha(c.inicio)}</option>)}</select></label>}{clase&&<p className="muted">{clase.asignatura} · {clase.grado} {clase.seccion} · {fecha(clase.inicio)}</p>}<div className="form-grid"><label>Enlace del video<input required type="url" value={url} onChange={e=>setUrl(e.target.value)} placeholder="https://www.youtube.com/watch?v=…"/></label><label>Número de parte<input required type="number" min={1} value={orden} onChange={e=>setOrden(Number(e.target.value))}/></label></div><label className="check"><input type="checkbox" checked={publicar} onChange={e=>setPublicar(e.target.checked)}/> Publicar para estudiantes</label>{url&&!canon&&<p className="error">Introduce una URL oficial de video de YouTube.</p>}{mut.error&&<p className="error">{errorDe(mut.error)}</p>}{hecho&&<p className="success">Enlace registrado.</p>}<button className="button primary" disabled={!clase||!canon||orden<1||mut.isPending}>Registrar parte</button></form>
}
function Video({g,editable}:{g:Grabacion;editable:boolean}){
  const qc=useQueryClient();const [editando,setEditando]=useState(false);const [url,setUrl]=useState(g.url);const canon=normalizarYoutube(url);
  const editar=useMutation({mutationFn:()=>put(`/grabaciones/${g.id}`,{url:canon}),onSuccess:()=>{qc.invalidateQueries({queryKey:['grabaciones-global']});qc.invalidateQueries({queryKey:['grabaciones-clase',g.clase_id]});qc.invalidateQueries({queryKey:['grabaciones-admin']});setEditando(false)}});
  const estado=useMutation({mutationFn:()=>put(`/grabaciones/${g.id}/estado`,{publicar:g.estado!=='PUBLICADA'}),onSuccess:()=>{qc.invalidateQueries({queryKey:['grabaciones-global']});qc.invalidateQueries({queryKey:['grabaciones-clase',g.clase_id]});qc.invalidateQueries({queryKey:['grabaciones-admin']})}});
  return <div className="video-row"><strong>Parte {g.orden}</strong><span className={'badge '+g.estado.toLowerCase()}>{g.estado==='PUBLICADA'?'Publicada':'Oculta'}</span><div className="row-actions"><a className="button secondary" href={g.url} target="_blank" rel="noopener noreferrer">Ver en YouTube ↗</a>{editable&&<><button className="button secondary" onClick={()=>setEditando(!editando)}>Editar enlace</button><button className="button secondary" disabled={estado.isPending} onClick={()=>estado.mutate()}>{g.estado==='PUBLICADA'?'Ocultar':'Publicar'}</button></>}</div>{editando&&<form className="form video-edit" onSubmit={e=>{e.preventDefault();editar.mutate()}}><label>Enlace YouTube<input type="url" required value={url} onChange={e=>setUrl(e.target.value)}/></label>{url&&!canon&&<small className="error">URL de video no válida.</small>}<button className="button primary" disabled={!canon||editar.isPending}>Guardar enlace</button></form>}{(editar.error||estado.error)&&<p className="error">{errorDe(editar.error||estado.error)}</p>}</div>
}

export function ClaseDetalle({perfil}:{perfil:Perfil}){
  const {claseId}=useParams();const [params]=useSearchParams();const qc=useQueryClient();
  const {data:clase,isLoading,error}=useQuery<Clase>({queryKey:['clase',claseId],queryFn:()=>api(`/clases/${claseId}`),enabled:!!claseId});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});const curso=cursos.find(c=>c.id===clase?.curso_id);
  const {data:videos=[]}=useQuery<Grabacion[]>({queryKey:['grabaciones-clase',claseId],queryFn:()=>api(`/grabaciones/clase/${claseId}`),enabled:!!claseId});
  const {data:historial=[]}=useQuery<Array<{numero:number;enlace:string;actualizado_en:string}>>({queryKey:['enlaces',claseId],queryFn:()=>api(`/clases/${claseId}/enlaces`),enabled:!!claseId&&perfil.tipo==='DOCENTE'});
  const {data:cambios=[]}=useQuery<Array<{accion:string;tema:string;inicio:string;fin:string;registrado_en:string}>>({queryKey:['historial-clase',claseId],queryFn:()=>api(`/clases/${claseId}/historial`),enabled:!!claseId});
  const cancelar=useMutation({mutationFn:()=>post(`/clases/${claseId}/cancelar`,{}),onSuccess:()=>{qc.invalidateQueries({queryKey:['clase',claseId]});qc.invalidateQueries({queryKey:['clases-global']});qc.invalidateQueries({queryKey:['historial-clase',claseId]})}});
  const regreso=rutaRegreso(params,clase?`/cursos/${clase.curso_id}`:'/clases');
  if(isLoading)return <p className="muted">Cargando clase…</p>;if(error||!clase)return <div className="alert">{errorDe(error||'Clase no encontrada')}</div>;
  return <><div className="page-head"><div><Link className="back" to={regreso}>← Volver al listado</Link><span className="eyebrow">CLASE VIRTUAL</span><h1>{clase.tema}</h1><p>{curso?.asignatura} · {curso?.grado} {curso?.seccion} · {fecha(clase.inicio)} a {fecha(clase.fin)}</p></div><span className={'badge '+clase.estado.toLowerCase()}>{estadoClase(clase)}</span></div>
    <div className="panel"><h2>Reunión Zoom</h2>{clase.estado==='CANCELADA'?<p>Esta clase fue cancelada.</p>:clase.enlace_actual?<a className="button primary" href={clase.enlace_actual} target="_blank" rel="noopener noreferrer">Abrir Zoom vigente ↗</a>:<p>No hay enlace vigente.</p>}{clase.enlace_actualizado&&<p className="muted spaced">Enlace actualizado: {fecha(clase.enlace_actualizado)}</p>}</div>
    {perfil.tipo==='DOCENTE'&&clase.estado==='PROGRAMADA'&&<><EditarClase clase={clase}/><EnlaceZoom clase={clase}/><div className="panel"><h2>Cancelar clase</h2><p>La clase permanecerá en el historial y los estudiantes recibirán un aviso.</p><button className="button danger" disabled={cancelar.isPending} onClick={()=>{if(window.confirm('¿Cancelar esta clase y avisar a los estudiantes?'))cancelar.mutate()}}>Cancelar clase</button>{cancelar.error&&<p className="error">{errorDe(cancelar.error)}</p>}</div></>}
    {perfil.tipo==='DOCENTE'&&historial.length>0&&<div className="panel"><h2>Historial de enlaces Zoom</h2><div className="list">{historial.map(h=><div className="video-row" key={h.numero}>Bloque {h.numero} · {fecha(h.actualizado_en)}<a href={h.enlace} target="_blank" rel="noopener noreferrer">Abrir ↗</a></div>)}</div></div>}
    {cambios.length>0&&<div className="panel"><h2>Historial de la clase</h2><div className="list">{cambios.map((h,i)=><div className="video-row" key={i}><strong>{h.accion==='MIGRADA'?'Estado anterior':h.accion==='CREADA'?'Creada':h.accion==='EDITADA'?'Editada':'Cancelada'}</strong><span>{h.tema} · {fecha(h.inicio)} a {fecha(h.fin)}</span><small className="muted">{fecha(h.registrado_en)}</small></div>)}</div></div>}
    <section className="section"><h2>Grabaciones de la clase</h2>{perfil.tipo==='DOCENTE'&&clase.estado==='PROGRAMADA'&&<RegistrarVideo clases={[{...clase,asignatura:curso?.asignatura,grado:curso?.grado,seccion:curso?.seccion}]} claseFija={clase.id}/ >}{!videos.length?<div className="empty">No hay enlaces de YouTube {perfil.tipo==='ESTUDIANTE'?'publicados':'registrados'} para esta clase.</div>:<div className="panel">{videos.map(g=><Video key={g.id} g={g} editable={perfil.tipo==='DOCENTE'}/>)}</div>}</section>
  </>
}

export function GrabacionesListado({perfil}:{perfil:Perfil}){
  const {params,cambiar}=useFiltros();const {data:grabaciones=[],isLoading,error}=useQuery<Grabacion[]>({queryKey:['grabaciones-global'],queryFn:()=>api('/grabaciones'),refetchInterval:30000});
  const {data:clases=[]}=useQuery<Clase[]>({queryKey:['clases-global'],queryFn:()=>api('/clases'),enabled:perfil.tipo==='DOCENTE'});
  const grupos=useMemo(()=>{const m=new Map<string,Grabacion[]>();for(const g of grabaciones){if((params.get('q')&&!g.tema.toLowerCase().includes(params.get('q')!.toLowerCase()))||(params.get('asignatura')&&g.asignatura!==params.get('asignatura'))||(params.get('grado')&&g.grado!==params.get('grado'))||(params.get('seccion')&&g.seccion!==params.get('seccion'))||(params.get('fecha')&&local(g.inicio).slice(0,10)!==params.get('fecha')))continue;const a=m.get(g.clase_id)||[];a.push(g);m.set(g.clase_id,a)}return [...m.values()]},[grabaciones,params]);
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>Grabaciones</h1><p>Videos de clases registrados como enlaces de YouTube.</p></div></div>
    {perfil.tipo==='DOCENTE'&&<RegistrarVideo clases={clases}/>}
    <Filtros params={params} cambiar={cambiar} asignaturas={unico(grabaciones.map(g=>g.asignatura))} grados={unico(grabaciones.map(g=>g.grado))} secciones={unico(grabaciones.map(g=>g.seccion))} conTitulo/>
    {isLoading?<p className="muted">Cargando grabaciones…</p>:error?<div className="alert">{errorDe(error)}</div>:!grupos.length?<div className="empty">No hay grabaciones para estos filtros.</div>:<div className="list">{paginaFilas(grupos,params).map(partes=>{const g=partes[0];return <div className="panel" key={g.clase_id}><div className="section-head"><div><h2>{g.tema}</h2><p className="muted">{g.asignatura} · {g.grado} {g.seccion} · {fecha(g.inicio)} · {g.docente}</p></div><Link className="text-link" to={`/clases/${g.clase_id}?return=${encodeURIComponent('/grabaciones'+window.location.search)}`}>Ver clase →</Link></div>{partes.map(parte=><Video key={parte.id} g={parte} editable={perfil.tipo==='DOCENTE'}/>)}</div>})}</div>}
    <Paginacion total={grupos.length} params={params} cambiar={cambiar}/>
  </>
}

export function AdminGrabaciones(){
  const {data:grabaciones=[],isLoading,error}=useQuery<Grabacion[]>({queryKey:['grabaciones-admin'],queryFn:()=>api('/grabaciones/administracion')});
  return <><div className="page-head"><div><span className="eyebrow">ADMINISTRACIÓN</span><h1>Referencias de YouTube</h1><p>Edita, publica u oculta referencias. El video original se administra fuera del aula.</p></div></div>{isLoading?<p className="muted">Cargando…</p>:error?<div className="alert">{errorDe(error)}</div>:!grabaciones.length?<div className="empty">No hay referencias de YouTube registradas.</div>:<div className="list">{grabaciones.map(g=><div className="panel" key={g.id}><h2>{g.tema} · Parte {g.orden}</h2><p className="muted">{g.asignatura} · {g.grado} {g.seccion} · {fecha(g.inicio)} · {g.docente}</p><Video g={g} editable/></div>)}</div>}</>
}

export function Notificaciones(){
  const qc=useQueryClient();const {data:avisos=[],isLoading,error}=useQuery<NotificacionClase[]>({queryKey:['notificaciones'],queryFn:()=>api('/notificaciones'),refetchInterval:30000});
  const leer=useMutation({mutationFn:(id:string)=>post(`/notificaciones/${id}/leer`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['notificaciones']})});
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>Notificaciones</h1><p>Avisos de tus clases virtuales.</p></div></div>{isLoading?<p className="muted">Cargando avisos…</p>:error?<div className="alert">{errorDe(error)}</div>:!avisos.length?<div className="empty">No tienes notificaciones.</div>:<div className="list">{avisos.map(n=><div className={'list-row stack-mobile '+(!n.leida_en?'unread':'')} key={n.id}><div><strong>{n.mensaje}</strong><span>{n.asignatura} · {n.grado} {n.seccion} · {fecha(n.creada_en)}</span></div><div className="row-actions"><Link className="button secondary" to={`/clases/${n.clase_id}?return=${encodeURIComponent('/notificaciones')}`}>Ver clase</Link>{!n.leida_en&&<button className="button secondary" disabled={leer.isPending} onClick={()=>leer.mutate(n.id)}>Marcar como leída</button>}</div></div>)}</div>}{leer.error&&<p className="error">{errorDe(leer.error)}</p>}</>
}
