import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import type { Sesion } from '../../shared/api/sesion'
import { erp } from '../../shared/api/erp'
import { EstadoIntegracion } from '../../shared/ui/EstadoIntegracion'

type Persona = { id: string; nombres: string; apellidos: string; documento: string | null }
type PaginaPersonas = { resultados: Persona[] }
type Seccion = { id: string; anio: number; nivel: string; grado: number; nombre: string }
type Matricula = { id: string; personaId: string | null; anio: number | null; seccionId: string | null; estado: string; borrador: Datos; creadoEn: string }
type FilaMatricula = { id: string; estudiante: string; documento: string | null; anio: number | null; seccion: string | null; estado: string; creadoEn: string }
type PaginaMatriculas = { resultados: FilaMatricula[]; total: number; pagina: number; tamano: number }
type Apoderado = { personaId?: string; nombres?: string; apellidos?: string; telefono?: string; correo?: string;
  tipoDocumento?: string; paisEmisor?: string; numeroDocumento?: string; sinParentesco?: boolean;
  parentesco?: string; autorizador?: string; medio?: string; referencia?: string }
type Datos = { personaId?: string; nombres?: string; apellidos?: string; fechaNacimiento?: string;
  tipoDocumento?: string; paisEmisor?: string; numeroDocumento?: string; telefono?: string; correo?: string;
  anio?: number; seccionId?: string; apoderado?: Apoderado }

function BuscarPersona({ sesion, valor, alElegir, etiqueta }: { sesion: Sesion; valor: string; alElegir: (id: string, nombre: string) => void; etiqueta: string }) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState<Persona[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    if (texto.trim().length < 2 || valor) { setResultados([]); return }
    const reloj = window.setTimeout(() => { void erp<PaginaPersonas>(`/personas?buscar=${encodeURIComponent(texto.trim())}&tamano=8`, sesion)
      .then(r => setResultados(r.resultados)).catch(e => setError(String(e))) }, 250)
    return () => window.clearTimeout(reloj)
  }, [texto, valor, sesion])
  return <div className="per-selector"><label className="per-campo">{etiqueta}<input value={texto} onChange={e => { setTexto(e.target.value); alElegir('', '') }} placeholder="Nombre o documento" /></label>
    {valor ? <p className="per-seleccionado">Seleccionado: {texto || valor}</p> : <>
      {error && <p className="per-aviso" role="alert">{error}</p>}
      {texto.length >= 2 && !resultados.length && <small>Sin coincidencias; revisa el documento o registra una persona nueva.</small>}
      {!!resultados.length && <div className="per-opciones">{resultados.map(p => <button key={p.id} type="button" onClick={() => { setTexto(`${p.nombres} ${p.apellidos}`); alElegir(p.id, `${p.nombres} ${p.apellidos}`) }}>
        <strong>{p.nombres} {p.apellidos}</strong><small>{p.documento ?? 'Sin documento'}</small></button>)}</div>}</>}
  </div>
}

function Listado({ sesion }: { sesion: Sesion }) {
  const [datos, setDatos] = useState<PaginaMatriculas | null>(null)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)
  const [anios, setAnios] = useState<number[]>([])
  const [seccionesFiltro, setSeccionesFiltro] = useState<Seccion[]>([])
  const [buscar, setBuscar] = useState('')
  const [anioFiltro, setAnioFiltro] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState('')
  const [seccionFiltro, setSeccionFiltro] = useState('')
  const [pagina, setPagina] = useState(0)
  const [revision, setRevision] = useState(0)
  const [anioCierre, setAnioCierre] = useState(0)
  const [vigentes, setVigentes] = useState<number | null>(null)
  const [cerrando, setCerrando] = useState(false)
  const [avisoCierre, setAvisoCierre] = useState('')
  useEffect(() => {
    void erp<number[]>('/oferta/anios', sesion).then(setAnios).catch(e => setError(String(e)))
  }, [sesion])
  useEffect(() => {
    if (!anioFiltro) { setSeccionesFiltro([]); return }
    let vigente = true
    void erp<Seccion[]>(`/oferta/secciones?anio=${anioFiltro}`, sesion)
      .then(r => { if (vigente) setSeccionesFiltro(r) }).catch(e => { if (vigente) setError(String(e)) })
    return () => { vigente = false }
  }, [anioFiltro, sesion])
  useEffect(() => {
    let vigente = true
    setCargando(true); setError('')
    const q = new URLSearchParams({ pagina: String(pagina), tamano: '15' })
    if (buscar.trim()) q.set('buscar', buscar.trim())
    if (anioFiltro) q.set('anio', anioFiltro)
    if (estadoFiltro) q.set('estado', estadoFiltro)
    if (seccionFiltro) q.set('seccionId', seccionFiltro)
    const reloj = window.setTimeout(() => {
      void erp<PaginaMatriculas>(`/matriculas?${q}`, sesion)
        .then(r => { if (vigente) setDatos(r) })
        .catch(e => { if (vigente) setError(String(e)) })
        .finally(() => { if (vigente) setCargando(false) })
    }, 250)
    return () => { vigente = false; window.clearTimeout(reloj) }
  }, [buscar, anioFiltro, estadoFiltro, seccionFiltro, pagina, revision, sesion])
  useEffect(() => {
    if (!anioCierre) { setVigentes(null); return }
    void erp<{ vigentes: number }>(`/matriculas/finalizar-anio/${anioCierre}/resumen`, sesion)
      .then(r => setVigentes(r.vigentes)).catch(e => setError(String(e)))
  }, [anioCierre, sesion])
  async function finalizarAnio() {
    if (!anioCierre || !vigentes || !window.confirm(`Finalizar ${vigentes} matrículas vigentes de ${anioCierre}? Se conservarán cursos, entregas y calificaciones. Esta operación no se puede deshacer.`)) return
    setError(''); setAvisoCierre(''); setCerrando(true)
    try {
      const resultado = await erp<{ finalizadas: number }>(`/matriculas/finalizar-anio/${anioCierre}`, sesion, 'POST')
      setAvisoCierre(`${resultado.finalizadas} matrículas finalizadas en ${anioCierre}. La sincronización con Aula puede quedar pendiente hasta su confirmación.`)
      setVigentes(0)
      setRevision(r => r + 1)
    } catch (e) { setError(String(e)) } finally { setCerrando(false) }
  }
  return <section className="pagina"><div className="erp-pagina-titulo"><div><p className="etiqueta">GESTIÓN ESCOLAR</p><h2>Matrículas</h2><p>Borradores y matrículas anuales guardados en el ERP.</p></div>
    <Link className="boton-principal" to="nueva">+ Nueva matrícula</Link></div>
    <form className="per-panel per-filtros" onSubmit={e => e.preventDefault()}>
      <label className="per-campo">Nombre o documento<input value={buscar} onChange={e => { setBuscar(e.target.value); setPagina(0) }} placeholder="Buscar estudiante" /></label>
      <label className="per-campo">Año escolar<select value={anioFiltro} onChange={e => { setAnioFiltro(e.target.value); setSeccionFiltro(''); setPagina(0) }}>
        <option value="">Todos</option>{anios.map(y => <option key={y} value={y}>{y}</option>)}
      </select></label>
      <label className="per-campo">Estado<select value={estadoFiltro} onChange={e => { setEstadoFiltro(e.target.value); setPagina(0) }}>
        <option value="">Todos</option><option value="BORRADOR">Borrador</option><option value="VIGENTE">Vigente</option><option value="RETIRADA">Retirada</option><option value="FINALIZADA">Finalizada</option>
      </select></label>
      <label className="per-campo">Sección<select value={seccionFiltro} disabled={!anioFiltro} onChange={e => { setSeccionFiltro(e.target.value); setPagina(0) }}>
        <option value="">{anioFiltro ? 'Todas' : 'Selecciona un año'}</option>
        {seccionesFiltro.map(s => <option key={s.id} value={s.id}>{s.nivel} · {s.grado}.º · {s.nombre}</option>)}
      </select></label>
    </form>
    <section className="per-panel" aria-busy={cargando}><div className="per-panel-cabecera"><h3>Expedientes</h3><span>{datos?.total ?? 0} resultados</span></div>
      {error && <p className="per-aviso" role="alert">{error}</p>}
      {cargando && <p role="status">Cargando matrículas…</p>}
      {!cargando && datos?.resultados.length === 0 && <p className="per-vacio">No hay matrículas para estos filtros.</p>}
      {!cargando && datos?.resultados.map(m => <div className="per-fila" key={m.id}><div><strong>{m.estudiante}</strong>
        <small>{m.documento ?? 'Sin documento'} · {m.anio ?? 'Año por elegir'} · {m.seccion ?? 'Sin sección'} · {m.estado} · {new Date(m.creadoEn).toLocaleDateString('es-PE')}</small></div>
        <Link to={m.id}>Abrir expediente →</Link></div>)}
      {!!datos?.total && <nav className="per-paginacion" aria-label="Paginación de matrículas">
        <button type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina(p => p - 1)}>Anterior</button>
        <span>Página {pagina + 1} de {Math.ceil(datos.total / datos.tamano)}</span>
        <button type="button" disabled={(pagina + 1) * datos.tamano >= datos.total || cargando} onClick={() => setPagina(p => p + 1)}>Siguiente</button>
      </nav>}
    </section>
    <section className="per-panel"><div className="per-panel-cabecera"><h3>Finalizar año escolar</h3></div>
      <div className="per-panel-cuerpo"><p>Dirección o Coordinación puede cerrar expresamente las matrículas vigentes de un año escolar concluido.</p>
        <label className="per-campo">Año escolar<select value={anioCierre || ''} onChange={e => { setAnioCierre(Number(e.target.value)); setAvisoCierre('') }}><option value="">Seleccionar año</option>
          {anios.filter(y => y < new Date().getFullYear()).map(y => <option key={y} value={y}>{y}</option>)}</select></label>
        {anioCierre > 0 && <p>{vigentes === null ? 'Consultando…' : `${vigentes} matrículas vigentes serán finalizadas en ${anioCierre}. Las matrículas Borrador, Retirada y Finalizada no cambiarán.`}</p>}
        <button type="button" className="boton-principal" disabled={!vigentes || cerrando} onClick={() => void finalizarAnio()}>{cerrando ? 'Finalizando…' : 'Finalizar matrículas del año'}</button>
        {avisoCierre && <p role="status">{avisoCierre}</p>}
      </div>
    </section></section>
}

function Expediente({ sesion, nuevo = false }: { sesion: Sesion; nuevo?: boolean }) {
  const { id } = useParams()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [matricula, setMatricula] = useState<Matricula | null>(null)
  const [datos, setDatos] = useState<Datos>({ paisEmisor: 'PE', tipoDocumento: 'DNI' })
  const [modo, setModo] = useState<'nuevo' | 'existente'>('nuevo')
  const [guardian, setGuardian] = useState<'nuevo' | 'existente'>('nuevo')
  const [paso, setPaso] = useState<number>(() => ubicacion.state?.paso ?? 0)
  const [anios, setAnios] = useState<number[]>([])
  const [anio, setAnio] = useState<number>(new Date().getFullYear())
  const [secciones, setSecciones] = useState<Seccion[]>([])
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [cargando, setCargando] = useState(!nuevo)
  const [confirmandoDescarte, setConfirmandoDescarte] = useState(false)
  const [confirmacionDescarte, setConfirmacionDescarte] = useState('')
  const recargar = useCallback(async () => { if (!id) return; const m = await erp<Matricula>(`/matriculas/${id}`, sesion); setMatricula(m); setDatos(m.borrador);
    setModo(m.borrador.personaId ? 'existente' : 'nuevo'); setGuardian(m.borrador.apoderado?.personaId ? 'existente' : 'nuevo');
    setAnio(m.anio ?? m.borrador.anio ?? anio) }, [id, sesion, anio])
  useEffect(() => { if (!nuevo) void recargar().catch(e => setError(String(e))).finally(() => setCargando(false)) }, [nuevo, recargar])
  useEffect(() => { void erp<number[]>('/oferta/anios', sesion).then(y => { setAnios(y); if (y.length && !y.includes(anio)) setAnio(y[0]) }).catch(e => setError(String(e))) }, [sesion])
  useEffect(() => { void erp<Seccion[]>(`/oferta/secciones?anio=${anio}`, sesion).then(setSecciones).catch(e => setError(String(e))) }, [sesion, anio])
  const actualizar = (clave: keyof Datos, valor: string) => setDatos(d => ({ ...d, [clave]: valor }))
  const actualizarGuardian = (clave: keyof Apoderado, valor: string | boolean) => setDatos(d => ({ ...d, apoderado: { ...d.apoderado, [clave]: valor } }))
  async function guardarBorrador() {
    const ruta=matricula ? `/matriculas/${matricula.id}` : '/matriculas'
    const completo = { ...datos, anio, tipoDocumento: datos.tipoDocumento ?? 'DNI', paisEmisor: datos.paisEmisor ?? 'PE',
      apoderado: datos.apoderado && Object.values(datos.apoderado).some(Boolean)
        ? { tipoDocumento: 'DNI', paisEmisor: 'PE', ...datos.apoderado } : undefined }
    const m=await erp<Matricula>(ruta, sesion, matricula ? 'PUT' : 'POST', { datos: completo })
    setMatricula(m)
    if (!matricula) navegar(`/matriculas/${m.id}`, { replace: true, state: { paso: Math.min(paso+1,2) } })
    return m
  }
  async function siguiente(e: FormEvent) {
    e.preventDefault(); setError(''); setAviso(''); setGuardando(true)
    try { await guardarBorrador(); setPaso(p => Math.min(p+1,2)); setAviso('Borrador guardado. Puedes volver y corregir los datos.') }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
    finally { setGuardando(false) }
  }
  async function confirmar() {
    if (!matricula) return
    setError(''); setGuardando(true)
    try { await guardarBorrador(); const m=await erp<Matricula>(`/matriculas/${matricula.id}/confirmar`,sesion,'POST');
      setMatricula(m); setAviso('Matrícula vigente en ERP. Comprueba la confirmación de Identidad y Aula en el expediente.') }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
    finally { setGuardando(false) }
  }
  async function cambiar(accion: 'retirar' | 'reincorporar') {
    if (!matricula || !window.confirm(`¿Confirmar ${accion} esta matrícula?`)) return
    setGuardando(true); setError('')
    try { const m=await erp<Matricula>(`/matriculas/${matricula.id}/${accion}`,sesion,'POST'); setMatricula(m); setAviso('Estado institucional actualizado. Comprueba la sincronización en el expediente.') }
    catch (e) { setError(String(e)) } finally { setGuardando(false) }
  }
  async function descartar() {
    if (!matricula || matricula.estado !== 'BORRADOR' || confirmacionDescarte !== 'DESCARTAR') return
    setGuardando(true); setError('')
    try { await erp<void>(`/matriculas/${matricula.id}`,sesion,'DELETE'); navegar('/matriculas', { replace: true }) }
    catch (e) { setError(String(e)) } finally { setGuardando(false) }
  }
  const g=datos.apoderado ?? {}
  if (cargando) return <p role="status">Cargando borrador…</p>
  return <section className="pagina"><Link className="per-volver" to="/matriculas">← Matrículas</Link>
    <div className="erp-pagina-titulo"><div><p className="etiqueta">PROCESO ANUAL</p><h2>{matricula?.estado === 'VIGENTE' ? 'Matrícula vigente' : 'Nueva matrícula'}</h2>
      <p>{matricula ? `Estado: ${matricula.estado}` : 'Completa el recorrido y confirma cuando los datos sean válidos.'}</p></div>
      {matricula?.estado === 'BORRADOR' && <button className="per-peligro" type="button" onClick={() => setConfirmandoDescarte(true)}>Descartar borrador</button>}</div>
    {error && <p className="per-aviso" role="alert">{error}</p>}{aviso && <p className="per-aviso exito" role="status">{aviso}</p>}
    {confirmandoDescarte && <section className="per-panel per-confirmar-eliminacion" role="group" aria-label="Confirmar descarte del borrador">
      <h3>Descartar este borrador</h3><p>Se retirará el borrador erróneo. Las matrículas confirmadas y los datos de Personas no cambiarán. Quedará constancia del descarte y del operador.</p>
      <label className="per-campo">Escribe DESCARTAR para confirmar<input value={confirmacionDescarte} onChange={e => setConfirmacionDescarte(e.target.value)} /></label>
      <div className="per-acciones"><button className="per-secundario" type="button" onClick={() => { setConfirmandoDescarte(false); setConfirmacionDescarte('') }}>Conservar borrador</button>
        <button className="per-peligro" type="button" disabled={confirmacionDescarte !== 'DESCARTAR' || guardando} onClick={() => void descartar()}>{guardando ? 'Descartando…' : 'Descartar definitivamente'}</button></div>
    </section>}
    {matricula && matricula.estado !== 'BORRADOR' ? <section className="per-panel"><div className="per-panel-cabecera"><h3>Expediente</h3></div>
      <dl className="per-datos"><dt>Alumno</dt><dd>{datos.nombres} {datos.apellidos || matricula.personaId}</dd><dt>Año</dt><dd>{matricula.anio}</dd><dt>Sección</dt><dd>{secciones.find(s=>s.id===matricula.seccionId)?.nombre ?? matricula.seccionId}</dd>
        <dt>Integración</dt><dd><EstadoIntegracion sesion={sesion} tipo="MATRICULA" id={matricula.id} /></dd></dl>
      {matricula.estado === 'VIGENTE' && <button className="per-secundario" disabled={guardando} onClick={() => void cambiar('retirar')}>Retirar</button>}
      {matricula.estado === 'RETIRADA' && <button className="boton-principal" disabled={guardando} onClick={() => void cambiar('reincorporar')}>Reincorporar</button>}
    </section> : <>
      <nav className="erp-pasos" aria-label="Etapas de la matrícula">{['Alumno','Apoderado','Revisión'].map((n,i) => <button type="button" key={n} className={paso===i?'actual':''} disabled={i>paso} onClick={() => { if (i<paso) setPaso(i) }}>{String(i+1).padStart(2,'0')} · {n}</button>)}</nav>
      {paso===0 && <form className="per-panel" onSubmit={e => void siguiente(e)}><div className="per-panel-cabecera"><h3>01 · Datos del alumno</h3></div>
        <div className="erp-opciones"><button type="button" className={modo==='nuevo'?'seleccionado':''} onClick={() => { setModo('nuevo'); actualizar('personaId','') }}>Registrar nuevo</button>
          <button type="button" className={modo==='existente'?'seleccionado':''} onClick={() => setModo('existente')}>Usar estudiante registrado</button></div>
        {modo==='existente' ? <BuscarPersona sesion={sesion} valor={datos.personaId ?? ''} alElegir={id => actualizar('personaId',id)} etiqueta="Buscar estudiante" /> : <div className="per-formulario">
          <label className="per-campo">Nombres<input value={datos.nombres ?? ''} onChange={e=>actualizar('nombres',e.target.value)} /></label>
          <label className="per-campo">Apellidos<input value={datos.apellidos ?? ''} onChange={e=>actualizar('apellidos',e.target.value)} /></label>
          <label className="per-campo">Nacimiento<input type="date" value={datos.fechaNacimiento ?? ''} onChange={e=>actualizar('fechaNacimiento',e.target.value)} /></label>
          <label className="per-campo">Documento<select value={datos.tipoDocumento ?? 'DNI'} onChange={e=>actualizar('tipoDocumento',e.target.value)}><option>DNI</option><option>CE</option><option>PASAPORTE</option></select></label>
          <label className="per-campo">País emisor<input maxLength={2} value={datos.paisEmisor ?? 'PE'} onChange={e=>actualizar('paisEmisor',e.target.value.toUpperCase())} /></label>
          <label className="per-campo">Número<input value={datos.numeroDocumento ?? ''} onChange={e=>actualizar('numeroDocumento',e.target.value)} /></label>
          <label className="per-campo">Teléfono (opcional)<input value={datos.telefono ?? ''} onChange={e=>actualizar('telefono',e.target.value)} /></label>
          <label className="per-campo">Correo (opcional)<input type="email" value={datos.correo ?? ''} onChange={e=>actualizar('correo',e.target.value)} /></label>
        </div>}
        <div className="per-formulario erp-espacio"><label className="per-campo">Año escolar<select value={anio} onChange={e=>{setAnio(Number(e.target.value)); setDatos(d=>({...d,anio:Number(e.target.value),seccionId:''}))}}>{anios.map(y=><option key={y}>{y}</option>)}</select></label>
          <label className="per-campo">Sección<select value={datos.seccionId ?? ''} onChange={e=>actualizar('seccionId',e.target.value)}><option value="">Seleccionar</option>
            {secciones.map(s=><option key={s.id} value={s.id}>{s.nivel} · {s.grado}.º · {s.nombre}</option>)}</select></label></div>
        {!anios.length && <p className="per-aviso">Primero crea un año y una sección en Oferta académica.</p>}
        <div className="per-acciones"><small>Al continuar se guarda un borrador; aún no se crea una cuenta de Aula.</small><button className="boton-principal" disabled={guardando}>Siguiente: apoderado →</button></div>
      </form>}
      {paso===1 && <form className="per-panel" onSubmit={e => void siguiente(e)}><div className="per-panel-cabecera"><h3>02 · Apoderado</h3></div>
        <p className="per-texto">Si el alumno es menor, se verificará un apoderado autorizado vigente al confirmar. Para un mayor, este paso es opcional. Los vínculos existentes se reutilizan.</p>
        <div className="erp-opciones"><button type="button" className={guardian==='nuevo'?'seleccionado':''} onClick={()=>{setGuardian('nuevo');actualizarGuardian('personaId','')}}>Nuevo apoderado</button><button type="button" className={guardian==='existente'?'seleccionado':''} onClick={()=>{setGuardian('existente');setDatos(d=>({...d,apoderado:{personaId:''}}))}}>Apoderado registrado</button></div>
        {guardian==='existente' ? <BuscarPersona sesion={sesion} valor={g.personaId ?? ''} alElegir={id=>actualizarGuardian('personaId',id)} etiqueta="Buscar apoderado" /> : <div className="per-formulario">
          <label className="per-campo">Nombres<input value={g.nombres ?? ''} onChange={e=>actualizarGuardian('nombres',e.target.value)} /></label>
          <label className="per-campo">Apellidos<input value={g.apellidos ?? ''} onChange={e=>actualizarGuardian('apellidos',e.target.value)} /></label>
          <label className="per-campo">Teléfono<input value={g.telefono ?? ''} onChange={e=>actualizarGuardian('telefono',e.target.value)} /></label>
          <label className="per-campo">Correo (opcional)<input type="email" value={g.correo ?? ''} onChange={e=>actualizarGuardian('correo',e.target.value)} /></label>
          <label className="per-campo">Documento<select value={g.tipoDocumento ?? 'DNI'} onChange={e=>actualizarGuardian('tipoDocumento',e.target.value)}><option>DNI</option><option>CE</option><option>PASAPORTE</option></select></label>
          <label className="per-campo">País emisor<input maxLength={2} value={g.paisEmisor ?? 'PE'} onChange={e=>actualizarGuardian('paisEmisor',e.target.value.toUpperCase())} /></label>
          <label className="per-campo">Número<input value={g.numeroDocumento ?? ''} onChange={e=>actualizarGuardian('numeroDocumento',e.target.value)} /></label>
        </div>}
        <label className="per-check erp-espacio"><input type="checkbox" checked={g.sinParentesco ?? false} onChange={e=>actualizarGuardian('sinParentesco',e.target.checked)} /> Sin parentesco familiar; autorización institucional comprobada</label>
        {g.sinParentesco ? <div className="per-formulario"><label className="per-campo">Quién autorizó<input value={g.autorizador ?? ''} onChange={e=>actualizarGuardian('autorizador',e.target.value)} /></label>
          <label className="per-campo">Medio de comprobación<input value={g.medio ?? ''} onChange={e=>actualizarGuardian('medio',e.target.value)} placeholder="Presencial o teléfono" /></label>
          <label className="per-campo">Referencia del sustento<input value={g.referencia ?? ''} onChange={e=>actualizarGuardian('referencia',e.target.value)} /></label></div>
          : <label className="per-campo erp-espacio">Parentesco<input value={g.parentesco ?? ''} onChange={e=>actualizarGuardian('parentesco',e.target.value)} placeholder="Madre, padre, familiar…" /></label>}
        <div className="per-acciones"><button type="button" className="per-secundario" onClick={()=>setPaso(0)}>← Anterior</button><button className="boton-principal" disabled={guardando}>Siguiente: revisión →</button></div>
      </form>}
      {paso===2 && <section className="per-panel"><div className="per-panel-cabecera"><h3>03 · Revisión</h3></div>
        <dl className="per-datos"><dt>Estudiante</dt><dd>{datos.personaId || `${datos.nombres ?? 'Sin nombre'} ${datos.apellidos ?? ''}`}</dd>
          <dt>Documento</dt><dd>{datos.personaId ? 'Persona registrada' : `${datos.tipoDocumento ?? '—'} · ${datos.numeroDocumento ?? '—'}`}</dd>
          <dt>Oferta</dt><dd>{anio} · {secciones.find(s=>s.id===datos.seccionId)?.nombre ?? 'Sin sección'}</dd>
          <dt>Apoderado</dt><dd>{g.personaId || `${g.nombres ?? ''} ${g.apellidos ?? ''}` || 'No indicado / vínculo existente'}</dd></dl>
        <p className="per-texto">Confirmar crea la Persona definitiva si es nueva, valida la matrícula anual y registra solicitudes pendientes para Identidad y Aula. No declara acceso externo completado.</p>
        <div className="per-acciones"><button className="per-secundario" type="button" onClick={()=>setPaso(1)}>← Anterior</button><button className="per-secundario" type="button" disabled={guardando} onClick={()=>void guardarBorrador().then(()=>setAviso('Borrador guardado.'))}>Guardar borrador</button>
          <button className="boton-principal" type="button" disabled={guardando || !matricula} onClick={()=>void confirmar()}>Confirmar matrícula</button></div>
      </section>}
    </>}
  </section>
}

export function MatriculasModulo({ sesion }: { sesion: Sesion }) {
  return <Routes><Route index element={<Listado sesion={sesion} />} /><Route path="nueva" element={<Expediente sesion={sesion} nuevo />} /><Route path=":id" element={<Expediente sesion={sesion} />} /></Routes>
}
