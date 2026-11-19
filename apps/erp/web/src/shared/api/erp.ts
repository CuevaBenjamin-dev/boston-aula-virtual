import type { Sesion } from './sesion'

export async function erp<T>(ruta: string, sesion: Sesion, metodo = 'GET', cuerpo?: unknown): Promise<T> {
  const respuesta = await fetch('/api/erp/v1' + ruta, {
    method: metodo,
    credentials: 'same-origin',
    headers: {
      ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(metodo === 'GET' ? {} : { 'X-XSRF-TOKEN': sesion.csrf }),
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
