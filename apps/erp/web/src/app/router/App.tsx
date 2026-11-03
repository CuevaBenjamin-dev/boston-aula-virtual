import { AsistenteMatricula as NuevaMatricula } from '../../modules/matriculas/pages/AsistenteMatricula'
import { Docentes } from '../../modules/asignaciones/pages/Docentes'
import { Importaciones } from '../../modules/matriculas/pages/Importaciones'
import { Apoderados } from '../../modules/personas/pages/Apoderados'
import { FichaMatricula } from '../../modules/matriculas/pages/FichaMatricula'
import { FichaEstudiante } from '../../modules/personas/pages/FichaEstudiante'
import { Estudiantes } from '../../modules/personas/pages/Estudiantes'
import { Inicio } from '../pages/Inicio'
import { useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { csrfCookie, sesion, type Sesion } from '../../shared/api/client'
import { Matriculas } from '../../modules/matriculas/pages/Matriculas'
import { Asignaturas } from '../../modules/oferta/pages/Asignaturas'
import { Oferta } from '../../modules/oferta/pages/Oferta'
import { PageHeadingContext, type HeadingMeta } from '../../shared/ui/PageHeading'

const menu = [
  ['/', 'Inicio', '⌂'],
  ['/estudiantes', 'Estudiantes', '◫'],
  ['/matriculas', 'Matrículas', '▤'],
  ['/apoderados', 'Apoderados', '♧'],
  ['/importaciones', 'Importación Excel', '⇧'],
  ['/oferta', 'Oferta académica', '▦'],
  ['/asignaturas', 'Asignaturas', '▧'],
  ['/docentes', 'Docentes', '♙']
]

async function cerrarSesion() {
  const { csrf } = await sesion()
  const formulario = document.createElement('form')
  formulario.method = 'POST'
  formulario.action = '/logout'
  const token = document.createElement('input')
  token.type = 'hidden'
  token.name = '_csrf'
  token.value = csrf || csrfCookie()
  formulario.appendChild(token)
  document.body.appendChild(formulario)
  formulario.submit()
}

function Estructura({ usuario, tipo }: { usuario: string; tipo: string }) {
  const { pathname } = useLocation()
  const [heading, setHeading] = useState<HeadingMeta | null>(null)
  const inicio = pathname === '/'
  const titulo = heading?.path === pathname ? heading.title :
    pathname.startsWith('/estudiantes/') ? 'Ficha del estudiante' :
    pathname === '/matriculas/nueva' ? 'Nueva matrícula' :
    pathname.startsWith('/matriculas/') ? 'Matrícula' :
    menu.find(([ruta]) => ruta === pathname)?.[1] || 'ERP institucional'
  const metrica = heading?.path === pathname ? heading : null
  return <PageHeadingContext.Provider value={setHeading}><div className={'app ' + (inicio ? '' : 'erp-compact')}>
    <aside className="sidebar">
      <div className="marca"><div className="bbs"><b>B</b><b>B</b><b>S</b></div><strong>Boston Bilingual School</strong><span>ERP INSTITUCIONAL</span></div>
      <nav aria-label="Navegación principal">{menu.map(([ruta, nombre, icono]) =>
        <NavLink key={ruta} to={ruta} end={ruta === '/'} className={({ isActive }) => isActive ? 'navitem activo' : 'navitem'}><i>{icono}</i>{nombre}</NavLink>)}</nav>
      <div className="sidebar-user"><strong>{usuario}</strong><small>{tipo === 'DIRECCION' ? 'Dirección' : 'Coordinación Académica'}</small><button onClick={() => { void cerrarSesion() }}>Cerrar sesión</button></div>
    </aside>
    <div className="contenedor">
      <header className="topbar"><div className="topbar-heading"><div className="top-title"><strong>Boston Bilingual School</strong><span> / ERP</span></div>{!inicio && <h1 className="topbar-page-title">{titulo}<i aria-hidden="true">.</i></h1>}</div>{!inicio && metrica?.metricValue && <div className="topbar-metric"><span>{metrica.metricLabel}</span><strong>{metrica.metricValue}</strong><small>{metrica.metricCaption}</small></div>}<div className="topbar-meta"><span>Gestión académica</span><b>{tipo === 'DIRECCION' ? 'DIRECCIÓN' : 'COORDINACIÓN'}</b></div></header>
      <main><Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/estudiantes" element={<Estudiantes />} />
        <Route path="/estudiantes/:id" element={<FichaEstudiante />} />
        <Route path="/matriculas" element={<Matriculas />} />
        <Route path="/matriculas/nueva" element={<NuevaMatricula />} />
        <Route path="/matriculas/:id" element={<FichaMatricula />} />
        <Route path="/apoderados" element={<Apoderados />} />
        <Route path="/importaciones" element={<Importaciones />} />
        <Route path="/oferta" element={<Oferta puedeEditar={tipo === 'DIRECCION'} />} />
        <Route path="/asignaturas" element={<Asignaturas puedeEditar={tipo === 'DIRECCION'} />} />
        <Route path="/docentes" element={<Docentes />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes></main>
    </div>
  </div></PageHeadingContext.Provider>
}

export default function App() {
  const [estado, setEstado] = useState<Sesion | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void sesion().then(s => {
    setEstado(s)
  }).catch(e => setError(String(e))) }, [])
  if (error) return <div className="acceso"><h1>No se pudo abrir ERP</h1><p>{error}</p><button onClick={() => location.reload()}>Reintentar</button></div>
  if (!estado) return <div className="acceso"><p>Cargando Boston ERP…</p></div>
  if (!estado.autenticado) return <div className="acceso"><div className="acceso-caja"><div className="bbs"><b>B</b><b>B</b><b>S</b></div><p className="eyebrow">BOSTON BILINGUAL SCHOOL</p><h1>ERP institucional<span className="punto">.</span></h1><p>Gestión de estudiantes, matrículas y oferta académica.</p><a className="boton primario" href="/oauth2/authorization/boston-erp">Iniciar sesión con Boston Identidad</a></div></div>
  if (!estado.autorizado) return <div className="acceso"><div className="acceso-caja"><p className="eyebrow">ACCESO INSTITUCIONAL</p><h1>Sin permiso académico</h1><p>Esta cuenta no tiene el perfil de Dirección o Coordinación Académica.</p><button onClick={() => { void cerrarSesion() }}>Cambiar cuenta</button></div></div>
  return <BrowserRouter><Estructura usuario={estado.usuario || ''} tipo={estado.tipo || ''} /></BrowserRouter>
}
