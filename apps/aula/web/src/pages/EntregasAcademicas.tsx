import {useEffect,useRef,useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,del,post,put,subirArchivo,type Archivo,type Entrega,type PreparacionEntrega,type Tarea} from '../api';

const archivoEntregaPermitido='.pdf,.docx,.pptx,.xlsx,.txt,.jpg,.jpeg,.png';
const mega=1024*1024;
function validarArchivosEntrega(actuales:Archivo[],pendientes:File[],nuevos:File[],sustituido?:Archivo){
  if(actuales.length+pendientes.length+nuevos.length-(sustituido?1:0)>4)return 'Máximo cuatro archivos por intento.';
  if(nuevos.some(f=>f.size<1||f.size>25*mega||!archivoEntregaPermitido.split(',').includes('.'+f.name.split('.').pop()?.toLowerCase())))return 'Cada archivo debe ser permitido y no superar 25 MB.';
  const total=actuales.reduce((n,f)=>n+(f.id===sustituido?.id?0:f.tamano),0)+pendientes.reduce((n,f)=>n+f.size,0)+nuevos.reduce((n,f)=>n+f.size,0);
  return total>100*mega?'El total no puede superar 100 MB.':null;
}

export function PrepararEntrega({tarea,presentada,confirmada}:{tarea:Tarea;presentada:boolean;confirmada:(id:string)=>void}){
  const qc=useQueryClient();
  const {data:estado,isLoading,error}=useQuery<PreparacionEntrega>({queryKey:['preparacion',tarea.id],queryFn:()=>api(`/tareas/${tarea.id}/preparacion`)});
  const [idLocal,setIdLocal]=useState<string|null>(null),[respuesta,setRespuesta]=useState(''),[guardada,setGuardada]=useState(''),[pendientes,setPendientes]=useState<File[]>([]),[progreso,setProgreso]=useState(0),[errorArchivo,setErrorArchivo]=useState(''),[subiendo,setSubiendo]=useState(false);
  const operacion=useRef(crypto.randomUUID());
  const cargadas=useRef(new Map<File,string>());
  const inicializada=useRef<string|null>(null);
  const id=(inicializada.current===tarea.id?idLocal:null)||estado?.id||null;
  const {data:archivos=[]}=useQuery<Archivo[]>({queryKey:['archivos','preparaciones',id],queryFn:()=>api(`/preparaciones/${id}/archivos`),enabled:!!id});
  useEffect(()=>{if(!estado||inicializada.current===tarea.id)return;inicializada.current=tarea.id;setIdLocal(estado.id||null);setRespuesta(estado.respuesta||'');setGuardada(estado.respuesta||'');setPendientes([]);setErrorArchivo('')},[tarea.id,estado]);
  const iniciar=useMutation({mutationFn:()=>post<PreparacionEntrega>(`/tareas/${tarea.id}/preparacion`,{tareaVersion:tarea.version}),onSuccess:p=>{setIdLocal(p.id||null);qc.setQueryData(['preparacion',tarea.id],{...estado,...p})}});
  const guardar=useMutation({mutationFn:async()=>{
    let destino=id;
    if(!destino){const p=await post<PreparacionEntrega>(`/tareas/${tarea.id}/preparacion`,{tareaVersion:tarea.version});destino=p.id||null;setIdLocal(destino);qc.setQueryData(['preparacion',tarea.id],{...estado,...p})}
    if(!destino)throw new Error('No se pudo crear la preparación');
    const p=await put<PreparacionEntrega>(`/preparaciones/${destino}`,{respuesta,tareaVersion:tarea.version});
    setGuardada(respuesta);qc.setQueryData(['preparacion',tarea.id],{...estado,...p,puede_iniciar:true});
    for(const archivo of pendientes)if(!cargadas.current.has(archivo))
      cargadas.current.set(archivo,await subirArchivo('preparaciones',destino,archivo,setProgreso));
    return destino;
  },onSuccess:destino=>{cargadas.current.clear();setPendientes([]);setProgreso(0);qc.invalidateQueries({queryKey:['archivos','preparaciones',destino]});qc.invalidateQueries({queryKey:['preparacion',tarea.id]})},onError:()=>{setPendientes(antes=>antes.filter(archivo=>!cargadas.current.has(archivo)));cargadas.current.clear();if(id)qc.invalidateQueries({queryKey:['archivos','preparaciones',id]})}});
  const confirmar=useMutation({mutationFn:()=>post<Entrega>(`/preparaciones/${id}/confirmar`,{operacionId:operacion.current}),onSuccess:e=>{confirmada(e.id);setIdLocal(null);setRespuesta('');setGuardada('');operacion.current=crypto.randomUUID();qc.setQueryData(['preparacion',tarea.id],{puede_iniciar:false,siguiente_intento:e.version+1});qc.invalidateQueries({queryKey:['entregas',tarea.id]});qc.invalidateQueries({queryKey:['preparacion',tarea.id]});qc.invalidateQueries({queryKey:['tareas',tarea.curso_id]});qc.invalidateQueries({queryKey:['vista-global']})}});
  async function retirar(archivo:Archivo){try{setErrorArchivo('');await del(`/archivos/${archivo.id}`);qc.invalidateQueries({queryKey:['archivos','preparaciones',id]})}catch(e){setErrorArchivo(String(e))}}
  async function reemplazar(archivo:Archivo,nuevo:File){const fallo=validarArchivosEntrega(archivos,pendientes,[nuevo],archivo);if(fallo){setErrorArchivo(fallo);return}if(!id)return;try{setSubiendo(true);setErrorArchivo('');await subirArchivo('preparaciones',id,nuevo,setProgreso,archivo.id);qc.invalidateQueries({queryKey:['archivos','preparaciones',id]})}catch(e){setErrorArchivo(String(e))}finally{setSubiendo(false);setProgreso(0)}}
  function elegir(nuevos:FileList|null){if(!nuevos)return;const lista=Array.from(nuevos),fallo=validarArchivosEntrega(archivos,pendientes,lista);if(fallo){setErrorArchivo(fallo);return}setErrorArchivo('');setPendientes(antes=>[...antes,...lista])}
  if(isLoading)return <p className="muted">Cargando preparación…</p>;
  if(error)return <p className="error">No se pudo consultar la preparación. {String(error)}</p>;
  if(tarea.estado!=='PUBLICADA'||tarea.tarea_sucesora_id)return <div className="panel">{id?'La preparación quedó guardada, pero esta tarea ya no recibe entregas.':'Esta tarea ya no recibe entregas.'}</div>;
  if(!id&&!estado?.puede_iniciar)return presentada?<div className="panel">Ya realizaste la única reentrega permitida fuera del plazo.</div>:null;
  if(presentada&&!id)return <div className="panel"><button className="button secondary" disabled={iniciar.isPending} onClick={()=>iniciar.mutate()}>Realizar nueva entrega</button>{iniciar.error&&<p className="error">{String(iniciar.error)}</p>}</div>;
  return <div className="panel form"><p>Intento {estado?.intento||estado?.siguiente_intento||1} · La preparación no se considera entregada hasta confirmarla.</p><label>Respuesta escrita (opcional si adjuntas archivos)<textarea rows={5} value={respuesta} onChange={e=>setRespuesta(e.target.value)}/></label>
    <label>Archivos (PDF, DOCX, PPTX, XLSX, TXT, JPG o PNG; máximo 4 archivos, 25 MB cada uno y 100 MB en total)<input type="file" multiple accept={archivoEntregaPermitido} onChange={e=>{elegir(e.target.files);e.target.value=''}}/></label>
    {pendientes.map((f,i)=><div className="row-actions" key={`${f.name}-${i}`}><span>{f.name}</span><button type="button" className="text-link" onClick={()=>setPendientes(antes=>antes.filter((_,indice)=>indice!==i))}>Retirar</button></div>)}
    {archivos.map(f=><div className="row-actions" key={f.id}><span>{f.nombre}</span><label className="text-link">Reemplazar<input type="file" accept={archivoEntregaPermitido} style={{display:'none'}} onChange={e=>{const nuevo=e.target.files?.[0];if(nuevo)reemplazar(f,nuevo);e.target.value=''}}/></label><button type="button" className="text-link" onClick={()=>retirar(f)}>Retirar</button></div>)}
    {progreso>0&&<progress value={progreso} max="100"/>}{errorArchivo&&<small className="error">{errorArchivo}</small>}{guardar.error&&<small className="error">{String(guardar.error)} La preparación guardada se conserva.</small>}{confirmar.error&&<small className="error">{String(confirmar.error)}</small>}
    <div className="row-actions"><button type="button" className="button secondary" disabled={guardar.isPending||confirmar.isPending||subiendo} onClick={()=>guardar.mutate()}>Guardar preparación</button><button type="button" className="button primary" disabled={!id||guardar.isPending||confirmar.isPending||subiendo||pendientes.length>0||respuesta!==guardada} onClick={()=>confirmar.mutate()}>Confirmar entrega</button></div>
  </div>;
}
