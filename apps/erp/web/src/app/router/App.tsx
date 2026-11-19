import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { ErpLayout } from '../layouts/ErpLayout'
import { consultarSesion, cerrarSesion, type Sesion } from '../../shared/api/sesion'
import { modulos } from '../../shared/lib/modulos'
import { EstadoPendiente } from '../../shared/ui/EstadoPendiente'
import { PersonasModulo } from '../../modules/personas/PersonasModulo'
import { OfertaModulo } from '../../modules/oferta/OfertaModulo'
import { MatriculasModulo } from '../../modules/matriculas/MatriculasModulo'
import { AsignacionesModulo } from '../../modules/asignaciones/AsignacionesModulo'
import { erp } from '../../shared/api/erp'

type Resumen = { anio: number | null; personas: number; borradores: number; vigentes: number;
  retiradas: number; finalizadas: number; secciones: number; docentesActivos: number; integracionesPendientes: number }

function Inicio({ sesion }: { sesion: Sesion }) {
  const [resumen, setResumen] = useState<Resumen | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void erp<Resumen>('/resumen', sesion).then(setResumen).catch(e => setError(String(e))) }, [sesion])
  return <div className="pagina">
    <div className="erp-pagina-titulo"><div><p className="etiqueta">BOSTON BILINGUAL SCHOOL</p><h2>Resumen institucional</h2>
      <p>{resumen?.anio ? `Último año configurado: ${resumen.anio}` : 'Crea un año escolar en Oferta académica para comenzar.'}</p></div></div>
    {error && <p className="per-aviso" role="alert">{error}</p>}
    {!resumen && !error && <p role="status">Cargando cifras del ERP…</p>}
    {resumen && <div className="erp-indicadores" aria-label="Cifras persistidas">
      {([['Personas',resumen.personas],['Matrículas vigentes',resumen.vigentes],['Borradores',resumen.borradores],
        ['Retiradas',resumen.retiradas],['Finalizadas',resumen.finalizadas],['Secciones',resumen.secciones],
        ['Docentes activos',resumen.docentesActivos],['Integraciones pendientes',resumen.integracionesPendientes]] as const)
        .map(([etiqueta,cifra])=><div className="erp-indicador" key={etiqueta}><span>{etiqueta}</span><strong>{cifra}</strong></div>)}</div>}
    <div className="cuadricula" aria-label="Módulos disponibles">
      {modulos.map((modulo, indice) => <Link className="tarjeta-modulo" key={modulo.ruta}
        to={'/' + modulo.ruta}>
        <span className="numero">0{indice + 1} / MÓDULO</span>
        <strong>{modulo.nombre}</strong>
        <span>{modulo.resumen}</span>
        <small>{['personas','oferta','matriculas','asignaciones'].includes(modulo.ruta) ? 'ABRIR MÓDULO' : 'EN DESARROLLO'} <span aria-hidden="true">→</span></small>
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
    <p>{sesion.rol ? 'Esta cuenta no tiene rol de Dirección ni Coordinación Académica para entrar al ERP.' : 'Ingresa con tu cuenta institucional de Dirección o Coordinación Académica.'}</p>
    {sesion.rol ? <button type="button" onClick={() => cerrarSesion(sesion.csrf)}>Salir de esta cuenta</button>
      : <a className="boton-principal" href="/oauth2/authorization/boston-erp">Iniciar sesión</a>}
  </div></div>
  return <BrowserRouter><Routes>
    <Route element={<ErpLayout sesion={sesion} />}>
      <Route index element={<Inicio sesion={sesion} />} />
      <Route path="personas/*" element={<PersonasModulo sesion={sesion} />} />
      <Route path="oferta/*" element={<OfertaModulo sesion={sesion} />} />
      <Route path="matriculas/*" element={<MatriculasModulo sesion={sesion} />} />
      <Route path="asignaciones/*" element={<AsignacionesModulo sesion={sesion} />} />
      {modulos.filter(modulo => !['personas','oferta','matriculas','asignaciones'].includes(modulo.ruta)).map(modulo => <Route key={modulo.ruta} path={modulo.ruta}
        element={<EstadoPendiente modulo={modulo} />} />)}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes></BrowserRouter>
}
