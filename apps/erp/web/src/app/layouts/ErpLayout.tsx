import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { IconoInstitucional } from '../../shared/ui/IconoInstitucional'
import { modulos } from '../../shared/lib/modulos'
import { cerrarSesion, type Sesion } from '../../shared/api/sesion'
import { erp } from '../../shared/api/erp'
import './asistencia-contador.css'

type ConteoJustificaciones = { anio: number; cantidad: number }

function useConteoJustificaciones(sesion: Sesion) {
  const [conteo, setConteo] = useState<ConteoJustificaciones[] | null>(null)
  const [error, setError] = useState(false)
  useEffect(() => {
    setConteo(null)
    setError(false)
    if (sesion.rol !== 'COORDINACION') return
    let vigente = true
    let secuencia = 0
    const consultar = async () => {
      const turno = ++secuencia
      try {
        const datos = await erp<ConteoJustificaciones[]>('/asistencia/justificaciones/conteo', sesion)
        if (!Array.isArray(datos) || datos.some(d => !Number.isInteger(d.anio) || !Number.isSafeInteger(d.cantidad) || d.cantidad < 0)) throw new Error('Conteo inválido')
        if (vigente && turno === secuencia) { setConteo(datos); setError(false) }
      } catch {
        if (vigente && turno === secuencia) setError(true)
      }
    }
    const alVolver = () => { if (!document.hidden) void consultar() }
    void consultar()
    const intervalo = window.setInterval(alVolver, 15000)
    window.addEventListener('focus', alVolver)
    document.addEventListener('visibilitychange', alVolver)
    window.addEventListener('bbs:justificaciones-actualizadas', alVolver)
    return () => {
      vigente = false
      window.clearInterval(intervalo)
      window.removeEventListener('focus', alVolver)
      document.removeEventListener('visibilitychange', alVolver)
      window.removeEventListener('bbs:justificaciones-actualizadas', alVolver)
    }
  }, [sesion])
  return { conteo, error }
}

export function ErpLayout({ sesion }: { sesion: Sesion }) {
  const { pathname } = useLocation()
  const { conteo, error: errorConteo } = useConteoJustificaciones(sesion)
  const total = conteo?.reduce((suma, fila) => suma + fila.cantidad, 0) ?? 0
  const mostrarConteo = sesion.rol === 'COORDINACION' && (total > 0 || errorConteo)
  const detalleConteo = errorConteo
    ? 'No se pudo actualizar el conteo de justificaciones' + (conteo ? ' (último dato conocido)' : '')
    : `${total} justificaciones pendientes · ${conteo?.map(fila => `${fila.anio}: ${fila.cantidad}`).join(' · ') ?? ''}`
  const actual = modulos.find(modulo => pathname === '/' + modulo.ruta || pathname.startsWith('/' + modulo.ruta + '/'))
  return <div className="app">
    <a className="saltar" href="#contenido">Ir al contenido</a>
    <aside className="barra-lateral">
      <NavLink to="/" className="marca" aria-label="Boston ERP, inicio">
        <span className="bbs" aria-hidden="true"><b>B</b><b>B</b><b>S</b></span>
        <strong>Boston Bilingual School</strong>
        <small>ERP INSTITUCIONAL</small>
      </NavLink>
      <nav aria-label="Módulos del ERP">
        <NavLink to="/" end className={({ isActive }) => isActive ? 'navegacion activa' : 'navegacion'}><IconoInstitucional nombre="inicio"/><span>Inicio</span></NavLink>
        {modulos.map(modulo => <NavLink key={modulo.ruta} to={'/' + modulo.ruta}
          className={({ isActive }) => (isActive ? 'navegacion activa' : 'navegacion') + (modulo.ruta === 'asistencia' && mostrarConteo ? ' navegacion-con-conteo' : '')}>
          <IconoInstitucional nombre={modulo.ruta}/><span>{modulo.nombre}</span>
          {modulo.ruta === 'asistencia' && mostrarConteo && <span className="erp-asistencia-conteo"
            title={detalleConteo} aria-label={detalleConteo}>{errorConteo && total === 0 ? '?' : total > 99 ? '99+' : total}</span>}
        </NavLink>)}
      </nav>
      <div className="usuario"><span>{sesion.usuario}</span><button type="button"
        onClick={() => cerrarSesion(sesion.csrf)}>Cerrar sesión</button></div>
    </aside>
    <div className="cuerpo">
      <header className="barra-superior">
        <div className="ruta">Boston Bilingual School <span>/ ERP</span></div>
        <h1>{actual?.nombre ?? 'ERP institucional'}<span className="punto">.</span></h1>
        <span className="fase">{sesion.rol === 'DIRECCION' ? 'DIRECCIÓN' : 'COORDINACIÓN'} · {new Date().getFullYear()}</span>
      </header>
      <main id="contenido"><Outlet /></main>
    </div>
  </div>
}
