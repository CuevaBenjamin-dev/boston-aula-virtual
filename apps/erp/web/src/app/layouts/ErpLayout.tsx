import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { modulos } from '../../shared/lib/modulos'
import { cerrarSesion, type Sesion } from '../../shared/api/sesion'

export function ErpLayout({ sesion }: { sesion: Sesion }) {
  const { pathname } = useLocation()
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
        <NavLink to="/" end className={({ isActive }) => isActive ? 'navegacion activa' : 'navegacion'}>Inicio</NavLink>
        {modulos.map(modulo => <NavLink key={modulo.ruta} to={'/' + modulo.ruta}
          className={({ isActive }) => isActive ? 'navegacion activa' : 'navegacion'}>
          {modulo.nombre}
        </NavLink>)}
      </nav>
      <div className="usuario"><span>{sesion.usuario}</span><button type="button"
        onClick={() => cerrarSesion(sesion.csrf)}>Cerrar sesión</button></div>
    </aside>
    <div className="cuerpo">
      <header className="barra-superior">
        <div className="ruta">Boston Bilingual School <span>/ ERP</span></div>
        <h1>{actual?.nombre ?? 'ERP institucional'}<span className="punto">.</span></h1>
        <span className="fase">BASE TÉCNICA</span>
      </header>
      <main id="contenido"><Outlet /></main>
    </div>
  </div>
}
