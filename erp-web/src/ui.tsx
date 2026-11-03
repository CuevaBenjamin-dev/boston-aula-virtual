import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { api, type Catalogos, type Fila, valor } from './api'
import { useLocation } from 'react-router-dom'
import { PageHeading } from './PageHeading'

export function Pagina({ titulo, intro, accion, children }: { titulo: string; intro: string; accion?: ReactNode; children: ReactNode }) {
  const ruta = useLocation().pathname
  const inicio = ruta === '/'
  const detalle = /^\/(estudiantes|matriculas)\/[^/]+$/.test(ruta) && ruta !== '/matriculas/nueva'
  return <div className="pagina"><PageHeading title={titulo} />{inicio
    ? <div className="pagehead"><div><p className="eyebrow">BOSTON ERP · GESTIÓN ACADÉMICA</p><h1>{titulo}<span className="punto">.</span></h1><p>{intro}</p></div>{accion}</div>
    : (accion || detalle) && <div className="page-context">{detalle && <span>{intro}</span>}{accion && <div className="page-context-action">{accion}</div>}</div>}{children}</div>
}
export function Panel({ titulo, subtitulo, accion, children }: { titulo: string; subtitulo?: string; accion?: ReactNode; children: ReactNode }) {
  return <section className="panel"><div className="panelhead"><div><h2>{titulo}</h2>{subtitulo && <p>{subtitulo}</p>}</div>{accion}</div><div className="panelbody">{children}</div></section>
}
export function Vacio({ texto = 'No hay registros para estos filtros.' }: { texto?: string }) { return <div className="vacio">{texto}</div> }
export function Aviso({ error, exito }: { error?: string; exito?: string }) {
  return <>{error && <div role="alert" className="aviso error">{error}</div>}{exito && <div role="status" className="aviso exito">{exito}</div>}</>
}
export function Campo({ etiqueta, valorActual, cambio, tipo = 'text', requerido = false, placeholder = '' }: {
  etiqueta: string; valorActual: string; cambio: (v: string) => void; tipo?: string; requerido?: boolean; placeholder?: string
}) {
  return <label className="campo"><span>{etiqueta}{requerido && ' *'}</span><input type={tipo} value={valorActual} onChange={e => cambio(e.target.value)} required={requerido} placeholder={placeholder} /></label>
}
export function Selector({ etiqueta, valorActual, cambio, opciones, vacio = 'Seleccionar', requerido = false }: {
  etiqueta: string; valorActual: string; cambio: (v: string) => void; opciones: { valor: string; texto: string }[]; vacio?: string; requerido?: boolean
}) {
  return <label className="campo"><span>{etiqueta}{requerido && ' *'}</span><select value={valorActual} onChange={e => cambio(e.target.value)} required={requerido}><option value="">{vacio}</option>{opciones.map(o => <option key={o.valor} value={o.valor}>{o.texto}</option>)}</select></label>
}
export function opciones(filas: Fila[], titulo: (f: Fila) => string) { return filas.map(f => ({ valor: valor(f.id), texto: titulo(f) })) }
export function useDatos<T>(ruta: string, inicial: T) {
  const [datos, setDatos] = useState<T>(inicial)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)
  const cargar = useCallback(async () => { if (!ruta) { setCargando(false); return } setCargando(true); try { setDatos(await api<T>(ruta)); setError('') } catch (e) { setError((e as Error).message) } finally { setCargando(false) } }, [ruta])
  useEffect(() => { void cargar() }, [cargar])
  return { datos, error, cargando, cargar }
}
export function useCatalogos() { return useDatos<Catalogos>('/catalogos', { periodos: [], niveles: [], grados: [], secciones: [], asignaturas: [], relaciones: [], ofertas: [] }) }
export function Etiqueta({ estado }: { estado: unknown }) {
  const v = valor(estado)
  return <span className={'etiqueta ' + (v === 'MATRICULADO' || v === 'ACTIVO' || v === 'ACTIVA' ? 'verde' : v === 'PROCESO_PENDIENTE' ? 'ambar' : '')}>{v.replaceAll('_', ' ')}</span>
}
