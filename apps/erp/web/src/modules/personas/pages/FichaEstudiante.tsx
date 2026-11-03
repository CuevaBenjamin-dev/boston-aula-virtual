import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, fecha, nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, Etiqueta, Pagina, Panel, Selector, useDatos, Vacio } from '../../../shared/ui/index'

export function FichaEstudiante() {
  const { id = '' } = useParams()
  const { datos, error, cargar } = useDatos<Fila>('/estudiantes/' + id, {})
  const [editar, setEditar] = useState(false)
  const [form, setForm] = useState({ nombres: '', apellidos: '', fechaNacimiento: '', tipoDocumento: '', documento: '', nacionalidad: '' })
  const [mensaje, setMensaje] = useState('')
  function comenzar() { setForm({ nombres: valor(datos.nombres), apellidos: valor(datos.apellidos), fechaNacimiento: valor(datos.fecha_nacimiento), tipoDocumento: valor(datos.tipo_documento), documento: valor(datos.documento), nacionalidad: valor(datos.nacionalidad) }); setEditar(true) }
  async function guardar(e: FormEvent) { e.preventDefault(); setMensaje(''); try { await api('/estudiantes/' + id, 'PUT', { ...form, fechaNacimiento: form.fechaNacimiento || null }); setEditar(false); await cargar() } catch (err) { setMensaje((err as Error).message) } }
  const matriculas = (datos.matriculas || []) as Fila[]
  const responsables = (datos.apoderados || []) as Fila[]
  return <Pagina titulo={nombre(datos) || 'Ficha del estudiante'} intro={'Código institucional: ' + valor(datos.codigo)} accion={<Link className="boton" to="/estudiantes">← Estudiantes</Link>}>
    <Aviso error={error} />
    <div className="dos-columnas"><Panel titulo="Identidad institucional" accion={<button className="boton" onClick={comenzar}>Corregir datos</button>}>
      {editar ? <form onSubmit={guardar} className="formulario"><Aviso error={mensaje} /><div className="campos-2">
        <Campo etiqueta="Nombres" valorActual={form.nombres} cambio={v => setForm({ ...form, nombres: v })} requerido />
        <Campo etiqueta="Apellidos" valorActual={form.apellidos} cambio={v => setForm({ ...form, apellidos: v })} requerido />
        <Campo etiqueta="Nacimiento" tipo="date" valorActual={form.fechaNacimiento} cambio={v => setForm({ ...form, fechaNacimiento: v })} />
        <Selector etiqueta="Documento" valorActual={form.tipoDocumento} cambio={v => setForm({ ...form, tipoDocumento: v })} opciones={['DNI', 'CE', 'PASAPORTE', 'PROVISIONAL'].map(x => ({ valor: x, texto: x }))} />
        <Campo etiqueta="Número" valorActual={form.documento} cambio={v => setForm({ ...form, documento: v })} />
        <Campo etiqueta="Nacionalidad" valorActual={form.nacionalidad} cambio={v => setForm({ ...form, nacionalidad: v })} />
      </div><div className="acciones"><button className="boton primario">Guardar</button><button type="button" className="boton" onClick={() => setEditar(false)}>Cancelar</button></div></form> :
        <dl className="ficha"><div><dt>Estado</dt><dd><Etiqueta estado={datos.estado} /></dd></div><div><dt>Documento</dt><dd>{valor(datos.tipo_documento)} {valor(datos.documento) || 'No registrado'}</dd></div><div><dt>Fecha de nacimiento</dt><dd>{fecha(datos.fecha_nacimiento)}</dd></div><div><dt>Nacionalidad</dt><dd>{valor(datos.nacionalidad) || 'No registrada'}</dd></div></dl>}
    </Panel><Panel titulo="Responsables vinculados" accion={<Link className="boton" to={'/apoderados?estudiante=' + id}>Gestionar →</Link>}>
      {responsables.length ? responsables.map(r => <div className="item" key={valor(r.persona_id)}><strong>{nombre(r)}</strong><small>{valor(r.rol)} · {valor(r.vinculo)} · {valor(r.documento)}</small></div>) : <Vacio texto="Aún no se ha vinculado un responsable." />}
    </Panel></div>
    <Panel titulo="Historial de matrículas" accion={<Link className="boton primario" to={'/matriculas/nueva?estudiante=' + id}>+ Nuevo periodo</Link>}>
      {matriculas.length ? matriculas.map(m => <div className="item item-linea" key={valor(m.id)}><div><strong>{valor(m.periodo)} · {valor(m.grado) || 'Sin grado'}</strong><small>{valor(m.seccion) ? 'Sección ' + m.seccion : 'Sección pendiente'} · {valor(m.procedencia)}</small></div><Etiqueta estado={m.estado} /><Link to={'/matriculas/' + m.id}>Abrir →</Link></div>) : <Vacio texto="No tiene matrículas registradas." />}
    </Panel>
  </Pagina>
}
