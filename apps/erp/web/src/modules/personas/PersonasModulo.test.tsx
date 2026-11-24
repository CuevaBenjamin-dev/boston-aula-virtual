// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
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

  it('recuerda la última fila, filtros y página al volver de una ficha', async () => {
    const primera = '11111111-1111-1111-1111-111111111111'
    const segunda = '22222222-2222-2222-2222-222222222222'
    const resultados = [
      { id: primera, nombres: 'Ana', apellidos: 'Ensayo', documento: '00009101', condiciones: ['ESTUDIANTE'] },
      { id: segunda, nombres: 'Luis', apellidos: 'Ensayo', documento: '00009102', condiciones: ['ESTUDIANTE'] },
    ]
    const detalle = { fechaNacimiento: null, telefono: null, correo: null, direccion: null,
      documentos: [], condiciones: [], parentescos: [], apoderamientos: [], referencias: {}, provisional: null }
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) => {
      const ruta = String(entrada)
      if (ruta.endsWith('/capacidades')) return respuesta(capacidades)
      const persona = resultados.find(p => ruta.endsWith('/' + p.id))
      if (persona) return respuesta({ ...persona, ...detalle })
      const q = new URLSearchParams(ruta.split('?')[1])
      const pagina = Number(q.get('pagina') ?? '0')
      if (q.get('buscar') === 'fuera') return respuesta({ resultados: [], total: 0, pagina: 0, tamano: 15 })
      const filas = pagina === 0 ? Array.from({ length: 15 }, (_, i) => ({
        id: `00000000-0000-0000-0000-${String(i + 1).padStart(12, '0')}`,
        nombres: `Prueba ${i + 1}`, apellidos: 'Ensayo', documento: null, condiciones: ['ESTUDIANTE'],
      })) : resultados
      return respuesta({ resultados: filas, total: 17, pagina, tamano: 15 })
    }))
    function Atras() {
      const navegar = useNavigate()
      return <button onClick={() => navegar(-1)}>Atrás del navegador</button>
    }
    render(<MemoryRouter initialEntries={['/personas']}><Atras /><Routes>
      <Route path="/personas/*" element={<PersonasModulo sesion={sesion} />} />
    </Routes></MemoryRouter>)
    await userEvent.type(await screen.findByRole('textbox', { name: 'Nombre o documento' }), 'Ensayo')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Condición' }), 'ESTUDIANTE')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Vigencia' }), 'true')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toHaveProperty('disabled', false))
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await userEvent.click(await screen.findByRole('link', { name: 'Ensayo, Ana' }))
    await userEvent.click(await screen.findByRole('link', { name: /directorio/i }))
    const filaAna = await screen.findByRole('row', { name: /Ensayo, Ana/ })
    expect(filaAna.classList.contains('per-directorio-visitada')).toBe(true)
    expect(screen.getByRole('textbox', { name: 'Nombre o documento' })).toHaveProperty('value', 'Ensayo')
    expect(screen.getByRole('combobox', { name: 'Condición' })).toHaveProperty('value', 'ESTUDIANTE')
    expect(screen.getByRole('combobox', { name: 'Vigencia' })).toHaveProperty('value', 'true')
    expect(screen.getByText('Página 2 de 2')).toBeTruthy()
    await userEvent.click(screen.getByRole('link', { name: 'Ensayo, Luis' }))
    await screen.findByRole('link', { name: /directorio/i })
    await userEvent.click(screen.getByRole('button', { name: 'Atrás del navegador' }))
    const filaLuis = await screen.findByRole('row', { name: /Ensayo, Luis/ })
    expect(filaLuis.classList.contains('per-directorio-visitada')).toBe(true)
    expect(screen.getByRole('row', { name: /Ensayo, Ana/ }).classList.contains('per-directorio-visitada')).toBe(false)
    const campo = screen.getByRole('textbox', { name: 'Nombre o documento' })
    await userEvent.clear(campo)
    await userEvent.type(campo, 'fuera')
    await screen.findByText('No hay personas para estos filtros.')
    expect(document.querySelector('.per-directorio-visitada')).toBeNull()
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

  it('agrupa la ficha en dos pestañas con edición directa por bloque', async () => {
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
    expect(screen.queryByRole('heading', { name: 'Vínculos institucionales' })).toBeNull()
    await userEvent.click(within(pestanas).getByRole('button', { name: /condiciones institucionales/i }))
    const condiciones = screen.getByRole('region', { name: 'Condiciones institucionales' })
    expect(within(condiciones).getByRole('heading', { name: 'Vínculos institucionales' })).toBeTruthy()
    await userEvent.click(within(condiciones).getByRole('button', { name: '+ Incorporar' }))
    const dialogo = screen.getByRole('dialog', { name: 'Incorporar condición' })
    expect(within(dialogo).getByRole('combobox', { name: 'Condición' })).toBeTruthy()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }))
    await userEvent.click(within(pestanas).getByRole('button', { name: /datos personales/i }))
    const datos = screen.getByRole('region', { name: 'Datos personales' })
    expect(within(datos).queryByRole('form')).toBeNull()
    await userEvent.click(within(datos).getByRole('button', { name: 'Editar identidad' }))
    expect(within(datos).getByRole('form', { name: 'Editar identidad' })).toBeTruthy()
    expect(within(datos).getByRole('textbox', { name: 'Nombres' })).toHaveProperty('value', 'Ana')
    expect(within(datos).queryByRole('textbox', { name: 'Teléfono' })).toBeNull()
  })

  it('edita contacto sin alterar identidad y conserva el formulario ante error de API', async () => {
    const persona = { id: '11111111-1111-1111-1111-111111111111', nombres: 'Ana', apellidos: 'Ensayo',
      fechaNacimiento: null, sexo: null, nacionalidad: null, telefono: '900000011', correo: null, direccion: 'Dirección de ensayo',
      documentos: [], condiciones: [], parentescos: [], apoderamientos: [], referencias: {}, provisional: null }
    let fallar = true
    const llamadas = vi.fn((entrada: RequestInfo | URL, init?: RequestInit) => {
      if (String(entrada).endsWith('/capacidades')) return respuesta(capacidades)
      if (init?.method === 'PUT') {
        if (fallar) return Promise.resolve(new Response(JSON.stringify({ mensaje: 'Contacto rechazado' }), { status: 400 }))
        Object.assign(persona, JSON.parse(String(init.body)))
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return respuesta(persona)
    })
    vi.stubGlobal('fetch', llamadas)
    montar('/personas/' + persona.id)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar contacto' }))
    const campo = screen.getByRole('textbox', { name: 'Teléfono' })
    await userEvent.clear(campo); await userEvent.type(campo, '900000022')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByText('Contacto rechazado')).toBeTruthy()
    expect(campo).toHaveProperty('value', '900000022')
    fallar = false
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByText('900000022')).toBeTruthy()
    const envio = llamadas.mock.calls.filter(c => c[1]?.method === 'PUT').at(-1)
    expect(String(envio?.[0])).toMatch(/\/contacto$/)
    expect(JSON.parse(String(envio?.[1]?.body))).toEqual({ telefono: '900000022', correo: '' })
    expect(persona.nombres).toBe('Ana')
  })

  it('agrupa vínculos por persona sin perder históricos ni mezclar sus operaciones', async () => {
    const familiar = '33333333-3333-3333-3333-333333333333'
    const estudiante = '22222222-2222-2222-2222-222222222222'
    const fecha = '2026-01-01T00:00:00Z'
    const persona = { id: familiar, nombres: 'Familiar', apellidos: 'Ensayo', fechaNacimiento: null,
      sexo: null, telefono: '900000011', correo: null, direccion: null, documentos: [], condiciones: [], provisional: null,
      parentescos: [{ id: 'rel-1', estudianteId: estudiante, familiarId: familiar, tipo: 'Madre', vigenteDesde: fecha, vigenteHasta: null },
        { id: 'rel-2', estudianteId: estudiante, familiarId: familiar, tipo: 'Madre', vigenteDesde: fecha, vigenteHasta: fecha }],
      apoderamientos: [{ id: 'aut-1', estudianteId: estudiante, apoderadoId: familiar, sinParentesco: false, vigenteDesde: fecha, vigenteHasta: null as string | null }],
      referencias: { [estudiante]: { nombres: 'Alumno', apellidos: 'Ensayo', sexo: 'M', tipoDocumento: 'DNI', numeroDocumento: '00009111' } } }
    const llamadas = vi.fn((entrada: RequestInfo | URL, init?: RequestInit) => {
      const ruta = String(entrada)
      if (ruta.endsWith('/capacidades')) return respuesta(capacidades)
      if (init?.method === 'POST') { persona.apoderamientos[0].vigenteHasta = fecha; return Promise.resolve(new Response(null, { status: 204 })) }
      return respuesta(persona)
    })
    vi.stubGlobal('fetch', llamadas)
    montar('/personas/' + familiar + '?tab=condiciones')
    const fila = (await screen.findByRole('link', { name: 'Alumno Ensayo' })).closest('tr')!
    expect(within(fila).getAllByText('Hijo')).toHaveLength(2)
    expect(within(fila).getAllByRole('button', { name: 'Desactivar parentesco' })).toHaveLength(1)
    await userEvent.click(within(fila).getByRole('button', { name: 'Desactivar autorización' }))
    const dialogo = screen.getByRole('dialog', { name: 'Desactivar autorización' })
    await userEvent.type(within(dialogo).getByRole('textbox', { name: 'Motivo' }), 'Ensayo')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar desactivación' }))
    await screen.findByText('Vínculo desactivado')
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Desactivar autorización' })).toBeNull())
    expect(screen.getByRole('button', { name: 'Desactivar parentesco' })).toBeTruthy()
    expect(llamadas.mock.calls.filter(c => c[1]?.method === 'POST').map(c => String(c[0]))).toEqual([
      '/api/erp/v1/personas/' + familiar + '/apoderamientos/aut-1/desactivar',
    ])
  })

  it('mantiene los permisos de edición y no ofrece corregir dirección al acceso de solo contacto', async () => {
    const persona = { id: '11111111-1111-1111-1111-111111111111', nombres: 'Ana', apellidos: 'Ensayo',
      fechaNacimiento: null, sexo: null, telefono: '900000011', correo: null, direccion: 'Dirección de ensayo',
      documentos: [], condiciones: [], parentescos: [], apoderamientos: [], referencias: {}, provisional: null }
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) => respuesta(String(entrada).endsWith('/capacidades') ? {
      ...capacidades, corregirIdentidad: false, gestionarVinculos: false, gestionarCondiciones: false, eliminarRegistroIncorrecto: false,
    } : persona)))
    montar('/personas/' + persona.id)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar contacto' }))
    expect(screen.queryByRole('button', { name: 'Editar identidad' })).toBeNull()
    expect(screen.queryByRole('button', { name: /corregir o regularizar/i })).toBeNull()
    expect(screen.getByRole('textbox', { name: 'Dirección' })).toHaveProperty('readOnly', true)
    await userEvent.click(screen.getByRole('button', { name: 'Condiciones institucionales' }))
    expect(screen.queryByRole('button', { name: '+ Incorporar' })).toBeNull()
    expect(screen.queryByRole('button', { name: '+ Apoderamiento' })).toBeNull()
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
    expect(within(within(seccion).getByRole('link', { name: 'Sofía Rojas' }).closest('tr')!).getByText('Hija')).toBeTruthy()
    expect(within(within(seccion).getByRole('link', { name: 'Luis Rojas' }).closest('tr')!).getByText('Hijo')).toBeTruthy()

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
    expect(within(within(seccionEstudiante).getByRole('link', { name: 'Carla Campos' }).closest('tr')!).getByText('Madre')).toBeTruthy()

    cleanup()
    vi.stubGlobal('fetch', vi.fn((entrada: RequestInfo | URL) =>
      respuesta(String(entrada).endsWith('/capacidades') ? capacidades : {
        ...persona, referencias: { [estudiante]: { nombres: 'Sofía', apellidos: 'Rojas', sexo: null,
          tipoDocumento: 'DNI', numeroDocumento: '00000014' } }
      })))
    montar('/personas/' + familiar + '?tab=condiciones')
    const seccionSinDato = await screen.findByRole('region', { name: 'Condiciones institucionales' })
    expect(within(within(seccionSinDato).getByRole('link', { name: 'Sofía Rojas' }).closest('tr')!).getByText('Hijo/a')).toBeTruthy()
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
