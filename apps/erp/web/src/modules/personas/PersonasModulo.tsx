import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import type { Sesion } from '../../shared/api/sesion'
import { api, guardar, type Capacidades, type Condicion, type Pagina, type Persona, type TipoDocumento } from './api'

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

function SelectorPersona({ sesion, titulo, condicion, valor, alElegir }: {
  sesion: Sesion; titulo: string; condicion?: Condicion; valor: string; alElegir: (id: string) => void
}) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState<Pagina | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    if (texto.trim().length < 2 || valor) { setResultados(null); return }
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
      <><small>Escribe al menos dos caracteres y elige una persona.</small>
        <Aviso mensaje={error} />
        {resultados && <div className="per-opciones" role="group" aria-label={`Resultados para ${titulo}`}>
          {resultados.resultados.length === 0 ? <p>Sin resultados.</p> : resultados.resultados.map(p =>
            <button type="button" key={p.id} onClick={() => { alElegir(p.id); setTexto(`${p.nombres} ${p.apellidos}`) }}>
              <strong>{p.nombres} {p.apellidos}</strong><small>{p.documento ?? 'Identificación provisional'}</small>
            </button>)}
        </div>}</>}
  </div>
}

function Directorio({ sesion, capacidades }: { sesion: Sesion; capacidades: Capacidades }) {
  const [buscar, setBuscar] = useState('')
  const [condicion, setCondicion] = useState('')
  const [vigente, setVigente] = useState('')
  const [pagina, setPagina] = useState(0)
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
  return <section className="per-pagina">
    <div className="per-titulo"><div><p className="etiqueta">REGISTRO INSTITUCIONAL</p><h2>Personas</h2>
      <p>Consulta identidades, condiciones y vínculos institucionales.</p></div>
      {capacidades.registrar && <Link className="boton-principal" to="nueva">+ Registrar persona</Link>}</div>
    {!capacidades.consultar ? <div className="per-panel">
      {capacidades.personaPropiaId ? <p className="per-texto">Puedes consultar y actualizar tus datos de contacto en tu ficha personal. <Link to={capacidades.personaPropiaId}>Abrir mi ficha →</Link></p>
        : <Aviso mensaje="Tu cuenta aún no dispone de un ámbito verificable para consultar el directorio. Contacta a Dirección." />}
    </div> : <>
      <form className="per-panel per-filtros" onSubmit={e => e.preventDefault()}>
        <Campo titulo="Nombre o documento"><input value={buscar} onChange={e => { setBuscar(e.target.value); setPagina(0) }} placeholder="Busca por nombre, apellido o documento" /></Campo>
        <Campo titulo="Condición"><select value={condicion} onChange={e => { setCondicion(e.target.value); setPagina(0) }}>
          <option value="">Todas</option>{tiposCondicion.map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
        </select></Campo>
        <Campo titulo="Vigencia"><select value={vigente} onChange={e => { setVigente(e.target.value); setPagina(0) }}>
          <option value="">Todas</option><option value="true">Activa</option><option value="false">Histórica</option>
        </select></Campo>
      </form>
      <section className="per-panel" aria-busy={cargando}>
        <div className="per-panel-cabecera"><h3>Resultados</h3><span>{datos?.total ?? 0} personas</span></div>
        <Aviso mensaje={error} />
        {cargando && <p role="status">Buscando personas…</p>}
        {!cargando && datos?.resultados.length === 0 && <p className="per-vacio">No hay personas para estos filtros.</p>}
        {!cargando && datos?.resultados.map(p => <div className="per-fila" key={p.id}>
          <div><strong>{p.apellidos}, {p.nombres}</strong><small>{p.documento ?? 'Identificación provisional'} · {p.condiciones.join(', ') || 'Sin condición vigente'}</small></div>
          <Link to={p.id}>Ver ficha <span aria-hidden="true">→</span></Link>
        </div>)}
        {!!datos?.total && <nav className="per-paginacion" aria-label="Paginación">
          <button type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {Math.ceil(datos.total / datos.tamano)}</span>
          <button type="button" disabled={(pagina + 1) * datos.tamano >= datos.total || cargando} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>}
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
      nombres: valor('nombres'), apellidos: valor('apellidos'), fechaNacimiento: valor('fechaNacimiento'),
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
    <div className="per-titulo"><div><p className="etiqueta">ALTA INSTITUCIONAL</p><h2>Registrar persona</h2><p>El documento se comprueba antes de crear un nuevo UUID.</p></div></div>
    <Aviso mensaje={exito} tipo="exito" />
    <form className="per-panel" onSubmit={e => void enviar(e)}>
      <div className="per-panel-cabecera"><h3>Condición inicial</h3></div>
      <div className="per-formulario">
        <Campo titulo="Condición institucional"><select value={condicion} onChange={e => { setCondicion(e.target.value as Condicion); setProvisional(false) }}>
          {tiposCondicion.filter(t => t.valor !== 'APODERADO' || capacidades.gestionarVinculos).map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}</select></Campo>
        <Campo titulo="Nombres"><input required value={valor('nombres')} onChange={e => actualizar('nombres', e.target.value)} /></Campo>
        <Campo titulo="Apellidos"><input required value={valor('apellidos')} onChange={e => actualizar('apellidos', e.target.value)} /></Campo>
        <Campo titulo="Fecha de nacimiento"><input type="date" required value={valor('fechaNacimiento')} onChange={e => actualizar('fechaNacimiento', e.target.value)} /></Campo>
        <Campo titulo="Sexo"><select required value={valor('sexo')} onChange={e => actualizar('sexo', e.target.value)}><option value="">Seleccionar</option><option value="F">Femenino</option><option value="M">Masculino</option><option value="OTRO">Otro</option></select></Campo>
        <Campo titulo="Nacionalidad"><input required value={valor('nacionalidad')} onChange={e => actualizar('nacionalidad', e.target.value)} /></Campo>
        <Campo titulo="Dirección"><input required value={valor('direccion')} onChange={e => actualizar('direccion', e.target.value)} /></Campo>
        <Campo titulo="Teléfono"><input required={condicion !== 'ESTUDIANTE'} value={valor('telefono')} onChange={e => actualizar('telefono', e.target.value)} /></Campo>
        <Campo titulo="Correo"><input type="email" required={condicion === 'DOCENTE' || condicion === 'PERSONAL_INSTITUCIONAL'} value={valor('correo')} onChange={e => actualizar('correo', e.target.value)} /></Campo>
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
  const [persona, setPersona] = useState<Persona | null>(null)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [accion, setAccion] = useState('')
  const [campos, setCampos] = useState<Record<string, string>>({})
  const valor = (clave: string) => campos[clave] ?? ''
  const cambiar = (clave: string, v: string) => setCampos(c => ({ ...c, [clave]: v }))
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
  if (cargando) return <p role="status">Cargando ficha…</p>
  if (!persona) return <Aviso mensaje={error || 'Persona no encontrada'} />
  return <section className="per-pagina"><Link className="per-volver" to="/personas">← Directorio</Link>
    <div className="per-titulo"><div><p className="etiqueta">FICHA INSTITUCIONAL</p><h2>{persona.nombres} {persona.apellidos}</h2><p>UUID {persona.id}</p></div></div>
    <Aviso mensaje={error} /><Aviso mensaje={exito} tipo="exito" />
    <div className="per-dos-columnas"><section className="per-panel"><div className="per-panel-cabecera"><h3>Datos personales</h3></div>
      <dl className="per-datos"><dt>Nacimiento</dt><dd>{persona.fechaNacimiento}</dd><dt>Sexo</dt><dd>{persona.sexo}</dd><dt>Nacionalidad</dt><dd>{persona.nacionalidad}</dd><dt>Teléfono</dt><dd>{persona.telefono || '—'}</dd><dt>Correo</dt><dd>{persona.correo || '—'}</dd><dt>Dirección</dt><dd>{persona.direccion}</dd></dl>
      {capacidades.editarContacto && <button className="per-secundario" onClick={() => { setAccion('contacto'); setCampos({ telefono: persona.telefono ?? '', correo: persona.correo ?? '', direccion: persona.direccion }) }}>Editar contacto</button>}
      {capacidades.corregirIdentidad && <button className="per-secundario" onClick={() => { setAccion('identidad'); setCampos({ nombres: persona.nombres, apellidos: persona.apellidos, fechaNacimiento: persona.fechaNacimiento, sexo: persona.sexo, nacionalidad: persona.nacionalidad, telefono: persona.telefono ?? '', correo: persona.correo ?? '', direccion: persona.direccion }) }}>Corregir identidad</button>}
    </section><section className="per-panel"><div className="per-panel-cabecera"><h3>Identificación</h3></div>
      {persona.documentos.map(d => <div className="per-fila" key={d.id}><div><strong>{d.tipo} · {d.pais} · {d.numero}</strong><small><Fecha valor={d.vigenteDesde} /> — <Fecha valor={d.vigenteHasta} /></small></div>{d.principal && !d.vigenteHasta && <span className="per-chip">Principal</span>}</div>)}
      {persona.provisional && <p className="per-texto">Identificación provisional: {persona.provisional.motivo} · {persona.provisional.sustentoReferencia} {persona.provisional.regularizadaEn && '(regularizada)'}</p>}
      {capacidades.corregirIdentidad && <button className="per-secundario" onClick={() => setAccion('documento')}>Corregir o regularizar documento</button>}
    </section></div>
    <section className="per-panel"><div className="per-panel-cabecera"><h3>Condiciones institucionales</h3>{capacidades.gestionarCondiciones && <button className="per-secundario" onClick={() => setAccion('condicion')}>+ Incorporar</button>}</div>
      {persona.condiciones.map(c => <div className="per-fila" key={c.id}><div><strong>{c.tipo}</strong><small><Fecha valor={c.vigenteDesde} /> — <Fecha valor={c.vigenteHasta} /> {c.motivoDesactivacion && '· ' + c.motivoDesactivacion}</small></div>
        {!c.vigenteHasta && capacidades.gestionarCondiciones && <button className="per-secundario" onClick={() => { setAccion('desactivar-condicion:' + c.id); setCampos({}) }}>Desactivar</button>}</div>)}
      {persona.apoderamientos.some(a => a.apoderadoId === persona.id && !a.vigenteHasta) && <div className="per-fila"><strong>APODERADO · activo por vínculo vigente</strong></div>}
      {!persona.condiciones.length && !persona.apoderamientos.length && <p className="per-vacio">Sin condiciones registradas.</p>}
    </section>
    <div className="per-dos-columnas"><section className="per-panel"><div className="per-panel-cabecera"><h3>Parentescos</h3>{capacidades.gestionarVinculos && <button className="per-secundario" onClick={() => setAccion('parentesco')}>+ Vincular</button>}</div>
      {persona.parentescos.map(p => <div className="per-fila" key={p.id}><div><strong>{p.tipo}</strong><small>Estudiante {p.estudianteId} · Familiar {p.familiarId}<br /><Fecha valor={p.vigenteHasta} /></small></div>{!p.vigenteHasta && capacidades.gestionarVinculos && <button className="per-secundario" onClick={() => void operar('/' + persona.id + '/parentescos/' + p.id + '/desactivar', 'POST', {}, 'Parentesco desactivado')}>Desactivar</button>}</div>)}
      {!persona.parentescos.length && <p className="per-vacio">Sin parentescos.</p>}
    </section><section className="per-panel"><div className="per-panel-cabecera"><h3>Apoderamientos</h3>{capacidades.gestionarVinculos && <button className="per-secundario" onClick={() => setAccion('apoderamiento')}>+ Vincular</button>}</div>
      {persona.apoderamientos.map(a => <div className="per-fila" key={a.id}><div><strong>Estudiante {a.estudianteId}</strong><small>Apoderado {a.apoderadoId} · {a.sinParentesco ? 'Autorización verificada' : 'Con parentesco'} · <Fecha valor={a.vigenteHasta} /></small></div>{!a.vigenteHasta && capacidades.gestionarVinculos && <button className="per-secundario" onClick={() => { setAccion('desactivar-apoderamiento:' + a.id); setCampos({}) }}>Desactivar</button>}</div>)}
      {!persona.apoderamientos.length && <p className="per-vacio">Sin apoderamientos.</p>}
    </section></div>
    {accion && <form className="per-panel per-editor" onSubmit={e => { e.preventDefault(); const base='/' + persona.id
      if ((accion === 'parentesco' || accion === 'apoderamiento') &&
          (!valor('estudianteId') || !valor(accion === 'parentesco' ? 'familiarId' : 'apoderadoId'))) {
        setError('Selecciona las dos personas del vínculo.'); return
      }
      if (accion === 'contacto') void operar(base + '/contacto', 'PUT', campos, 'Contacto actualizado')
      else if (accion === 'identidad') void operar(base, 'PUT', campos, 'Identidad corregida')
      else if (accion === 'documento') void operar(base + '/documentos', 'POST', { tipo: valor('tipo'), pais: valor('pais'), numero: valor('numero') }, 'Documento actualizado')
      else if (accion === 'condicion') void operar(base + '/condiciones', 'POST', { tipo: valor('tipo') }, 'Condición incorporada')
      else if (accion === 'parentesco') void operar('/parentescos', 'POST', { estudianteId: valor('estudianteId'), familiarId: valor('familiarId'), tipo: valor('tipo') }, 'Parentesco registrado')
      else if (accion === 'apoderamiento') void operar('/apoderamientos', 'POST', { estudianteId: valor('estudianteId'), apoderadoId: valor('apoderadoId'), sinParentesco: valor('sinParentesco') === 'true', autorizacion: valor('sinParentesco') === 'true' ? { autorizador: valor('autorizador'), medio: valor('medio'), fecha: new Date().toISOString(), sustentoTipo: valor('sustentoTipo') || 'VERBAL', sustentoReferencia: valor('referencia') } : null }, 'Apoderamiento registrado')
      else if (accion.startsWith('desactivar-condicion:')) void operar(base + '/condiciones/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Condición desactivada')
      else if (accion.startsWith('desactivar-apoderamiento:')) void operar(base + '/apoderamientos/' + accion.split(':')[1] + '/desactivar', 'POST', { motivo: valor('motivo') }, 'Vínculo desactivado')
    }}><div className="per-panel-cabecera"><h3>{accion.startsWith('desactivar') ? 'Confirmar desactivación' : 'Actualizar ficha'}</h3></div><div className="per-formulario">
      {accion === 'contacto' && <><Campo titulo="Teléfono"><input value={valor('telefono')} onChange={e => cambiar('telefono', e.target.value)} /></Campo><Campo titulo="Correo"><input type="email" value={valor('correo')} onChange={e => cambiar('correo', e.target.value)} /></Campo><Campo titulo="Dirección"><input required value={valor('direccion')} onChange={e => cambiar('direccion', e.target.value)} /></Campo></>}
      {accion === 'identidad' && <>{['nombres','apellidos','sexo','nacionalidad','telefono','correo','direccion'].map(c => <Campo key={c} titulo={c}><input required={!['telefono','correo'].includes(c)} value={valor(c)} onChange={e => cambiar(c,e.target.value)} /></Campo>)}<Campo titulo="Nacimiento"><input type="date" required value={valor('fechaNacimiento')} onChange={e => cambiar('fechaNacimiento', e.target.value)} /></Campo></>}
      {accion === 'documento' && <><Campo titulo="Tipo"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option><option>DNI</option><option>CE</option><option>PASAPORTE</option></select></Campo><Campo titulo="País ISO 2"><input required maxLength={2} value={valor('pais')} onChange={e => cambiar('pais',e.target.value.toUpperCase())} /></Campo><Campo titulo="Número"><input required value={valor('numero')} onChange={e => cambiar('numero',e.target.value)} /></Campo></>}
      {accion === 'condicion' && <Campo titulo="Condición"><select required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)}><option value="">Seleccionar</option>{tiposCondicion.filter(t => t.valor !== 'APODERADO').map(t => <option value={t.valor} key={t.valor}>{t.texto}</option>)}</select></Campo>}
      {(accion === 'parentesco' || accion === 'apoderamiento') && <><SelectorPersona sesion={sesion} titulo="Estudiante" condicion="ESTUDIANTE" valor={valor('estudianteId')} alElegir={id => cambiar('estudianteId',id)} /><SelectorPersona sesion={sesion} titulo={accion === 'parentesco' ? 'Familiar' : 'Apoderado'} valor={valor(accion === 'parentesco' ? 'familiarId' : 'apoderadoId')} alElegir={id => cambiar(accion === 'parentesco' ? 'familiarId' : 'apoderadoId',id)} /></>}
      {accion === 'parentesco' && <Campo titulo="Tipo de parentesco"><input required value={valor('tipo')} onChange={e => cambiar('tipo',e.target.value)} /></Campo>}
      {accion === 'apoderamiento' && <><Campo titulo="Parentesco"><select value={valor('sinParentesco')} onChange={e => cambiar('sinParentesco',e.target.value)}><option value="">Con parentesco registrado</option><option value="true">Sin parentesco, autorización institucional</option></select></Campo>{valor('sinParentesco') === 'true' && <><Campo titulo="Autorizador"><input required value={valor('autorizador')} onChange={e => cambiar('autorizador',e.target.value)} /></Campo><Campo titulo="Medio"><input required value={valor('medio')} onChange={e => cambiar('medio',e.target.value)} /></Campo><Campo titulo="Tipo de sustento"><input value={valor('sustentoTipo')} onChange={e => cambiar('sustentoTipo',e.target.value)} placeholder="VERBAL" /></Campo><Campo titulo="Referencia"><input required value={valor('referencia')} onChange={e => cambiar('referencia',e.target.value)} /></Campo></>}</>}
      {accion.startsWith('desactivar') && <Campo titulo="Motivo"><input required value={valor('motivo')} onChange={e => cambiar('motivo',e.target.value)} /></Campo>}
    </div><div className="per-acciones"><button className="per-secundario" type="button" onClick={() => setAccion('')}>Cancelar</button><button className="boton-principal">Guardar cambios</button></div></form>}
  </section>
}

export function PersonasModulo({ sesion }: { sesion: Sesion }) {
  const [capacidades, setCapacidades] = useState<Capacidades | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void api<Capacidades>('/capacidades', sesion).then(setCapacidades).catch(e => setError(String(e.message ?? e))) }, [sesion])
  if (error) return <Aviso mensaje={error} />
  if (!capacidades) return <p role="status">Cargando Personas…</p>
  return <Routes><Route index element={<Directorio sesion={sesion} capacidades={capacidades} />} />
    <Route path="nueva" element={<Registro sesion={sesion} capacidades={capacidades} />} />
    <Route path=":id" element={<Ficha sesion={sesion} capacidades={capacidades} />} /></Routes>
}
