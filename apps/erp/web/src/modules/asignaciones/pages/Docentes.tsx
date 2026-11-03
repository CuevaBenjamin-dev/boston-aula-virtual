import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, Etiqueta, opciones, Pagina, Panel, Selector, useCatalogos, useDatos, Vacio } from '../../../shared/ui/index'

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
