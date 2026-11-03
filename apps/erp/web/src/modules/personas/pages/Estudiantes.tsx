import { useState } from 'react'
import { Link } from 'react-router-dom'
import { nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, Etiqueta, Pagina, Panel, useDatos, Vacio } from '../../../shared/ui/index'

export function Estudiantes() {
  const [buscar, setBuscar] = useState('')
  const { datos, error } = useDatos<Fila[]>('/estudiantes?buscar=' + encodeURIComponent(buscar), [])
  return <Pagina titulo="Estudiantes" intro="Registro permanente, búsqueda y trayectoria académica." accion={<Link className="boton primario" to="/matriculas/nueva">+ Iniciar matrícula</Link>}>
    <Panel titulo="Directorio de estudiantes" subtitulo="Busca por código, nombre, apellido o documento."><Campo etiqueta="Buscar estudiante" valorActual={buscar} cambio={setBuscar} placeholder="Nombre, código o documento" /><Aviso error={error} />
      {datos.length ? <div className="tabla-wrap"><table><thead><tr><th>Código</th><th>Estudiante</th><th>Documento</th><th>Estado</th><th></th></tr></thead><tbody>{datos.map(r => <tr key={valor(r.id)}><td><strong>{valor(r.codigo)}</strong></td><td>{nombre(r)}</td><td>{valor(r.tipo_documento)} {valor(r.documento)}</td><td><Etiqueta estado={r.estado} /></td><td><Link to={'/estudiantes/' + r.id}>Ver ficha →</Link></td></tr>)}</tbody></table></div> : <Vacio />}
    </Panel>
  </Pagina>
}
