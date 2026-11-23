// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
beforeEach(() => { document.cookie = 'XSRF-TOKEN=csrf-test' })
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

  it('consulta páginas y filtros en la API sin descargar todo el listado', async () => {
    const peticiones = vi.fn((entrada: RequestInfo | URL) => {
      const ruta = String(entrada)
      if (ruta.endsWith('/oferta/anios')) return respuesta([2027])
      if (ruta.includes('/oferta/secciones')) return respuesta([{ id: seccion, anio: 2027, nivel: 'PRIMARIA', grado: 3, nombre: 'B' }])
      if (ruta.includes('/matriculas?')) {
        const q = new URL(ruta, 'http://localhost').searchParams
        const resultados = q.has('buscar') ? [{ id, estudiante: 'Alumno buscado', documento: '00000014', anio: 2027,
          seccion: 'PRIMARIA · 3.º · B', estado: 'VIGENTE', creadoEn: '2026-11-23T00:00:00Z' }]
          : Array.from({ length: q.get('pagina') === '1' ? 7 : 15 }, (_, i) => ({
            id: `expediente-${q.get('pagina')}-${i}`, estudiante: `Alumno ${q.get('pagina')}-${i}`,
            documento: null, anio: 2027, seccion: 'PRIMARIA · 3.º · B', estado: 'VIGENTE', creadoEn: '2026-11-23T00:00:00Z' }))
        return respuesta({ resultados, total: q.has('buscar') ? 1 : 22, pagina: Number(q.get('pagina')), tamano: 15 })
      }
      throw new Error(`Ruta inesperada: ${ruta}`)
    })
    vi.stubGlobal('fetch', peticiones)
    render(<MemoryRouter initialEntries={['/matriculas']}><Routes>
      <Route path="/matriculas/*" element={<MatriculasModulo sesion={sesion} />} />
    </Routes></MemoryRouter>)
    await screen.findByText('Alumno 0-0')
    expect(screen.getByText('22 resultados')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await screen.findByText('Alumno 1-0')
    expect(peticiones.mock.calls.some(([ruta]) => String(ruta).includes('pagina=1&tamano=15'))).toBe(true)
    await userEvent.type(screen.getByRole('textbox', { name: 'Nombre o documento' }), '00000014')
    await screen.findByText('Alumno buscado')
    expect(peticiones.mock.calls.some(([ruta]) => String(ruta).includes('buscar=00000014'))).toBe(true)
  })

  it('solicita confirmación explícita antes de descartar un borrador', async () => {
    const peticiones = vi.fn((entrada: RequestInfo | URL, opciones?: RequestInit) => {
      const ruta = String(entrada)
      if (ruta.endsWith('/oferta/anios')) return respuesta([2026])
      if (ruta.includes('/oferta/secciones')) return respuesta([])
      if (ruta.endsWith(`/matriculas/${id}`) && opciones?.method === 'DELETE')
        return Promise.resolve(new Response(null, { status: 204 }))
      if (ruta.endsWith(`/matriculas/${id}`)) return respuesta({ id, personaId: null, anio: null,
        seccionId: null, estado: 'BORRADOR', borrador: { nombres: 'Erróneo' }, creadoEn: '2026-11-23T00:00:00Z' })
      if (ruta.includes('/matriculas?')) return respuesta({ resultados: [], total: 0, pagina: 0, tamano: 15 })
      throw new Error(`Ruta inesperada: ${ruta}`)
    })
    vi.stubGlobal('fetch', peticiones)
    render(<MemoryRouter initialEntries={[`/matriculas/${id}`]}><Routes>
      <Route path="/matriculas/*" element={<MatriculasModulo sesion={sesion} />} />
    </Routes></MemoryRouter>)
    await screen.findByRole('button', { name: 'Descartar borrador' })
    await userEvent.click(screen.getByRole('button', { name: 'Descartar borrador' }))
    expect(screen.getByRole('button', { name: 'Descartar definitivamente' }).hasAttribute('disabled')).toBe(true)
    await userEvent.type(screen.getByRole('textbox', { name: 'Escribe DESCARTAR para confirmar' }), 'DESCARTAR')
    await userEvent.click(screen.getByRole('button', { name: 'Descartar definitivamente' }))
    await screen.findByRole('heading', { name: 'Expedientes' })
    expect(peticiones.mock.calls.some(([ruta, opciones]) => String(ruta).endsWith(`/matriculas/${id}`) && opciones?.method === 'DELETE')).toBe(true)
  })
})
