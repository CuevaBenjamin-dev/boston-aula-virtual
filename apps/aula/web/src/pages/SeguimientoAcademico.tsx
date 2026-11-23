import {useRef,useState} from 'react';
import {Link} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,post,put,type Entrega,type Nota,type Seguimiento,type Tarea} from '../api';
import {ArchivosDe} from './AdjuntosAcademicos';
import {fecha} from './AcademicoComun';

function Calificar({tarea,estudiante,entregaVersion}:{tarea:string;estudiante:string;entregaVersion:number}){
  const [abierto,setAbierto]=useState(false),[valor,setValor]=useState(''),[comentario,setComentario]=useState(''),[edicion,setEdicion]=useState<Nota|null>(null),[verHistorial,setVerHistorial]=useState(false),qc=useQueryClient();
  const {data:t}=useQuery<Tarea>({queryKey:['tarea',tarea],queryFn:()=>api(`/tareas/${tarea}`)});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',t?.curso_id],queryFn:()=>api(`/cursos/${t?.curso_id}/notas`),enabled:!!t?.curso_id});
  const actual=notas.find(n=>n.tarea_id===tarea&&n.estudiante_erp_id===estudiante&&n.entrega_version===entregaVersion);
  const vigente=edicion||actual;
  const hayBorrador=vigente?.estado==='BORRADOR'||vigente?.valor_borrador!=null;
  const valorGuardado=vigente?.estado==='BORRADOR'?vigente.valor:vigente?.valor_borrador;
  const comentarioGuardado=vigente?.estado==='BORRADOR'?vigente.comentario:vigente?.comentario_borrador;
  const borradorSinCambios=hayBorrador&&Number(valor)===valorGuardado&&comentario===(comentarioGuardado??'');
  const {data:historial=[]}=useQuery<{id:string;valor_anterior:number|null;valor_nuevo:number;comentario:string|null;instante:string}[]>({queryKey:['historial-nota',actual?.id],queryFn:()=>api(`/notas/${actual?.id}/historial`),enabled:verHistorial&&!!actual?.id});
  const refrescar=()=>{qc.invalidateQueries({queryKey:['notas',t?.curso_id]});qc.invalidateQueries({queryKey:['seguimiento',tarea]});qc.invalidateQueries({queryKey:['tarea',tarea]});qc.invalidateQueries({queryKey:['tareas',t?.curso_id]});qc.invalidateQueries({queryKey:['vista-global']});qc.invalidateQueries({queryKey:['historial-nota',actual?.id]})};
  const guardar=useMutation({mutationFn:()=>put<Nota>(`/tareas/${tarea}/calificacion/borrador`,{estudianteId:estudiante,valor:Number(valor),comentario,version:vigente?.version||0,entregaVersion}),onSuccess:n=>{setEdicion(n);refrescar()}});
  const publicar=useMutation({mutationFn:()=>post<Nota>(`/tareas/${tarea}/calificacion/publicar`,{estudianteId:estudiante,entregaVersion,version:vigente?.version,operacionId:crypto.randomUUID()}),onSuccess:n=>{setEdicion(n);setAbierto(false);refrescar()}});
  const abrir=()=>{if(!abierto){setEdicion(null);setValor(String(actual?.valor_borrador??actual?.valor??''));setComentario(actual?.comentario_borrador??actual?.comentario??'')}setAbierto(!abierto)};
  return <div><button className="button secondary" onClick={abrir}>{actual?.estado==='PUBLICADA'?`Nota ${actual.valor}/${t?.escala_maxima} · ${hayBorrador?'Corrección en borrador':'Corregir'}`:actual?'Borrador de nota · Continuar':'Calificar'}</button>{abierto&&<div className="form compact"><label>Nota sobre {t?.escala_maxima}<input type="number" min="0" max={Math.min(20,t?.escala_maxima||20)} step="0.01" value={valor} onChange={e=>setValor(e.target.value)}/></label><label>Observaciones<input value={comentario} onChange={e=>setComentario(e.target.value)}/></label><div className="row-actions"><button type="button" className="button secondary" disabled={guardar.isPending||publicar.isPending||valor===''||!/^\d+(\.\d{1,2})?$/.test(valor)} onClick={()=>guardar.mutate()}>Guardar borrador</button>{hayBorrador&&<button type="button" className="button primary" disabled={!borradorSinCambios||publicar.isPending||guardar.isPending} onClick={()=>publicar.mutate()}>Publicar nota</button>}</div>{guardar.error&&<small className="error">{String(guardar.error)}</small>}{publicar.error&&<small className="error">{String(publicar.error)}</small>}{vigente?.estado==='PUBLICADA'&&vigente.valor_borrador!=null&&<small>La nota publicada sigue visible para el estudiante hasta publicar la corrección.</small>}</div>}{actual?.estado==='PUBLICADA'&&<><button type="button" className="text-link" onClick={()=>setVerHistorial(!verHistorial)}>{verHistorial?'Ocultar historial':'Ver historial de notas'}</button>{verHistorial&&<div className="list">{historial.map(h=><p key={h.id}>{fecha(h.instante)} · {h.valor_anterior==null?'Publicada':'Corregida'}: {h.valor_nuevo}/20{h.comentario?` · ${h.comentario}`:''}</p>)}</div>}</>}</div>;
}

export function SeguimientoTarea({tarea,entregas,cargandoEntregas,errorEntregas}:{tarea:Tarea;entregas:Entrega[];cargandoEntregas:boolean;errorEntregas:Error|null}){
  const [grupo,setGrupo]=useState<'TODOS'|'ENTREGARON'|'TARDIAS'|'PENDIENTES'|'REVISION'>('TODOS');
  const [seleccionado,setSeleccionado]=useState<string|null>(null);
  const [altoReservado,setAltoReservado]=useState(0),resultadosRef=useRef<HTMLDivElement>(null);
  const {data:filas=[],error,isLoading:cargando}=useQuery<Seguimiento[]>({queryKey:['seguimiento',tarea.id,grupo],queryFn:()=>api(`/tareas/${tarea.id}/seguimiento?grupo=${grupo}`)});
  const {data:notas=[]}=useQuery<Nota[]>({queryKey:['notas',tarea.curso_id],queryFn:()=>api(`/cursos/${tarea.curso_id}/notas`)});
  const opciones:[typeof grupo,string,number|undefined][]=[['TODOS','Total',tarea.total_estudiantes],['ENTREGARON','Entregaron',tarea.entregados],['TARDIAS','Tardías',tarea.tardias],['PENDIENTES','Pendientes',tarea.pendientes],['REVISION','Por revisar',tarea.pendientes_revision]];
  const filtrar=(siguiente:typeof grupo)=>{
    if(siguiente===grupo)return;
    const altoActual=resultadosRef.current?.getBoundingClientRect().height||0;
    const altoNecesario=window.scrollY+window.innerHeight-(document.documentElement.scrollHeight-altoActual);
    setAltoReservado(Math.max(0,Math.ceil(altoNecesario)+2));
    setSeleccionado(null);
    setGrupo(siguiente);
  };
  return <section className="section tarea-seguimiento-seccion"><h2>Seguimiento de esta versión</h2><div className="row-actions">{opciones.map(([clave,etiqueta,total])=><button key={clave} className={'button '+(grupo===clave?'primary':'secondary')} onClick={()=>filtrar(clave)}>{etiqueta} {total??0}</button>)}</div>
    <div ref={resultadosRef} style={altoReservado?{minHeight:altoReservado}:undefined}>{error?<p className="error">No se pudo consultar el seguimiento. {String(error)}</p>:cargando?<p className="muted">Cargando estudiantes…</p>:!filas.length?<div className="empty">No hay estudiantes en este filtro.</div>:<div className="list">{filas.map(e=>{
      if(!e.entrega_id)return <div className="list-row" key={e.estudiante_erp_id}><div><strong>{e.estudiante}</strong><span>Sin entregar</span></div></div>;
      const abierto=seleccionado===e.estudiante_erp_id;
      const historial=entregas.filter(entrega=>entrega.estudiante_erp_id===e.estudiante_erp_id).sort((a,b)=>b.version-a.version);
      return <div className="student-entry" key={e.estudiante_erp_id}><button type="button" className="student-entry-toggle" aria-expanded={abierto} aria-controls={`detalle-${e.estudiante_erp_id}`} onClick={()=>setSeleccionado(abierto?null:e.estudiante_erp_id)}><span><strong>{e.estudiante}</strong><small>{e.estado_entrega==='TARDIA'?'Entregada tardía':'Entregada'}{e.entregado_en?` · ${fecha(e.entregado_en)}`:''}</small></span><b className="text-link">{abierto?'Ocultar entrega ↑':'Ver entrega ↓'}</b></button>
        {abierto&&<div className="student-entry-detail" id={`detalle-${e.estudiante_erp_id}`}>{errorEntregas?<p className="error">No se pudo consultar la entrega. {String(errorEntregas)}</p>:cargandoEntregas?<p className="muted">Cargando entrega…</p>:!historial.length?<p className="muted">No se encontró el detalle de esta entrega.</p>:<>{historial.map(entrega=><div className="student-delivery" key={entrega.id}><strong>Entrega {entrega.version}</strong><span>{entrega.estado==='TARDIA'?'Entregada tardía':'Entregada'} el {fecha(entrega.entregado_en)} (Lima) · Plazo al recibir: {fecha(entrega.fecha_limite_al_entregar)}</span>{entrega.respuesta&&<p className="prewrap">{entrega.respuesta}</p>}<ArchivosDe padre="entregas" id={entrega.id}/>{notas.filter(n=>n.tarea_id===tarea.id&&n.estudiante_erp_id===e.estudiante_erp_id&&n.entrega_version===entrega.version).map(n=><p key={n.id}>{n.estado==='PUBLICADA'?<Link className="text-link" to={`/notas/${n.id}`}>Nota {n.valor}/{n.escala_maxima} · Ver historial →</Link>:<span>Borrador de nota</span>}</p>)}{entrega.version===e.entrega_version&&<div className="row-actions"><Calificar tarea={tarea.id} estudiante={e.estudiante_erp_id} entregaVersion={entrega.version}/></div>}</div>)}</>}</div>}
      </div>;
    })}</div>}</div>
  </section>;
}
