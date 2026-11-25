import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import type { Sesion } from '../../shared/api/sesion'
import { api, guardar, type Capacidades, type Condicion, type Pagina, type Persona, type ReferenciaPersona, type TipoDocumento } from './api'
import { EstadoIntegracion } from '../../shared/ui/EstadoIntegracion'
import { IconoInstitucional } from '../../shared/ui/IconoInstitucional'
import './directorio.css'
import './ficha.css'
import './identificacion.css'

const tiposCondicion: { valor: Condicion; texto: string }[] = [
  { valor: 'ESTUDIANTE', texto: 'Estudiante' }, { valor: 'DOCENTE', texto: 'Docente' },
  { valor: 'PERSONAL_INSTITUCIONAL', texto: 'Personal institucional' }, { valor: 'APODERADO', texto: 'Apoderado' },
]

function Fecha({ valor }: { valor: string | null }) {
  return <>{valor ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(new Date(valor)) : 'Vigente'}</>
}

function Aviso({ mensaje, tipo = 'error' }: { mensaje: string; tipo?: 'error' | 'exito' }) {
  return mensaje ? <p className={`per-aviso ${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>{mensaje}</p> : null
}

function Campo({ titulo, children }: { titulo: string; children: ReactNode }) {
  return <label className="per-campo"><span>{titulo}</span>{children}</label>
}

function NombreVinculado({ id, referencia }: { id: string; referencia?: ReferenciaPersona }) {
  return <Link to={'/personas/' + id}>{referencia ? `${referencia.nombres} ${referencia.apellidos}` : 'Ver ficha de la persona'}</Link>
}

function DocumentoVinculado({ referencia }: { referencia?: ReferenciaPersona }) {
  return referencia?.numeroDocumento ? <>{referencia.tipoDocumento} {referencia.numeroDocumento} · </> : null
}

function parentescoVisible(tipo: string, esEstudiante: boolean, sexoEstudiante?: string | null) {
  if (esEstudiante) return tipo
  const normalizado = tipo.trim().toLocaleLowerCase('es-PE').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  const sexo = (sexoEstudiante ?? '').trim().toLocaleLowerCase('es-PE')
  const variante = (masculino: string, femenino: string, sinDato: string) =>
    sexo === 'masculino' || sexo === 'm' ? masculino : sexo === 'femenino' || sexo === 'f' ? femenino : sinDato
  if (normalizado === 'madre' || normalizado === 'padre') return variante('Hijo', 'Hija', 'Hijo/a')
  if (['abuelo', 'abuela', 'abuelo/a'].includes(normalizado)) return variante('Nieto', 'Nieta', 'Nieto/a')
  if (['hermano', 'hermana', 'hermano/a'].includes(normalizado)) return variante('Hermano', 'Hermana', 'Hermano/a')
  if (['tio', 'tia', 'tio/a'].includes(normalizado)) return variante('Sobrino', 'Sobrina', 'Sobrino/a')
  return 'Familiar'
}

function SelectorPersona({ sesion, titulo, condicion, valor, alElegir }: {
  sesion: Sesion; titulo: string; condicion?: Condicion; valor: string; alElegir: (id: string) => void
}) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState<Pagina | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    if (texto.trim().length < 1 || valor) { setResultados(null); return }
    const abortar = new AbortController()
    const espera = window.setTimeout(() => {
      const q = new URLSearchParams({ buscar: texto.trim(), tamano: '5', pagina: '0' })
      if (condicion) { q.set('condicion', condicion); q.set('vigente', 'true') }
      void api<Pagina>('?' + q, sesion, { signal: abortar.signal })
        .then(setResultados).catch(e => { if (!abortar.signal.aborted) setError(String(e.message ?? e)) })
    }, 250)
    return () => { window.clearTimeout(espera); abortar.abort() }
  }, [texto, condicion, valor, sesion])
  return <div className="per-selector">
    <Campo titulo={titulo}><input value={texto} onChange={e => { setTexto(e.target.value); alElegir(''); setError('') }}
      placeholder="Busca por nombre o documento" /></Campo>
    {valor ? <p className="per-seleccionado" role="status">Persona seleccionada: {texto}</p> :
      <><small>Escribe para ver sugerencias y elige una persona.</small>
        <Aviso mensaje={error} />
        {resultados && <div className="per-opciones" role="group" aria-label={`Resultados para ${titulo}`}>
          {resultados.resultados.length === 0 ? <p>Sin resultados.</p> : resultados.resultados.map(p =>
            <button type="button" key={p.id} onClick={() => { alElegir(p.id); setTexto(`${p.nombres} ${p.apellidos}`) }}>
              <strong>{p.nombres} {p.apellidos}</strong><small>{p.documento ?? 'Identificación provisional'}</small>
            </button>)}
        </div>}</>}
  </div>
}

function useEstadoDirectorio() {
  const [buscar, setBuscar] = useState('')
  const [condicion, setCondicion] = useState('')
  const [vigente, setVigente] = useState('')
  const [pagina, setPagina] = useState(0)
  const [ultimaPersona, setUltimaPersona] = useState<string | null>(null)
  return { buscar, setBuscar, condicion, setCondicion, vigente, setVigente, pagina, setPagina,
    ultimaPersona, setUltimaPersona }
}

function Directorio({ sesion, capacidades, estado }: {
  sesion: Sesion; capacidades: Capacidades; estado: ReturnType<typeof useEstadoDirectorio>
}) {
  const { buscar, setBuscar, condicion, setCondicion, vigente, setVigente, pagina, setPagina,
    ultimaPersona, setUltimaPersona } = estado
  const filaVisitada = useRef<HTMLTableRowElement>(null)
  const restaurarPosicion = useRef(true)
  const [datos, setDatos] = useState<Pagina | null>(null)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)
  useEffect(() => {
    if (!capacidades.consultar) return
    const abortar = new AbortController()
    setCargando(true); setError('')
    const q = new URLSearchParams({ pagina: String(pagina), tamano: '15' })
    if (buscar.trim()) q.set('buscar', buscar.trim())
    if (condicion) q.set('condicion', condicion)
    if (vigente) q.set('vigente', vigente)
    const reloj = window.setTimeout(() => {
      void api<Pagina>('?' + q.toString(), sesion, { signal: abortar.signal })
        .then(setDatos).catch(e => { if (!abortar.signal.aborted) setError(String(e.message ?? e)) })
        .finally(() => { if (!abortar.signal.aborted) setCargando(false) })
    }, 250)
    return () => { window.clearTimeout(reloj); abortar.abort() }
  }, [buscar, condicion, vigente, pagina, sesion, capacidades.consultar])
  useEffect(() => {
    if (!restaurarPosicion.current || cargando || error || !datos) return
    restaurarPosicion.current = false
    filaVisitada.current?.scrollIntoView?.({ block: 'nearest', inline: 'nearest', behavior: 'instant' })
  }, [datos, cargando, error])
    const listadoCargado = datos !== null

  useEffect(() => {
    if (!ultimaPersona || !listadoCargado) return

    const reloj = window.setTimeout(() => {
      setUltimaPersona(actual =>
        actual === ultimaPersona ? null : actual
      )
    }, 2350)

    return () => window.clearTimeout(reloj)
  }, [ultimaPersona, listadoCargado, setUltimaPersona])
  return <section className="per-pagina per-directorio-renovado">
    <div className="per-titulo"><div><p className="etiqueta">REGISTRO INSTITUCIONAL</p>
      <p>Consulta identidades, condiciones y vínculos institucionales.</p></div>
      {capacidades.registrar && <Link className="boton-principal" to="nueva">+ Registrar persona</Link>}</div>
    {!capacidades.consultar ? <div className="per-panel">
      {capacidades.personaPropiaId ? <p className="per-texto">Puedes consultar y actualizar tus datos de contacto en tu ficha personal. <Link to={capacidades.personaPropiaId}>Abrir mi ficha →</Link></p>
        : <Aviso mensaje="Tu cuenta aún no dispone de un ámbito verificable para consultar el directorio. Contacta a Dirección." />}
    </div> : <>
      <form className="per-panel per-filtros" onSubmit={e => e.preventDefault()}>
        <Campo titulo="Nombre o documento"><input value={buscar} onChange={e => { setBuscar(e.target.value); setPagina(0) }}
          placeholder="Busca por nombre, apellido o documento" /></Campo>
        <Campo titulo="Condición"><select value={condicion} onChange={e => { setCondicion(e.target.value); setPagina(0) }}>
          <option value="">Todas</option>{tiposCondicion.map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
        </select></Campo>
        <Campo titulo="Vigencia"><select value={vigente} onChange={e => { setVigente(e.target.value); setPagina(0) }}>
          <option value="">Todas</option><option value="true">Activa</option><option value="false">Histórica</option>
        </select></Campo>
      </form>
      <section className="per-panel per-directorio-resultados" aria-busy={cargando} aria-labelledby="per-resultados-titulo">
        <div className="per-panel-cabecera">
          <h3 id="per-resultados-titulo">Resultados</h3>
          <div className="per-directorio-resumen">
            {(buscar || condicion || vigente) && <button type="button" className="per-directorio-limpiar" onClick={() => {
              setBuscar(''); setCondicion(''); setVigente(''); setPagina(0)
            }}>Limpiar filtros</button>}
            <span aria-live="polite">{cargando ? 'Buscando…' : error ? 'Consulta no disponible' : datos ? `${datos.total} ${datos.total === 1 ? 'persona' : 'personas'}` : '—'}</span>
          </div>
        </div>
        <Aviso mensaje={error} />
        {cargando && <p className="per-vacio" role="status">Buscando personas…</p>}
        {!cargando && !error && datos?.resultados.length === 0 && <p className="per-vacio">No hay personas para estos filtros.</p>}
        {!cargando && !error && !!datos?.resultados.length && <div className="per-directorio-scroll" role="region" aria-label="Listado de personas" tabIndex={0}>
          <table className="per-directorio-tabla" aria-labelledby="per-resultados-titulo">
            <colgroup><col className="per-directorio-col-nombre" /><col className="per-directorio-col-doc" /><col /></colgroup>
            <thead><tr><th scope="col">Apellidos y nombres</th><th scope="col">Documento</th><th scope="col">Condiciones vigentes</th></tr></thead>
            <tbody>{datos.resultados.map(p => <tr key={p.id}
              className={ultimaPersona === p.id ? 'per-directorio-visitada' : undefined}
              ref={ultimaPersona === p.id ? filaVisitada : undefined}>
              <td><Link className="per-directorio-persona" to={p.id} onClick={e => {
                if (!e.defaultPrevented && e.button === 0 && !e.ctrlKey && !e.metaKey && !e.shiftKey && !e.altKey) {
                  setUltimaPersona(p.id)
                }
              }}>
                {p.apellidos}, {p.nombres}
              </Link></td>
              <td className="per-directorio-documento">{p.documento ?? <span className="per-directorio-neutro">Sin documento principal</span>}</td>
              <td><span className="etiqueta per-directorio-condicion">
                {(p.condiciones.map(c => tiposCondicion.find(t => t.valor === c)?.texto ?? c).join(' · ') || 'Sin condición vigente').toLocaleUpperCase('es-PE')}
              </span></td>
            </tr>)}</tbody>
          </table>
        </div>}
        {!error && !!datos?.total && <div className="per-directorio-pie">
          <span className="per-directorio-neutro">{cargando ? 'Actualizando resultados…' : datos.resultados.length ? `Mostrando ${datos.pagina * datos.tamano + 1}–${datos.pagina * datos.tamano + datos.resultados.length} de ${datos.total}` : `Total: ${datos.total}`}</span>
          <nav className="per-paginacion" aria-label="Paginación">
            <button type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina(pagina - 1)}>Anterior</button>
            <span>Página {pagina + 1} de {Math.ceil(datos.total / datos.tamano)}</span>
            <button type="button" disabled={(pagina + 1) * datos.tamano >= datos.total || cargando} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
          </nav>
        </div>}
      </section>
    </>}
  </section>
}

function Registro({ sesion, capacidades }: { sesion: Sesion; capacidades: Capacidades }) {
  const navegar = useNavigate()
  const [tipos, setTipos] = useState<TipoDocumento[]>([])
  const [condicion, setCondicion] = useState<Condicion>('ESTUDIANTE')
  const [provisional, setProvisional] = useState(false)
  const [sinParentesco, setSinParentesco] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [duplicado, setDuplicado] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [datos, setDatos] = useState<Record<string, string>>({ sexo: '', nacionalidad: 'PE', tipo: 'DNI', pais: 'PE' })
  const valor = (clave: string) => datos[clave] ?? ''
  const actualizar = (clave: string, nuevo: string) => setDatos(d => ({ ...d, [clave]: nuevo }))
  async function verificar() {
    if (provisional || !valor('numero').trim()) return
    const q = new URLSearchParams({ tipo: valor('tipo'), pais: valor('pais'), numero: valor('numero') })
    try { const r = await api<{ existe: boolean }>('/duplicado-documental?' + q, sesion); setDuplicado(r.existe) }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
  }
  useEffect(() => { void api<TipoDocumento[]>('/tipos-documento', sesion).then(setTipos).catch(() => setTipos([])) }, [sesion])
  async function enviar(e: FormEvent) {
    e.preventDefault(); setError(''); setExito('')
    if (!capacidades.registrar) return
    if (!provisional && valor('tipo') === 'DNI' && !/^\d{8}$/.test(valor('numero').trim())) { setError('El DNI peruano debe tener ocho dígitos.'); return }
    if (condicion === 'APODERADO' && !valor('estudianteId')) { setError('Selecciona el estudiante vinculado.'); return }
    const cuerpo = {
      nombres: valor('nombres'), apellidos: valor('apellidos'), fechaNacimiento: valor('fechaNacimiento') || null,
      sexo: valor('sexo'), nacionalidad: valor('nacionalidad'), telefono: valor('telefono') || null,
      correo: valor('correo') || null, direccion: valor('direccion'), condicion,
      documento: provisional ? null : { tipo: valor('tipo'), pais: valor('pais'), numero: valor('numero') },
      provisional: provisional ? { motivo: valor('motivo'), sustentoTipo: valor('sustentoTipo'), sustentoReferencia: valor('sustentoReferencia') } : null,
      apoderamientoInicial: condicion === 'APODERADO' ? { estudianteId: valor('estudianteId'), sinParentesco,
        autorizacion: sinParentesco ? { autorizador: valor('autorizador'), medio: valor('medio'), fecha: new Date().toISOString(), sustentoTipo: valor('sustentoTipo') || 'VERBAL', sustentoReferencia: valor('referencia') } : null } : null,
      parentescoInicialTipo: condicion === 'APODERADO' && !sinParentesco ? valor('parentesco') : null,
    }
    setEnviando(true)
    try {
      const destino = await guardar<string>('', sesion, 'POST', cuerpo)
      if (capacidades.consultar) navegar('/personas/' + destino.split('/').at(-1))
      else { setExito('Persona registrada. La consulta de su ficha requiere un ámbito autorizado.'); setDatos({ sexo: '', nacionalidad: 'PE', tipo: 'DNI', pais: 'PE' }) }
    }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
    finally { setEnviando(false) }
  }
  if (!capacidades.registrar) return <Aviso mensaje="No tienes permiso para registrar personas." />
  return <section className="per-pagina"><Link className="per-volver" to="/personas">← Directorio</Link>
    <div className="per-titulo"><div><p className="etiqueta">ALTA INSTITUCIONAL</p><h2>Registrar persona</h2><p>El documento se comprueba antes de crear la ficha.</p></div></div>
    <Aviso mensaje={exito} tipo="exito" />
    <form className="per-panel" onSubmit={e => void enviar(e)}>
      <div className="per-panel-cabecera"><h3>Condición inicial</h3></div>
      <div className="per-formulario">
        <Campo titulo="Condición institucional"><select value={condicion} onChange={e => { setCondicion(e.target.value as Condicion); setProvisional(false) }}>
          {tiposCondicion.filter(t => t.valor !== 'APODERADO' || capacidades.gestionarVinculos).map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}</select></Campo>
        <Campo titulo="Nombres"><input required value={valor('nombres')} onChange={e => actualizar('nombres', e.target.value)} /></Campo>
        <Campo titulo="Apellidos"><input required value={valor('apellidos')} onChange={e => actualizar('apellidos', e.target.value)} /></Campo>
        {condicion === 'ESTUDIANTE' && <Campo titulo="Fecha de nacimiento"><input type="date" required value={valor('fechaNacimiento')} onChange={e => actualizar('fechaNacimiento', e.target.value)} /></Campo>}
        <Campo titulo="Teléfono"><input required={condicion !== 'ESTUDIANTE'} value={valor('telefono')} onChange={e => actualizar('telefono', e.target.value)} /></Campo>
        <Campo titulo="Correo (opcional)"><input type="email" value={valor('correo')} onChange={e => actualizar('correo', e.target.value)} /></Campo>
      </div>
      <div className="per-panel-cabecera"><h3>Identificación</h3></div>
      <div className="per-formulario">
        {condicion === 'ESTUDIANTE' && <label className="per-check"><input type="checkbox" checked={provisional} onChange={e => setProvisional(e.target.checked)} /> Sin documento oficial, con sustento provisional</label>}
        {provisional ? <>
          <Campo titulo="Motivo"><input required value={valor('motivo')} onChange={e => actualizar('motivo', e.target.value)} /></Campo>
          <Campo titulo="Tipo de sustento"><input required value={valor('sustentoTipo')} onChange={e => actualizar('sustentoTipo', e.target.value)} placeholder="Ej. Declaración jurada" /></Campo>
          <Campo titulo="Referencia de sustento"><input required value={valor('sustentoReferencia')} onChange={e => actualizar('sustentoReferencia', e.target.value)} /></Campo>
        </> : <>
          <Campo titulo="Tipo de documento"><select required value={valor('tipo')} onChange={e => actualizar('tipo', e.target.value)}>{(tipos.length ? tipos : [{ codigo: 'DNI', nombre: 'DNI', activo: true, patron: null }]).filter(t => t.activo).map(t => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}</select></Campo>
          <Campo titulo="País emisor (ISO 2)"><input required maxLength={2} value={valor('pais')} onChange={e => actualizar('pais', e.target.value.toUpperCase())} /></Campo>
          <Campo titulo="Número"><input required value={valor('numero')} onChange={e => { actualizar('numero', e.target.value); setDuplicado(false) }} onBlur={() => void verificar()} /></Campo>
        </>}
      </div>
      {duplicado && <p className="per-aviso" role="alert">Este documento ya está registrado. Busca la ficha existente antes de crear otra persona.</p>}
      {condicion === 'APODERADO' && <><div className="per-panel-cabecera"><h3>Vínculo inicial</h3></div><div className="per-formulario">
        <SelectorPersona sesion={sesion} titulo="Estudiante" condicion="ESTUDIANTE" valor={valor('estudianteId')} alElegir={id => actualizar('estudianteId', id)} />
        <label className="per-check"><input type="checkbox" checked={sinParentesco} onChange={e => setSinParentesco(e.target.checked)} /> Sin parentesco familiar</label>
        {sinParentesco ? <>
          <Campo titulo="Persona que autorizó"><input required value={valor('autorizador')} onChange={e => actualizar('autorizador', e.target.value)} /></Campo>
          <Campo titulo="Medio de comprobación"><input required value={valor('medio')} onChange={e => actualizar('medio', e.target.value)} /></Campo>
          <Campo titulo="Tipo de sustento"><input value={valor('sustentoTipo')} onChange={e => actualizar('sustentoTipo', e.target.value)} placeholder="VERBAL" /></Campo>
          <Campo titulo="Referencia del sustento"><input required value={valor('referencia')} onChange={e => actualizar('referencia', e.target.value)} /></Campo>
        </> : <Campo titulo="Parentesco"><input required value={valor('parentesco')} onChange={e => actualizar('parentesco', e.target.value)} /></Campo>}
      </div></>}
      <div className="per-acciones"><Aviso mensaje={error} /><button className="boton-principal" disabled={enviando}>{enviando ? 'Guardando…' : 'Registrar persona'}</button></div>
    </form>
  </section>
}

function FichaDialogo({ titulo, ocupado, cerrar, children }: {
  titulo: string; ocupado: boolean; cerrar: () => void; children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialogo = ref.current
    const anterior = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (dialogo && !dialogo.open) {
      if (typeof dialogo.showModal === 'function') dialogo.showModal()
      else dialogo.setAttribute('open', '')
    }
    return () => {
      if (dialogo?.open && typeof dialogo.close === 'function') dialogo.close()
      if (anterior?.isConnected) anterior.focus()
    }
  }, [])
  return <dialog ref={ref} className="per-nuevo-dialogo" aria-labelledby="per-dialogo-titulo"
    onCancel={e => { e.preventDefault(); if (!ocupado) cerrar() }}>
    <div className="per-nuevo-dialogo-cabecera"><h3 id="per-dialogo-titulo">{titulo}</h3>
      <button type="button" aria-label="Cerrar diálogo" className="per-secundario" disabled={ocupado} onClick={cerrar}>×</button></div>
    <div className="per-nuevo-dialogo-cuerpo">{children}</div>
  </dialog>
}

function Ficha({ sesion, capacidades }: { sesion: Sesion; capacidades: Capacidades }) {
  const { id } = useParams()
  return <FichaContenido key={id} id={id} sesion={sesion} capacidades={capacidades} />
}

function FichaContenido({ id, sesion, capacidades }: { id: string | undefined; sesion: Sesion; capacidades: Capacidades }) {
  const navegar = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const pestana = parametros.get('tab') === 'condiciones' ? 'condiciones' : 'datos'
  const [persona, setPersona] = useState<Persona | null>(null)
  const [error, setError] = useState('')
  const [errorEdicion, setErrorEdicion] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [accion, setAccion] = useState('')
  const [campos, setCampos] = useState<Record<string, string>>({})
  const [ocupado, setOcupado] = useState(false)
  const bloqueo = useRef(false)
  const menu = useRef<HTMLDetailsElement>(null)
  const valor = (clave: string) => campos[clave] ?? ''
  const cambiar = (clave: string, v: string) => setCampos(c => ({ ...c, [clave]: v }))
  function cancelar() { if (!bloqueo.current) { setAccion(''); setCampos({}); setErrorEdicion('') } }
  function abrir(tipo: string, inicial: Record<string, string> = {}) {
    if (bloqueo.current) return
    if (menu.current) menu.current.open = false
    setErrorEdicion(''); setExito(''); setCampos(inicial); setAccion(tipo)
  }
  function cambiarPestana(destino: 'datos' | 'condiciones') {
    if (bloqueo.current) return
    cancelar(); setParametros(destino === 'datos' ? {} : { tab: destino })
  }
  useEffect(() => {
    if (!id) { setError('Persona no encontrada'); setCargando(false); return }
    const abortar = new AbortController()
    setCargando(true); setError(''); setPersona(null)
    void api<Persona>('/' + id, sesion, { signal: abortar.signal })
      .then(p => { if (!abortar.signal.aborted) setPersona(p) })
      .catch(e => { if (!abortar.signal.aborted) setError(String(e instanceof Error ? e.message : e)) })
      .finally(() => { if (!abortar.signal.aborted) setCargando(false) })
    return () => abortar.abort()
  }, [id, sesion])
  useEffect(() => { if (!bloqueo.current) { setAccion(''); setCampos({}); setErrorEdicion('') } }, [pestana])
  useEffect(() => { if (!exito) return; const reloj = window.setTimeout(() => setExito(''), 3500); return () => window.clearTimeout(reloj) }, [exito])
  async function operar(ruta: string, metodo: 'POST' | 'PUT', cuerpo: unknown, mensaje: string) {
    if (bloqueo.current) return
    bloqueo.current = true; setOcupado(true); setErrorEdicion(''); setError('')
    try {
      await guardar<void>(ruta, sesion, metodo, typeof cuerpo === 'function' ? await cuerpo() : cuerpo)
      setAccion(''); setCampos({}); setExito(mensaje)
      try { setPersona(await api<Persona>('/' + id, sesion)) }
      catch { setPersona(null); setError('El cambio se guardó, pero no se pudo volver a consultar la ficha. Recarga la página antes de realizar otra operación.') }
    } catch (e) { setErrorEdicion(String(e instanceof Error ? e.message : e)) }
    finally { bloqueo.current = false; setOcupado(false) }
  }
  async function eliminarRegistro() {
    if (!persona || !capacidades.eliminarRegistroIncorrecto || valor('confirmacion') !== 'ELIMINAR' || bloqueo.current) return
    bloqueo.current = true; setOcupado(true); setErrorEdicion('')
    try { await api<void>('/' + persona.id, sesion, { method: 'DELETE' }); navegar('/personas', { replace: true }) }
    catch (e) { setErrorEdicion(String(e instanceof Error ? e.message : e)) }
    finally { bloqueo.current = false; setOcupado(false) }
  }
  if (cargando) return <p role="status">Cargando ficha…</p>
  if (!persona) return <Aviso mensaje={error || 'Persona no encontrada'} />
  const p = persona
  const base = '/' + p.id
  const documentoPrincipal = p.documentos.find(d => d.principal && !d.vigenteHasta)
  const apoderadoActivo = p.apoderamientos.some(a => a.apoderadoId === p.id && !a.vigenteHasta)
  const condicionesActivas = [...new Set([...p.condiciones.filter(c => !c.vigenteHasta).map(c => c.tipo), ...(apoderadoActivo ? ['APODERADO'] : [])])]
  const nombreCondicion = (tipo: string) => tiposCondicion.find(t => t.valor === tipo)?.texto ?? tipo
  const contacto = { telefono: p.telefono ?? '', correo: p.correo ?? '', direccion: p.direccion ?? '' }
  const estudianteActivo = p.condiciones.some(c => c.tipo === 'ESTUDIANTE' && !c.vigenteHasta)
  const idsVinculados = [...new Set([...p.parentescos.map(v => v.estudianteId === p.id ? v.familiarId : v.estudianteId),
    ...p.apoderamientos.map(v => v.estudianteId === p.id ? v.apoderadoId : v.estudianteId)])]
  const nombreOtro = (otro: string) => p.referencias[otro] ? `${p.referencias[otro].nombres} ${p.referencias[otro].apellidos}` : 'Persona vinculada'
  const abrirVinculo = (tipo: 'parentesco' | 'apoderamiento', otro?: string, direccion?: string) => abrir(tipo, {
    direccion: direccion ?? (estudianteActivo ? 'estudiante' : 'relacionada'), otroId: otro ?? '', otroNombre: otro ? nombreOtro(otro) : '',
  })
  const abrirIdentidad = () => abrir('identidad', { nombres: p.nombres, apellidos: p.apellidos, fechaNacimiento: p.fechaNacimiento ?? '', sexo: p.sexo ?? '', nacionalidad: p.nacionalidad ?? '' })
  const tituloAccion = accion === 'identidad' ? 'Editar identidad' : accion === 'contacto' ? 'Editar contacto' : accion === 'documento' ? 'Corregir o regularizar documento' : accion === 'condicion' ? (valor('tipo') && p.condiciones.some(c => c.tipo === valor('tipo') && c.vigenteHasta) ? 'Reactivar condición' : 'Incorporar condición') : accion === 'parentesco' ? 'Vincular parentesco' : accion === 'apoderamiento' ? 'Vincular apoderamiento' : accion.startsWith('desactivar-condicion:') ? 'Desactivar condición' : accion.startsWith('desactivar-parentesco:') ? 'Desactivar parentesco' : accion.startsWith('desactivar-apoderamiento:') ? 'Desactivar autorización' : 'Eliminar registro incorrecto'
  function enviar(e: FormEvent) {
    e.preventDefault()
    if (bloqueo.current) return
    if (accion === 'identidad' && capacidades.corregirIdentidad) void operar(base, 'PUT', async () => {
      const actual = await api<Persona>(base, sesion)
      return { telefono: actual.telefono, correo: actual.correo, direccion: actual.direccion, ...campos, fechaNacimiento: valor('fechaNacimiento') || null }
    }, 'Identidad corregida')
    else if (accion === 'contacto' && (capacidades.editarContacto || capacidades.corregirIdentidad)) {
      // El PUT /contacto actual solo persiste teléfono y correo; la dirección exige permiso de identidad.
      if (capacidades.corregirIdentidad && (!capacidades.editarContacto || valor('direccion') !== contacto.direccion)) {
        void operar(base, 'PUT', async () => {
          const actual = await api<Persona>(base, sesion)
          return { nombres: actual.nombres, apellidos: actual.apellidos, fechaNacimiento: actual.fechaNacimiento,
            sexo: actual.sexo, nacionalidad: actual.nacionalidad, ...campos }
        }, 'Contacto actualizado')
      } else void operar(base + '/contacto', 'PUT', { telefono: valor('telefono'), correo: valor('correo') }, 'Contacto actualizado')
    }
    else if (accion === 'documento' && capacidades.corregirIdentidad) void operar(base + '/documentos', 'POST', { tipo: valor('tipo'), pais: valor('pais'), numero: valor('numero') }, 'Documento actualizado')
    else if (accion === 'condicion' && capacidades.gestionarCondiciones) void operar(base + '/condiciones', 'POST', { tipo: valor('tipo') }, 'Condición incorporada')
    else if ((accion === 'parentesco' || accion === 'apoderamiento') && capacidades.gestionarVinculos) {
      const otro = valor('otroId')
      if (!otro || otro === p.id) { setErrorEdicion('Selecciona otra persona para el vínculo.'); return }
      const estudianteId = valor('direccion') === 'estudiante' ? p.id : otro
      const relacionadaId = estudianteId === p.id ? otro : p.id
      if (accion === 'parentesco') void operar('/parentescos', 'POST', { estudianteId, familiarId: relacionadaId, tipo: valor('tipo') }, 'Parentesco registrado')
      else void operar('/apoderamientos', 'POST', { estudianteId, apoderadoId: relacionadaId, sinParentesco: valor('sinParentesco') === 'true', autorizacion: valor('sinParentesco') === 'true' ? { autorizador: valor('autorizador'), medio: valor('medio'), fecha: new Date().toISOString(), sustentoTipo: valor('sustentoTipo') || 'VERBAL', sustentoReferencia: valor('referencia') } : null }, 'Apoderamiento registrado')
    } else if (accion.startsWith('desactivar-condicion:') && capacidades.gestionarCondiciones) void operar(base + '/condiciones/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Condición desactivada')
    else if (accion.startsWith('desactivar-parentesco:') && capacidades.gestionarVinculos) void operar(base + '/parentescos/' + accion.split(':')[1] + '/desactivar', 'POST', {}, 'Parentesco desactivado')
    else if (accion.startsWith('desactivar-apoderamiento:') && capacidades.gestionarVinculos) void operar(base + '/apoderamientos/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Vínculo desactivado')
    else if (accion === 'eliminar') void eliminarRegistro()
  }
  const editor = <form className="per-nuevo-editor" aria-label={tituloAccion} onSubmit={enviar}>
    <fieldset disabled={ocupado} className="per-nuevo-campos">
      {accion === 'identidad' && <><Campo titulo="Nombres"><input required value={valor('nombres')} onChange={e => cambiar('nombres', e.target.value)} /></Campo>
        <Campo titulo="Apellidos"><input required value={valor('apellidos')} onChange={e => cambiar('apellidos', e.target.value)} /></Campo>
        <Campo titulo="Nacimiento"><input type="date" required={estudianteActivo} value={valor('fechaNacimiento')} onChange={e => cambiar('fechaNacimiento', e.target.value)} /></Campo></>}
      {accion === 'contacto' && <><Campo titulo="Teléfono"><input value={valor('telefono')} onChange={e => cambiar('telefono', e.target.value)} /></Campo><Campo titulo="Correo"><input type="email" value={valor('correo')} onChange={e => cambiar('correo', e.target.value)} /></Campo><Campo titulo="Dirección"><input readOnly={!capacidades.corregirIdentidad} value={valor('direccion')} onChange={e => cambiar('direccion', e.target.value)} /></Campo></>}
      {accion === 'documento' && <><p className="per-texto per-nuevo-ancho">La corrección conserva la ficha institucional. La actualización de accesos se gestiona por la integración existente.</p><Campo titulo="Tipo"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option><option>DNI</option><option>CE</option><option>PASAPORTE</option></select></Campo><Campo titulo="País ISO 2"><input required maxLength={2} value={valor('pais')} onChange={e => cambiar('pais',e.target.value.toUpperCase())} /></Campo><Campo titulo="Número"><input required value={valor('numero')} onChange={e => cambiar('numero',e.target.value)} /></Campo></>}
      {accion === 'condicion' && <><Campo titulo="Condición"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option>{tiposCondicion.filter(t => t.valor !== 'APODERADO' && !condicionesActivas.includes(t.valor)).map(t => <option value={t.valor} key={t.valor}>{t.texto}</option>)}</select></Campo><p className="per-texto per-nuevo-ancho">La condición de apoderado se deriva de un vínculo vigente.</p></>}
      {(accion === 'parentesco' || accion === 'apoderamiento') && <><div className="per-nuevo-ancho"><Campo titulo="Papel de esta persona en el vínculo"><select value={valor('direccion')} onChange={e => setCampos(c => ({ ...c, direccion: e.target.value, otroId: '', otroNombre: '' }))}><option value="estudiante">Estudiante</option><option value="relacionada">{accion === 'parentesco' ? 'Familiar' : 'Apoderado'}</option></select></Campo></div>
        <div className="per-nuevo-ancho">{valor('otroNombre') ? <div className="per-nuevo-elegida"><strong>{valor('otroNombre')}</strong><button type="button" className="per-secundario" onClick={() => setCampos(c => ({ ...c, otroId: '', otroNombre: '' }))}>Cambiar persona</button></div> : <SelectorPersona key={valor('direccion')} sesion={sesion} titulo={valor('direccion') === 'estudiante' ? accion === 'parentesco' ? 'Familiar' : 'Apoderado' : 'Estudiante'} condicion={valor('direccion') === 'estudiante' ? undefined : 'ESTUDIANTE'} valor={valor('otroId')} alElegir={v => cambiar('otroId', v)} />}</div></>}
      {accion === 'parentesco' && <Campo titulo="Tipo de parentesco (desde el estudiante)"><input required value={valor('tipo')} onChange={e => cambiar('tipo', e.target.value)} /></Campo>}
      {accion === 'apoderamiento' && <><div className="per-nuevo-ancho"><Campo titulo="Parentesco"><select value={valor('sinParentesco')} onChange={e => cambiar('sinParentesco',e.target.value)}><option value="">Con parentesco registrado</option><option value="true">Sin parentesco, autorización institucional</option></select></Campo></div>{valor('sinParentesco') === 'true' && <><Campo titulo="Autorizador"><input required value={valor('autorizador')} onChange={e => cambiar('autorizador',e.target.value)} /></Campo><Campo titulo="Medio"><input required value={valor('medio')} onChange={e => cambiar('medio',e.target.value)} /></Campo><Campo titulo="Tipo de sustento"><input value={valor('sustentoTipo')} onChange={e => cambiar('sustentoTipo',e.target.value)} placeholder="VERBAL" /></Campo><Campo titulo="Referencia"><input required value={valor('referencia')} onChange={e => cambiar('referencia',e.target.value)} /></Campo></>}</>}
      {accion.startsWith('desactivar') && <p className="per-texto per-nuevo-ancho">Confirma la finalización de este registro: {valor('objetivo')}. Se conserva su historial.</p>}
      {(accion.startsWith('desactivar-condicion:') || accion.startsWith('desactivar-apoderamiento:')) && <Campo titulo="Motivo"><input required value={valor('motivo')} onChange={e => cambiar('motivo',e.target.value)} /></Campo>}
      {accion === 'eliminar' && <><p className="per-texto per-nuevo-ancho">Esta acción libera el documento y no se puede deshacer. Solo se permite si la persona no tiene matrículas, vínculos, asignaciones ni cambios posteriores a su registro. No es la forma de desactivar una condición.</p><div className="per-nuevo-ancho"><Campo titulo="Escribe ELIMINAR para confirmar"><input autoComplete="off" value={valor('confirmacion')} onChange={e => cambiar('confirmacion',e.target.value)} /></Campo></div></>}
    </fieldset>
    <Aviso mensaje={errorEdicion} />
    <div className="per-nuevo-acciones"><button className="per-secundario" type="button" disabled={ocupado} onClick={cancelar}>Cancelar</button><button className={accion === 'eliminar' ? 'per-peligro' : 'boton-principal'} disabled={ocupado || (accion === 'eliminar' && valor('confirmacion') !== 'ELIMINAR')}>{ocupado ? 'Guardando…' : accion === 'eliminar' ? 'Eliminar definitivamente' : accion.startsWith('desactivar') ? 'Confirmar desactivación' : 'Guardar cambios'}</button></div>
  </form>
  const campoVisible = (titulo: string, dato: string | null, ancho = false) => <div className={ancho ? 'per-nuevo-ancho' : undefined}><dt>{titulo}</dt><dd>{dato || '—'}</dd></div>
  return <section className="per-pagina per-nueva-ficha"><Link className="per-volver" to="/personas">← Directorio</Link>
    <header className="per-nuevo-perfil"><div><p className="etiqueta">FICHA INSTITUCIONAL</p><h2>{p.nombres} {p.apellidos}</h2><div className="per-nuevo-identidadlinea"><span>{documentoPrincipal ? `${documentoPrincipal.tipo} · ${documentoPrincipal.numero}` : p.provisional && !p.provisional.regularizadaEn ? 'Identificación provisional' : 'Sin documento principal vigente'}</span><span className="etiqueta">{condicionesActivas.map(nombreCondicion).join(' · ') || 'Sin condición vigente'}</span></div></div>
      {capacidades.eliminarRegistroIncorrecto && <details className="per-nuevo-menu" ref={menu}><summary>Más acciones</summary><div><button type="button" className="per-peligro" disabled={ocupado} onClick={() => abrir('eliminar')}>Eliminar registro incorrecto</button></div></details>}
    </header>
    <Aviso mensaje={error} /><Aviso mensaje={exito} tipo="exito" />
    <nav className="per-nuevo-pestanas" aria-label="Secciones de la ficha"><button type="button" disabled={ocupado} className={pestana === 'datos' ? 'active' : ''} aria-current={pestana === 'datos' ? 'page' : undefined} onClick={() => cambiarPestana('datos')}>Datos personales</button><button type="button" disabled={ocupado} className={pestana === 'condiciones' ? 'active' : ''} aria-current={pestana === 'condiciones' ? 'page' : undefined} onClick={() => cambiarPestana('condiciones')}>Condiciones institucionales</button></nav>
    {pestana === 'datos' ? <section className="per-nuevo-grid" aria-label="Datos personales">
      <div className="per-nuevo-panel">
        <section className="per-nuevo-bloque"><div className="per-nuevo-cabecera"><h3>Identidad</h3>{capacidades.corregirIdentidad && <button type="button" className="per-secundario" disabled={ocupado} onClick={accion === 'identidad' ? cancelar : abrirIdentidad}>{accion === 'identidad' ? 'Cancelar edición' : 'Editar identidad'}</button>}</div>
          {accion === 'identidad' ? editor : <dl className="per-nuevo-datos">{campoVisible('Nombres',p.nombres)}{campoVisible('Apellidos',p.apellidos)}{campoVisible('Nacimiento',p.fechaNacimiento)}{campoVisible('Sexo registrado',p.sexo)}</dl>}</section>
        <section className="per-nuevo-bloque"><div className="per-nuevo-cabecera"><h3>Contacto</h3>{(capacidades.editarContacto || capacidades.corregirIdentidad) && <button type="button" className="per-secundario" disabled={ocupado} onClick={() => accion === 'contacto' ? cancelar() : abrir('contacto', contacto)}>{accion === 'contacto' ? 'Cancelar edición' : 'Editar contacto'}</button>}</div>
          {accion === 'contacto' ? editor : <dl className="per-nuevo-datos">{campoVisible('Teléfono',p.telefono)}{campoVisible('Correo',p.correo)}{campoVisible('Dirección',p.direccion,true)}</dl>}</section>
      </div>
      <section className="per-nuevo-panel per-identificacion" aria-labelledby="per-identificacion-titulo">
        <header className="per-identificacion-cabecera"><IconoInstitucional nombre="documentos" /><h3 id="per-identificacion-titulo">Identificación</h3></header>
        <div className="per-identificacion-cuerpo">
          {!p.documentos.length && <p className="per-identificacion-vacio">Sin documentos oficiales registrados.</p>}
          {[...p.documentos].sort((a, b) => Number(!!a.vigenteHasta) - Number(!!b.vigenteHasta) || Number(b.principal) - Number(a.principal)).map(d =>
            <div className={`per-identificacion-documento${d.vigenteHasta ? ' historico' : d.principal ? ' principal' : ''}`} key={d.id}>
              <div className="per-identificacion-icono"><IconoInstitucional nombre="identidad" /></div>
              <div className="per-identificacion-datos">
                <div className="per-identificacion-linea"><span className="per-identificacion-tipo">{d.tipo} · {d.pais}</span>
                  <span className={`per-identificacion-estado${d.vigenteHasta ? ' historico' : ' vigente'}`}><IconoInstitucional nombre={d.vigenteHasta ? 'informacion' : 'confirmado'} />{d.vigenteHasta ? 'Histórico' : 'Vigente'}</span>
                </div>
                <strong className="per-identificacion-numero">{d.numero}</strong>
                {d.principal && !d.vigenteHasta && <span className="per-identificacion-principal">Documento principal</span>}
                <small>Desde <Fecha valor={d.vigenteDesde} />{d.vigenteHasta && <> · Hasta <Fecha valor={d.vigenteHasta} /></>}</small>
              </div>
            </div>)}
          {p.provisional && <div className={`per-identificacion-provisional${p.provisional.regularizadaEn ? ' regularizada' : ''}`}>
            <IconoInstitucional nombre={p.provisional.regularizadaEn ? 'informacion' : 'pendiente'} />
            <div><strong>Identificación provisional{p.provisional.regularizadaEn ? ' · Regularizada' : ''}</strong><p>{p.provisional.motivo}</p><small>Sustento: {p.provisional.sustentoReferencia}</small></div>
          </div>}
        </div>
        {capacidades.corregirIdentidad && <footer className="per-identificacion-pie"><button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrir('documento', { tipo: documentoPrincipal?.tipo ?? 'DNI', pais: documentoPrincipal?.pais ?? 'PE', numero: documentoPrincipal?.numero ?? '' })}>Corregir o regularizar documento</button></footer>}
      </section>
    </section> : <section className="per-nuevo-pila" aria-label="Condiciones institucionales">
      <section className="per-nuevo-panel"><div className="per-nuevo-franja"><div><h3>Condiciones institucionales</h3><p>Una persona puede tener más de una condición.</p></div>{capacidades.gestionarCondiciones && <button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrir('condicion')}>+ Incorporar</button>}</div>
        <div className="per-nuevo-bloque">{p.condiciones.map(c => <div className="per-nuevo-condicion" key={c.id}><div><strong className="etiqueta">{nombreCondicion(c.tipo)}</strong><small className={c.vigenteHasta ? 'per-nuevo-inactivo' : 'per-nuevo-vigente'}><Fecha valor={c.vigenteDesde} /> — <Fecha valor={c.vigenteHasta} />{c.motivoDesactivacion && ' · ' + c.motivoDesactivacion}</small></div>{!c.vigenteHasta && capacidades.gestionarCondiciones && <button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrir('desactivar-condicion:' + c.id, { objetivo: nombreCondicion(c.tipo) })}>Desactivar condición</button>}{c.vigenteHasta && c.tipo !== 'APODERADO' && !condicionesActivas.includes(c.tipo) && p.condiciones.find(v => v.tipo === c.tipo)?.id === c.id && capacidades.gestionarCondiciones && <button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrir('condicion', { tipo: c.tipo })}>Reactivar condición</button>}</div>)}
          {apoderadoActivo && <div className="per-nuevo-condicion"><div><strong className="etiqueta">APODERADO</strong><small className="per-nuevo-vigente">Activo por vínculo vigente</small></div><span className="per-texto">Gestionar en vínculos</span></div>}
          {!p.condiciones.length && !apoderadoActivo && <p className="per-texto">Sin condiciones registradas.</p>}
        </div>
      </section>
      <section className="per-nuevo-panel"><div className="per-nuevo-franja"><div><h3>Vínculos institucionales</h3><p>Parentescos y autorizaciones, sin duplicar personas.</p></div>{capacidades.gestionarVinculos && <div className="per-nuevo-botones"><button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrirVinculo('parentesco')}>+ Parentesco</button><button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrirVinculo('apoderamiento')}>+ Apoderamiento</button></div>}</div>
        {!idsVinculados.length ? <p className="per-vacio">Sin vínculos registrados.</p> : <div className="per-nuevo-scroll" role="region" aria-label="Listado de vínculos" tabIndex={0}><table className="per-nuevo-tabla"><thead><tr><th scope="col">Persona vinculada</th><th scope="col">Parentesco</th><th scope="col">Apoderamiento</th></tr></thead><tbody>
          {idsVinculados.map(otro => { const referencia = p.referencias[otro]; const parentescos = p.parentescos.filter(v => (v.estudianteId === p.id ? v.familiarId : v.estudianteId) === otro); const apoderamientos = p.apoderamientos.filter(v => (v.estudianteId === p.id ? v.apoderadoId : v.estudianteId) === otro); const orientacion = parentescos[0]?.estudianteId ?? apoderamientos[0]?.estudianteId; const direccion = orientacion === p.id ? 'estudiante' : 'relacionada'; return <tr key={otro}>
            <td><strong><NombreVinculado id={otro} referencia={referencia} /></strong><small>{referencia?.numeroDocumento ? <><DocumentoVinculado referencia={referencia} />Ficha institucional</> : 'Ficha institucional'}</small></td>
            <td>{parentescos.length ? parentescos.map(v => <div className="per-nuevo-vinculo" key={v.id}><strong>{parentescoVisible(v.tipo,v.estudianteId === p.id,referencia?.sexo)}</strong>{v.estudianteId !== p.id && <small>Relación registrada: {v.tipo} del estudiante</small>}<small className={v.vigenteHasta ? 'per-nuevo-inactivo' : 'per-nuevo-vigente'}>{v.vigenteHasta ? 'Finalizado' : 'Vigente'} · <Fecha valor={v.vigenteDesde} />{v.vigenteHasta && <> — <Fecha valor={v.vigenteHasta} /></>}</small>{!v.vigenteHasta && capacidades.gestionarVinculos && <button type="button" className="per-secundario" disabled={ocupado} onClick={() => abrir('desactivar-parentesco:' + v.id, { objetivo: nombreOtro(otro) })}>Desactivar parentesco</button>}</div>) : <span className="per-texto">Sin parentesco registrado</span>}
              {!parentescos.some(v => !v.vigenteHasta) && capacidades.gestionarVinculos && <button type="button" className="per-secundario" disabled={ocupado} onClick={() => abrirVinculo('parentesco',otro,direccion)}>Vincular parentesco</button>}</td>
            <td>{apoderamientos.length ? apoderamientos.map(v => <div className="per-nuevo-vinculo" key={v.id}><strong>{v.estudianteId === p.id ? 'Apoderado' : 'Estudiante'} · {v.sinParentesco ? 'Autorización verificada' : 'Con parentesco'}</strong><small className={v.vigenteHasta ? 'per-nuevo-inactivo' : 'per-nuevo-vigente'}>{v.vigenteHasta ? 'Finalizado' : 'Vigente'} · <Fecha valor={v.vigenteDesde} />{v.vigenteHasta && <> — <Fecha valor={v.vigenteHasta} /></>}</small>{v.autorizador && <small>Autorizador: {v.autorizador}{v.medio && ' · ' + v.medio}</small>}{v.verificadoEn && <small>Verificado: <Fecha valor={v.verificadoEn} /></small>}{!v.vigenteHasta && capacidades.gestionarVinculos && <button className="per-secundario" type="button" disabled={ocupado} onClick={() => abrir('desactivar-apoderamiento:' + v.id, { objetivo: nombreOtro(otro) })}>Desactivar autorización</button>}</div>) : <span className="per-texto">Sin apoderamiento registrado</span>}
              {!apoderamientos.some(v => !v.vigenteHasta) && capacidades.gestionarVinculos && <button type="button" className="per-secundario" disabled={ocupado} onClick={() => abrirVinculo('apoderamiento',otro,direccion)}>Vincular autorización</button>}</td>
          </tr> })}</tbody></table></div>}
        <p className="per-nuevo-nota">El parentesco y el apoderamiento son independientes. Un parentesco por sí solo no concede autorización.</p>
      </section>
      {p.condiciones.some(c => c.tipo === 'DOCENTE') && <section className="per-nuevo-panel per-nuevo-bloque"><h3>Cuenta docente de Aula</h3><EstadoIntegracion sesion={sesion} tipo="PERSONA" id={p.id} /></section>}
    </section>}
    {accion && accion !== 'identidad' && accion !== 'contacto' && <FichaDialogo titulo={tituloAccion} ocupado={ocupado} cerrar={cancelar}>{editor}</FichaDialogo>}
  </section>
}

export function PersonasModulo({ sesion }: { sesion: Sesion }) {
  const estadoDirectorio = useEstadoDirectorio()
  const [capacidades, setCapacidades] = useState<Capacidades | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void api<Capacidades>('/capacidades', sesion).then(setCapacidades).catch(e => setError(String(e.message ?? e))) }, [sesion])
  if (error) return <Aviso mensaje={error} />
  if (!capacidades) return <p role="status">Cargando Personas…</p>
  return <Routes><Route index element={<Directorio sesion={sesion} capacidades={capacidades} estado={estadoDirectorio} />} />
    <Route path="nueva" element={<Registro sesion={sesion} capacidades={capacidades} />} />
    <Route path=":id" element={<Ficha sesion={sesion} capacidades={capacidades} />} /></Routes>
}
