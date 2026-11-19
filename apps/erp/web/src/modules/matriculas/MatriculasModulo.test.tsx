// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MatriculasModulo } from './MatriculasModulo'
import type { Sesion } from '../../shared/api/sesion'

const sesion: Sesion = { autenticado: true, usuario: 'coordinacion', rol: 'COORDINACION', csrf: 'csrf-test' }
const seccion = '11111111-1111-1111-1111-111111111111'
const id = '22222222-2222-2222-2222-222222222222'
function respuesta(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify(data), { status: 200,
    headers: { 'Content-Type': 'application/json' } }))
}
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('Asistente de matrículas', () => {
  it('guarda el borrador al avanzar y conserva los datos al volver', async () => {
    let borrador: Record<string, unknown> = {}
    const peticiones = vi.fn((entrada: RequestInfo | URL, opciones?: RequestInit) => {
      const ruta=String(entrada)
      if (ruta.endsWith('/oferta/anios')) return respuesta([2026])
      if (ruta.includes('/oferta/secciones')) return respuesta([{ id: seccion, anio: 2026, nivel: 'PRIMARIA', grado: 1, nombre: 'A' }])
      if (ruta.endsWith('/matriculas') && opciones?.method==='POST') {
        borrador=(JSON.parse(String(opciones.body)) as { datos: Record<string, unknown> }).datos
        return respuesta({ id, personaId: null, anio: null, seccionId: null, estado: 'BORRADOR', borrador,
          creadoEn: '2026-11-19T00:00:00Z' })
      }
      if (ruta.endsWith(`/matriculas/${id}`)) return respuesta({ id, personaId: null, anio: null,
        seccionId: null, estado: 'BORRADOR', borrador, creadoEn: '2026-11-19T00:00:00Z' })
      throw new Error(`Ruta inesperada: ${ruta}`)
    })
    vi.stubGlobal('fetch',peticiones)
    render(<MemoryRouter initialEntries={['/matriculas/nueva']}><Routes>
      <Route path="/matriculas/*" element={<MatriculasModulo sesion={sesion} />} />
    </Routes></MemoryRouter>)
    await screen.findByRole('heading', { name: '01 · Datos del alumno' })
    await userEvent.type(screen.getByRole('textbox', { name: 'Nombres' }), 'Lucía')
    await userEvent.type(screen.getByRole('textbox', { name: 'Apellidos' }), 'Paredes')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sección' }), seccion)
    await userEvent.click(screen.getByRole('button', { name: /siguiente: apoderado/i }))
    await screen.findByRole('heading', { name: '02 · Apoderado' })
    expect(borrador).toMatchObject({ nombres: 'Lucía', apellidos: 'Paredes', anio: 2026, seccionId: seccion })
    await userEvent.click(screen.getByRole('button', { name: /anterior/i }))
    expect((screen.getByRole('textbox', { name: 'Nombres' }) as HTMLInputElement).value).toBe('Lucía')
  })
})
