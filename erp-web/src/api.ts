export type Fila = Record<string, unknown>
export type Sesion = { autenticado: boolean; autorizado?: boolean; tipo?: string; usuario?: string; csrf: string }
export type Catalogos = { periodos: Fila[]; niveles: Fila[]; grados: Fila[]; secciones: Fila[]; asignaturas: Fila[]; relaciones: Fila[]; ofertas: Fila[] }

export function csrfCookie(): string {
  const valor = document.cookie.split('; ').find(parte => parte.startsWith('XSRF-TOKEN='))
  return valor ? decodeURIComponent(valor.slice('XSRF-TOKEN='.length)) : ''
}
export async function sesion(): Promise<Sesion> {
  const r = await fetch('/api/erp-web/sesion', { credentials: 'same-origin' })
  if (!r.ok) throw new Error('El servicio ERP no está disponible. Intenta nuevamente en unos segundos.')
  const d = await r.json() as Sesion
  return d
}
export async function api<T = unknown>(ruta: string, metodo = 'GET', datos?: unknown): Promise<T> {
  const esFormulario = datos instanceof FormData
  const r = await fetch('/api/erp-web/v1' + ruta, {
    method: metodo,
    credentials: 'same-origin',
    headers: {
      ...(metodo !== 'GET' ? { 'X-XSRF-TOKEN': csrfCookie() } : {}),
      ...(!esFormulario && datos !== undefined ? { 'Content-Type': 'application/json' } : {})
    },
    body: datos === undefined ? undefined : esFormulario ? datos : JSON.stringify(datos)
  })
  if (r.status === 401) {
    window.location.assign('/')
    throw new Error('Tu sesión terminó. Inicia sesión nuevamente.')
  }
  if (!r.ok) {
    let mensaje = 'Error ' + r.status
    try { const x = await r.json(); mensaje = x.error || x.detail || mensaje } catch { /* respuesta sin JSON */ }
    throw new Error(mensaje)
  }
  if (r.status === 204 || r.headers.get('Content-Length') === '0') return undefined as T
  const tipo = r.headers.get('Content-Type') || ''
  if (tipo.includes('json')) return r.json() as Promise<T>
  return r.blob() as Promise<T>
}
export const valor = (v: unknown) => v === null || v === undefined ? '' : String(v)
export const nombre = (f: Fila) => [f.nombres, f.apellidos].filter(Boolean).join(' ')
export const fecha = (v: unknown) => {
  if (!v) return '—'
  const d = new Date(String(v))
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleDateString('es-PE')
}
