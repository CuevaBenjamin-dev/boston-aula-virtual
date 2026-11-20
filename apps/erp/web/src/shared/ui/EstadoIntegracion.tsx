import { useEffect, useState } from 'react'
import type { Sesion } from '../api/sesion'
import { erp } from '../api/erp'

type Estado = { id: string; sistema: string; accion: string; estado: string; motivo: string }

export function EstadoIntegracion({ sesion, tipo, id }: { sesion: Sesion; tipo: 'MATRICULA' | 'ASIGNACION' | 'PERSONA'; id: string }) {
  const [filas, setFilas] = useState<Estado[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    let vigente = true
    const cargar = () => { void erp<Estado[]>(`/integracion/estado?tipo=${tipo}&id=${id}`, sesion)
      .then(datos => { if (vigente) { setFilas(datos); setError('') } }).catch(e => { if (vigente) setError(String(e)) }) }
    cargar(); const reloj = window.setInterval(cargar, 10000)
    return () => { vigente = false; window.clearInterval(reloj) }
  }, [sesion, tipo, id])
  async function reintentar(solicitud: string) {
    try { await erp<void>(`/integracion/${solicitud}/reintentar`, sesion, 'POST'); setError('');
      setFilas(f => f.map(x => x.id === solicitud ? { ...x, estado: 'ERROR', motivo: 'Reintento solicitado' } : x)) }
    catch (e) { setError(String(e)) }
  }
  const recientes = ['IDENTIDAD', 'AULA'].map(sistema => filas.find(f => f.sistema === sistema)).filter((f): f is Estado => !!f)
  return <div aria-live="polite">{error && <small className="per-aviso">{error}</small>}
    {!recientes.length && <small>Sin solicitudes externas para este registro.</small>}
    {recientes.map(f => <p key={f.id}><strong>{f.sistema}</strong>: {f.estado === 'CONFIRMADA' ? 'Confirmada' :
      f.estado === 'INTERVENCION' ? 'Requiere intervención' : f.estado === 'ERROR' ? 'Error reintentable' :
      f.estado === 'PROCESANDO' ? 'Procesando' : 'Pendiente de sincronización'} · {f.motivo}
      {f.estado === 'INTERVENCION' && <button className="per-secundario" type="button" onClick={() => void reintentar(f.id)}>Reintentar</button>}</p>)}
  </div>
}
