import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, opciones, Pagina, Panel, Selector, useDatos } from '../../../shared/ui/index'

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
