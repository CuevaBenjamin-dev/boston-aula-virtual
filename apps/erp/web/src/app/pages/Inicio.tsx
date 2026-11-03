import { Link } from 'react-router-dom'
import { type Fila, valor } from '../../shared/api/client'
import { Aviso, Pagina, Panel, useDatos, Vacio } from '../../shared/ui/index'

export function Inicio() {
  const { datos, error } = useDatos<Fila[]>('/resumen', [])
  const pendientes = datos.reduce((n, r) => n + Number(r.pendientes || 0), 0)
  const reservas = datos.reduce((n, r) => n + Number(r.reservadas || 0), 0)
  const confirmadas = datos.reduce((n, r) => n + Number(r.confirmadas || 0), 0)
  return <Pagina titulo="Inicio" intro="Estado institucional de las matrículas y estudiantes." accion={<Link className="boton primario" to="/matriculas/nueva">+ Nueva matrícula</Link>}>
    <Aviso error={error} /><div className="indicadores"><div><small>PROCESOS PENDIENTES</small><strong>{pendientes}</strong></div><div><small>VACANTES RESERVADAS</small><strong>{reservas}</strong></div><div><small>MATRÍCULAS CONFIRMADAS</small><strong>{confirmadas}</strong></div></div>
    <Panel titulo="Estudiantes por periodo, nivel y grado" subtitulo="Conteos de la API ERP. La fila sin grado corresponde a registros aún incompletos.">
      {datos.length ? <div className="tabla-wrap"><table><thead><tr><th>Periodo</th><th>Nivel</th><th>Grado</th><th>Pendientes</th><th>Reservadas</th><th>Confirmadas</th></tr></thead><tbody>{datos.map((r, i) => <tr key={i}><td>{valor(r.periodo)}</td><td>{valor(r.nivel) || '—'}</td><td>{valor(r.grado) || 'Sin asignar'}</td><td>{valor(r.pendientes)}</td><td>{valor(r.reservadas)}</td><td>{valor(r.confirmadas)}</td></tr>)}</tbody></table></div> : <Vacio texto="Aún no hay periodos registrados." />}
    </Panel>
  </Pagina>
}
