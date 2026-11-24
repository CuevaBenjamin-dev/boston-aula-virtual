// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OfertaModulo } from './OfertaModulo'
import { erp } from '../../shared/api/erp'
import type { Sesion } from '../../shared/api/sesion'

vi.mock('../../shared/api/erp', () => ({ erp: vi.fn() }))
const sesion: Sesion = { autenticado: true, usuario: 'prueba', rol: 'COORDINACION', csrf: 'csrf-test' }
const api = vi.mocked(erp)
const seccion = { id: 's1', anio: 2027, nivel: 'PRIMARIA', grado: 1, nombre: 'A' }
const materia = { id: 'a1', anio: 2027, nivel: 'SECUNDARIA', grado: 4, nombre: 'Matemática', codigo: 'SE-04-MAT-27' }

beforeEach(() => {
  // jsdom no necesariamente implementa los métodos de diálogo nativo.
  if (!HTMLDialogElement.prototype.showModal) HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  if (!HTMLDialogElement.prototype.close) HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  api.mockImplementation(async <T,>(ruta: string, _sesion: Sesion, metodo = 'GET', cuerpo?: unknown): Promise<T> => {
    if (metodo === 'POST') {
      if (ruta === '/oferta/anios') return cuerpo as T
      const p = cuerpo as Record<string, unknown>
      return { ...p, id: 'nuevo', ...(ruta.endsWith('/asignaturas') ? { codigo: `SE-04-${p.codigo}-27` } : {}) } as T
    }
    if (ruta === '/oferta/anios') return [2027, 2026] as T
    if (ruta === '/oferta/secciones?anio=2027') return [seccion] as T
    if (ruta === '/oferta/asignaturas?anio=2027') return [materia] as T
    return [] as T
  })
})
afterEach(() => { cleanup(); vi.resetAllMocks() })

async function montar() {
  render(<OfertaModulo sesion={sesion} />)
  const selector = await screen.findByRole('combobox', { name: 'Año escolar' })
  await waitFor(() => expect(screen.getByRole('option', { name: '2027' })).toBeTruthy())
  await userEvent.selectOptions(selector, '2027')
  await screen.findByText('Sección A')
  return selector
}

describe('Oferta académica ERP Web', () => {
  it('consulta los 11 grados y conserva el grado al cambiar de año', async () => {
    const selector = await montar()
    const nav = screen.getByRole('navigation', { name: 'Navegación por grados' })
    expect(within(nav).getAllByRole('button')).toHaveLength(11)
    await userEvent.click(within(nav).getByRole('button', { name: /4.º de Secundaria/ }))
    expect(screen.getByText('SE-04-MAT-27')).toBeTruthy()
    expect(within(nav).getByRole('button', { name: /4.º de Secundaria/ }).getAttribute('aria-pressed')).toBe('true')
    await userEvent.selectOptions(selector, '2026')
    expect(await screen.findByText('Aún no hay asignaturas')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '4.º de Secundaria' })).toBeTruthy()
    expect(screen.queryByText('SE-04-MAT-27')).toBeNull()
  })

  it('rechaza duplicados y registra la asignatura en su contexto con sigla anual', async () => {
    await montar()
    await userEvent.click(screen.getByRole('button', { name: /4.º de Secundaria/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Agregar asignatura' }))
    const modal = screen.getByRole('dialog', { name: 'Agregar asignatura' })
    const nombre = within(modal).getByRole('textbox', { name: 'Nombre de asignatura' })
    const sigla = within(modal).getByRole('textbox', { name: 'Sigla' })
    await userEvent.type(nombre, 'Matematica')
    await userEvent.type(sigla, 'comp')
    expect(within(modal).getByText('SE-04-COMP-27')).toBeTruthy()
    await userEvent.click(within(modal).getByRole('button', { name: 'Agregar asignatura' }))
    expect(within(modal).getByRole('alert').textContent).toMatch(/ya existe/i)
    expect(api.mock.calls.filter(c => c[2] === 'POST')).toHaveLength(0)
    await userEvent.clear(nombre)
    await userEvent.type(nombre, 'Computación')
    await userEvent.click(within(modal).getByRole('button', { name: 'Agregar asignatura' }))
    await waitFor(() => expect(api).toHaveBeenCalledWith('/oferta/asignaturas', sesion, 'POST', { anio: 2027, nivel: 'SECUNDARIA', grado: 4, codigo: 'COMP', nombre: 'Computación' }))
    const tabla = await screen.findByRole('table', { name: 'Asignaturas de 4.º de Secundaria, 2027' })
    expect(within(tabla).getByRole('row', { name: /Computación SE-04-COMP-27/ }).className).toBe('new')
    expect(within(screen.getByRole('group', { name: 'Totales de todo el año' })).getByText('2')).toBeTruthy()
  })

  it('crea un año independiente y permite registrar una sección sin duplicarla', async () => {
    const selector = await montar()
    await userEvent.click(screen.getByRole('button', { name: /Nuevo año escolar/ }))
    const modal = screen.getByRole('dialog', { name: 'Nuevo año escolar' })
    const campo = within(modal).getByRole('spinbutton', { name: 'Año escolar' })
    await userEvent.clear(campo)
    await userEvent.type(campo, '2027')
    await userEvent.click(within(modal).getByRole('button', { name: 'Crear año' }))
    expect(within(modal).getByRole('alert').textContent).toMatch(/ya existe/i)
    expect(api.mock.calls.filter(c => c[2] === 'POST')).toHaveLength(0)
    await userEvent.clear(campo)
    await userEvent.type(campo, '2028')
    await userEvent.click(within(modal).getByRole('button', { name: 'Crear año' }))
    expect(await screen.findByText('Aún no hay secciones')).toBeTruthy()
    expect(selector).toHaveProperty('value', '2028')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar sección' }))
    const seccionModal = screen.getByRole('dialog', { name: 'Agregar sección' })
    await userEvent.type(within(seccionModal).getByRole('textbox', { name: 'Nombre de sección' }), 'a')
    await userEvent.click(within(seccionModal).getByRole('button', { name: 'Agregar sección' }))
    expect(await screen.findByText('Sección A')).toBeTruthy()
    expect(api).toHaveBeenCalledWith('/oferta/secciones', sesion, 'POST', { anio: 2028, nivel: 'PRIMARIA', grado: 1, nombre: 'A' })
    await userEvent.click(screen.getByRole('button', { name: 'Agregar sección' }))
    const duplicado = screen.getByRole('dialog', { name: 'Agregar sección' })
    await userEvent.type(within(duplicado).getByRole('textbox', { name: 'Nombre de sección' }), 'A')
    await userEvent.click(within(duplicado).getByRole('button', { name: 'Agregar sección' }))
    expect(within(duplicado).getByRole('alert').textContent).toMatch(/ya existe/i)
    expect(api.mock.calls.filter(c => c[2] === 'POST')).toHaveLength(2)
  })

  it('mantiene abierto el formulario ante un rechazo del servidor y permite cancelar', async () => {
    await montar()
    api.mockImplementationOnce(async () => { throw new Error('No se pudo registrar la sección') })
    await userEvent.click(screen.getByRole('button', { name: 'Agregar sección' }))
    const modal = screen.getByRole('dialog', { name: 'Agregar sección' })
    const campo = within(modal).getByRole('textbox', { name: 'Nombre de sección' })
    await userEvent.type(campo, 'B')
    await userEvent.click(within(modal).getByRole('button', { name: 'Agregar sección' }))
    expect((await within(modal).findByRole('alert')).textContent).toMatch(/no se pudo/i)
    expect(campo).toHaveProperty('value', 'B')
    await userEvent.click(within(modal).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByText('Sección B')).toBeNull()
  })
})
