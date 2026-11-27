import {useEffect,useRef,useState} from 'react';
import {api,post,put,type Perfil} from '../api';
import './asistencia.scss';

type Seccion={seccionId:string;seccion:string;fecha:string;total:number;marcados:number;confirmada:boolean;cerrada:boolean;alertaSinMarcas:boolean};
type Alumno={personaId:string;nombre:string;marca:string|null;borrador:string|null;visible:string;justificada:boolean};
type Jornada={seccionId:string;fecha:string;lectivo:boolean;version:number;confirmada:boolean;cerrada:boolean;alumnos:Alumno[]};
type Hecho={fecha:string;seccion:string;marca:string;visible:string;valido:boolean;registroId:string;puedeJustificar:boolean};
type Stats={presente:number;tardanza:number;tardanzaJustificada:number;falta:number;justificada:number;pendiente:number;marcasValidas:number;diasElegibles:number;presenciaFisica:number|null;cobertura:number|null};
type Justificacion={id:string;registroId:string;fecha:string;marca:string;estado:string;motivo:string;version:number;comentario:string|null};
type Adjunto={id:string;nombre:string;tipo:string;tamano:number};
type Salida={id:string;estado:string;motivo:string;ocurridoEn:string};
const etiqueta=(s:string)=>({PRESENTE:'Presente',TARDANZA:'Tardanza',FALTA:'Falta',PENDIENTE:'Pendiente',JUSTIFICADA:'Justificada',TARDANZA_JUSTIFICADA:'Tardanza justificada'} as Record<string,string>)[s]||s;
const hoyLima=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());

function Docente(){
  const [secciones,setSecciones]=useState<Seccion[]>([]),[seccion,setSeccion]=useState(''),[jornada,setJornada]=useState<Jornada|null>(null);
  const [cambios,setCambios]=useState<Record<string,string>>({}),[estado,setEstado]=useState(''),[error,setError]=useState(''),[publicando,setPublicando]=useState(false);
  const ocupado=useRef<Promise<unknown>|null>(null);
  const jornadaActual=useRef<Jornada|null>(null);
  const cargar=async()=>{const s=await api<Seccion[]>('/asistencia/hoy/secciones');setSecciones(s)};
  useEffect(()=>{void cargar().catch(e=>setError(String(e)))},[]);
  useEffect(()=>{if(!seccion)return;void api<Jornada>(`/asistencia/hoy/secciones/${seccion}`).then(j=>{jornadaActual.current=j;setJornada(j);setCambios({})}).catch(e=>setError(String(e)))},[seccion]);
  const guardarAvance=async(snapshot:Record<string,string>)=>{
    if(ocupado.current)await ocupado.current;
    const actual=jornadaActual.current;
    if(!actual||!Object.keys(snapshot).length)return actual;
    setEstado('Guardando avance privado…');setError('');
    const tarea=put<Jornada>(`/asistencia/hoy/secciones/${seccion}/avance`,
      {version:actual.version,clave:crypto.randomUUID(),marcas:Object.entries(snapshot).map(([personaId,valor])=>({personaId,valor}))});
    ocupado.current=tarea;
    try{const j=await tarea;jornadaActual.current=j;setJornada(j);setCambios(actual=>Object.fromEntries(Object.entries(actual).filter(([id,v])=>snapshot[id]!==v)));
      setEstado('Avance guardado en ERP, aún no publicado.');return j}
    catch(e){setError(String(e));setEstado('No se guardó el avance. Reintenta.');throw e}
    finally{if(ocupado.current===tarea)ocupado.current=null}
  };
  useEffect(()=>{if(!jornada||publicando||!Object.keys(cambios).length)return;
    const t=window.setTimeout(()=>{if(!ocupado.current)void guardarAvance(cambios).catch(()=>{})},1600);
    return()=>window.clearTimeout(t);
  },[cambios,jornada?.version,seccion,publicando]);
  async function publicar(confirmar:boolean){if(!jornada||publicando)return;setPublicando(true);setError('');setEstado('Publicando…');
    try{if(ocupado.current)await ocupado.current;let j=jornadaActual.current??jornada;
      if(Object.keys(cambios).length)j=await guardarAvance(cambios)??j;
      j=await post<Jornada>(`/asistencia/hoy/secciones/${seccion}/publicar`,{version:j.version,clave:crypto.randomUUID(),confirmar});
      jornadaActual.current=j;setJornada(j);setCambios({});setEstado(confirmar?'Lista confirmada.':'Cambios publicados. Puedes volver a editar hoy.');await cargar()}
    catch(e){setError(String(e));setEstado('La publicación no se confirmó. Conserva esta pantalla y reintenta.')}
    finally{setPublicando(false)}
  }
  function todosPresentes(){if(!jornada)return;
    if(jornada.alumnos.some(a=>!a.justificada&&a.marca&&a.marca!=='PRESENTE')&&!window.confirm('Esto reemplazará las marcas no justificadas por Presente. ¿Continuar?'))return;
    setCambios(Object.fromEntries(jornada.alumnos.filter(a=>!a.justificada).map(a=>[a.personaId,'PRESENTE'])));
  }
  return <main className="ast-aula"><header className="ast-portada"><span>CONTROL DIARIO · {hoyLima()}</span><h1>Asistencia</h1><p>Marca el padrón de cualquier sección lectiva. Los avances quedan privados hasta publicar.</p></header>
    {error&&<div className="alert" role="alert">{error}</div>}{estado&&<p role="status">{estado}</p>}
    {!secciones.length?<div className="empty">Hoy no hay secciones lectivas con calendario habilitado.</div>:<div className="ast-aula-grid"><section className="ast-aula-panel"><h2>Secciones</h2>{secciones.map(s=><button disabled={publicando||!!ocupado.current||!!Object.keys(cambios).length} className={'ast-aula-seccion '+(s.seccionId===seccion?'active':'')} key={s.seccionId} onClick={()=>setSeccion(s.seccionId)}><strong>{s.seccion}</strong><span>{s.marcados}/{s.total} · {s.confirmada?'Confirmada':s.alertaSinMarcas?'Sin registro':'Pendiente'}</span></button>)}</section>
      <section className="ast-aula-panel"><h2>{secciones.find(s=>s.seccionId===seccion)?.seccion??'Selecciona una sección'}</h2>{jornada&&<>{!jornada.lectivo?<p>Día no lectivo. Registro bloqueado.</p>:<><p>{jornada.alumnos.length} estudiantes · {jornada.confirmada?'Confirmada':'Registro parcial permitido'}</p><div className="ast-aula-lista">{jornada.alumnos.map(a=><div className="ast-aula-alumno" key={a.personaId}><div><strong>{a.nombre}</strong><small>{etiqueta(a.visible)}{a.borrador?' · Avance privado':''}</small></div><div className="ast-aula-marcas" role="group" aria-label={`Marcar a ${a.nombre}`}>{(['PRESENTE','TARDANZA','FALTA'] as const).map(v=><button type="button" key={v} title={etiqueta(v)} aria-label={`${etiqueta(v)}: ${a.nombre}`} disabled={a.justificada||publicando} className={(cambios[a.personaId]??a.borrador??a.marca)===v?'active':''} onClick={()=>setCambios({...cambios,[a.personaId]:v})}>{v[0]}</button>)}</div></div>)}</div><div className="ast-aula-acciones"><button className="button secondary" disabled={publicando} onClick={todosPresentes}>Todos presentes</button><button className="button primary" disabled={publicando||!jornada.alumnos.length} onClick={()=>void publicar(false)}>Publicar marcas</button><button className="button secondary" disabled={publicando||!jornada.alumnos.length||jornada.alumnos.some(a=>!cambios[a.personaId]&&!a.borrador&&!a.marca)} onClick={()=>void publicar(true)}>Confirmar lista completa</button></div></>}</>}</section></div>}
  </main>;
}

function Estudiante(){
  const [anio,setAnio]=useState(Number(hoyLima().slice(0,4))),[anios,setAnios]=useState<number[]>([]),[hechos,setHechos]=useState<Hecho[]>([]),[stats,setStats]=useState<Stats|null>(null),[solicitudes,setSolicitudes]=useState<Justificacion[]>([]),[salidas,setSalidas]=useState<Salida[]>([]),[cargando,setCargando]=useState(true);
  const [registro,setRegistro]=useState(''),[motivo,setMotivo]=useState(''),[complemento,setComplemento]=useState<Record<string,string>>({}),[adjuntos,setAdjuntos]=useState<Record<string,Adjunto[]>>({}),[error,setError]=useState(''),[aviso,setAviso]=useState('');
  const cargar=async()=>{setCargando(true);const [h,e,j,s]=await Promise.all([
    api<Hecho[]>(`/asistencia/mi-asistencia?anio=${anio}`),api<Stats>(`/asistencia/mis-estadisticas?anio=${anio}`),
    api<Justificacion[]>('/asistencia/mis-justificaciones'),api<Salida[]>('/asistencia/mis-salidas')]);
    setHechos(h);setStats(e);setSolicitudes(j);setSalidas(s);setCargando(false)};
  useEffect(()=>{void api<number[]>('/asistencia/mis-anios').then(a=>{setAnios(a);if(a.length&&!a.includes(anio))setAnio(a[0])}).catch(e=>setError(String(e)))},[]);
  useEffect(()=>{void cargar().catch(e=>{setError(String(e));setCargando(false)})},[anio]);
  async function solicitar(){setError('');try{await post(`/asistencia/justificaciones/${registro}`,{motivo,clave:crypto.randomUUID()});setAviso('Solicitud enviada a Coordinación.');setRegistro('');setMotivo('');await cargar()}catch(e){setError(String(e))}}
  async function complementar(j:Justificacion){setError('');try{await post(`/asistencia/justificaciones/${j.id}/complementar`,{texto:complemento[j.id],version:j.version});setComplemento({...complemento,[j.id]:''});await cargar()}catch(e){setError(String(e))}}
  async function subir(j:Justificacion,archivo:File){setError('');try{const form=new FormData();form.append('archivo',archivo);await api(`/asistencia/justificaciones/${j.id}/adjuntos`,{method:'POST',body:form});setAdjuntos({...adjuntos,[j.id]:await api<Adjunto[]>(`/asistencia/justificaciones/${j.id}/adjuntos`)});await cargar()}catch(e){setError(String(e))}}
  return <main className="ast-aula"><header className="ast-portada"><span>EXPEDIENTE PERSONAL</span><h1>Mi asistencia</h1><p>Consulta tus marcas, justificaciones y salidas. Una justificación no cambia el hecho original.</p></header>
    {error&&<div className="alert" role="alert">{error}</div>}{aviso&&<p role="status">{aviso}</p>}
    <label>Año escolar <select value={anio} onChange={e=>setAnio(Number(e.target.value))}>{[...new Set([anio,...anios])].sort((a,b)=>b-a).map(a=><option key={a}>{a}</option>)}</select></label>
    {cargando&&<p role="status">Consultando expediente de Asistencia…</p>}
    {stats&&<div className="ast-aula-metricas"><span>Presencia física <strong>{stats.presenciaFisica==null?'No disponible':stats.presenciaFisica.toFixed(1)+'%'}</strong></span><span>Cobertura <strong>{stats.cobertura==null?'No disponible':stats.cobertura.toFixed(1)+'%'}</strong></span><span>Marcas / días lectivos <strong>{stats.marcasValidas} / {stats.diasElegibles}</strong></span><span>Pendientes <strong>{stats.pendiente}</strong></span></div>}
    <section className="ast-aula-panel"><h2>Historial de asistencia</h2>{!hechos.length?<div className="empty">No hay marcas publicadas en este año.</div>:<div className="ast-aula-lista">{hechos.map(h=><div className="ast-aula-fila" key={h.registroId}><span>{h.fecha} · {h.seccion}</span><strong>{etiqueta(h.visible)}</strong>{h.puedeJustificar&&!solicitudes.some(j=>j.registroId===h.registroId&&j.estado!=='RECHAZADA')&&<button className="button secondary" onClick={()=>setRegistro(h.registroId)}>Solicitar justificación</button>}</div>)}</div>}</section>
    {registro&&<section className="ast-aula-panel"><h2>Justificar incidencia</h2><label>Motivo<textarea value={motivo} onChange={e=>setMotivo(e.target.value)} /></label><div className="ast-aula-acciones"><button className="button secondary" onClick={()=>setRegistro('')}>Cancelar</button><button className="button primary" disabled={!motivo.trim()} onClick={()=>void solicitar()}>Enviar solicitud</button></div></section>}
    <section className="ast-aula-panel"><h2>Mis justificaciones</h2>{!solicitudes.length?<p>Sin solicitudes.</p>:solicitudes.map(j=><article className="ast-aula-fila" key={j.id}><div><strong>{j.fecha} · {etiqueta(j.marca)} · {j.estado}</strong><p>{j.motivo}</p>{j.comentario&&<p>Resolución: {j.comentario}</p>}{j.estado==='EN_REVISION'&&<><label>Complemento<input value={complemento[j.id]??''} onChange={e=>setComplemento({...complemento,[j.id]:e.target.value})}/></label><button className="button secondary" disabled={!complemento[j.id]?.trim()} onClick={()=>void complementar(j)}>Agregar complemento</button><label>Adjuntar PDF o imagen<input type="file" accept="application/pdf,image/png,image/jpeg" onChange={e=>{const archivo=e.target.files?.[0];if(archivo)void subir(j,archivo)}}/></label></>}<button className="button secondary" type="button" onClick={()=>void api<Adjunto[]>(`/asistencia/justificaciones/${j.id}/adjuntos`).then(a=>setAdjuntos(actual=>({...actual,[j.id]:a}))).catch(e=>setError(String(e)))}>Ver comprobantes</button>{adjuntos[j.id]?.map(a=><a key={a.id} href={`/api/aula/asistencia/adjuntos/${a.id}`}>{a.nombre}</a>)}</div></article>)}</section>
    <section className="ast-aula-panel"><h2>Salidas anticipadas</h2>{!salidas.length?<p>Sin salidas registradas.</p>:salidas.map(s=><p key={s.id}>{s.ocurridoEn} · {s.estado} · {s.motivo}</p>)}</section>
  </main>;
}
export default function AsistenciaAula({perfil}:{perfil:Perfil}){return perfil.tipo==='DOCENTE'?<Docente/>:<Estudiante/>}
