import { useState } from 'react'
import { Link } from 'react-router-dom'
import { nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Etiqueta, opciones, Pagina, Panel, Selector, useCatalogos, useDatos, Vacio } from '../../../shared/ui/index'

export function Matriculas() {
  const { datos: cat } = useCatalogos()
  const [periodo, setPeriodo] = useState('')
  const [estado, setEstado] = useState('')
  const ruta = '/matriculas?' + new URLSearchParams({ ...(periodo ? { periodo } : {}), ...(estado ? { estado } : {}) }).toString()
  const { datos, error } = useDatos<Fila[]>(ruta, [])
  return <Pagina titulo="Matrículas" intro="Procesos pendientes, reservas e inscripciones confirmadas por periodo." accion={<Link className="boton primario" to="/matriculas/nueva">+ Iniciar matrícula</Link>}>
    <Panel titulo="Buscar matrículas" subtitulo="La matrícula de un nuevo periodo conserva el historial anterior."><div className="campos-2">
      <Selector etiqueta="Periodo" valorActual={periodo} cambio={setPeriodo} opciones={opciones(cat.periodos, p => valor(p.nombre))} vacio="Todos los periodos" />
      <Selector etiqueta="Estado" valorActual={estado} cambio={setEstado} opciones={['PROCESO_PENDIENTE', 'VACANTE_RESERVADA', 'MATRICULADO', 'CANCELADA'].map(x => ({ valor: x, texto: x.replaceAll('_', ' ') }))} vacio="Todos los estados" />
    </div></Panel><Panel titulo="Registros" subtitulo={datos.length + ' matrículas encontradas'}><Aviso error={error} />
      {datos.length ? <div className="tabla-wrap"><table><thead><tr><th>Estudiante</th><th>Periodo</th><th>Grado / sección</th><th>Procedencia</th><th>Estado</th><th></th></tr></thead><tbody>{datos.map(m => <tr key={valor(m.id)}><td><strong>{nombre(m)}</strong><small>{valor(m.codigo) || 'Sin código institucional'}</small></td><td>{valor(m.periodo)}</td><td>{valor(m.grado) || 'Pendiente'} {valor(m.seccion) && '· ' + valor(m.seccion)}</td><td>{valor(m.procedencia)}</td><td><Etiqueta estado={m.estado} /></td><td><Link to={'/matriculas/' + m.id}>Abrir →</Link></td></tr>)}</tbody></table></div> : <Vacio />}
    </Panel>
  </Pagina>
}
