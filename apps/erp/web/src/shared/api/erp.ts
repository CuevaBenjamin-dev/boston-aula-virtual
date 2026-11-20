import type { Sesion } from './sesion'

export function tokenCsrf(): string {
  const cookie = document.cookie.split(';').map(parte => parte.trim())
    .find(parte => parte.startsWith('XSRF-TOKEN='))
  const token = cookie?.slice('XSRF-TOKEN='.length)
  if (!token) throw new Error('No se encontró el token de seguridad. Actualiza la página e inténtalo de nuevo.')
  return decodeURIComponent(token)
}

export async function erp<T>(ruta: string, _sesion: Sesion, metodo = 'GET', cuerpo?: unknown): Promise<T> {
  const respuesta = await fetch('/api/erp/v1' + ruta, {
    method: metodo,
    credentials: 'same-origin',
    headers: {
      ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(metodo === 'GET' ? {} : { 'X-XSRF-TOKEN': tokenCsrf() }),
    },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })
  if (!respuesta.ok) {
    const error = await respuesta.json().catch(() => null) as { mensaje?: string } | null
    throw new Error(error?.mensaje ?? `No se completó la operación (${respuesta.status}).`)
  }
  if (respuesta.status === 204) return undefined as T
  return respuesta.json() as Promise<T>
}
