import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { ErpLayout } from '../layouts/ErpLayout'
import { consultarSesion, type Sesion } from '../../shared/api/sesion'
import { modulos } from '../../shared/lib/modulos'
import { EstadoPendiente } from '../../shared/ui/EstadoPendiente'
import { PersonasModulo } from '../../modules/personas/PersonasModulo'

function Inicio() {
  return <div className="pagina">
    <div className="introduccion">
      <p className="etiqueta">BOSTON BILINGUAL SCHOOL</p>
      <h2>Una base clara para construir el ERP<span className="punto">.</span></h2>
      <p>Los cinco módulos están preparados para desarrollarse a partir de requisitos aprobados.</p>
    </div>
    <div className="cuadricula" aria-label="Módulos disponibles">
      {modulos.map((modulo, indice) => <Link className="tarjeta-modulo" key={modulo.ruta}
        to={'/' + modulo.ruta}>
        <span className="numero">0{indice + 1} / MÓDULO</span>
        <strong>{modulo.nombre}</strong>
        <span>{modulo.resumen}</span>
        <small>{modulo.ruta === 'personas' ? 'ABRIR MÓDULO' : 'PENDIENTE DE IMPLEMENTACIÓN'} <span aria-hidden="true">→</span></small>
      </Link>)}
    </div>
  </div>
}

export default function App() {
  const [sesion, setSesion] = useState<Sesion | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    void consultarSesion().then(setSesion).catch(e => setError(String(e)))
  }, [])
  if (error) return <div className="acceso" role="alert"><div className="acceso-caja">
    <p className="etiqueta">ERP INSTITUCIONAL</p><h1>No se pudo abrir el ERP</h1>
    <p>{error}</p><button type="button" onClick={() => location.reload()}>Reintentar</button>
  </div></div>
  if (!sesion) return <div className="acceso"><p>Cargando Boston ERP…</p></div>
  if (!sesion.autenticado) return <div className="acceso"><div className="acceso-caja">
    <div className="bbs" aria-hidden="true"><b>B</b><b>B</b><b>S</b></div>
    <p className="etiqueta">BOSTON BILINGUAL SCHOOL</p>
    <h1>ERP institucional<span className="punto">.</span></h1>
    <p>Ingresa con tu cuenta institucional para consultar los módulos del ERP.</p>
    <a className="boton-principal" href="/oauth2/authorization/boston-erp">Iniciar sesión</a>
  </div></div>
  return <BrowserRouter><Routes>
    <Route element={<ErpLayout sesion={sesion} />}>
      <Route index element={<Inicio />} />
      <Route path="personas/*" element={<PersonasModulo sesion={sesion} />} />
      {modulos.filter(modulo => modulo.ruta !== 'personas').map(modulo => <Route key={modulo.ruta} path={modulo.ruta}
        element={<EstadoPendiente modulo={modulo} />} />)}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes></BrowserRouter>
}
