import { useState, type FormEvent } from 'react'
import { api, type Fila } from '../../../shared/api/client'
import { Aviso, Pagina, Panel } from '../../../shared/ui/index'

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
