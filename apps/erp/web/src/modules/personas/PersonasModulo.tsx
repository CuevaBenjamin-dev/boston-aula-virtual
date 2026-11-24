import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import type { Sesion } from '../../shared/api/sesion'
import { api, guardar, type Capacidades, type Condicion, type Pagina, type Persona, type ReferenciaPersona, type TipoDocumento } from './api'
import { EstadoIntegracion } from '../../shared/ui/EstadoIntegracion'
import './directorio.css'

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
  return <section className="per-pagina">
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

function Ficha({ sesion, capacidades }: { sesion: Sesion; capacidades: Capacidades }) {
  const { id } = useParams()
  const navegar = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const pestana = parametros.get('tab') === 'condiciones' ? 'condiciones' : 'datos'
  const [persona, setPersona] = useState<Persona | null>(null)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [accion, setAccion] = useState('')
  const [editandoCondiciones, setEditandoCondiciones] = useState(false)
  const [campos, setCampos] = useState<Record<string, string>>({})
  const [confirmandoEliminar, setConfirmandoEliminar] = useState(false)
  const [confirmacion, setConfirmacion] = useState('')
  const [eliminando, setEliminando] = useState(false)
  const valor = (clave: string) => campos[clave] ?? ''
  const cambiar = (clave: string, v: string) => setCampos(c => ({ ...c, [clave]: v }))
  function cambiarPestana(destino: 'datos' | 'condiciones') {
    setAccion(''); setCampos({}); setEditandoCondiciones(false)
    setParametros(destino === 'datos' ? {} : { tab: destino })
  }
  async function recargar() {
    if (!id) return
    setCargando(true); setError('')
    try { setPersona(await api<Persona>('/' + id, sesion)) }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
    finally { setCargando(false) }
  }
  useEffect(() => { void recargar() }, [id, sesion])
  async function operar(ruta: string, metodo: 'POST' | 'PUT', cuerpo: unknown, mensaje: string) {
    try { setError(''); await guardar<void>(ruta, sesion, metodo, cuerpo); setExito(mensaje); setAccion(''); setCampos({}); await recargar() }
    catch (e) { setError(String(e instanceof Error ? e.message : e)) }
  }
  async function eliminarRegistro() {
    if (!persona || confirmacion !== 'ELIMINAR') return
    setError(''); setEliminando(true)
    try {
      await api<void>('/' + persona.id, sesion, { method: 'DELETE' })
      navegar('/personas', { replace: true })
    } catch (e) {
      setError(String(e instanceof Error ? e.message : e))
      setEliminando(false)
    }
  }
  if (cargando) return <p role="status">Cargando ficha…</p>
  if (!persona) return <Aviso mensaje={error || 'Persona no encontrada'} />
  const documentoPrincipal = persona.documentos.find(d => d.principal && !d.vigenteHasta) ?? persona.documentos[0]
  const editor = accion && <form className="per-editor-inline" aria-label="Editar ficha" onSubmit={e => { e.preventDefault(); const base='/' + persona.id
      if ((accion === 'parentesco' || accion === 'apoderamiento') &&
          (!valor('estudianteId') || !valor(accion === 'parentesco' ? 'familiarId' : 'apoderadoId'))) {
        setError('Selecciona las dos personas del vínculo.'); return
      }
      if (accion === 'contacto') void operar(base + '/contacto', 'PUT', campos, 'Contacto actualizado')
      else if (accion === 'identidad') void operar(base, 'PUT', { ...campos, fechaNacimiento: valor('fechaNacimiento') || null }, 'Identidad corregida')
      else if (accion === 'documento') void operar(base + '/documentos', 'POST', { tipo: valor('tipo'), pais: valor('pais'), numero: valor('numero') }, 'Documento actualizado')
      else if (accion === 'condicion') void operar(base + '/condiciones', 'POST', { tipo: valor('tipo') }, 'Condición incorporada')
      else if (accion === 'parentesco') void operar('/parentescos', 'POST', { estudianteId: valor('estudianteId'), familiarId: valor('familiarId'), tipo: valor('tipo') }, 'Parentesco registrado')
      else if (accion === 'apoderamiento') void operar('/apoderamientos', 'POST', { estudianteId: valor('estudianteId'), apoderadoId: valor('apoderadoId'), sinParentesco: valor('sinParentesco') === 'true', autorizacion: valor('sinParentesco') === 'true' ? { autorizador: valor('autorizador'), medio: valor('medio'), fecha: new Date().toISOString(), sustentoTipo: valor('sustentoTipo') || 'VERBAL', sustentoReferencia: valor('referencia') } : null }, 'Apoderamiento registrado')
      else if (accion.startsWith('desactivar-condicion:')) void operar(base + '/condiciones/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Condición desactivada')
      else if (accion.startsWith('desactivar-apoderamiento:')) void operar(base + '/apoderamientos/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Vínculo desactivado')
    }}><h4>{accion.startsWith('desactivar') ? 'Confirmar desactivación' : 'Actualizar ficha'}</h4><div className="per-formulario">
      {accion === 'identidad' && <><Campo titulo="Nombres"><input required value={valor('nombres')} onChange={e => cambiar('nombres',e.target.value)} /></Campo><Campo titulo="Apellidos"><input required value={valor('apellidos')} onChange={e => cambiar('apellidos',e.target.value)} /></Campo><Campo titulo="Nacimiento"><input type="date" required={persona.condiciones.some(c=>c.tipo==='ESTUDIANTE' && !c.vigenteHasta)} value={valor('fechaNacimiento')} onChange={e => cambiar('fechaNacimiento', e.target.value)} /></Campo></>}
      {(accion === 'identidad' || accion === 'contacto') && <><Campo titulo="Teléfono"><input value={valor('telefono')} onChange={e => cambiar('telefono', e.target.value)} /></Campo><Campo titulo="Correo"><input type="email" value={valor('correo')} onChange={e => cambiar('correo', e.target.value)} /></Campo><Campo titulo="Dirección"><input value={valor('direccion')} onChange={e => cambiar('direccion', e.target.value)} /></Campo></>}
      {accion === 'documento' && <><Campo titulo="Tipo"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option><option>DNI</option><option>CE</option><option>PASAPORTE</option></select></Campo><Campo titulo="País ISO 2"><input required maxLength={2} value={valor('pais')} onChange={e => cambiar('pais',e.target.value.toUpperCase())} /></Campo><Campo titulo="Número"><input required value={valor('numero')} onChange={e => cambiar('numero',e.target.value)} /></Campo></>}
      {accion === 'condicion' && <Campo titulo="Condición"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option>{tiposCondicion.filter(t => t.valor !== 'APODERADO').map(t => <option value={t.valor} key={t.valor}>{t.texto}</option>)}</select></Campo>}
      {(accion === 'parentesco' || accion === 'apoderamiento') && <><SelectorPersona sesion={sesion} titulo="Estudiante" condicion="ESTUDIANTE" valor={valor('estudianteId')} alElegir={id => cambiar('estudianteId',id)} /><SelectorPersona sesion={sesion} titulo={accion === 'parentesco' ? 'Familiar' : 'Apoderado'} valor={valor(accion === 'parentesco' ? 'familiarId' : 'apoderadoId')} alElegir={id => cambiar(accion === 'parentesco' ? 'familiarId' : 'apoderadoId',id)} /></>}
      {accion === 'parentesco' && <Campo titulo="Tipo de parentesco"><input required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)} /></Campo>}
      {accion === 'apoderamiento' && <><Campo titulo="Parentesco"><select value={valor('sinParentesco')} onChange={e => cambiar('sinParentesco',e.target.value)}><option value="">Con parentesco registrado</option><option value="true">Sin parentesco, autorización institucional</option></select></Campo>{valor('sinParentesco') === 'true' && <><Campo titulo="Autorizador"><input required value={valor('autorizador')} onChange={e => cambiar('autorizador',e.target.value)} /></Campo><Campo titulo="Medio"><input required value={valor('medio')} onChange={e => cambiar('medio',e.target.value)} /></Campo><Campo titulo="Tipo de sustento"><input value={valor('sustentoTipo')} onChange={e => cambiar('sustentoTipo',e.target.value)} placeholder="VERBAL" /></Campo><Campo titulo="Referencia"><input required value={valor('referencia')} onChange={e => cambiar('referencia',e.target.value)} /></Campo></>}</>}
      {accion.startsWith('desactivar') && <Campo titulo="Motivo"><input required value={valor('motivo')} onChange={e => cambiar('motivo',e.target.value)} /></Campo>}
    </div><div className="per-acciones"><button className="per-secundario" type="button" onClick={() => setAccion('')}>Cancelar</button><button className="boton-principal">Guardar cambios</button></div></form>
  return <section className="per-pagina"><Link className="per-volver" to="/personas">← Directorio</Link>
    <div className="per-titulo"><div><p className="etiqueta">FICHA INSTITUCIONAL</p><h2>{persona.nombres} {persona.apellidos}</h2><p>{documentoPrincipal ? `${documentoPrincipal.tipo} · ${documentoPrincipal.numero}` : 'Identificación provisional'}</p></div>
      {capacidades.eliminarRegistroIncorrecto && <button className="per-peligro" type="button" onClick={() => setConfirmandoEliminar(true)}>Eliminar registro incorrecto</button>}</div>
    <Aviso mensaje={error} /><Aviso mensaje={exito} tipo="exito" />
    {confirmandoEliminar && <section className="per-panel per-confirmar-eliminacion" aria-label="Confirmar eliminación de registro incorrecto">
      <h3>Eliminar registro incorrecto</h3>
      <p>Esta acción libera el documento y no se puede deshacer. Solo se permite si la persona no tiene matrículas, vínculos, asignaciones ni cambios posteriores a su registro.</p>
      <Campo titulo="Escribe ELIMINAR para confirmar"><input value={confirmacion} onChange={e => setConfirmacion(e.target.value)} autoComplete="off" /></Campo>
      <div className="per-acciones"><button className="per-secundario" type="button" onClick={() => { setConfirmandoEliminar(false); setConfirmacion('') }}>Conservar registro</button>
        <button className="per-peligro" type="button" disabled={confirmacion !== 'ELIMINAR' || eliminando} onClick={() => void eliminarRegistro()}>{eliminando ? 'Eliminando…' : 'Eliminar definitivamente'}</button></div>
    </section>}
    <nav className="per-ficha-pestanas" aria-label="Secciones de la ficha">
      <button type="button" className={pestana === 'datos' ? 'active' : ''} aria-current={pestana === 'datos' ? 'page' : undefined} onClick={() => cambiarPestana('datos')}><span>01</span> Datos personales</button>
      <button type="button" className={pestana === 'condiciones' ? 'active' : ''} aria-current={pestana === 'condiciones' ? 'page' : undefined} onClick={() => cambiarPestana('condiciones')}><span>02</span> Condiciones institucionales</button>
    </nav>
    {pestana === 'datos' ? <section className="per-panel per-ficha-panel" aria-label="Datos personales"><div className="per-ficha-toolbar"><p>Identidad, contacto y documentos</p>{(capacidades.editarContacto || capacidades.corregirIdentidad) && <button className="per-secundario" type="button" onClick={() => {
      if (accion === 'identidad' || accion === 'contacto') { setAccion(''); setCampos({}); return }
      setAccion(capacidades.corregirIdentidad ? 'identidad' : 'contacto')
      setCampos({ nombres: persona.nombres, apellidos: persona.apellidos, fechaNacimiento: persona.fechaNacimiento ?? '', sexo: persona.sexo ?? '', nacionalidad: persona.nacionalidad ?? '', telefono: persona.telefono ?? '', correo: persona.correo ?? '', direccion: persona.direccion ?? '' })
    }}>{accion === 'identidad' || accion === 'contacto' ? 'Cancelar edición' : 'Editar datos'}</button>}</div>
      <div className="per-ficha-identidad-grid"><div>
        {accion === 'identidad' || accion === 'contacto' ? editor : <dl className="per-ficha-datos-grid">
          <div><dt>Nombres</dt><dd>{persona.nombres}</dd></div><div><dt>Apellidos</dt><dd>{persona.apellidos}</dd></div>
          <div><dt>Nacimiento</dt><dd>{persona.fechaNacimiento || '—'}</dd></div><div><dt>Sexo registrado</dt><dd>{persona.sexo || '—'}</dd></div>
          <div><dt>Teléfono</dt><dd>{persona.telefono || '—'}</dd></div><div><dt>Correo</dt><dd>{persona.correo || '—'}</dd></div>
          <div className="per-ficha-dato-ancho"><dt>Dirección</dt><dd>{persona.direccion || '—'}</dd></div>
        </dl>}
      </div><div className="per-ficha-documentos"><h3>Identificación</h3>
        {persona.documentos.map(d => <div className="per-fila" key={d.id}><div><strong>{d.tipo} · {d.pais} · {d.numero}</strong><small><Fecha valor={d.vigenteDesde} /> — <Fecha valor={d.vigenteHasta} /></small></div>{d.principal && !d.vigenteHasta && <span className="per-chip">Principal</span>}</div>)}
        {persona.provisional && <p className="per-texto">Identificación provisional: {persona.provisional.motivo} · {persona.provisional.sustentoReferencia} {persona.provisional.regularizadaEn && '(regularizada)'}</p>}
        {capacidades.corregirIdentidad && <button className="per-secundario" type="button" onClick={() => { setAccion(accion === 'documento' ? '' : 'documento'); setCampos({ tipo: documentoPrincipal?.tipo ?? '', pais: documentoPrincipal?.pais ?? 'PE', numero: documentoPrincipal?.numero ?? '' }) }}>{accion === 'documento' ? 'Cancelar edición' : 'Corregir o regularizar documento'}</button>}
        {accion === 'documento' && editor}
      </div></div>
    </section> : <section className="per-panel per-ficha-panel" aria-label="Condiciones institucionales"><div className="per-ficha-toolbar"><p>Condiciones y vínculos de esta persona</p>{(capacidades.gestionarCondiciones || capacidades.gestionarVinculos) && <button className="per-secundario" type="button" onClick={() => { setEditandoCondiciones(!editandoCondiciones); setAccion(''); setCampos({}) }}>{editandoCondiciones ? 'Terminar edición' : 'Editar datos'}</button>}</div>
      <div className="per-ficha-relaciones">
      <div className="per-ficha-grupo"><div className="per-ficha-grupo-cabecera"><h4>Condiciones</h4>{editandoCondiciones && capacidades.gestionarCondiciones && <button className="per-secundario" type="button" onClick={() => { setAccion('condicion'); setCampos({}) }}>+ Incorporar</button>}</div>
        {persona.condiciones.map(c => <div className="per-fila" key={c.id}><div><strong>{c.tipo}</strong><small><Fecha valor={c.vigenteDesde} /> — <Fecha valor={c.vigenteHasta} /> {c.motivoDesactivacion && '· ' + c.motivoDesactivacion}</small></div>
          {!c.vigenteHasta && editandoCondiciones && capacidades.gestionarCondiciones && <button className="per-secundario" type="button" onClick={() => { setAccion('desactivar-condicion:' + c.id); setCampos({}) }}>Desactivar</button>}</div>)}
        {persona.apoderamientos.some(a => a.apoderadoId === persona.id && !a.vigenteHasta) && <div className="per-fila"><strong>APODERADO · activo por vínculo vigente</strong></div>}
        {!persona.condiciones.length && !persona.apoderamientos.length && <p className="per-vacio">Sin condiciones registradas.</p>}
        {editandoCondiciones && (accion === 'condicion' || accion.startsWith('desactivar-condicion:')) && editor}
      </div>
      <div className="per-ficha-grupo"><div className="per-ficha-grupo-cabecera"><h4>Parentescos</h4>{editandoCondiciones && capacidades.gestionarVinculos && <button className="per-secundario" type="button" onClick={() => { setAccion('parentesco'); setCampos({}) }}>+ Vincular</button>}</div>
        {persona.parentescos.map(p => { const esEstudiante = p.estudianteId === persona.id; const otroId = esEstudiante ? p.familiarId : p.estudianteId; const referencia = persona.referencias[otroId]; return <div className="per-fila per-vinculo" key={p.id}><div><strong>{parentescoVisible(p.tipo, esEstudiante, referencia?.sexo)} · <NombreVinculado id={otroId} referencia={referencia} /></strong><small>{esEstudiante ? 'Familiar' : 'Estudiante'} · <DocumentoVinculado referencia={referencia} />{p.vigenteHasta ? 'Finalizado' : 'Vigente'}{!esEstudiante && <span title={`Parentesco registrado desde la perspectiva del estudiante: ${p.tipo}`}> · Relación registrada: {p.tipo} del estudiante</span>}</small></div>{!p.vigenteHasta && editandoCondiciones && capacidades.gestionarVinculos && <button className="per-secundario" type="button" onClick={() => void operar('/' + persona.id + '/parentescos/' + p.id + '/desactivar', 'POST', {}, 'Parentesco desactivado')}>Desactivar</button>}</div> })}
        {!persona.parentescos.length && <p className="per-vacio">Sin parentescos.</p>}
        {editandoCondiciones && accion === 'parentesco' && editor}
      </div>
      <div className="per-ficha-grupo"><div className="per-ficha-grupo-cabecera"><h4>Apoderamientos</h4>{editandoCondiciones && capacidades.gestionarVinculos && <button className="per-secundario" type="button" onClick={() => { setAccion('apoderamiento'); setCampos({}) }}>+ Vincular</button>}</div>
        {persona.apoderamientos.map(a => { const esEstudiante = a.estudianteId === persona.id; const otroId = esEstudiante ? a.apoderadoId : a.estudianteId; const referencia = persona.referencias[otroId]; return <div className="per-fila per-vinculo" key={a.id}><div><strong>{esEstudiante ? 'Apoderado' : 'Estudiante'} · <NombreVinculado id={otroId} referencia={referencia} /></strong><small><DocumentoVinculado referencia={referencia} />{a.sinParentesco ? 'Autorización verificada' : 'Con parentesco'} · {a.vigenteHasta ? 'Finalizado' : 'Vigente'}</small></div>{!a.vigenteHasta && editandoCondiciones && capacidades.gestionarVinculos && <button className="per-secundario" type="button" onClick={() => { setAccion('desactivar-apoderamiento:' + a.id); setCampos({}) }}>Desactivar</button>}</div> })}
        {!persona.apoderamientos.length && <p className="per-vacio">Sin apoderamientos.</p>}
        {editandoCondiciones && (accion === 'apoderamiento' || accion.startsWith('desactivar-apoderamiento:')) && editor}
      </div>
      </div>
      {persona.condiciones.some(c => c.tipo === 'DOCENTE') && <div className="per-ficha-integracion"><h3>Cuenta docente de Aula</h3><EstadoIntegracion sesion={sesion} tipo="PERSONA" id={persona.id} /></div>}
    </section>}

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
