import { useEffect, useState, type FormEvent } from 'react'
import type { Sesion } from '../../shared/api/sesion'
import { erp } from '../../shared/api/erp'
import { EstadoIntegracion } from '../../shared/ui/EstadoIntegracion'

type Persona = { id: string; nombres: string; apellidos: string; documento: string | null }
type Pagina = { resultados: Persona[] }
type Seccion = { id: string; anio: number; nivel: string; grado: number; nombre: string }
type Asignatura = { id: string; anio: number; nivel: string; grado: number; codigo: string; nombre: string }
type Asignacion = { id: string; docente: string; seccion: string; asignatura: string; anio: number;
  tipo: string; desde: string; hasta: string | null; horario: string | null; estado: string; coberturaPendiente: boolean }

export function AsignacionesModulo({ sesion }: { sesion: Sesion }) {
  const [anios, setAnios] = useState<number[]>([])
  const [anio, setAnio] = useState<number>(new Date().getFullYear())
  const [secciones, setSecciones] = useState<Seccion[]>([])
  const [asignaturas, setAsignaturas] = useState<Asignatura[]>([])
  const [filas, setFilas] = useState<Asignacion[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [docentes, setDocentes] = useState<Persona[]>([])
  const [docenteId, setDocenteId] = useState('')
  const [seccionId, setSeccionId] = useState('')
  const [asignaturaId, setAsignaturaId] = useState('')
  const [tipo, setTipo] = useState('TITULAR')
  const [desde, setDesde] = useState(new Date().toISOString().slice(0, 10))
  const [hasta, setHasta] = useState('')
  const [horario, setHorario] = useState('')
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => { void erp<number[]>('/oferta/anios', sesion).then(v => { setAnios(v); if (v.length) setAnio(v[0]) }).catch(e => setError(String(e))) }, [sesion])
  useEffect(() => { void Promise.all([
    erp<Seccion[]>(`/oferta/secciones?anio=${anio}`, sesion),
    erp<Asignatura[]>(`/oferta/asignaturas?anio=${anio}`, sesion),
    erp<Asignacion[]>(`/asignaciones?anio=${anio}`, sesion),
  ]).then(([s, a, f]) => { setSecciones(s); setAsignaturas(a); setFilas(f) }).catch(e => setError(String(e))) }, [anio, sesion])
  useEffect(() => {
    if (busqueda.trim().length < 2 || docenteId) { setDocentes([]); return }
    const reloj = window.setTimeout(() => { void erp<Pagina>(`/personas?condicion=DOCENTE&vigente=true&buscar=${encodeURIComponent(busqueda.trim())}&tamano=8`, sesion)
      .then(p => setDocentes(p.resultados)).catch(e => setError(String(e))) }, 250)
    return () => window.clearTimeout(reloj)
  }, [busqueda, docenteId, sesion])
  const seccion=secciones.find(s => s.id===seccionId)
  const compatibles=asignaturas.filter(a => seccion && a.nivel===seccion.nivel && a.grado===seccion.grado)
  async function crear(e: FormEvent) {
    e.preventDefault(); setError(''); setAviso(''); setGuardando(true)
    try { const a=await erp<Asignacion>('/asignaciones',sesion,'POST',{ docenteId,seccionId,asignaturaId,tipo,desde,hasta:hasta||null,horario:horario||null })
      setFilas(f=>[a,...f]); setAviso('Asignación registrada en ERP. Comprueba la confirmación de Aula en la fila.'); setAsignaturaId('') }
    catch (e) { setError(String(e instanceof Error?e.message:e)) } finally { setGuardando(false) }
  }
  async function cerrar(id: string) {
    if (!window.confirm('¿Cerrar esta asignación? Su historial permanecerá disponible.')) return
    setError(''); setGuardando(true)
    try { const a=await erp<Asignacion>(`/asignaciones/${id}/cerrar`,sesion,'POST'); setFilas(f=>f.map(x=>x.id===id?a:x)); setAviso('Asignación cerrada; comprueba la actualización de Aula.') }
    catch (e) { setError(String(e instanceof Error?e.message:e)) } finally { setGuardando(false) }
  }

  return <section className="pagina">
    <div className="erp-pagina-titulo"><div><p className="etiqueta">ORGANIZACIÓN DOCENTE</p><h2>Asignaciones docentes</h2>
      <p>Vincula un docente activo a una asignatura y sección del mismo grado y año.</p></div></div>
    {error && <p className="per-aviso" role="alert">{error}</p>}{aviso && <p className="per-aviso exito" role="status">{aviso}</p>}
    <section className="per-panel"><div className="per-panel-cabecera"><h3>Nueva asignación</h3></div>
      {!anios.length && <p className="per-vacio">Primero configura un año en Oferta académica y registra al docente en Personas.</p>}
      <form onSubmit={e=>void crear(e)}>
        <div className="per-formulario"><label className="per-campo">Año escolar<select value={anio} onChange={e=>{setAnio(Number(e.target.value));setSeccionId('');setAsignaturaId('')}}>
          {anios.map(y=><option key={y}>{y}</option>)}</select></label>
          <label className="per-campo">Buscar docente<input value={busqueda} onChange={e=>{setBusqueda(e.target.value);setDocenteId('')}} placeholder="Nombre o documento" /></label></div>
        {!!docentes.length && <div className="per-opciones">{docentes.map(d=><button type="button" key={d.id} onClick={()=>{setDocenteId(d.id);setBusqueda(`${d.nombres} ${d.apellidos}`);setDocentes([])}}>
          <strong>{d.nombres} {d.apellidos}</strong><small>{d.documento}</small></button>)}</div>}
        {docenteId && <p className="per-seleccionado">Docente seleccionado: {busqueda}</p>}
        <div className="per-formulario"><label className="per-campo">Sección<select required value={seccionId} onChange={e=>{setSeccionId(e.target.value);setAsignaturaId('')}}>
          <option value="">Seleccionar</option>{secciones.map(s=><option value={s.id} key={s.id}>{s.nivel} · {s.grado}.º · {s.nombre}</option>)}</select></label>
          <label className="per-campo">Asignatura<select required value={asignaturaId} onChange={e=>setAsignaturaId(e.target.value)}>
            <option value="">Seleccionar</option>{compatibles.map(a=><option value={a.id} key={a.id}>{a.nombre} ({a.codigo})</option>)}</select></label>
          <label className="per-campo">Tipo<select value={tipo} onChange={e=>setTipo(e.target.value)}><option value="TITULAR">Titular</option><option value="REEMPLAZO">Reemplazo</option></select></label>
          <label className="per-campo">Desde<input required type="date" value={desde} onChange={e=>setDesde(e.target.value)} /></label>
          <label className="per-campo">Hasta (opcional)<input type="date" value={hasta} onChange={e=>setHasta(e.target.value)} /></label>
          <label className="per-campo">Horario (referencia opcional)<input value={horario} onChange={e=>setHorario(e.target.value)} placeholder="Lunes 08:00–09:00" /></label></div>
        <div className="per-acciones"><small>La asignación queda persistida; Aula se actualiza al confirmar el destino.</small>
          <button className="boton-principal" disabled={guardando||!docenteId||!anios.length}>Registrar asignación</button></div>
      </form>
    </section>
    <section className="per-panel"><div className="per-panel-cabecera"><h3>Asignaciones · {anio}</h3><span>{filas.length}</span></div>
      {!filas.length && <p className="per-vacio">No hay asignaciones en este año.</p>}
      {filas.map(a=><div className="per-fila" key={a.id}><div><strong>{a.asignatura} · {a.seccion}</strong>
        <small>{a.docente} · {a.tipo} · {a.desde}{a.hasta?` a ${a.hasta}`:''} · {a.estado}</small>
        {a.coberturaPendiente && <small className="per-aviso">Cobertura futura por resolver: docente inactivo.</small>}
        <EstadoIntegracion sesion={sesion} tipo="ASIGNACION" id={a.id} /></div>
        {a.estado==='ACTIVA' && <button className="per-secundario" type="button" disabled={guardando} onClick={()=>void cerrar(a.id)}>Cerrar</button>}</div>)}
    </section>
  </section>
}
