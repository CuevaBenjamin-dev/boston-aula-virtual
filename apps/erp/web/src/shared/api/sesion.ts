export interface Sesion {
  autenticado: boolean
  usuario: string | null
  csrf: string
}

export async function consultarSesion(): Promise<Sesion> {
  const respuesta = await fetch('/api/erp/v1/sesion', { credentials: 'same-origin' })
  if (!respuesta.ok) throw new Error('El servicio ERP no está disponible.')
  return respuesta.json() as Promise<Sesion>
}

export function cerrarSesion(csrf: string): void {
  const formulario = document.createElement('form')
  formulario.method = 'POST'
  formulario.action = '/logout'
  const token = document.createElement('input')
  token.type = 'hidden'
  token.name = '_csrf'
  token.value = csrf
  formulario.appendChild(token)
  document.body.appendChild(formulario)
  formulario.submit()
}
