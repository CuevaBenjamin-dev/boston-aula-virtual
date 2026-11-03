import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, nombre, type Fila, valor } from './api'
import { Aviso, Campo, opciones, Pagina, Panel, Selector, useCatalogos } from './ui'

type Persona = { nombres: string; apellidos: string; tipoDocumento: string; documento: string; telefono: string; correo: string }
type Candidato = Fila & { matriculado?: boolean; proceso_activo_id?: string; ultimo_estado?: string }
type Verificacion = {
  encontrado: boolean
  estudiante?: Fila & { edad?: number; fecha_nacimiento?: string }
  matriculado?: boolean
  historial?: { periodo: string; grado: string; seccion?: string; desde: string; hasta: string; cicloCompleto: boolean; enCurso: boolean }[]
  diasRegistrados?: number
  apoderado?: Fila & { vinculo?: string; contacto_emergencia?: boolean }
  procesoActivo?: { id: string; estado: string; periodo_id: string; periodo: string }
}
type Solicitud = {
  procedencia: string; usarExistente: boolean; estudianteId: string; estudiante: Persona & { fechaNacimiento: string }
  periodoId: string; gradoId: string; seccionId: string; detalle: string
  apoderado: Persona; vinculo: string; emergencia: boolean
  responsableFinanciero: string; financiero: Persona
  pagoEstado: string; pagoMedio: string; pagoMonto: string
}
const persona = (): Persona => ({ nombres: '', apellidos: '', tipoDocumento: 'DNI', documento: '', telefono: '', correo: '' })
const documentoOpciones = ['DNI', 'CE', 'PASAPORTE', 'PROVISIONAL'].map(x => ({ valor: x, texto: x }))
const pagoOpciones = ['EFECTIVO', 'TRANSFERENCIA', 'YAPE', 'PLIN', 'TARJETA', 'OTRO'].map(x => ({ valor: x, texto: x.charAt(0) + x.slice(1).toLowerCase() }))
const inicial = (): Solicitud => ({ procedencia: 'NUEVO', usarExistente: false, estudianteId: '', estudiante: { ...persona(), tipoDocumento: '', fechaNacimiento: '' }, periodoId: '', gradoId: '', seccionId: '', detalle: '', apoderado: persona(), vinculo: '', emergencia: false, responsableFinanciero: 'SIN_DESIGNAR', financiero: persona(), pagoEstado: 'PENDIENTE', pagoMedio: '', pagoMonto: '' })
const fechaLegible = (fecha: string) => fecha ? new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(fecha.slice(0, 10) + 'T12:00:00')) : 'Sin fecha registrada'
const mesLegible = (fecha: string) => fecha ? new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' }).format(new Date(fecha.slice(0, 10) + 'T12:00:00')) : 'fecha no disponible'

export function AsistenteMatricula() {
  const navigate = useNavigate()
  const { datos: cat } = useCatalogos()
  const [datos, setDatos] = useState<Solicitud>(() => ({ ...inicial(), estudianteId: new URLSearchParams(location.search).get('estudiante') || '', usarExistente: Boolean(new URLSearchParams(location.search).get('estudiante')), procedencia: new URLSearchParams(location.search).get('estudiante') ? 'ANTIGUO' : 'NUEVO' }))
  const [paso, setPaso] = useState(1)
  const [borrador, setBorrador] = useState(new URLSearchParams(location.search).get('borrador') || '')
  const [busqueda, setBusqueda] = useState('')
  const [candidatos, setCandidatos] = useState<Candidato[]>([])
  const [errorBusqueda, setErrorBusqueda] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [verificacion, setVerificacion] = useState<Verificacion | null>(null)
  const [verificacionAceptada, setVerificacionAceptada] = useState<Verificacion | null>(null)
  const [resultadoDocumento, setResultadoDocumento] = useState('')
  const [alumnoSeleccionado, setAlumnoSeleccionado] = useState<Fila | null>(null)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [cargando, setCargando] = useState(Boolean(borrador))

  useEffect(() => {
    const consulta = busqueda.trim()
    if (consulta.length < 2) { setCandidatos([]); setErrorBusqueda(''); setBuscando(false); return }
    let activo = true
    setBuscando(true)
    const espera = window.setTimeout(() => { void api<Candidato[]>('/estudiantes/candidatos?buscar=' + encodeURIComponent(consulta))
      .then(filas => { if (activo) { setCandidatos(filas); setErrorBusqueda('') } })
      .catch(e => { if (activo) { setCandidatos([]); setErrorBusqueda((e as Error).message) } })
      .finally(() => { if (activo) setBuscando(false) }) }, 250)
    return () => { activo = false; window.clearTimeout(espera) }
  }, [busqueda])

  useEffect(() => {
    if (!borrador) return
    let activo = true
    void api<{ paso: number; datos: Solicitud; finalizado: boolean; matriculaId: string }>('/matriculas/borradores/' + borrador)
      .then(r => { if (!activo) return; if (r.finalizado) { navigate('/matriculas/' + r.matriculaId); return } setDatos({ ...inicial(), ...r.datos }); setPaso(r.paso) })
      .catch(e => { if (activo) setError((e as Error).message) })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [borrador, navigate])

  useEffect(() => {
    if (!datos.estudianteId) { setAlumnoSeleccionado(null); return }
    let activo = true
    void api<Verificacion>('/estudiantes/' + datos.estudianteId + '/verificar')
      .then(v => { if (activo && v.estudiante) { setAlumnoSeleccionado(v.estudiante); setVerificacionAceptada(v) } })
      .catch(() => {})
    return () => { activo = false }
  }, [datos.estudianteId])

  function cambiar<K extends keyof Solicitud>(clave: K, valorNuevo: Solicitud[K]) { setDatos(d => ({ ...d, [clave]: valorNuevo })) }
  function estudiante(clave: keyof Solicitud['estudiante'], v: string) { setDatos(d => ({ ...d, estudiante: { ...d.estudiante, [clave]: v } })) }
  function apoderado(clave: keyof Persona, v: string) { setDatos(d => ({ ...d, apoderado: { ...d.apoderado, [clave]: v } })) }
  function financiero(clave: keyof Persona, v: string) { setDatos(d => ({ ...d, financiero: { ...d.financiero, [clave]: v } })) }

  async function comprobarDocumento(mostrarSinResultado = true): Promise<boolean> {
    if (!datos.estudiante.tipoDocumento || !datos.estudiante.documento.trim()) {
      if (mostrarSinResultado) setResultadoDocumento('Indica tipo y número de documento para verificar.')
      return false
    }
    const v = await api<Verificacion>('/estudiantes/verificar?tipoDocumento=' + encodeURIComponent(datos.estudiante.tipoDocumento) + '&documento=' + encodeURIComponent(datos.estudiante.documento.trim()))
    if (!v.encontrado) {
      setResultadoDocumento('No se encontró un registro previo con este documento.')
      return false
    }
    setResultadoDocumento('')
    setVerificacion(v)
    return true
  }
  async function verificarAhora() {
    setError(''); setResultadoDocumento(''); setOcupado(true)
    try { await comprobarDocumento() } catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  async function abrirCandidato(c: Candidato) {
    setError(''); setOcupado(true)
    try { const v = await api<Verificacion>('/estudiantes/' + valor(c.id) + '/verificar'); setVerificacion(v) }
    catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  function usarRegistro(v: Verificacion): Solicitud {
    const s = v.estudiante!
    const a = v.apoderado
    const nuevo: Solicitud = {
      ...datos, usarExistente: true, estudianteId: valor(s.id),
      procedencia: v.matriculado ? (datos.procedencia === 'NUEVO' ? 'ANTIGUO' : datos.procedencia) : 'NUEVO',
      estudiante: { ...datos.estudiante, nombres: valor(s.nombres), apellidos: valor(s.apellidos), tipoDocumento: valor(s.tipo_documento), documento: valor(s.documento), fechaNacimiento: valor(s.fecha_nacimiento) },
      apoderado: a ? { nombres: valor(a.nombres), apellidos: valor(a.apellidos), tipoDocumento: valor(a.tipo_documento), documento: valor(a.documento), telefono: valor(a.telefono), correo: valor(a.correo) } : datos.apoderado,
      vinculo: a ? valor(a.vinculo) : datos.vinculo,
      emergencia: a ? Boolean(a.contacto_emergencia) : datos.emergencia
    }
    setDatos(nuevo); setAlumnoSeleccionado(s); setVerificacionAceptada(v); setVerificacion(null)
    return nuevo
  }
  async function continuarVerificado() {
    if (!verificacion?.estudiante) return
    const v = verificacion
    if (v.procesoActivo?.periodo_id === datos.periodoId) { setError('Este alumno ya tiene una matrícula en proceso para el periodo elegido. Abre ese proceso para continuar.'); setVerificacion(null); return }
    const nuevo = usarRegistro(v)
    if (!nuevo.periodoId || !nuevo.gradoId) { setResultadoDocumento('Registro seleccionado. Completa el periodo y grado para continuar.'); return }
    setOcupado(true)
    try { await guardar(2, nuevo); setPaso(2); window.scrollTo(0, 0) }
    catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }

  function validar(p: number) {
    if (p === 1) {
      if (!datos.periodoId || !datos.gradoId) throw new Error('Selecciona el periodo y grado del alumno.')
      if (datos.usarExistente && !datos.estudianteId) throw new Error('Selecciona un estudiante registrado.')
      if (!datos.usarExistente && (!datos.estudiante.nombres.trim() || !datos.estudiante.apellidos.trim())) throw new Error('Completa los nombres y apellidos del alumno.')
      if (!datos.usarExistente && Boolean(datos.estudiante.tipoDocumento) !== Boolean(datos.estudiante.documento.trim())) throw new Error('Completa tipo y número de documento del alumno.')
    }
    if (p === 2 && (!datos.apoderado.nombres.trim() || !datos.apoderado.apellidos.trim() || !datos.apoderado.tipoDocumento || !datos.apoderado.documento.trim() || !datos.vinculo.trim())) throw new Error('Completa la identidad, documento y vínculo del apoderado.')
    if (p === 4) {
      if (!datos.pagoMedio || !datos.pagoMonto || Number(datos.pagoMonto) <= 0 || !/^\d+(\.\d{1,2})?$/.test(datos.pagoMonto)) throw new Error('Indica medio y monto positivo del pago externo, con hasta dos decimales.')
      if (datos.responsableFinanciero === 'OTRA_PERSONA' && (!datos.financiero.nombres.trim() || !datos.financiero.apellidos.trim() || !datos.financiero.documento.trim())) throw new Error('Completa la identidad del responsable financiero o deja la opción sin designar.')
    }
  }
  async function guardar(pasoDestino: number, contenido = datos): Promise<string> {
    if (borrador) { await api('/matriculas/borradores/' + borrador, 'PUT', { paso: pasoDestino, datos: contenido }); return borrador }
    const r = await api<{ id: string }>('/matriculas/borradores', 'POST', { paso: pasoDestino, datos: contenido })
    setBorrador(r.id)
    window.history.replaceState(null, '', '/matriculas/nueva?borrador=' + r.id)
    return r.id
  }
  async function avanzar(e: FormEvent) {
    e.preventDefault(); setError('')
    try { validar(paso); if (paso === 1 && verificacionAceptada?.procesoActivo?.periodo_id === datos.periodoId) throw new Error('Este alumno ya tiene una matrícula en proceso para el periodo elegido. Abre ese proceso para continuar.'); setOcupado(true); if (paso === 1 && !datos.usarExistente && datos.estudiante.documento.trim() && await comprobarDocumento(false)) return; await guardar(paso + 1); setPaso(paso + 1); window.scrollTo(0, 0) }
    catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  async function retroceder() {
    setError(''); setOcupado(true)
    try { await guardar(paso - 1); setPaso(paso - 1); window.scrollTo(0, 0) }
    catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  async function irAPago() {
    setError(''); setOcupado(true)
    try { await guardar(4); setPaso(4); window.scrollTo(0, 0) }
    catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  async function finalizar(pagado: boolean) {
    setError(''); setOcupado(true)
    try {
      validar(1); validar(2); if (pagado) validar(4)
      const final = { ...datos, pagoEstado: pagado ? 'REGISTRADO' : 'PENDIENTE', pagoMedio: pagado ? datos.pagoMedio : '', pagoMonto: pagado ? datos.pagoMonto : '', responsableFinanciero: pagado ? datos.responsableFinanciero : 'SIN_DESIGNAR' }
      const id = await guardar(paso, final)
      const r = await api<{ id: string }>('/matriculas/borradores/' + id + '/finalizar', 'POST')
      navigate('/matriculas/' + r.id)
    } catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }

  const candidato = candidatos.find(c => valor(c.id) === datos.estudianteId) || alumnoSeleccionado
  const periodo = cat.periodos.find(x => valor(x.id) === datos.periodoId)
  const grado = cat.grados.find(x => valor(x.id) === datos.gradoId)
  const seccion = cat.secciones.find(x => valor(x.id) === datos.seccionId)
  return <Pagina titulo="Nueva matrícula" intro="Registro institucional" accion={<Link className="boton" to="/matriculas">← Matrículas</Link>}>
    <div className="matricula-pasos" aria-label="Progreso de la matrícula">{['Alumno', 'Apoderado', 'Revisión', 'Pago externo'].map((texto, i) => <div key={texto} className={'matricula-paso ' + (paso === i + 1 ? 'actual' : paso > i + 1 ? 'completo' : '')}><span>{String(i + 1).padStart(2, '0')}</span><strong>{texto}</strong></div>)}</div>
    <Aviso error={error} />{cargando ? <Panel titulo="Cargando borrador"><p>Recuperando los datos guardados…</p></Panel> : <>
      {paso === 1 && <form onSubmit={avanzar}>
        <Panel titulo="01 · Datos del alumno" subtitulo="Los datos se guardan al continuar. El código institucional se asignará al concluir la matrícula.">
          <p className="seccion-etiqueta">MODALIDAD DE INGRESO</p><div className="opciones-grandes">{[
            ['NUEVO', 'Nuevo', 'Primer ingreso al colegio'], ['ANTIGUO', 'Antiguo', 'Continuidad académica'], ['TRASLADO', 'Traslado', 'Ingreso desde otro colegio'], ['REINCORPORACION', 'Reincorporación', 'Retorno al colegio']
          ].map(([id, titulo, desc]) => <button key={id} type="button" className={'opcion ' + (datos.procedencia === id ? 'seleccionada' : '')} onClick={() => { setDatos(d => ({ ...d, procedencia: id, usarExistente: id === 'ANTIGUO' || id === 'REINCORPORACION' ? true : id === 'TRASLADO' ? false : d.usarExistente })) }}><strong>{titulo}</strong><small>{desc}</small></button>)}</div>
          <div className="separador" /><p className="seccion-etiqueta">IDENTIDAD DEL ALUMNO</p>
          {datos.procedencia === 'NUEVO' && <div className="acciones"><button type="button" className={'boton ' + (!datos.usarExistente ? 'primario' : '')} onClick={() => { setDatos(d => ({ ...d, usarExistente: false, estudianteId: '' })); setVerificacionAceptada(null) }}>Registrar nuevo</button><button type="button" className={'boton ' + (datos.usarExistente ? 'primario' : '')} onClick={() => cambiar('usarExistente', true)}>Usar estudiante registrado</button></div>}
          {datos.usarExistente ? <div className="busqueda-estudiante"><Campo etiqueta="Buscar estudiante" valorActual={busqueda} cambio={setBusqueda} placeholder="Nombre, documento o código" />
            {candidato && <div className="alumno-elegido"><strong>Alumno seleccionado: {nombre(candidato)}</strong><span>{valor(candidato.documento)} · {valor(candidato.codigo) || 'Sin código institucional'}</span></div>}
            {busqueda.trim().length < 2 ? <p className="ayuda-busqueda">Escribe al menos dos caracteres para buscar.</p> : buscando ? <p className="ayuda-busqueda">Buscando alumnos…</p> : errorBusqueda ? <Aviso error={errorBusqueda} /> : candidatos.length ? <div className="lista-candidatos">{candidatos.map(c => <button type="button" key={valor(c.id)} className="candidato-estudiante" onClick={() => void abrirCandidato(c)}><strong>{nombre(c)}</strong><span>{valor(c.tipo_documento)} {valor(c.documento)} · {valor(c.codigo) || 'Ficha preliminar'} · {c.matriculado ? 'Con matrícula confirmada' : c.proceso_activo_id ? 'Matrícula en proceso' : 'Sin matrícula confirmada'}</span><b>Revisar →</b></button>)}</div> : <p className="ayuda-busqueda">No se encontraron alumnos. Revisa el nombre o documento ingresado.</p>}
          </div> : <div className="campos-3">
            <Campo etiqueta="Nombres" valorActual={datos.estudiante.nombres} cambio={v => estudiante('nombres', v)} requerido /><Campo etiqueta="Apellidos" valorActual={datos.estudiante.apellidos} cambio={v => estudiante('apellidos', v)} requerido /><Campo etiqueta="Nacimiento" tipo="date" valorActual={datos.estudiante.fechaNacimiento} cambio={v => estudiante('fechaNacimiento', v)} />
            <Selector etiqueta="Tipo de documento" valorActual={datos.estudiante.tipoDocumento} cambio={v => { estudiante('tipoDocumento', v); setResultadoDocumento(''); setVerificacionAceptada(null) }} opciones={documentoOpciones} vacio="Sin documento" /><div className="verificar-documento"><Campo etiqueta="Número de documento" valorActual={datos.estudiante.documento} cambio={v => { estudiante('documento', v); setResultadoDocumento(''); setVerificacionAceptada(null) }} /><button type="button" className="boton" onClick={() => void verificarAhora()} disabled={ocupado || !datos.estudiante.tipoDocumento || !datos.estudiante.documento.trim()}>Verificar</button></div>
          </div>}
          {resultadoDocumento && <p role="status" className="ayuda-busqueda">{resultadoDocumento}</p>}
          {verificacionAceptada && datos.usarExistente && <p className="ayuda-busqueda">Se usarán los datos del alumno seleccionado. Puedes editar los datos del apoderado en el siguiente paso.</p>}
          <div className="separador" /><p className="seccion-etiqueta">UBICACIÓN ACADÉMICA</p><div className="campos-3">
            <Selector etiqueta="Periodo escolar" valorActual={datos.periodoId} cambio={v => setDatos(d => ({ ...d, periodoId: v, seccionId: '' }))} opciones={opciones(cat.periodos.filter(p => p.habilitado), p => valor(p.nombre))} requerido />
            <Selector etiqueta="Grado" valorActual={datos.gradoId} cambio={v => setDatos(d => ({ ...d, gradoId: v, seccionId: '' }))} opciones={opciones(cat.grados.filter(g => g.habilitado), g => valor(g.nivel) + ' · ' + valor(g.nombre))} requerido />
            <Selector etiqueta="Sección" valorActual={datos.seccionId} cambio={v => cambiar('seccionId', v)} opciones={opciones(cat.secciones.filter(s => s.habilitada && valor(s.grado_id) === datos.gradoId && valor(s.periodo_id) === datos.periodoId), s => valor(s.nombre))} vacio="Asignar después" />
          </div>{(datos.procedencia === 'TRASLADO' || datos.procedencia === 'REINCORPORACION') && <Campo etiqueta="Procedencia o antecedentes" valorActual={datos.detalle} cambio={v => cambiar('detalle', v)} />}
        </Panel><div className="barra-acciones"><span>Al pulsar Siguiente se guarda el borrador privado. Aún no se registra al estudiante.</span><button className="boton primario" disabled={ocupado}>Siguiente: apoderado →</button></div>
      </form>}
      {paso === 2 && <form onSubmit={avanzar}><Panel titulo="02 · Datos del apoderado" subtitulo="Identifica a la persona responsable de la matrícula y el contacto del alumno."><div className="campos-3">
        <Campo etiqueta="Nombres" valorActual={datos.apoderado.nombres} cambio={v => apoderado('nombres', v)} requerido /><Campo etiqueta="Apellidos" valorActual={datos.apoderado.apellidos} cambio={v => apoderado('apellidos', v)} requerido /><Selector etiqueta="Documento" valorActual={datos.apoderado.tipoDocumento} cambio={v => apoderado('tipoDocumento', v)} opciones={documentoOpciones} requerido />
        <Campo etiqueta="Número de documento" valorActual={datos.apoderado.documento} cambio={v => apoderado('documento', v)} requerido /><Campo etiqueta="Teléfono" valorActual={datos.apoderado.telefono} cambio={v => apoderado('telefono', v)} /><Campo etiqueta="Correo" tipo="email" valorActual={datos.apoderado.correo} cambio={v => apoderado('correo', v)} />
        <Campo etiqueta="Vínculo con el alumno" valorActual={datos.vinculo} cambio={v => cambiar('vinculo', v)} placeholder="Madre, padre, tutor…" requerido />
      </div><label className="check"><input type="checkbox" checked={datos.emergencia} onChange={e => cambiar('emergencia', e.target.checked)} /> También es contacto de emergencia</label></Panel><div className="barra-acciones"><button type="button" className="boton" onClick={() => void retroceder()} disabled={ocupado}>← Volver</button><button className="boton primario" disabled={ocupado}>Siguiente: revisar →</button></div></form>}
      {paso === 3 && <><Panel titulo="03 · Revisar matrícula" subtitulo="Comprueba los datos antes del registro definitivo."><div className="matricula-revision">
        <div><small>ALUMNO</small><strong>{datos.usarExistente ? candidato ? nombre(candidato) : datos.estudiante.nombres + ' ' + datos.estudiante.apellidos : datos.estudiante.nombres + ' ' + datos.estudiante.apellidos}</strong><span>{datos.usarExistente ? 'Registro existente' : datos.estudiante.tipoDocumento + ' ' + datos.estudiante.documento}</span></div>
        <div><small>APODERADO</small><strong>{datos.apoderado.nombres} {datos.apoderado.apellidos}</strong><span>{datos.vinculo} · {datos.apoderado.tipoDocumento} {datos.apoderado.documento}</span></div>
        <div><small>PERIODO Y GRADO</small><strong>{valor(periodo?.nombre)} · {valor(grado?.nivel)} {valor(grado?.nombre)}</strong><span>{seccion ? 'Sección ' + valor(seccion.nombre) : 'Sección por asignar'}</span></div>
        <div><small>MODALIDAD</small><strong>{datos.procedencia.replaceAll('_', ' ')}</strong><span>{datos.detalle || 'Sin antecedentes adicionales'}</span></div>
      </div><p className="matricula-nota">Al guardar, la matrícula quedará confirmada y el alumno recibirá su código institucional. El pago puede quedar pendiente y registrarse después.</p></Panel>
        <div className="barra-acciones"><button className="boton" onClick={() => void retroceder()} disabled={ocupado}>← Corregir datos</button><div className="acciones"><button className="boton" onClick={() => void finalizar(false)} disabled={ocupado}>Guardar con pago pendiente</button><button className="boton primario" onClick={() => void irAPago()} disabled={ocupado}>Registrar pago externo →</button></div></div></>}
      {paso === 4 && <form onSubmit={e => { e.preventDefault(); void finalizar(true) }}><Panel titulo="04 · Pago realizado fuera del sistema" subtitulo="Confirma únicamente un pago que ya se realizó. No se cobrará desde el ERP."><div className="campos-2"><Selector etiqueta="Medio de pago" valorActual={datos.pagoMedio} cambio={v => cambiar('pagoMedio', v)} opciones={pagoOpciones} requerido /><Campo etiqueta="Monto recibido (S/)" tipo="number" valorActual={datos.pagoMonto} cambio={v => cambiar('pagoMonto', v)} requerido /></div>
        <div className="separador" /><p className="seccion-etiqueta">RESPONSABLE FINANCIERO · OPCIONAL</p><div className="opciones-grandes opciones-financieras">{[['SIN_DESIGNAR', 'Sin designar'], ['MISMO_APODERADO', 'El apoderado'], ['OTRA_PERSONA', 'Otra persona']].map(([id, texto]) => <button key={id} type="button" className={'opcion ' + (datos.responsableFinanciero === id ? 'seleccionada' : '')} onClick={() => cambiar('responsableFinanciero', id)}><strong>{texto}</strong></button>)}</div>
        {datos.responsableFinanciero === 'OTRA_PERSONA' && <div className="campos-3"><Campo etiqueta="Nombres" valorActual={datos.financiero.nombres} cambio={v => financiero('nombres', v)} requerido /><Campo etiqueta="Apellidos" valorActual={datos.financiero.apellidos} cambio={v => financiero('apellidos', v)} requerido /><Selector etiqueta="Tipo de documento" valorActual={datos.financiero.tipoDocumento} cambio={v => financiero('tipoDocumento', v)} opciones={documentoOpciones} requerido /><Campo etiqueta="Número de documento" valorActual={datos.financiero.documento} cambio={v => financiero('documento', v)} requerido /><Campo etiqueta="Teléfono" valorActual={datos.financiero.telefono} cambio={v => financiero('telefono', v)} /><Campo etiqueta="Correo" tipo="email" valorActual={datos.financiero.correo} cambio={v => financiero('correo', v)} /></div>}
      </Panel><div className="barra-acciones"><button type="button" className="boton" onClick={() => void retroceder()} disabled={ocupado}>← Volver a revisión</button><button className="boton primario" disabled={ocupado}>Confirmar matrícula y registrar pago</button></div></form>}
    </>}
    {verificacion && <div className="modal-fondo" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget) setVerificacion(null) }}><div className="modal modal-verificacion" role="dialog" aria-modal="true" aria-labelledby="titulo-verificacion">
      <p className="seccion-etiqueta">VERIFICACIÓN DE IDENTIDAD</p><h2 id="titulo-verificacion">{verificacion.matriculado ? 'Alumno con historial en el colegio' : 'Registro previo encontrado'}</h2>
      <p>{verificacion.matriculado ? 'Revisa sus datos y periodos registrados antes de continuar.' : 'Esta ficha no tiene ninguna matrícula confirmada. No indica que haya estudiado en el colegio.'}</p>
      <div className="verificacion-resumen"><div><small>ALUMNO</small><strong>{nombre(verificacion.estudiante || {})}</strong></div><div><small>DOCUMENTO</small><strong>{valor(verificacion.estudiante?.tipo_documento)} {valor(verificacion.estudiante?.documento)}</strong></div><div><small>FECHA DE NACIMIENTO</small><strong>{fechaLegible(valor(verificacion.estudiante?.fecha_nacimiento))}</strong></div><div><small>EDAD ACTUAL</small><strong>{verificacion.estudiante?.edad == null ? 'No registrada' : verificacion.estudiante.edad + ' años'}</strong></div></div>
      {Boolean(verificacion.historial?.length) && <section className="verificacion-seccion"><h3>Tiempo y ciclos escolares registrados</h3><p>{verificacion.historial?.length} {verificacion.historial?.length === 1 ? 'ciclo registrado' : 'ciclos registrados'} · {verificacion.diasRegistrados} días según las matrículas confirmadas.</p><div className="verificacion-historial">{verificacion.historial?.map((h, i) => <div key={i}><strong>{h.periodo} · {h.grado}{h.seccion ? ' · Sección ' + h.seccion : ''}</strong><span>{h.cicloCompleto ? 'Ciclo escolar completo' : 'De ' + mesLegible(h.desde) + ' a ' + mesLegible(h.hasta)}{h.enCurso ? ' · En curso' : ''}</span></div>)}</div></section>}
      <section className="verificacion-seccion"><h3>Apoderado registrado</h3>{verificacion.apoderado ? <p><strong>{nombre(verificacion.apoderado)}</strong> · {valor(verificacion.apoderado.vinculo) || 'Vínculo no registrado'}<br />{valor(verificacion.apoderado.tipo_documento)} {valor(verificacion.apoderado.documento)}{verificacion.apoderado.telefono ? ' · ' + valor(verificacion.apoderado.telefono) : ''}</p> : <p>No hay apoderado registrado. Podrás ingresarlo en el siguiente paso.</p>}</section>
      {verificacion.procesoActivo && <div className="verificacion-proceso"><strong>Matrícula en proceso: {verificacion.procesoActivo.periodo}</strong><span>Estado: {verificacion.procesoActivo.estado.replaceAll('_', ' ').toLowerCase()}.</span><Link to={'/matriculas/' + verificacion.procesoActivo.id}>Abrir proceso existente →</Link></div>}
      <div className="acciones verificacion-acciones"><button type="button" className="boton" onClick={() => setVerificacion(null)}>Corregir datos</button>{verificacion.procesoActivo?.periodo_id !== datos.periodoId && <button type="button" className="boton primario" onClick={() => void continuarVerificado()} disabled={ocupado}>{verificacion.matriculado ? 'Continuar con este alumno' : 'Usar esta ficha'}</button>}</div>
    </div></div>}
  </Pagina>
}
