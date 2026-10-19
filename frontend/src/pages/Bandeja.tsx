import {useEffect,useState} from 'react';
import {Link,useSearchParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,post,type Curso,type NotificacionClase,type Perfil} from '../api';

type Destino={id:string;usuario:string;tipo:string};
type Mensaje={id:string;asunto:string;cuerpo:string;emisor_cuenta:string;emisor_usuario:string;creado_en:string;leida_en?:string|null;destinatarios?:string|Array<{usuario:string;leida_en:string|null}>;total_destinatarios?:number};
const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short'});

export default function Bandeja({perfil}:{perfil:Perfil}){
  const qc=useQueryClient(),[params,setParams]=useSearchParams();
  const tab=params.get('tab')==='notificaciones'?'notificaciones':'mensajes';
  const carpeta=params.get('carpeta')==='enviados'?'enviados':'recibidos';
  const seleccionado=params.get('mensaje'),nuevo=params.get('nuevo')==='1';
  const cambiar=(cambios:Record<string,string|null>)=>{const siguiente=new URLSearchParams(params);for(const [clave,valor] of Object.entries(cambios))if(valor)siguiente.set(clave,valor);else siguiente.delete(clave);setParams(siguiente)};
  const {data:mensajes=[],isLoading:mensajesCargando,error:mensajesError}=useQuery<Mensaje[]>({queryKey:['mensajes',carpeta],queryFn:()=>api(`/mensajes?carpeta=${carpeta}`),enabled:tab==='mensajes',refetchInterval:30000});
  const {data:detalle}=useQuery<Mensaje>({queryKey:['mensaje',seleccionado],queryFn:()=>api(`/mensajes/${seleccionado}`),enabled:!!seleccionado&&tab==='mensajes'});
  const {data:avisos=[],isLoading:avisosCargando,error:avisosError}=useQuery<NotificacionClase[]>({queryKey:['notificaciones'],queryFn:()=>api('/notificaciones'),enabled:tab==='notificaciones',refetchInterval:30000});
  const leerMensaje=useMutation({mutationFn:(id:string)=>post(`/mensajes/${id}/leer`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['mensajes']})});
  const leerAviso=useMutation({mutationFn:(id:string)=>post(`/notificaciones/${id}/leer`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['notificaciones']})});
  useEffect(()=>{if(detalle&&carpeta==='recibidos'&&!detalle.leida_en&&!leerMensaje.isPending)leerMensaje.mutate(detalle.id)},[detalle?.id]);
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>Bandeja de mensajes</h1><p>Mensajes entre cuentas del aula y avisos de cambios académicos.</p></div>{tab==='mensajes'&&!nuevo&&<button className="button primary" onClick={()=>cambiar({nuevo:'1',mensaje:null,para:null})}>Nuevo mensaje</button>}</div>
    <div className="tabs"><button className={tab==='mensajes'?'active':''} onClick={()=>cambiar({tab:'mensajes',mensaje:null,nuevo:null})}>Mensajes</button><button className={tab==='notificaciones'?'active':''} onClick={()=>cambiar({tab:'notificaciones',mensaje:null,nuevo:null})}>Notificaciones</button></div>
    {tab==='mensajes'&&(nuevo?<Componer perfil={perfil} cerrar={()=>cambiar({nuevo:null,carpeta:'enviados',para:null})} responderA={params.get('para')}/>:seleccionado&&detalle?<div className="panel"><button className="text-link" onClick={()=>cambiar({mensaje:null})}>← Volver a mensajes</button><h2 className="spaced">{detalle.asunto}</h2><p className="muted">De {detalle.emisor_usuario} · {fecha(detalle.creado_en)}</p>{carpeta==='enviados'&&Array.isArray(detalle.destinatarios)&&<p className="muted">Para: {detalle.destinatarios.map(d=>d.usuario).join(', ')}</p>}<p className="prewrap">{detalle.cuerpo}</p>{carpeta==='recibidos'&&<button className="button secondary" onClick={()=>cambiar({nuevo:'1',mensaje:null,para:detalle.emisor_cuenta})}>Responder</button>}</div>:<><div className="tabs small"><button className={carpeta==='recibidos'?'active':''} onClick={()=>cambiar({carpeta:'recibidos',mensaje:null})}>Recibidos</button><button className={carpeta==='enviados'?'active':''} onClick={()=>cambiar({carpeta:'enviados',mensaje:null})}>Enviados</button></div>{mensajesCargando?<p className="muted">Cargando mensajes…</p>:mensajesError?<div className="alert">{String(mensajesError)}</div>:!mensajes.length?<div className="empty">No hay mensajes en esta carpeta.</div>:<div className="list">{mensajes.map(m=><button className={'list-row message-row '+(carpeta==='recibidos'&&!m.leida_en?'unread':'')} key={m.id} onClick={()=>cambiar({mensaje:m.id})}><div><strong>{m.asunto}</strong><span>{carpeta==='recibidos'?`De ${m.emisor_usuario}`:`Para ${m.destinatarios}`} · {fecha(m.creado_en)}</span><p>{m.cuerpo}</p></div><span>Leer →</span></button>)}</div>}</>)}
    {tab==='notificaciones'&&(avisosCargando?<p className="muted">Cargando notificaciones…</p>:avisosError?<div className="alert">{String(avisosError)}</div>:!avisos.length?<div className="empty">No hay notificaciones.</div>:<div className="list">{avisos.map(n=><div className={'list-row stack-mobile '+(!n.leida_en?'unread':'')} key={n.id}><div><strong>{n.mensaje}</strong><span>{n.asignatura} · {n.grado} {n.seccion} · {fecha(n.creada_en)}{n.nuevas_entregas?` · ${n.nuevas_entregas} ${n.nuevas_entregas===1?'entrega nueva':'entregas nuevas'}`:''}</span></div><div className="row-actions">{n.tarea_id&&n.puede_abrir!==false&&<Link className="button secondary" onClick={()=>{if(!n.leida_en)leerAviso.mutate(n.id)}} to={`/tareas/${n.tarea_id}?return=${encodeURIComponent('/notificaciones?tab=notificaciones')}`}>Ver tarea</Link>}{n.clase_id&&perfil.tipo!=='ADMINISTRADOR'&&<Link className="button secondary" onClick={()=>{if(!n.leida_en)leerAviso.mutate(n.id)}} to={`/clases/${n.clase_id}?return=${encodeURIComponent('/notificaciones?tab=notificaciones')}`}>Ver clase</Link>}{!n.leida_en&&<button className="button secondary" disabled={leerAviso.isPending} onClick={()=>leerAviso.mutate(n.id)}>Marcar como leída</button>}</div></div>)}</div>)}
  </>;
}

function Componer({perfil,cerrar,responderA}:{perfil:Perfil;cerrar:()=>void;responderA:string|null}){
  const qc=useQueryClient();
  const [asunto,setAsunto]=useState(''),[cuerpo,setCuerpo]=useState(''),[elegidos,setElegidos]=useState<string[]>(responderA?[responderA]:[]),[buscar,setBuscar]=useState(''),[cursoId,setCursoId]=useState(''),[cursoCompleto,setCursoCompleto]=useState(false);
  const {data:directorio=[],error}=useQuery<Destino[]>({queryKey:['directorio-mensajes'],queryFn:()=>api('/mensajes/directorio')});
  const {data:cursos=[]}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos'),enabled:perfil.tipo==='DOCENTE'});
  const enviar=useMutation({mutationFn:()=>post('/mensajes',{asunto,cuerpo,destinatarios:elegidos,cursoId:cursoCompleto?cursoId:null,cursoCompleto}),onSuccess:()=>{qc.invalidateQueries({queryKey:['mensajes']});cerrar()}});
  const opciones=directorio.filter(d=>`${d.usuario} ${d.tipo}`.toLocaleLowerCase('es').includes(buscar.toLocaleLowerCase('es')));
  return <form className="panel form" onSubmit={e=>{e.preventDefault();enviar.mutate()}}><div className="section-head"><h2>Nuevo mensaje</h2><button type="button" className="text-link" onClick={cerrar}>Cerrar</button></div>
    <label>Destinatarios<input value={buscar} onChange={e=>setBuscar(e.target.value)} placeholder="Buscar usuario o rol"/></label>
    <div className="recipient-list">{error?<p className="error">No se pudo cargar el directorio.</p>:opciones.length?opciones.map(d=><label className="recipient" key={d.id}><input type="checkbox" checked={elegidos.includes(d.id)} onChange={e=>setElegidos(actual=>e.target.checked?[...actual,d.id]:actual.filter(id=>id!==d.id))}/><span>{d.usuario} · {d.tipo.toLowerCase()}</span></label>):<p className="muted">No hay usuarios con esa búsqueda.</p>}</div>
    {perfil.tipo==='DOCENTE'&&<label className="check"><input type="checkbox" checked={cursoCompleto} onChange={e=>setCursoCompleto(e.target.checked)}/> Enviar también a toda un aula</label>}
    {cursoCompleto&&<label>Aula<select value={cursoId} required onChange={e=>setCursoId(e.target.value)}><option value="">Seleccionar curso</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.grado} {c.seccion}</option>)}</select></label>}
    <label>Asunto<input value={asunto} onChange={e=>setAsunto(e.target.value)} maxLength={200} required/></label><label>Mensaje<textarea rows={7} value={cuerpo} onChange={e=>setCuerpo(e.target.value)} maxLength={10000} required/></label>
    {enviar.error&&<p className="error">{String(enviar.error)}</p>}
    <button className="button primary" disabled={enviar.isPending||(!elegidos.length&&!cursoCompleto)}>Enviar mensaje</button>
  </form>;
}
