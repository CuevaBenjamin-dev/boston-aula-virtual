import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { erp } from '../../shared/api/erp'
import type { Sesion } from '../../shared/api/sesion'
import './asistencia.css'

type Seccion={seccionId:string;seccion:string;fecha:string;total:number;marcados:number;confirmada:boolean;cerrada:boolean;alertaSinMarcas:boolean}
type SeccionOferta={id:string;anio:number;nivel:string;grado:number;nombre:string}
type Alumno={personaId:string;matriculaId:string;nombre:string;marca:string|null;borrador:string|null;visible:string;justificada:boolean;origen:string|null}
type Jornada={seccionId:string;fecha:string;lectivo:boolean;version:number;confirmada:boolean;cerrada:boolean;alertaSinMarcas:boolean;alumnos:Alumno[]}
type Calendario={anio:number;inicio:string;fin:string;diasSemana:number[];habilitadoDesde:string;recordatorioHora:string;version:number}
type Excepcion={id:string;fecha:string;seccion_id:string|null;lectivo:boolean;motivo:string}
type Persona={id:string;nombres:string;apellidos:string;documento?:string}
type Justificacion={id:string;registroId:string;estudianteId:string;nombre:string;marca:string;fecha:string;estado:string;motivo:string;version:number;comentario:string|null}
type Salida={id:string;personaId:string;nombre:string;seccion:string;estado:string;motivo:string;ocurridoEn:string}
type Historial={fecha:string;seccion:string;marca:string;visible:string;origen:string;valido:boolean}
type Estadisticas={presente:number;tardanza:number;tardanzaJustificada:number;falta:number;justificada:number;pendiente:number;marcasValidas:number;diasElegibles:number;presenciaFisica:number|null;cobertura:number|null}
type Pendiente={fecha:string;seccionId:string;seccion:string;sinMarca:number;causa:string;accion:string}
const fechaLima=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
const clave=()=>crypto.randomUUID()
const textoMarca=(valor:string|null)=>({PRESENTE:'Presente',TARDANZA:'Tardanza',FALTA:'Falta',PENDIENTE:'Pendiente',JUSTIFICADA:'Justificada',TARDANZA_JUSTIFICADA:'Tardanza justificada'} as Record<string,string>)[valor??'PENDIENTE']??valor
const tabs=['Control diario','Historial','Justificaciones','Salidas anticipadas','Calendario','Pendientes'] as const

function BuscarPersona({sesion,seleccionar}:{sesion:Sesion;seleccionar:(persona:Persona)=>void}){
  const [q,setQ]=useState(''),[personas,setPersonas]=useState<Persona[]>([]),[error,setError]=useState('')
  useEffect(()=>{if(q.trim().length<2){setPersonas([]);return}
    const t=setTimeout(()=>void erp<{resultados:Persona[]}>(`/personas?buscar=${encodeURIComponent(q)}&tamano=10`,sesion)
      .then(r=>setPersonas(r.resultados)).catch(e=>setError(String(e))),250)
    return()=>clearTimeout(t)
  },[q,sesion])
  return <div className="ast-buscar"><label>Buscar estudiante<input value={q} onChange={e=>setQ(e.target.value)} placeholder="Nombre o documento"/></label>
    {error&&<p role="alert">{error}</p>}{personas.length>0&&<div className="ast-opciones">{personas.map(p=><button key={p.id} type="button" onClick={()=>{seleccionar(p);setQ(`${p.nombres} ${p.apellidos}`);setPersonas([])}}>{p.nombres} {p.apellidos}</button>)}</div>}</div>
}

export function AsistenciaModulo({sesion}:{sesion:Sesion}){
  const contexto = useOutletContext<{ justificacionesPendientes: number } | null>()
  const hayJustificacionesPendientes = sesion.rol === 'COORDINACION' && (contexto?.justificacionesPendientes ?? 0) > 0
  const [tab,setTab]=useState<typeof tabs[number]>('Control diario'),[anio,setAnio]=useState(new Date().getFullYear()),[anios,setAnios]=useState<number[]>([])
  const [fecha,setFecha]=useState(fechaLima()),[secciones,setSecciones]=useState<Seccion[]>([]),[seccionesOferta,setSeccionesOferta]=useState<SeccionOferta[]>([]),[seccion,setSeccion]=useState('')
  const [jornada,setJornada]=useState<Jornada|null>(null),[marcas,setMarcas]=useState<Record<string,string>>({}),[motivo,setMotivo]=useState('')
  const [cal,setCal]=useState<Calendario|null>(null),[inicio,setInicio]=useState(''),[fin,setFin]=useState(''),[dias,setDias]=useState<number[]>([1,2,3,4,5])
  const [horaAviso,setHoraAviso]=useState('16:00')
  const [excepciones,setExcepciones]=useState<Excepcion[]>([]),[excFecha,setExcFecha]=useState(''),[excSeccion,setExcSeccion]=useState(''),[excLectivo,setExcLectivo]=useState(false),[excMotivo,setExcMotivo]=useState('')
  const [persona,setPersona]=useState<Persona|null>(null),[historial,setHistorial]=useState<Historial[]>([]),[estadisticas,setEstadisticas]=useState<Estadisticas|null>(null)
  const [justificaciones,setJustificaciones]=useState<Justificacion[]>([]),[adjuntos,setAdjuntos]=useState<Record<string,Array<{id:string;nombre:string}>>>({}),[salidas,setSalidas]=useState<Salida[]>([]),[pendientes,setPendientes]=useState<Pendiente[]>([])
  const [salidaEstado,setSalidaEstado]=useState('AUTORIZADA'),[salidaMotivo,setSalidaMotivo]=useState('')
  const salidaPendiente=useRef<{firma:string;clave:string}|null>(null)
  const [error,setError]=useState(''),[aviso,setAviso]=useState(''),[ocupado,setOcupado]=useState(false)
  const [pendientesCargando,setPendientesCargando]=useState(false),[pendientesError,setPendientesError]=useState(false)
  const ejecutar=async(fn:()=>Promise<void>)=>{setError('');setAviso('');setOcupado(true);try{await fn()}catch(e){setError(String(e))}finally{setOcupado(false)}}
  useEffect(()=>{void erp<number[]>('/oferta/anios',sesion).then(a=>setAnios(a)).catch(e=>setError(String(e)))},[sesion])
  useEffect(()=>{
    if(tab==='Control diario') void erp<Seccion[]>(`/asistencia/control?fecha=${fecha}&anio=${anio}`,sesion).then(setSecciones).catch(e=>setError(String(e)))
    if(tab==='Calendario') {void erp<Calendario|null>(`/asistencia/calendario/${anio}`,sesion).then(c=>{setCal(c);if(c)setHoraAviso(c.recordatorioHora.slice(0,5))}).catch(e=>setError(String(e)))
      void erp<SeccionOferta[]>(`/oferta/secciones?anio=${anio}`,sesion).then(setSeccionesOferta).catch(e=>setError(String(e)))
      void erp<Excepcion[]>(`/asistencia/excepciones/${anio}`,sesion).then(setExcepciones).catch(e=>setError(String(e)))}
    if(tab==='Justificaciones') void erp<Justificacion[]>(`/asistencia/justificaciones?anio=${anio}`,sesion).then(setJustificaciones).catch(e=>setError(String(e)))
    if(tab==='Salidas anticipadas') void erp<Salida[]>('/asistencia/salidas',sesion).then(setSalidas).catch(e=>setError(String(e)))
    if(tab==='Pendientes') {setPendientesCargando(true);setPendientesError(false);setPendientes([])
      void erp<Pendiente[]>(`/asistencia/pendientes/${anio}`,sesion)
        .then(setPendientes).catch(e=>{setPendientesError(true);setError(String(e))}).finally(()=>setPendientesCargando(false))}
  },[tab,anio,fecha,sesion])
  useEffect(()=>{if(!seccion){setJornada(null);return}void erp<Jornada>(`/asistencia/jornadas/${seccion}?fecha=${fecha}`,sesion).then(j=>{setJornada(j);setMarcas({})}).catch(e=>setError(String(e)))},[seccion,fecha,sesion])
  useEffect(()=>{if(!persona||tab!=='Historial')return;void erp<Historial[]>(`/asistencia/historial/${persona.id}?anio=${anio}`,sesion).then(setHistorial).catch(e=>setError(String(e)))
    void erp<Estadisticas>(`/asistencia/estadisticas/${persona.id}?anio=${anio}`,sesion).then(setEstadisticas).catch(e=>setError(String(e)))},[persona,anio,tab,sesion])
  async function guardar(confirmar:boolean){if(!jornada)return;await ejecutar(async()=>{
    const cambios=Object.entries(marcas).map(([personaId,valor])=>({personaId,valor}))
    let j=jornada
    if(cambios.length){j=await erp<Jornada>(`/asistencia/jornadas/${seccion}/avance?fecha=${fecha}`,sesion,'PUT',{version:j.version,clave:clave(),marcas:cambios});setJornada(j);setMarcas({})}
    j=await erp<Jornada>(`/asistencia/jornadas/${seccion}/publicar?fecha=${fecha}`,sesion,'POST',{version:j.version,clave:clave(),confirmar,motivo})
    setJornada(j);setMarcas({});setAviso(confirmar?'Jornada confirmada.':'Cambios publicados.');
    setSecciones(await erp<Seccion[]>(`/asistencia/control?fecha=${fecha}&anio=${anio}`,sesion))
  })}
  async function crearCalendario(){await ejecutar(async()=>{const c=await erp<Calendario>('/asistencia/calendario',sesion,'POST',{anio,inicio,fin,diasSemana:dias});setCal(c);setAviso('Calendario anual habilitado desde '+c.habilitadoDesde)})}
  async function crearExcepcion(){await ejecutar(async()=>{await erp('/asistencia/excepciones',sesion,'PUT',{fecha:excFecha,seccionId:excSeccion||null,lectivo:excLectivo,motivo:excMotivo});setExcepciones(await erp<Excepcion[]>(`/asistencia/excepciones/${anio}`,sesion));setAviso('Excepción guardada. Las marcas invalidadas conservan auditoría.')})}
  async function cambiarHora(){await ejecutar(async()=>{await erp(`/asistencia/recordatorio/${anio}`,sesion,'PUT',{valor:horaAviso});setCal(await erp<Calendario>(`/asistencia/calendario/${anio}`,sesion));setAviso('Hora de recordatorio actualizada.')})}
  async function resolver(j:Justificacion,aprobar:boolean){const comentario=aprobar?'':prompt('Motivo de rechazo')??'';if(!aprobar&&!comentario.trim())return;
    await ejecutar(async()=>{await erp(`/asistencia/justificaciones/${j.id}/resolver`,sesion,'POST',{aprobar,comentario,version:j.version});window.dispatchEvent(new Event('bbs:justificaciones-actualizadas'));setJustificaciones(await erp<Justificacion[]>(`/asistencia/justificaciones?anio=${anio}`,sesion));setAviso('Decisión registrada y aviso pendiente de entrega al Aula.')})}
  async function registrarSalida(){if(!persona)return;await ejecutar(async()=>{
    const firma=JSON.stringify([persona.id,salidaEstado,salidaMotivo])
    if(salidaPendiente.current?.firma!==firma) salidaPendiente.current={firma,clave:clave()}
    await erp('/asistencia/salidas',sesion,'POST',{personaId:persona.id,estado:salidaEstado,motivo:salidaMotivo,clave:salidaPendiente.current.clave})
    salidaPendiente.current=null;setSalidaMotivo('');setAviso('Salida registrada.')
    try{setSalidas(await erp<Salida[]>('/asistencia/salidas',sesion))}
    catch{setError('La salida se registró, pero no se pudo actualizar el listado. Recarga la página para consultarla.')}
  })}
  return <section className="pagina ast"><div className="ast-intro"><p className="etiqueta">CONTROL INSTITUCIONAL</p><p>Asistencia por día y sección. El ERP conserva el expediente oficial.</p></div>
    <div className="ast-filtros"><label>Año escolar<select value={anio} onChange={e=>{setAnio(Number(e.target.value));setSeccion('')}}>{[...new Set([anio,...anios])].sort((a,b)=>b-a).map(a=><option key={a}>{a}</option>)}</select></label>
      {tab==='Control diario'&&<label>Fecha<input type="date" value={fecha} onChange={e=>{setFecha(e.target.value);setSeccion('')}} max={fechaLima()}/></label>}</div>
    <nav className="ast-tabs" aria-label="Áreas de Asistencia">{tabs.map(t=><button key={t} className={tab===t?'activo':''} aria-current={tab===t?'page':undefined} onClick={()=>{setTab(t);setError('');setAviso('')}} aria-label={t==='Justificaciones'&&hayJustificacionesPendientes?'Justificaciones, solicitudes pendientes':undefined}>{t}{t==='Justificaciones'&&hayJustificacionesPendientes&&<span className="ast-punto-pendiente" aria-hidden="true"/>}</button>)}</nav>
    {error&&<p className="ast-error" role="alert">{error}</p>}{aviso&&<p className="ast-ok" role="status">{aviso}</p>}
    {tab==='Control diario'&&<div className="ast-grid"><div className="ast-panel"><h3>Secciones lectivas</h3>{!secciones.length?<p>No hay secciones lectivas con calendario habilitado para esta fecha.</p>:secciones.map(s=><button className={'ast-seccion '+(seccion===s.seccionId?'activo':'')} key={s.seccionId} onClick={()=>setSeccion(s.seccionId)}><strong>{s.seccion}</strong><span>{s.marcados}/{s.total} marcas · {s.confirmada?'Confirmada':s.alertaSinMarcas?'Sin registro':s.cerrada?'Cerrada':'Pendiente'}</span></button>)}</div>
      <div className="ast-panel"><h3>{seccion?secciones.find(s=>s.seccionId===seccion)?.seccion:'Elige una sección'}</h3>{jornada&&<>{!jornada.lectivo?<p>Día no lectivo: el registro está bloqueado.</p>:<><p>{jornada.alumnos.length} estudiantes · {jornada.confirmada?'Confirmada':jornada.cerrada?'Cierre diario aplicado':'Edición disponible'}</p><div className="ast-lista">{jornada.alumnos.map(a=><div key={a.personaId} className="ast-alumno"><div><strong>{a.nombre}</strong><small>{textoMarca(a.visible)}{a.origen==='SISTEMA'?' · Sistema':''}</small></div><div className="ast-marcas" role="group" aria-label={`Asistencia de ${a.nombre}`}>{(['PRESENTE','TARDANZA','FALTA'] as const).map(v=><button key={v} disabled={a.justificada||ocupado} className={(marcas[a.personaId]??a.borrador??a.marca)===v?'activo':''} onClick={()=>setMarcas({...marcas,[a.personaId]:v})}>{v[0]}</button>)}</div></div>)}</div><div className="ast-acciones"><button disabled={ocupado} onClick={()=>setMarcas(Object.fromEntries(jornada.alumnos.filter(a=>!a.justificada).map(a=>[a.personaId,'PRESENTE'])))}>Marcar todos presentes</button>{fecha<fechaLima()&&<label>Motivo de regularización<input value={motivo} onChange={e=>setMotivo(e.target.value)}/></label>}<button disabled={ocupado||(!Object.keys(marcas).length&&jornada.alumnos.length===0)} onClick={()=>void guardar(false)}>Guardar y cerrar edición</button><button disabled={ocupado||jornada.alumnos.some(a=>!marcas[a.personaId]&&!a.borrador&&!a.marca)} onClick={()=>void guardar(true)}>Confirmar lista completa</button></div></>}</>}</div></div>}
    {tab==='Historial'&&<div className="ast-panel"><BuscarPersona sesion={sesion} seleccionar={setPersona}/>{persona&&<><h3>{persona.nombres} {persona.apellidos}</h3>{estadisticas&&<div className="ast-metricas"><span>Presencia física: {estadisticas.presenciaFisica==null?'No disponible':estadisticas.presenciaFisica.toFixed(1)+'%'}</span><span>Cobertura: {estadisticas.cobertura==null?'No disponible':estadisticas.cobertura.toFixed(1)+'%'}</span><span>{estadisticas.marcasValidas} marcas / {estadisticas.diasElegibles} días elegibles</span><span>{estadisticas.pendiente} pendientes · {estadisticas.falta} faltas sin justificar</span></div>}{!historial.length?<p>Sin marcas publicadas en este año.</p>:<table><thead><tr><th>Fecha</th><th>Sección</th><th>Estado</th><th>Origen</th></tr></thead><tbody>{historial.map((h,i)=><tr key={i}><td>{h.fecha}</td><td>{h.seccion}</td><td>{textoMarca(h.visible)}{!h.valido?' · Invalidada':''}</td><td>{h.origen}</td></tr>)}</tbody></table>}</>}</div>}
    {tab==='Justificaciones'&&<div className="ast-panel"><h3>Solicitudes</h3>{!justificaciones.length?<p>No hay solicitudes en este año.</p>:<div className="ast-lista">{justificaciones.map(j=><article className="ast-fila" key={j.id}><div><strong>{j.nombre}</strong><p>{j.fecha} · {textoMarca(j.marca)} · {j.estado}</p><p>{j.motivo}</p>{j.comentario&&<small>Resolución: {j.comentario}</small>}<button type="button" disabled={ocupado} onClick={()=>void ejecutar(async()=>{setAdjuntos({...adjuntos,[j.id]:await erp<Array<{id:string;nombre:string}>>(`/asistencia/justificaciones/${j.id}/adjuntos`,sesion)})})}>Ver comprobantes</button>{adjuntos[j.id]?.map(a=><a key={a.id} href={`/api/erp/v1/asistencia/adjuntos/${a.id}`}>{a.nombre}</a>)}</div>{j.estado==='EN_REVISION'&&<div className="ast-acciones"><button disabled={ocupado} onClick={()=>void resolver(j,true)}>Aprobar</button><button disabled={ocupado} onClick={()=>void resolver(j,false)}>Rechazar</button></div>}</article>)}</div>}</div>}
    {tab==='Salidas anticipadas'&&<div className="ast-panel"><h3>Registrar salida</h3><BuscarPersona sesion={sesion} seleccionar={setPersona}/>{persona&&<p>Estudiante: <strong>{persona.nombres} {persona.apellidos}</strong></p>}<div className="ast-filtros"><label>Estado<select value={salidaEstado} onChange={e=>setSalidaEstado(e.target.value)}><option value="AUTORIZADA">Autorizada</option><option value="NO_AUTORIZADA">No autorizada</option></select></label><label>Motivo<input value={salidaMotivo} onChange={e=>setSalidaMotivo(e.target.value)}/></label><button disabled={!persona||!salidaMotivo.trim()||ocupado} onClick={()=>void registrarSalida()}>Registrar salida</button></div><h3>Salidas registradas</h3>{salidas.map(s=><article className="ast-fila" key={s.id}><strong>{s.nombre}</strong><span>{s.seccion} · {s.estado} · {s.ocurridoEn}</span><p>{s.motivo}</p></article>)}</div>}
    {tab==='Calendario'&&<div className="ast-panel"><h3>Calendario {anio}</h3>{cal?<><p>Base lectiva: {cal.inicio} a {cal.fin}. Habilitado desde {cal.habilitadoDesde}. Recordatorio: {cal.recordatorioHora}.</p><div className="ast-filtros"><label>Hora de recordatorio<input type="time" value={horaAviso} onChange={e=>setHoraAviso(e.target.value)}/></label><button disabled={ocupado} onClick={()=>void cambiarHora()}>Actualizar hora</button></div></>:<><p>Define explícitamente el periodo y los días lectivos. No se generará asistencia anterior a la habilitación.</p><div className="ast-filtros"><label>Inicio<input type="date" value={inicio} onChange={e=>setInicio(e.target.value)}/></label><label>Fin<input type="date" value={fin} onChange={e=>setFin(e.target.value)}/></label><label>Días (1=lunes, 7=domingo)<input value={dias.join(',')} onChange={e=>setDias(e.target.value.split(',').map(Number).filter(n=>n>=1&&n<=7))}/></label><button disabled={ocupado||!inicio||!fin} onClick={()=>void crearCalendario()}>Habilitar calendario</button></div></>}
      {cal&&<><h3>Excepción lectiva o no lectiva</h3><div className="ast-filtros"><label>Fecha<input type="date" value={excFecha} onChange={e=>setExcFecha(e.target.value)}/></label><label>Sección (opcional)<select value={excSeccion} onChange={e=>setExcSeccion(e.target.value)}><option value="">Todas</option>{seccionesOferta.map(s=><option key={s.id} value={s.id}>{s.nivel} {s.grado}° {s.nombre}</option>)}</select></label><label>Tipo<select value={String(excLectivo)} onChange={e=>setExcLectivo(e.target.value==='true')}><option value="false">No lectivo</option><option value="true">Lectivo</option></select></label><label>Motivo<input value={excMotivo} onChange={e=>setExcMotivo(e.target.value)}/></label><button disabled={ocupado||!excFecha||!excMotivo.trim()} onClick={()=>void crearExcepcion()}>Guardar excepción</button></div>{excepciones.map(e=><p key={e.id}>{e.fecha} · {e.seccion_id?seccionesOferta.find(s=>s.id===e.seccion_id)?.nombre??'Sección':'Todas las secciones'} · {e.lectivo?'Lectivo':'No lectivo'} · {e.motivo}</p>)}</>}</div>}
    {tab==='Pendientes'&&<div className="ast-panel"><h3>Jornadas pendientes</h3>{pendientesCargando?<p role="status">Consultando jornadas pendientes…</p>:pendientesError?<p>No se pudo confirmar el estado de las jornadas. Reintenta la consulta.</p>:!pendientes.length?<p>No hay pendientes registrados para el calendario habilitado.</p>:pendientes.map((p,i)=><button className="ast-seccion" key={i} onClick={()=>{setFecha(p.fecha);setSeccion(p.seccionId);setTab('Control diario')}}><strong>{p.fecha} · {p.seccion}</strong><span>{p.sinMarca} sin marca · {p.causa} · Revisar y regularizar</span></button>)}</div>}
  </section>
}
