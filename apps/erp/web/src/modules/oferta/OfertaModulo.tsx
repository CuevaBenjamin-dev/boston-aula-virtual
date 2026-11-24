import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Sesion } from '../../shared/api/sesion'
import { erp } from '../../shared/api/erp'
import './oferta.css'

type Nivel = 'PRIMARIA' | 'SECUNDARIA'
type Grado = { nivel: Nivel; grado: number }
type Seccion = Grado & { id: string; anio: number; nombre: string }
type Asignatura = Seccion & { codigo: string }
type Datos = { anio: number; secciones: Seccion[]; asignaturas: Asignatura[] }
type Modal = { tipo: 'anio' } | ({ tipo: 'seccion' | 'asignatura'; anio: number } & Grado)
const niveles = [{ nivel: 'PRIMARIA', nombre: 'Primaria', cantidad: 6 },
  { nivel: 'SECUNDARIA', nombre: 'Secundaria', cantidad: 5 }] as const
const grados: Grado[] = niveles.flatMap(n => Array.from({ length: n.cantidad }, (_, i) => ({ nivel: n.nivel, grado: i + 1 })))
const nombreNivel = (n: Nivel) => n === 'PRIMARIA' ? 'Primaria' : 'Secundaria'
const nombreGrado = (g: Grado) => `${g.grado}.º de ${nombreNivel(g.nivel)}`
const mismoGrado = (a: Grado, b: Grado) => a.nivel === b.nivel && a.grado === b.grado
const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`
const normalizar = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toUpperCase()
const codigoAnual = (g: Grado & { anio: number }, sigla: string) => `${g.nivel === 'PRIMARIA' ? 'PR' : 'SE'}-${String(g.grado).padStart(2, '0')}-${sigla || 'SIGLA'}-${String(g.anio % 100).padStart(2, '0')}`
const mensajeError = (e: unknown) => e instanceof Error ? e.message : String(e)

export function OfertaModulo({ sesion }: { sesion: Sesion }) {
  const [anios, setAnios] = useState<number[] | null>(null)
  const [anio, setAnio] = useState<number | null>(null)
  const [indice, setIndice] = useState(0)
  const [datos, setDatos] = useState<Datos | null>(null)
  const [errorAnios, setErrorAnios] = useState('')
  const [errorOferta, setErrorOferta] = useState('')
  const [revisionAnios, setRevisionAnios] = useState(0)
  const [revisionOferta, setRevisionOferta] = useState(0)
  const [modal, setModal] = useState<Modal | null>(null)
  const [nuevoAnio, setNuevoAnio] = useState('')
  const [nombre, setNombre] = useState('')
  const [sigla, setSigla] = useState('')
  const [errorFormulario, setErrorFormulario] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState('')
  const [marcador, setMarcador] = useState<string | null>(null)
  const dialogo = useRef<HTMLDialogElement>(null)
  const enviando = useRef(false)
  const vivo = useRef(false)
  const seleccionado = grados[indice]
  const actual = anio !== null && anios?.includes(anio) && datos?.anio === anio ? datos : null
  const secciones = actual?.secciones.filter(s => mismoGrado(s, seleccionado)) ?? []
  const asignaturas = actual?.asignaturas.filter(a => mismoGrado(a, seleccionado)) ?? []
  const listo = actual !== null && !errorOferta

  useEffect(() => { vivo.current = true; return () => { vivo.current = false } }, [])
  useEffect(() => {
    let vigente = true
    setErrorAnios('')
    setAnios(null)
    void erp<number[]>('/oferta/anios', sesion).then(y => {
      if (!vigente) return
      const ordenados = [...y].sort((a, b) => b - a)
      setAnios(ordenados)
      setAnio(prev => prev !== null && ordenados.includes(prev) ? prev :
        ordenados.includes(new Date().getFullYear()) ? new Date().getFullYear() : ordenados[0] ?? null)
    }).catch(e => { if (vigente) setErrorAnios(mensajeError(e)) })
    return () => { vigente = false }
  }, [sesion, revisionAnios])

  useEffect(() => {
    let vigente = true
    setDatos(null)
    setErrorOferta('')
    if (anio === null) return
    void Promise.all([
      erp<Seccion[]>(`/oferta/secciones?anio=${anio}`, sesion),
      erp<Asignatura[]>(`/oferta/asignaturas?anio=${anio}`, sesion),
    ]).then(([s, a]) => {
      if (vigente) setDatos({ anio, secciones: s, asignaturas: a })
    }).catch(e => { if (vigente) setErrorOferta(mensajeError(e)) })
    return () => { vigente = false }
  }, [anio, sesion, revisionOferta])

  useEffect(() => {
    const el = dialogo.current
    if (!modal || !el) return
    const origen = document.activeElement
    el.showModal()
    const campo = el.querySelector('input')
    campo?.focus()
    if (modal.tipo === 'anio') campo?.select()
    return () => {
      if (el.open) el.close()
      if (origen instanceof HTMLElement && origen.isConnected) origen.focus({ preventScroll: true })
    }
  }, [modal])

  useEffect(() => {
    if (!marcador) return
    document.getElementById(`oferta-alta-${marcador}`)?.scrollIntoView?.({ block: 'nearest', behavior: 'instant' })
    const reloj = window.setTimeout(() => setMarcador(null), 2350)
    return () => window.clearTimeout(reloj)
  }, [marcador])
  useEffect(() => {
    if (!aviso) return
    const reloj = window.setTimeout(() => setAviso(''), 3500)
    return () => window.clearTimeout(reloj)
  }, [aviso])

  function elegirGrado(i: number) {
    setIndice(i)
    setMarcador(null)
  }
  function elegirAnio(y: number) {
    if (y === anio) return
    setDatos(null)
    setErrorOferta('')
    setMarcador(null)
    setAnio(y)
  }
  function abrir(tipo: Modal['tipo']) {
    setErrorFormulario('')
    setNombre('')
    setSigla('')
    if (tipo === 'anio') {
      if (!anios) return
      setNuevoAnio(String(Math.min(2100, Math.max(new Date().getFullYear(), ...anios) + 1)))
      setModal({ tipo })
    } else if (anio !== null && listo) {
      setModal({ tipo, anio, ...seleccionado })
    }
  }
  function cerrar() { if (!enviando.current) setModal(null) }

  async function guardar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!modal || enviando.current) return
    setErrorFormulario('')
    const contexto = modal
    const limpio = nombre.trim()
    const abreviatura = sigla.trim().toUpperCase()
    const y = Number(nuevoAnio)
    let error = ''
    if (contexto.tipo === 'anio') {
      if (!Number.isInteger(y) || y < 2000 || y > 2100) error = 'Introduce un año entre 2000 y 2100.'
      else if (anios?.includes(y)) error = 'Este año ya existe. Selecciónalo en el listado.'
    } else if (contexto.tipo === 'seccion') {
      if (!/^[A-Z0-9]{1,6}$/.test(limpio)) error = 'Usa de 1 a 6 letras o números.'
      else if (secciones.some(s => s.nombre === limpio)) error = 'Esta sección ya existe en el grado seleccionado.'
    } else {
      if (!limpio || limpio.length > 120) error = 'Escribe un nombre de hasta 120 caracteres.'
      else if (!/^[A-Z0-9_]{1,20}$/.test(abreviatura)) error = 'Usa de 1 a 20 letras, números o guion bajo para la sigla.'
      else if (asignaturas.some(a => normalizar(a.nombre) === normalizar(limpio))) error = 'Ya existe una asignatura con ese nombre en este grado.'
      else if (actual?.asignaturas.some(a => a.codigo === codigoAnual(contexto, abreviatura))) error = 'El código generado ya está registrado. Usa otra sigla.'
    }
    if (error) { setErrorFormulario(error); return }
    enviando.current = true
    setGuardando(true)
    setAviso('')
    try {
      if (contexto.tipo === 'anio') {
        const creado = await erp<{ anio: number }>('/oferta/anios', sesion, 'POST', { anio: y })
        if (!vivo.current) return
        setAnios(prev => [...new Set([...(prev ?? []), creado.anio])].sort((a, b) => b - a))
        elegirAnio(creado.anio)
        elegirGrado(0)
        setAviso(`Año escolar ${creado.anio} creado.`)
      } else if (contexto.tipo === 'seccion') {
        const s = await erp<Seccion>('/oferta/secciones', sesion, 'POST', { anio: contexto.anio, nivel: contexto.nivel, grado: contexto.grado, nombre: limpio })
        if (!vivo.current) return
        setDatos(prev => prev?.anio === contexto.anio ? { ...prev, secciones: [...prev.secciones.filter(v => v.id !== s.id), s] } : prev)
        setMarcador(s.id)
        setAviso(`Sección ${s.nombre} agregada.`)
      } else {
        const a = await erp<Asignatura>('/oferta/asignaturas', sesion, 'POST', { anio: contexto.anio, nivel: contexto.nivel, grado: contexto.grado, codigo: abreviatura, nombre: limpio })
        if (!vivo.current) return
        setDatos(prev => prev?.anio === contexto.anio ? { ...prev, asignaturas: [...prev.asignaturas.filter(v => v.id !== a.id), a] } : prev)
        setMarcador(a.id)
        setAviso(`Asignatura ${a.nombre} agregada.`)
      }
      setModal(null)
    } catch (e) {
      if (vivo.current) setErrorFormulario(mensajeError(e))
    } finally {
      enviando.current = false
      if (vivo.current) setGuardando(false)
    }
  }

  const tituloModal = modal?.tipo === 'anio' ? 'Nuevo año escolar' : modal?.tipo === 'seccion' ? 'Agregar sección' : 'Agregar asignatura'
  return (
    <section className="pagina oferta-bbs">
      <div className="intro"><div><p className="etiqueta">ORGANIZACIÓN ACADÉMICA</p><p>Configura las secciones y asignaturas de cada año escolar.</p></div></div>
      <section className="yearbar" aria-label="Contexto del año escolar">
        <div className="year-control"><label className="field-label" htmlFor="oferta-anio">Año escolar</label>
          <select id="oferta-anio" className="year-select" value={anio ?? ''} disabled={!anios?.length || guardando} onChange={e => elegirAnio(Number(e.target.value))}>
            {anio === null && <option value="">{anios === null ? 'Cargando…' : 'Sin años registrados'}</option>}
            {anios?.map(y => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
        <div className="year-summary" role="group" aria-label="Totales de todo el año" aria-live="polite">
          <div className="metric"><b>{actual?.secciones.length ?? '—'}</b><span>secciones</span></div>
          <div className="metric"><b>{actual?.asignaturas.length ?? '—'}</b><span>asignaturas</span></div>
        </div>
        <button type="button" className="secondary" disabled={anios === null || guardando} onClick={() => abrir('anio')}>+ Nuevo año escolar</button>
      </section>
      {errorAnios ? <div className="per-aviso" role="alert">{errorAnios} <button type="button" className="secondary" onClick={() => setRevisionAnios(v => v + 1)}>Reintentar</button></div> :
        anios === null ? <p role="status">Cargando años escolares…</p> :
        !anios.length ? <div className="panel empty"><strong>Aún no hay años escolares</strong><p>Crea el primero para organizar la oferta académica.</p></div> :
        <div className="workspace">
          <nav className="grade-panel" aria-label="Navegación por grados">
            <div className="grade-head"><h2>Estructura académica</h2><p className="muted">Selecciona un grado</p></div>
            {niveles.map(n => <div className="grade-group" key={n.nivel}>
              <div className="eyebrow">{n.nombre}</div>
              <div className="grade-cols grade-legend" aria-hidden="true"><span>Grado</span><span>Sec.</span><span>Asig.</span></div>
              {grados.map((g, i) => {
                if (g.nivel !== n.nivel) return null
                const s = actual?.secciones.filter(v => mismoGrado(v, g)).length
                const a = actual?.asignaturas.filter(v => mismoGrado(v, g)).length
                return <button type="button" key={g.grado} className="grade-button" aria-pressed={i === indice} aria-label={`${nombreGrado(g)}${s !== undefined && a !== undefined ? `, ${plural(s, 'sección', 'secciones')}, ${plural(a, 'asignatura', 'asignaturas')}` : ''}`} onClick={() => elegirGrado(i)} disabled={guardando}>
                  <span>{g.grado}.º grado</span><span className={s === 0 ? 'zero' : ''}>{s ?? '—'}</span><span className={a === 0 ? 'zero' : ''}>{a ?? '—'}</span>
                </button>
              })}
            </div>)}
          </nav>
          <section className="detail" aria-label="Oferta del grado seleccionado" aria-busy={!actual && !errorOferta}>
            <div className="detail-title"><div><div className="eyebrow">Oferta {anio} · {nombreNivel(seleccionado.nivel)}</div><h2>{nombreGrado(seleccionado)}</h2>
              <p className="muted">{actual ? `${plural(secciones.length, 'sección', 'secciones')} · ${plural(asignaturas.length, 'asignatura', 'asignaturas')}` : 'Consulta de la oferta académica'}</p></div>
              <div className="grade-nav"><button type="button" className="secondary" aria-label="Grado anterior" title="Grado anterior" disabled={indice === 0 || guardando} onClick={() => elegirGrado(indice - 1)}>←</button><button type="button" className="secondary" aria-label="Grado siguiente" title="Grado siguiente" disabled={indice === grados.length - 1 || guardando} onClick={() => elegirGrado(indice + 1)}>→</button></div>
            </div>
            {errorOferta ? <div className="per-aviso" role="alert">{errorOferta} <button type="button" className="secondary" onClick={() => { setErrorOferta(''); setRevisionOferta(v => v + 1) }}>Reintentar</button></div> : !actual ? <p role="status">Cargando oferta…</p> :
              <div className="panels">
                <section className="panel"><header className="panel-head"><h3>Secciones <span className="count">{secciones.length}</span></h3><button type="button" className="secondary" aria-label="Agregar sección" disabled={guardando} onClick={() => abrir('seccion')}>+ Agregar</button></header>
                  {secciones.length ? <ul className="section-list">{[...secciones].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es', { numeric: true })).map(s => <li key={s.id} id={`oferta-alta-${s.id}`} className={marcador === s.id ? 'new' : ''}>Sección {s.nombre}</li>)}</ul> : <div className="empty"><strong>Aún no hay secciones</strong><p>Agrega la primera sección de este grado.</p></div>}
                  <div className="panel-foot">Secciones del grado seleccionado.</div>
                </section>
                <section className="panel"><header className="panel-head"><h3>Asignaturas <span className="count">{asignaturas.length}</span></h3><button type="button" className="secondary" aria-label="Agregar asignatura" disabled={guardando} onClick={() => abrir('asignatura')}>+ Agregar</button></header>
                  {asignaturas.length ? <div className="table-scroll"><table aria-label={`Asignaturas de ${nombreGrado(seleccionado)}, ${anio}`}><thead><tr><th scope="col">Asignatura</th><th scope="col">Código</th></tr></thead><tbody>{[...asignaturas].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')).map(a => <tr key={a.id} id={`oferta-alta-${a.id}`} className={marcador === a.id ? 'new' : ''}><td>{a.nombre}</td><td>{a.codigo}</td></tr>)}</tbody></table></div> : <div className="empty"><strong>Aún no hay asignaturas</strong><p>Agrega la primera asignatura de la malla del grado.</p></div>}
                  <div className="panel-foot">Malla compartida por todas las secciones del grado.</div>
                </section>
              </div>}
            <p className="detail-note"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 7.5v1"/></svg> Cada asignatura tiene un código que identifica nivel, grado, sigla y año. No es necesario registrarla nuevamente para cada sección.</p>
          </section>
        </div>}
      {modal && <dialog ref={dialogo} aria-labelledby="oferta-modal-titulo" onCancel={e => { e.preventDefault(); cerrar() }}>
        <form onSubmit={guardar} aria-busy={guardando}>
          <header className="modal-head"><h2 id="oferta-modal-titulo">{tituloModal}</h2><button type="button" className="ghost" aria-label="Cerrar" disabled={guardando} onClick={cerrar}>×</button></header>
          <div className="modal-body">
            <div className="context">{modal.tipo === 'anio' ? <><b>Crear una oferta independiente</b><span>El nuevo año comenzará sin secciones ni asignaturas.</span></> : <><b>{modal.anio} · {nombreGrado(modal)}</b><span>{modal.tipo === 'seccion' ? 'La sección compartirá las asignaturas de este grado.' : 'La asignatura estará disponible para todas las secciones del grado.'}</span></>}</div>
            <div className="form-fields">
              {modal.tipo === 'anio' ? <div><label className="field-label" htmlFor="oferta-nuevo-anio">Año escolar</label><input id="oferta-nuevo-anio" type="number" min={2000} max={2100} step={1} required value={nuevoAnio} disabled={guardando} onChange={e => setNuevoAnio(e.target.value)}/></div> :
                modal.tipo === 'seccion' ? <div><label className="field-label" htmlFor="oferta-nombre-seccion">Nombre de sección</label><input id="oferta-nombre-seccion" required maxLength={6} value={nombre} disabled={guardando} onChange={e => setNombre(e.target.value.toUpperCase())} placeholder="Ej. D" autoComplete="off" aria-describedby="oferta-ayuda-seccion"/><small id="oferta-ayuda-seccion">De 1 a 6 letras o números.</small></div> : <>
                  <div><label className="field-label" htmlFor="oferta-nombre-asignatura">Nombre de asignatura</label><input id="oferta-nombre-asignatura" required maxLength={120} value={nombre} disabled={guardando} onChange={e => setNombre(e.target.value)} placeholder="Ej. Computación" autoComplete="off"/></div>
                  <div><label className="field-label" htmlFor="oferta-sigla">Sigla</label><input id="oferta-sigla" required maxLength={20} value={sigla} disabled={guardando} onChange={e => setSigla(e.target.value.toUpperCase())} placeholder="Ej. COMP" autoComplete="off" aria-describedby="oferta-ayuda-sigla oferta-codigo-previo"/><small id="oferta-ayuda-sigla">Letras, números o guion bajo.</small></div>
                  <div className="preview" id="oferta-codigo-previo"><span className="muted">Código generado</span><b>{codigoAnual(modal, sigla.trim())}</b></div>
                </>}
            </div>
            {errorFormulario && <div className="error" role="alert">{errorFormulario}</div>}
          </div>
          <footer className="modal-foot"><button type="button" className="secondary" disabled={guardando} onClick={cerrar}>Cancelar</button><button type="submit" className="primary" disabled={guardando}>{guardando ? 'Guardando…' : modal.tipo === 'anio' ? 'Crear año' : tituloModal}</button></footer>
        </form>
      </dialog>}
      <div className="toast" role="status" aria-live="polite">{aviso}</div>
    </section>
  )
}
