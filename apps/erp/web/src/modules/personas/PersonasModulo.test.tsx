// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PersonasModulo } from './PersonasModulo'
import type { Sesion } from '../../shared/api/sesion'

const sesion: Sesion = { autenticado: true, usuario: 'prueba', rol: 'COORDINACION', csrf: 'csrf-test' }
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
    expect(screen.queryByRole('listbox')).toBeNull()
    const tabla = screen.getByRole('table', { name: 'Resultados' })
    expect(within(tabla).getAllByRole('columnheader').map(c => c.textContent)).toEqual([
      'Apellidos y nombres', 'Documento', 'Condiciones vigentes',
    ])
    const fila = within(tabla).getByRole('row', { name: /Paredes, Lucía/ })
    expect(within(fila).getByRole('cell', { name: 'DNI 12345678' })).toBeTruthy()
    expect(within(fila).getByRole('cell', { name: 'ESTUDIANTE' })).toBeTruthy()
    expect(within(fila).getByRole('link', { name: 'Paredes, Lucía' }).getAttribute('href')).toBe('/personas/11111111-1111-1111-1111-111111111111')
    expect(screen.getByRole('navigation', { name: 'Paginación' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: /ver ficha/i })).toBeNull()
    expect(within(fila).getAllByRole('link')).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(campo).toHaveProperty('value', '')
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
    expect(screen.getByRole('textbox', { name: 'Correo (opcional)' })).toHaveProperty('required', false)
  })

  it('agrupa la ficha en dos pestañas y edita dentro de la sección activa', async () => {
    const persona = { id: '11111111-1111-1111-1111-111111111111', nombres: 'Ana', apellidos: 'Lagos',
      fechaNacimiento: null, telefono: '900000011', correo: null, direccion: null,
      documentos: [{ id: 'doc-1', tipo: 'DNI', pais: 'PE', numero: '00000011',
        principal: true, vigenteDesde: '2026-01-01', vigenteHasta: null }],
      condiciones: [], parentescos: [], apoderamientos: [], referencias: {}, provisional: null }
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) =>
      respuesta(String(entrada).endsWith('/capacidades') ? capacidades : persona)))
    montar('/personas/' + persona.id)
    const pestanas = await screen.findByRole('navigation', { name: 'Secciones de la ficha' })
    expect(within(pestanas).getAllByRole('button')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Identificación' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Parentescos' })).toBeNull()

    await userEvent.click(within(pestanas).getByRole('button', { name: /condiciones institucionales/i }))
    const condiciones = screen.getByRole('region', { name: 'Condiciones institucionales' })
    expect(within(condiciones).queryByRole('heading', { name: 'Condiciones institucionales' })).toBeNull()
    expect(within(condiciones).getByRole('heading', { name: 'Parentescos' })).toBeTruthy()
    expect(within(condiciones).getByRole('heading', { name: 'Apoderamientos' })).toBeTruthy()
    await userEvent.click(within(condiciones).getByRole('button', { name: 'Editar datos' }))
    await userEvent.click(within(condiciones).getByRole('button', { name: '+ Incorporar' }))
    expect(within(condiciones).getByRole('form')).toBeTruthy()

    await userEvent.click(within(pestanas).getByRole('button', { name: /datos personales/i }))
    const datos = screen.getByRole('region', { name: 'Datos personales' })
    expect(within(datos).queryByRole('heading', { name: 'Datos personales' })).toBeNull()
    expect(within(datos).queryByRole('form')).toBeNull()
    await userEvent.click(within(datos).getByRole('button', { name: 'Editar datos' }))
    expect(within(datos).getByRole('form')).toBeTruthy()
    expect(within(datos).getByRole('textbox', { name: 'Nombres' })).toHaveProperty('value', 'Ana')
  })

  it('muestra el parentesco desde la persona consultada y evita inferir sexo por nombre', async () => {
    const estudiante = '22222222-2222-2222-2222-222222222222'
    const otroEstudiante = '44444444-4444-4444-4444-444444444444'
    const familiar = '33333333-3333-3333-3333-333333333333'
    const persona = { id: familiar, nombres: 'Carla', apellidos: 'Campos', fechaNacimiento: null,
      sexo: null, telefono: null, correo: null, direccion: null, documentos: [], condiciones: [],
      apoderamientos: [], provisional: null,
      parentescos: [{ id: 'vinculo', estudianteId: estudiante, familiarId: familiar,
        tipo: 'Madre', vigenteDesde: '2026-01-01', vigenteHasta: null },
        { id: 'vinculo-2', estudianteId: otroEstudiante, familiarId: familiar,
          tipo: 'Madre', vigenteDesde: '2026-01-01', vigenteHasta: null }],
      referencias: { [estudiante]: { nombres: 'Sofía', apellidos: 'Rojas', sexo: 'F',
        tipoDocumento: 'DNI', numeroDocumento: '00000014' },
        [otroEstudiante]: { nombres: 'Luis', apellidos: 'Rojas', sexo: 'M',
          tipoDocumento: 'DNI', numeroDocumento: '00000012' } } }
    const llamadas = vi.fn((entrada: RequestInfo | URL) =>
      respuesta(String(entrada).endsWith('/capacidades') ? capacidades : persona))
    vi.stubGlobal('fetch', llamadas)
    montar('/personas/' + familiar + '?tab=condiciones')
    const seccion = await screen.findByRole('region', { name: 'Condiciones institucionales' })
    expect(within(seccion).getByRole('link', { name: 'Sofía Rojas' }).closest('strong')?.textContent).toBe('Hija · Sofía Rojas')
    expect(within(seccion).getByRole('link', { name: 'Luis Rojas' }).closest('strong')?.textContent).toBe('Hijo · Luis Rojas')

    cleanup()
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) =>
      respuesta(String(entrada).endsWith('/capacidades') ? capacidades : {
        ...persona, id: estudiante, nombres: 'Sofía', apellidos: 'Rojas', sexo: 'F',
        parentescos: persona.parentescos.slice(0, 1),
        referencias: { [familiar]: { nombres: 'Carla', apellidos: 'Campos', sexo: null,
          tipoDocumento: 'DNI', numeroDocumento: '00000013' } }
      })))
    montar('/personas/' + estudiante + '?tab=condiciones')
    const seccionEstudiante = await screen.findByRole('region', { name: 'Condiciones institucionales' })
    expect(within(seccionEstudiante).getByRole('link', { name: 'Carla Campos' }).closest('strong')?.textContent).toBe('Madre · Carla Campos')

    cleanup()
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) =>
      respuesta(String(entrada).endsWith('/capacidades') ? capacidades : {
        ...persona, referencias: { [estudiante]: { nombres: 'Sofía', apellidos: 'Rojas', sexo: null,
          tipoDocumento: 'DNI', numeroDocumento: '00000014' } }
      })))
    montar('/personas/' + familiar + '?tab=condiciones')
    const seccionSinDato = await screen.findByRole('region', { name: 'Condiciones institucionales' })
    expect(within(seccionSinDato).getByRole('link', { name: 'Sofía Rojas' }).closest('strong')?.textContent).toBe('Hijo/a · Sofía Rojas')
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
