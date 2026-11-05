import type { Sesion } from '../../shared/api/sesion'

export type Condicion = 'ESTUDIANTE' | 'DOCENTE' | 'PERSONAL_INSTITUCIONAL' | 'APODERADO'
export type Capacidades = { registrar: boolean; consultar: boolean; editarContacto: boolean; corregirIdentidad: boolean; gestionarVinculos: boolean; gestionarCondiciones: boolean; accesoDocentePendiente: boolean; personaPropiaId: string | null }
export type Resumen = { id: string; nombres: string; apellidos: string; documento: string | null; condiciones: Condicion[] }
export type Pagina = { resultados: Resumen[]; total: number; pagina: number; tamano: number }
export type Documento = { id: string; tipo: string; pais: string; numero: string; principal: boolean; vigenteDesde: string; vigenteHasta: string | null }
export type CondicionRegistro = { id: string; tipo: Condicion; vigenteDesde: string; vigenteHasta: string | null; motivoDesactivacion: string | null }
export type Parentesco = { id: string; estudianteId: string; familiarId: string; tipo: string; vigenteDesde: string; vigenteHasta: string | null }
export type Apoderamiento = { id: string; estudianteId: string; apoderadoId: string; sinParentesco: boolean; autorizador: string | null; medio: string | null; verificadoEn: string | null; vigenteDesde: string; vigenteHasta: string | null }
export type Persona = { id: string; nombres: string; apellidos: string; fechaNacimiento: string; sexo: string; nacionalidad: string; telefono: string | null; correo: string | null; direccion: string; documentos: Documento[]; condiciones: CondicionRegistro[]; provisional: { motivo: string; sustentoTipo: string; sustentoReferencia: string; registradaEn: string; regularizadaEn: string | null } | null; parentescos: Parentesco[]; apoderamientos: Apoderamiento[] }
export type TipoDocumento = { codigo: string; nombre: string; patron: string | null; activo: boolean }

export class ErrorApi extends Error {
  constructor(message: string, public status: number) { super(message) }
}

export async function api<T>(ruta: string, sesion: Sesion, init?: RequestInit): Promise<T> {
  const respuesta = await fetch('/api/erp/v1/personas' + ruta, {
    ...init,
    credentials: 'same-origin',
    headers: { ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.method && init.method !== 'GET' ? { 'X-XSRF-TOKEN': sesion.csrf } : {}),
      ...init?.headers },
  })
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => null) as { mensaje?: string } | null
    throw new ErrorApi(cuerpo?.mensaje ?? `No se pudo completar la operación (${respuesta.status}).`, respuesta.status)
  }
  if (respuesta.status === 204) return undefined as T
  if (respuesta.status === 201) return respuesta.headers.get('Location') as T
  return respuesta.json() as Promise<T>
}

export function guardar<T>(ruta: string, sesion: Sesion, method: 'POST' | 'PUT', datos: unknown): Promise<T> {
  return api<T>(ruta, sesion, { method, body: JSON.stringify(datos) })
}
