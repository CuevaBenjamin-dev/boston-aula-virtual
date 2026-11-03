import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, nombre, type Fila, valor } from './api'
import { Aviso, Campo, Etiqueta, opciones, Pagina, Panel, Selector, useCatalogos, useDatos, Vacio } from './ui'
import { CatalogoAsignaturas } from './CatalogoAsignaturas'

export function Apoderados() {
  const [params] = useSearchParams()
  const estudiante = params.get('estudiante') || ''
  const [tipo, setTipo] = useState('DNI')
  const [documento, setDocumento] = useState('')
  const [encontrados, setEncontrados] = useState<Fila[]>([])
  const [persona, setPersona] = useState('')
  const [nuevo, setNuevo] = useState({ nombres: '', apellidos: '', telefono: '', correo: '' })
  const [rol, setRol] = useState('PRINCIPAL')
  const [vinculo, setVinculo] = useState('')
  const [emergencia, setEmergencia] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [estudianteBuscar, setEstudianteBuscar] = useState('')
  const [estudianteElegido, setEstudianteElegido] = useState(estudiante)
  const { datos: estudiantes } = useDatos<Fila[]>('/estudiantes?buscar=' + encodeURIComponent(estudianteBuscar), [])
  async function buscar(e: FormEvent) { e.preventDefault(); setError(''); setExito(''); try {
    const r = await api<Fila[]>('/personas?tipo=' + encodeURIComponent(tipo) + '&documento=' + encodeURIComponent(documento))
    setEncontrados(r); setPersona(r.length ? valor(r[0].id) : '')
  } catch (err) { setError((err as Error).message) } }
  async function crear(e: FormEvent) { e.preventDefault(); setError(''); try {
    const r = await api<{ id: string }>('/personas', 'POST', { ...nuevo, tipoDocumento: tipo, documento })
    setPersona(r.id); setExito('Persona registrada. Ahora puedes vincularla a un estudiante.')
  } catch (err) { setError((err as Error).message) } }
  async function vincular(e: FormEvent) { e.preventDefault(); setError(''); try {
    await api('/estudiantes/' + estudianteElegido + '/responsables', 'POST',
      { personaId: persona, rol, vinculo, emergencia, telefono: nuevo.telefono || null, correo: nuevo.correo || null })
    setExito('Responsable vinculado al estudiante.')
  } catch (err) { setError((err as Error).message) } }
  return <Pagina titulo="Apoderados" intro="Personas reutilizables y vínculos independientes con cada estudiante." accion={estudiante && <Link className="boton" to={'/estudiantes/' + estudiante}>← Ficha del estudiante</Link>}>
    <Aviso error={error} exito={exito} /><Panel titulo="01 · Buscar persona" subtitulo="El tipo y número de documento identifican a una persona ya registrada."><form onSubmit={buscar} className="formulario"><div className="campos-3">
      <Selector etiqueta="Tipo de documento" valorActual={tipo} cambio={setTipo} opciones={['DNI','CE','PASAPORTE','PROVISIONAL'].map(x => ({valor:x,texto:x}))} />
      <Campo etiqueta="Número" valorActual={documento} cambio={setDocumento} requerido />
      <button className="boton primario boton-alineado">Buscar persona</button>
    </div></form>
    {encontrados.length > 0 && <div className="resultado"><strong>Persona encontrada: {nombre(encontrados[0])}</strong><span>{valor(encontrados[0].documento)} · Se reutilizará este registro.</span></div>}
    </Panel>
    {documento && encontrados.length === 0 && !persona && <Panel titulo="02 · Registrar persona" subtitulo="Completa los datos si aún no existe."><form onSubmit={crear} className="formulario"><div className="campos-2">
      <Campo etiqueta="Nombres" valorActual={nuevo.nombres} cambio={v => setNuevo({ ...nuevo, nombres: v })} requerido />
      <Campo etiqueta="Apellidos" valorActual={nuevo.apellidos} cambio={v => setNuevo({ ...nuevo, apellidos: v })} requerido />
      <Campo etiqueta="Teléfono" valorActual={nuevo.telefono} cambio={v => setNuevo({ ...nuevo, telefono: v })} />
      <Campo etiqueta="Correo" tipo="email" valorActual={nuevo.correo} cambio={v => setNuevo({ ...nuevo, correo: v })} />
    </div><button className="boton primario">Registrar persona</button></form></Panel>}
    {persona && <Panel titulo="03 · Vincular a estudiante" subtitulo="Principal, segundo responsable o contacto suplente."><form onSubmit={vincular} className="formulario">
      <Campo etiqueta="Buscar estudiante" valorActual={estudianteBuscar} cambio={setEstudianteBuscar} placeholder="Código, nombre o documento" />
      <div className="campos-3"><Selector etiqueta="Estudiante" valorActual={estudianteElegido} cambio={setEstudianteElegido} opciones={opciones(estudiantes, e => valor(e.codigo) + ' · ' + nombre(e))} requerido />
      <Selector etiqueta="Rol" valorActual={rol} cambio={setRol} opciones={['PRINCIPAL','SEGUNDO','SUPLENTE'].map(x => ({valor:x,texto:x}))} />
      <Campo etiqueta="Vínculo" valorActual={vinculo} cambio={setVinculo} placeholder="Madre, padre, tío…" requerido /></div>
      <label className="check"><input type="checkbox" checked={emergencia} onChange={e => setEmergencia(e.target.checked)} /> Contacto de emergencia</label>
      <button className="boton primario">Guardar vínculo</button>
    </form></Panel>}
  </Pagina>
}

type Reporte = { filas: { fila: number; datos: Record<string,string>; errores: string[]; estudianteExistente: boolean }[]; total: number; validas: number; resultados?: Fila[] }
type Previo = { id: string; estado: string; reporte: Reporte }
export function Importaciones() {
  const [archivo, setArchivo] = useState<File | null>(null)
  const [previo, setPrevio] = useState<Previo | null>(null)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)
  async function plantilla() { setError(''); try {
    const blob = await api<Blob>('/importaciones/plantilla')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'plantilla-matriculas-boston.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch (e) { setError((e as Error).message) } }
  async function previsualizar(e: FormEvent) { e.preventDefault(); if (!archivo) return; setCargando(true); setError(''); try {
    const datos = new FormData(); datos.append('archivo', archivo)
    setPrevio(await api<Previo>('/importaciones/previsualizar', 'POST', datos))
  } catch (err) { setError((err as Error).message) } finally { setCargando(false) } }
  async function confirmar() { if (!previo) return; setCargando(true); setError(''); try {
    const reporte = await api<Reporte>('/importaciones/' + previo.id + '/confirmar', 'POST', { confirmacionExplicita: true })
    setPrevio({ ...previo, estado: 'CONFIRMADA', reporte })
  } catch (err) { setError((err as Error).message) } finally { setCargando(false) } }
  return <Pagina titulo="Importación Excel" intro="Plantilla, validación por fila y confirmación institucional." accion={<button className="boton" onClick={() => void plantilla()}>↓ Descargar plantilla .xlsx</button>}>
    <Aviso error={error} />
    <Panel titulo="Cargar archivo" subtitulo="Máximo 2 MB y 500 filas. La previsualización no modifica estudiantes ni matrículas.">
      <form onSubmit={previsualizar} className="formulario"><label className="campo"><span>Archivo .xlsx</span><input type="file" accept=".xlsx" onChange={e => { setArchivo(e.target.files?.[0] || null); setPrevio(null) }} required /></label><button className="boton primario" disabled={cargando}>{cargando ? 'Procesando…' : 'Validar y previsualizar'}</button></form>
    </Panel>
    {previo && <Panel titulo="Resultado de validación" subtitulo={previo.reporte.validas + ' de ' + previo.reporte.total + ' filas válidas · ' + previo.estado}>
      <div className="tabla-wrap"><table><thead><tr><th>Fila</th><th>Estudiante</th><th>Periodo</th><th>Estado solicitado</th><th>Validación</th></tr></thead><tbody>{previo.reporte.filas.map(f => <tr key={f.fila}><td>{f.fila}</td><td>{f.datos.nombres_estudiante} {f.datos.apellidos_estudiante}<small>{f.estudianteExistente ? 'Registro existente' : 'Registro nuevo'}</small></td><td>{f.datos.periodo}</td><td>{f.datos.estado}</td><td>{f.errores.length ? <span className="texto-error">{f.errores.join('; ')}</span> : <span className="texto-ok">Válida</span>}</td></tr>)}</tbody></table></div>
      {previo.estado === 'CONFIRMADA' ? <div className="aviso exito">Importación confirmada. Al volver a cargar el mismo archivo no se duplicarán registros.</div> :
        <div className="barra-acciones"><span>Las filas con errores bloquean la importación. Corrige el archivo y vuelve a cargarlo.</span><button className="boton primario" disabled={cargando || previo.reporte.validas !== previo.reporte.total} onClick={() => void confirmar()}>Confirmar importación</button></div>}
    </Panel>}
  </Pagina>
}

export function Asignaturas({ puedeEditar }: { puedeEditar: boolean }) {
  const { datos: cat, error: errorCat, cargar } = useCatalogos()
  const [grado, setGrado] = useState('')
  const [periodo, setPeriodo] = useState('')
  const { datos: relaciones, cargar: cargarRelaciones } = useDatos<Fila[]>(grado ? '/asignaturas/grado/' + grado + (periodo ? '?periodo=' + periodo : '') : '', [])
  const [asignatura, setAsignatura] = useState('')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  async function ejecutar(ruta: string, metodo: string, datos: unknown, texto: string): Promise<boolean> { setError(''); setExito(''); try {
    await api(ruta, metodo, datos); await cargar(); await cargarRelaciones(); setExito(texto); return true
  } catch (e) { setError((e as Error).message); return false } }
  return <Pagina titulo="Malla curricular" intro="Cada grado tiene asignaturas con código propio; sus secciones comparten esa malla." accion={<Link className="boton" to="/oferta">← Oferta académica</Link>}>
    <Aviso error={error || errorCat} exito={exito} />
    <div className="asignaturas-grid"><Panel titulo="Niveles y grados" subtitulo="Selecciona el grado para organizar sus asignaturas.">
      <Selector etiqueta="Periodo (opcional)" valorActual={periodo} cambio={setPeriodo} opciones={opciones(cat.periodos, p => valor(p.nombre))} vacio="Relaciones generales" />
      {cat.niveles.map(n => <div className="grupo" key={valor(n.id)}><h3>{valor(n.nombre)}</h3>{cat.grados.filter(g => g.nivel_id === n.id).map(g => <button key={valor(g.id)} className={'grado-opcion ' + (grado === g.id ? 'seleccionado' : '')} onClick={() => setGrado(valor(g.id))}>{valor(g.nombre)} <span>→</span></button>)}</div>)}
    </Panel><Panel titulo={grado ? 'Asignaturas del grado' : 'Selecciona un grado'} subtitulo="La malla del grado es compartida por sus secciones.">
      {grado ? <><p className="malla-ayuda">Se muestran por posición de lectura. «Malla base» se aplica en todos los periodos; una relación de periodo específico se aplica solo en ese año. La posición no define horario ni importancia académica.</p>{relaciones.length ? relaciones.map((r, indice) => <div className="item item-linea" key={valor(r.id)}><div><strong>{valor(r.nombre)}</strong><small>{valor(r.codigo)} · {r.periodo_id ? 'Solo periodo ' + valor(cat.periodos.find(p => p.id === r.periodo_id)?.nombre) : 'Malla base · todos los periodos'}</small></div><span className="malla-posicion" title="Posición de lectura en esta lista">{indice + 1}.</span><Etiqueta estado={r.activa ? 'ACTIVO' : 'INACTIVO'} />{puedeEditar && <><button className="link-button" onClick={() => { const n = prompt('Posición de lectura (número entero, sin relación con horarios)', valor(r.orden)); if (n !== null && /^\d+$/.test(n.trim())) void ejecutar('/asignaturas/grados/' + r.id,'PUT',{ orden: Number(n), activa: r.activa },'Posición actualizada.') }}>Cambiar posición</button><button className="link-button" onClick={() => void ejecutar('/asignaturas/grados/' + r.id,'PUT',{ orden: r.orden, activa: !r.activa },'Relación actualizada.')}>{r.activa ? 'Retirar' : 'Reactivar'}</button></>}</div>) : <Vacio texto="Este grado no tiene asignaturas relacionadas." />}
        {puedeEditar && <form className="formulario" onSubmit={e => { e.preventDefault(); void ejecutar('/asignaturas/grados','POST',{ asignaturaId: asignatura, gradoId: grado, periodoId: periodo || null, orden: Math.max(0, ...relaciones.map(r => Number(r.orden) || 0)) + 1, activa: true },'Asignatura añadida al grado.'); setAsignatura('') }}><h3>Añadir al grado</h3><div className="form-linea"><Selector etiqueta="Asignatura" valorActual={asignatura} cambio={setAsignatura} opciones={opciones(cat.asignaturas.filter(a => a.activa && !relaciones.some(r => r.asignatura_id === a.id && (r.periodo_id || '') === (periodo || ''))), a => valor(a.codigo) + ' · ' + valor(a.nombre))} requerido /><button className="boton primario boton-alineado">Añadir al final</button></div><p className="malla-ayuda">El sistema asigna automáticamente la siguiente posición de lectura.</p></form>}</> : <Vacio texto="Selecciona un nivel y grado a la izquierda." />}
    </Panel></div>
    <CatalogoAsignaturas asignaturas={cat.asignaturas} puedeEditar={puedeEditar} ejecutar={ejecutar} />
  </Pagina>
}

export function Docentes() {
  const { datos: cat } = useCatalogos()
  const [buscar, setBuscar] = useState('')
  const { datos, error: errorLista, cargar } = useDatos<Fila[]>('/docentes?buscar=' + encodeURIComponent(buscar), [])
  const [seleccion, setSeleccion] = useState('')
  const { datos: asignaciones, cargar: cargarAsignaciones } = useDatos<Fila[]>(seleccion ? '/docentes/' + seleccion + '/asignaciones' : '', [])
  const { datos: cuentas, error: errorIdentidad, cargar: cargarCuentas } = useDatos<Fila[]>(seleccion ? '/docentes/cuentas-identidad' : '', [])
  const [form, setForm] = useState({ nombres: '', apellidos: '', tipoDocumento: '', documento: '', correoInstitucional: '', telefono: '', cuentaIdentidadId: '' })
  const [modoEdicion, setModoEdicion] = useState(false)
  const [estadoDocente, setEstadoDocente] = useState('ACTIVO')
  const [usuarioDocente, setUsuarioDocente] = useState('')
  const [claveDocente, setClaveDocente] = useState('')
  const [gradoOferta, setGradoOferta] = useState('')
  const [asignaturaOferta, setAsignaturaOferta] = useState('')
  const [ofertasElegidas, setOfertasElegidas] = useState<string[]>([])
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  function elegir(d: Fila) {
    setSeleccion(valor(d.id)); setModoEdicion(true); setEstadoDocente(valor(d.estado))
    setForm({ nombres: valor(d.nombres), apellidos: valor(d.apellidos), tipoDocumento: valor(d.tipo_documento), documento: valor(d.documento), correoInstitucional: valor(d.correo_institucional), telefono: valor(d.telefono), cuentaIdentidadId: valor(d.cuenta_identidad_id) })
  }
  async function guardar(e: FormEvent) { e.preventDefault(); setError(''); try {
    if (modoEdicion && seleccion) {
      await api('/docentes/' + seleccion, 'PUT', { docente: { ...form, cuentaIdentidadId: form.cuentaIdentidadId || null }, estado: estadoDocente })
      setExito('Datos del docente actualizados.')
    } else {
      const r = await api<{ id: string }>('/docentes','POST',{ ...form, cuentaIdentidadId: null })
      setSeleccion(r.id); setModoEdicion(true); setExito('Docente registrado.')
    }
    await cargar()
  } catch (err) { setError((err as Error).message) } }
  async function crearCuenta(e: FormEvent) { e.preventDefault(); setError(''); try {
    await api('/docentes/' + seleccion + '/cuenta','POST',{ usuario: usuarioDocente, claveInicial: claveDocente })
    setClaveDocente(''); await cargar(); await cargarCuentas(); setExito('Cuenta docente creada y vinculada. Entrega al docente su usuario y la clave inicial que elegiste.')
  } catch (err) { setError((err as Error).message) } }
  async function asignar(e: FormEvent) { e.preventDefault(); setError(''); try {
    if (!ofertasElegidas.length) throw new Error('Selecciona al menos una sección para asignar.')
    await api('/docentes/asignaciones/lote','POST',{ docenteId: seleccion, ofertasIds: ofertasElegidas })
    await cargarAsignaciones(); setOfertasElegidas([]); setExito('Cursos asignados sin retirar las demás asignaciones.')
  } catch (err) { setError((err as Error).message) } }
  async function retirar(id: string) { setError(''); try { await api('/docentes/asignaciones/' + id + '/retirar','POST',{}); await cargarAsignaciones(); setExito('Asignación retirada.') } catch (err) { setError((err as Error).message) } }
  const docente = datos.find(d => d.id === seleccion)
  const cuentasCorrespondientes = cuentas.filter(c => valor(c.referencia_erp) === seleccion)
  const cursosDelGrado = cat.ofertas.filter(o => o.grado_id === gradoOferta && o.estado === 'ACTIVA')
  const asignaturasDelGrado = cat.asignaturas.filter(a => cursosDelGrado.some(o => o.asignatura_id === a.id))
  const seccionesCurso = cursosDelGrado.filter(o => o.asignatura_id === asignaturaOferta)
  function elegirOferta(id: string) { setOfertasElegidas(actual => actual.includes(id) ? actual.filter(x => x !== id) : [...actual, id]) }
  return <Pagina titulo="Docentes" intro="Identidad básica y asignaciones académicas compartidas." accion={<Link className="boton" to="/oferta">Ver oferta →</Link>}>
    <Aviso error={error || errorLista || (seleccion ? errorIdentidad : '')} exito={exito} /><div className="dos-columnas"><Panel titulo="Directorio docente" subtitulo="Busca y selecciona un registro.">
      <Campo etiqueta="Buscar docente" valorActual={buscar} cambio={setBuscar} placeholder="Nombre, código o documento" />
      {datos.length ? datos.map(d => <button key={valor(d.id)} className={'docente-item ' + (seleccion === d.id ? 'seleccionado' : '')} onClick={() => elegir(d)}><strong>{nombre(d)}</strong><small>{valor(d.codigo)} · {valor(d.correo_institucional) || 'Sin correo institucional'}</small></button>) : <Vacio />}
    </Panel><Panel titulo={modoEdicion ? 'Editar docente' : 'Registrar docente'} subtitulo="El código interno se genera desde la API ERP. La cuenta de Identidad puede vincularse después." accion={modoEdicion && <button className="boton" onClick={() => { setSeleccion(''); setModoEdicion(false); setForm({ nombres: '', apellidos: '', tipoDocumento: '', documento: '', correoInstitucional: '', telefono: '', cuentaIdentidadId: '' }) }}>+ Nuevo docente</button>}>
      <form onSubmit={guardar} className="formulario"><div className="campos-2">
        <Campo etiqueta="Nombres" valorActual={form.nombres} cambio={v => setForm({ ...form, nombres: v })} requerido />
        <Campo etiqueta="Apellidos" valorActual={form.apellidos} cambio={v => setForm({ ...form, apellidos: v })} requerido />
        <Selector etiqueta="Documento" valorActual={form.tipoDocumento} cambio={v => setForm({ ...form, tipoDocumento: v })} opciones={['DNI','CE','PASAPORTE'].map(x => ({valor:x,texto:x}))} vacio="Sin documento" />
        <Campo etiqueta="Número" valorActual={form.documento} cambio={v => setForm({ ...form, documento: v })} />
        <Campo etiqueta="Correo institucional" tipo="email" valorActual={form.correoInstitucional} cambio={v => setForm({ ...form, correoInstitucional: v })} />
        <Campo etiqueta="Teléfono" valorActual={form.telefono} cambio={v => setForm({ ...form, telefono: v })} />
        {modoEdicion && <Selector etiqueta="Cuenta de Boston Identidad" valorActual={form.cuentaIdentidadId} cambio={v => setForm({ ...form, cuentaIdentidadId: v })} opciones={cuentasCorrespondientes.map(c => ({ valor: valor(c.id), texto: valor(c.usuario) }))} vacio="Sin cuenta vinculada" />}
        {modoEdicion && <Selector etiqueta="Estado" valorActual={estadoDocente} cambio={setEstadoDocente} opciones={['ACTIVO','INACTIVO'].map(x => ({ valor: x, texto: x }))} />}
      </div><button className="boton primario">{modoEdicion ? 'Guardar cambios' : 'Registrar docente'}</button></form>
      {modoEdicion && seleccion && !docente?.cuenta_identidad_id && <form onSubmit={crearCuenta} className="formulario"><h3>Crear acceso al Aula</h3><p className="ayuda">El alta de acceso es independiente del registro académico. Indica una clave inicial de al menos 12 caracteres y entrégala al docente.</p><div className="campos-2"><Campo etiqueta="Usuario docente" valorActual={usuarioDocente} cambio={setUsuarioDocente} requerido placeholder="doc.nombre.apellido" /><Campo etiqueta="Clave inicial" tipo="password" valorActual={claveDocente} cambio={setClaveDocente} requerido /></div><button className="boton">Crear y vincular cuenta</button></form>}
    </Panel></div>
    {seleccion && <Panel titulo={'Asignaciones · ' + (docente ? nombre(docente) : 'Docente')} subtitulo="Un docente puede tener múltiples cursos y un curso puede tener docentes compartidos.">
      {asignaciones.length ? <div className="tabla-wrap"><table><thead><tr><th>Asignatura</th><th>Grado</th><th>Sección</th><th>Periodo</th><th>Estado</th><th></th></tr></thead><tbody>{asignaciones.map(a => <tr key={valor(a.id)}><td>{valor(a.asignatura)}<small>{valor(a.codigo_asignatura)}</small></td><td>{valor(a.nivel)} · {valor(a.grado)}</td><td>{valor(a.seccion)}</td><td>{valor(a.periodo)}</td><td><Etiqueta estado={a.estado} /></td><td>{a.estado === 'ACTIVA' && <button className="link-button" onClick={() => void retirar(valor(a.id))}>Retirar</button>}</td></tr>)}</tbody></table></div> : <Vacio texto="Sin asignaciones registradas." />}
      <form onSubmit={asignar} className="formulario"><h3>Agregar asignaciones</h3><p className="ayuda">Elige un grado y una asignatura, marca las secciones y repite para otros cursos o grados. Guarda todas juntas.</p><div className="campos-2"><Selector etiqueta="Grado" valorActual={gradoOferta} cambio={v => { setGradoOferta(v); setAsignaturaOferta('') }} opciones={opciones(cat.grados, g => valor(g.nivel) + ' · ' + valor(g.nombre))} /><Selector etiqueta="Asignatura" valorActual={asignaturaOferta} cambio={setAsignaturaOferta} opciones={opciones(asignaturasDelGrado, a => valor(a.codigo) + ' · ' + valor(a.nombre))} /></div>
        {seccionesCurso.length > 0 && <div className="acciones">{seccionesCurso.map(o => <label className="check" key={valor(o.id)}><input type="checkbox" checked={ofertasElegidas.includes(valor(o.id))} onChange={() => elegirOferta(valor(o.id))} /> {valor(o.periodo)} · Sección {valor(o.seccion)}</label>)}</div>}
        <div className="barra-acciones"><span>{ofertasElegidas.length} curso(s) y sección(es) seleccionados</span><button className="boton primario" disabled={!ofertasElegidas.length}>Asignar seleccionados</button></div></form>
    </Panel>}
  </Pagina>
}
