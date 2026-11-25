import { useEffect, useState } from 'react'
import type { Sesion } from '../api/sesion'
import { erp } from '../api/erp'
import { IconoInstitucional } from './IconoInstitucional'

type Estado = { id: string; sistema: string; accion: string; estado: string; motivo: string }

export function EstadoIntegracion({ sesion, tipo, id, tarjeta = false }: { sesion: Sesion; tipo: 'MATRICULA' | 'ASIGNACION' | 'PERSONA'; id: string; tarjeta?: boolean }) {
  const [filas, setFilas] = useState<Estado[]>([])
  const [error, setError] = useState('')
  const [cargado, setCargado] = useState(false)
  useEffect(() => {
    let vigente = true
    const cargar = () => { void erp<Estado[]>(`/integracion/estado?tipo=${tipo}&id=${id}`, sesion)
      .then(datos => { if (vigente) { setFilas(datos); setCargado(true); setError('') } }).catch(e => { if (vigente) setError(String(e)) }) }
    cargar(); const reloj = window.setInterval(cargar, 10000)
    return () => { vigente = false; window.clearInterval(reloj) }
  }, [sesion, tipo, id])
  async function reintentar(solicitud: string) {
    try { await erp<void>(`/integracion/${solicitud}/reintentar`, sesion, 'POST'); setError('');
      setFilas(f => f.map(x => x.id === solicitud ? { ...x, estado: 'ERROR', motivo: 'Reintento solicitado' } : x)) }
    catch (e) { setError(String(e)) }
  }
  const recientes = ['IDENTIDAD', 'AULA'].map(sistema => filas.find(f => f.sistema === sistema)).filter((f): f is Estado => !!f)
  if (tarjeta) return <div className="mat-integracion-lista" aria-live="polite">
    {error && <small className="per-aviso mat-integracion-consulta">{cargado ? 'No se pudo actualizar. Se muestra la última consulta disponible. ' : 'No se pudo consultar la integración. '}{error}</small>}
    {(['IDENTIDAD', 'AULA'] as const).map(sistema => {
      const f = recientes.find(r => r.sistema === sistema)
      const estado = f?.estado
      const etiqueta = !f ? (!cargado ? (error ? 'No disponible' : 'Consultando') : 'Sin solicitud') :
        estado === 'CONFIRMADA' ? 'Confirmada' : estado === 'INTERVENCION' ? 'Requiere intervención' :
        estado === 'ERROR' ? (f.motivo === 'Reintento solicitado' ? 'Reintento solicitado' : 'Error reintentable') :
        estado === 'PROCESANDO' ? 'Procesando' : 'Pendiente de sincronización'
      const color = estado === 'CONFIRMADA' ? 'confirmada' : estado === 'INTERVENCION' || estado === 'ERROR' && f?.motivo !== 'Reintento solicitado' ? 'incidencia' : f ? 'pendiente' : ''
      const simbolo = color === 'confirmada' ? 'confirmado' : color === 'incidencia' ? 'alerta' : color === 'pendiente' ? 'pendiente' : 'informacion'
      return <div className="mat-integracion-fila" key={sistema}>
        <span className="mat-integracion-icono"><IconoInstitucional nombre={sistema === 'IDENTIDAD' ? 'identidad' : 'aula'}/></span>
        <div className="mat-integracion-texto"><strong>{sistema === 'IDENTIDAD' ? 'Identidad' : 'Aula Virtual'}</strong><small>{f?.motivo || (cargado ? 'Sin solicitudes externas para este servicio.' : error ? 'Estado no verificado.' : 'Consultando el estado del servicio…')}</small></div>
        <span className={`mat-integracion-estado ${color}`}><IconoInstitucional nombre={simbolo}/>{etiqueta}</span>
        {f?.estado === 'INTERVENCION' && !error && <button className="per-secundario mat-integracion-reintento" type="button" onClick={() => void reintentar(f.id)}>Reintentar</button>}
      </div>
    })}
  </div>
  return <div aria-live="polite">{error && <small className="per-aviso">{error}</small>}
    {!recientes.length && <small>Sin solicitudes externas para este registro.</small>}
    {recientes.map(f => <p key={f.id}><strong>{f.sistema}</strong>: {f.estado === 'CONFIRMADA' ? 'Confirmada' :
      f.estado === 'INTERVENCION' ? 'Requiere intervención' : f.estado === 'ERROR' ? 'Error reintentable' :
      f.estado === 'PROCESANDO' ? 'Procesando' : 'Pendiente de sincronización'} · {f.motivo}
      {f.estado === 'INTERVENCION' && <button className="per-secundario" type="button" onClick={() => void reintentar(f.id)}>Reintentar</button>}</p>)}
  </div>
}
