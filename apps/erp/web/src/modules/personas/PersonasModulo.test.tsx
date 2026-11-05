// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PersonasModulo } from './PersonasModulo'
import type { Sesion } from '../../shared/api/sesion'

const sesion: Sesion = { autenticado: true, usuario: 'prueba', csrf: 'csrf-test' }
const capacidades = { registrar: true, consultar: true, editarContacto: true,
  corregirIdentidad: true, gestionarVinculos: true, gestionarCondiciones: true,
  accesoDocentePendiente: false, personaPropiaId: null }

function respuesta(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify(data), { status: 200,
    headers: { 'Content-Type': 'application/json' } }))
}

function montar(ruta: string) {
  return render(<MemoryRouter initialEntries={[ruta]}><Routes>
    <Route path="/personas/*" element={<PersonasModulo sesion={sesion} />} />
  </Routes></MemoryRouter>)
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('Personas ERP Web', () => {
  it('restringe el directorio cuando la sesión carece de ámbito comprobable', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta({ ...capacidades, consultar: false,
      gestionarVinculos: false, gestionarCondiciones: false })))
    montar('/personas')
    expect(await screen.findByText(/no dispone de un ámbito verificable/i)).toBeTruthy()
    expect(screen.getByRole('link', { name: /registrar persona/i })).toBeTruthy()
    expect(screen.queryByText('Resultados')).toBeNull()
  })

  it('dirige al docente a su ficha sin mostrarle el directorio', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta({ ...capacidades, registrar: false,
      consultar: false, corregirIdentidad: false, gestionarVinculos: false,
      gestionarCondiciones: false, personaPropiaId: '11111111-1111-1111-1111-111111111111' })))
    montar('/personas')
    const enlace = await screen.findByRole('link', { name: /abrir mi ficha/i })
    expect(enlace.getAttribute('href')).toBe('/personas/11111111-1111-1111-1111-111111111111')
    expect(screen.queryByText('Resultados')).toBeNull()
  })

  it('busca y muestra resultados paginados sin perder las etiquetas', async () => {
    const llamadas = vi.fn((entrada: RequestInfo | URL) => {
      const ruta = String(entrada)
      if (ruta.endsWith('/capacidades')) return respuesta(capacidades)
      return respuesta({ resultados: [{ id: '11111111-1111-1111-1111-111111111111',
        nombres: 'Lucía', apellidos: 'Paredes', documento: 'DNI 12345678',
        condiciones: ['ESTUDIANTE'] }], total: 1, pagina: 0, tamano: 15 })
    })
    vi.stubGlobal('fetch', llamadas)
    montar('/personas')
    const campo = await screen.findByRole('textbox', { name: 'Nombre o documento' })
    await userEvent.type(campo, 'Lucía')
    await waitFor(() => expect(llamadas.mock.calls.some(c => String(c[0]).includes('buscar=Luc'))).toBe(true))
    expect(await screen.findByText('Paredes, Lucía')).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Paginación' })).toBeTruthy()
    expect(screen.getByRole('link', { name: /ver ficha/i })).toBeTruthy()
  })

  it('muestra identificación provisional solo para estudiante y filtra apoderado sin facultad', async () => {
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) => {
      if (String(entrada).endsWith('/capacidades')) return respuesta({ ...capacidades,
        consultar: false, gestionarVinculos: false })
      return respuesta([{ codigo: 'DNI', nombre: 'DNI', patron: '^[0-9]{8}$', activo: true }])
    }))
    montar('/personas/nueva')
    const condicion = await screen.findByRole('combobox', { name: 'Condición institucional' })
    expect(screen.queryByRole('option', { name: 'Apoderado' })).toBeNull()
    await userEvent.click(screen.getByRole('checkbox', { name: /sin documento oficial/i }))
    expect(screen.getByRole('textbox', { name: 'Referencia de sustento' })).toBeTruthy()
    await userEvent.selectOptions(condicion, 'DOCENTE')
    expect(screen.queryByRole('checkbox', { name: /sin documento oficial/i })).toBeNull()
    expect(screen.getByRole('textbox', { name: 'Correo' })).toHaveProperty('required', true)
  })

  it('no presenta infracciones automáticas de etiquetas y atributos ARIA en el registro', async () => {
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) =>
      String(entrada).endsWith('/capacidades') ? respuesta(capacidades) :
        respuesta([{ codigo: 'DNI', nombre: 'DNI', patron: '^[0-9]{8}$', activo: true }])))
    const { container } = montar('/personas/nueva')
    await screen.findByRole('combobox', { name: 'Condición institucional' })
    const resultado = await axe.run(container, { runOnly: { type: 'rule', values: [
      'label', 'aria-required-attr', 'aria-valid-attr', 'aria-valid-attr-value', 'button-name', 'link-name'
    ] } })
    expect(resultado.violations.map(v => v.id)).toEqual([])
  })
})
