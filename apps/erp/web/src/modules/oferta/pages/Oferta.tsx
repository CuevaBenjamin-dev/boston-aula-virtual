import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, Etiqueta, opciones, Panel, Selector, useCatalogos, Vacio } from '../../../shared/ui/index'
import { PageHeading } from '../../../shared/ui/PageHeading'

type Vista = 'cursos' | 'secciones' | 'estructura'
type Filtros = { buscar: string; periodo: string; nivel: string; grado: string; estado: string }
const filtrosIniciales: Filtros = { buscar: '', periodo: '', nivel: '', grado: '', estado: '' }
const letras = ['A', 'B', 'C', 'D']
const porPagina = 10
const ordenGrados = ['primero', 'segundo', 'tercero', 'cuarto', 'quinto', 'sexto']
function gradosEnOrden(grados: Fila[]) {
  return [...grados].sort((a, b) => {
    const ordenA = ordenGrados.indexOf(valor(a.nombre).toLocaleLowerCase('es'))
    const ordenB = ordenGrados.indexOf(valor(b.nombre).toLocaleLowerCase('es'))
    return (ordenA < 0 ? 99 : ordenA) - (ordenB < 0 ? 99 : ordenB) || valor(a.nombre).localeCompare(valor(b.nombre), 'es')
  })
}

function Paginacion({ pagina, paginas, total, cambio }: { pagina: number; paginas: number; total: number; cambio: (n: number) => void }) {
  if (total <= porPagina) return null
  const numeros = Array.from({ length: Math.min(5, paginas) }, (_, i) => Math.max(1, Math.min(paginas - 4, pagina - 2)) + i)
  return <nav className="oferta-paginacion" aria-label="Paginación de resultados">
    <span>Página {pagina} de {paginas}</span>
    <div>
      <button className="boton" disabled={pagina === 1} onClick={() => cambio(pagina - 1)}>← Anterior</button>
      {numeros.map(n => <button key={n} className={'oferta-pagina ' + (n === pagina ? 'actual' : '')} aria-label={'Página ' + n} aria-current={n === pagina ? 'page' : undefined} onClick={() => cambio(n)}>{n}</button>)}
      <button className="boton" disabled={pagina === paginas} onClick={() => cambio(pagina + 1)}>Siguiente →</button>
    </div>
  </nav>
}

function SeccionTarjeta({ letra, seccion, puedeEditar, guardar, alternar }: {
  letra: string; seccion?: Fila; puedeEditar: boolean; guardar: (letra: string, seccion: Fila | undefined, capacidad: string) => Promise<boolean>; alternar: (seccion: Fila) => Promise<void>
}) {
  const [editando, setEditando] = useState(false)
  const [capacidad, setCapacidad] = useState(valor(seccion?.capacidad))
  useEffect(() => { setCapacidad(valor(seccion?.capacidad)); setEditando(false) }, [seccion?.id, seccion?.capacidad])
  const activa = seccion?.habilitada === true
  async function enviar(e: FormEvent) {
    e.preventDefault()
    if (await guardar(letra, seccion, capacidad)) setEditando(false)
  }
  return <article className={'oferta-seccion ' + (activa ? 'habilitada' : '')}>
    <div className="oferta-seccion-superior"><span className="oferta-letra">{letra}</span><Etiqueta estado={seccion ? (activa ? 'ACTIVO' : 'INACTIVO') : 'SIN CREAR'} /></div>
    <div className="oferta-seccion-info"><strong>{seccion ? 'Sección ' + letra : 'Sin habilitar'}</strong><span>{seccion ? 'Capacidad: ' + (valor(seccion.capacidad) || 'sin límite definido') : 'Disponible para crear en este grado.'}</span></div>
    {puedeEditar && <>
      {!editando ? <button className="boton" onClick={() => setEditando(true)}>{seccion ? 'Configurar sección' : 'Crear sección'}</button> :
        <form className="oferta-seccion-form" onSubmit={e => void enviar(e)}>
          <Campo etiqueta="Capacidad (opcional)" tipo="number" valorActual={capacidad} cambio={setCapacidad} placeholder="Sin límite definido" />
          <div className="oferta-seccion-acciones">
            <button className="boton primario">{seccion ? 'Guardar capacidad' : 'Crear y habilitar'}</button>
            <button type="button" className="link-button" onClick={() => setEditando(false)}>Cancelar</button>
          </div>
        </form>}
      {seccion && !editando && <button className="link-button oferta-estado-link" onClick={() => void alternar(seccion)}>{activa ? 'Inactivar sección' : 'Habilitar sección'}</button>}
    </>}
  </article>
}

export function Oferta({ puedeEditar }: { puedeEditar: boolean }) {
  const { datos: cat, error: errorCat, cargar } = useCatalogos()
  const [vista, setVista] = useState<Vista>('cursos')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales)
  const [paginaCursos, setPaginaCursos] = useState(1)
  const [paginaPeriodos, setPaginaPeriodos] = useState(1)
  const [periodoSecciones, setPeriodoSecciones] = useState('')
  const [nivelSecciones, setNivelSecciones] = useState('')
  const [gradoSecciones, setGradoSecciones] = useState('')
  const [nivelEstructura, setNivelEstructura] = useState('')
  const [mostrarCrearCurso, setMostrarCrearCurso] = useState(false)
  const [periodoNuevo, setPeriodoNuevo] = useState({ nombre: '', inicio: '', fin: '' })
  const [nivelNuevo, setNivelNuevo] = useState('')
  const [gradoNuevo, setGradoNuevo] = useState({ nivelId: '', nombre: '' })
  const [cursoNuevo, setCursoNuevo] = useState({ periodoId: '', nivelId: '', gradoId: '', asignaturaId: '' })

  const periodoActual = periodoSecciones || valor(cat.periodos.find(p => p.habilitado)?.id) || valor(cat.periodos[0]?.id)
  const nivelPrimaria = cat.niveles.find(n => valor(n.nombre).toLowerCase().includes('primaria'))
  const nivelActual = nivelSecciones || valor(nivelPrimaria?.id) || valor(cat.niveles[0]?.id)
  const gradosDelNivel = gradosEnOrden(cat.grados.filter(g => valor(g.nivel_id) === nivelActual))
  const gradoActual = gradosDelNivel.some(g => valor(g.id) === gradoSecciones) ? gradoSecciones : valor(gradosDelNivel[0]?.id)
  const gradoSeleccionado = cat.grados.find(g => valor(g.id) === gradoActual)
  const periodoSeleccionado = cat.periodos.find(p => valor(p.id) === periodoActual)
  const nivelSeleccionado = nivelEstructura || nivelActual
  const gradosFiltrables = gradosEnOrden(cat.grados.filter(g => !filtros.nivel || valor(g.nivel_id) === filtros.nivel))

  const gruposCursos = new Map<string, { curso: Fila; estados: Set<string> }>()
  for (const oferta of cat.ofertas) {
    const clave = [valor(oferta.periodo_id), valor(oferta.grado_id), valor(oferta.asignatura_id)].join(':')
    const grupo = gruposCursos.get(clave)
    if (grupo) grupo.estados.add(valor(oferta.estado))
    else gruposCursos.set(clave, { curso: oferta, estados: new Set([valor(oferta.estado)]) })
  }
  const cursosPorGrado = [...gruposCursos.values()].map(({ curso, estados }): Fila => ({
    ...curso, estado: estados.size === 1 ? [...estados][0] : 'MIXTA'
  })).sort((a, b) => {
    const nivel = (valor(a.nivel).includes('Primaria') ? 0 : 1) - (valor(b.nivel).includes('Primaria') ? 0 : 1)
    const gradoA = ordenGrados.indexOf(valor(a.grado).toLocaleLowerCase('es'))
    const gradoB = ordenGrados.indexOf(valor(b.grado).toLocaleLowerCase('es'))
    return valor(b.periodo).localeCompare(valor(a.periodo), 'es', { numeric: true }) || nivel || gradoA - gradoB || valor(a.asignatura).localeCompare(valor(b.asignatura), 'es')
  })
  const texto = filtros.buscar.trim().toLocaleLowerCase('es')
  const cursosFiltrados = cursosPorGrado.filter(o =>
    (!texto || [o.asignatura, o.codigo_asignatura, o.grado, o.nivel].some(v => valor(v).toLocaleLowerCase('es').includes(texto))) &&
    (!filtros.periodo || valor(o.periodo_id) === filtros.periodo) &&
    (!filtros.nivel || cat.grados.some(g => valor(g.id) === valor(o.grado_id) && valor(g.nivel_id) === filtros.nivel)) &&
    (!filtros.grado || valor(o.grado_id) === filtros.grado) &&
    (!filtros.estado || valor(o.estado) === filtros.estado)
  )
  const paginasCursos = Math.max(1, Math.ceil(cursosFiltrados.length / porPagina))
  const paginaCursosActual = Math.min(paginaCursos, paginasCursos)
  const cursosPagina = cursosFiltrados.slice((paginaCursosActual - 1) * porPagina, paginaCursosActual * porPagina)
  const paginasPeriodos = Math.max(1, Math.ceil(cat.periodos.length / 8))
  const paginaPeriodosActual = Math.min(paginaPeriodos, paginasPeriodos)
  const periodosPagina = cat.periodos.slice((paginaPeriodosActual - 1) * 8, paginaPeriodosActual * 8)
  const seccionesGrado = cat.secciones.filter(s => valor(s.periodo_id) === periodoActual && valor(s.grado_id) === gradoActual)
  const gradosCursoNuevo = gradosEnOrden(cat.grados.filter(g => !cursoNuevo.nivelId || valor(g.nivel_id) === cursoNuevo.nivelId))
  const asignaturasDisponibles = cat.asignaturas.filter(a => a.activa && cat.relaciones.some(r =>
    valor(r.asignatura_id) === valor(a.id) && valor(r.grado_id) === cursoNuevo.gradoId &&
    (!r.periodo_id || valor(r.periodo_id) === cursoNuevo.periodoId)
  ))

  function cambiarFiltro(clave: keyof Filtros, nuevo: string) {
    setFiltros(prev => ({ ...prev, [clave]: nuevo, ...(clave === 'nivel' ? { grado: '' } : {}) }))
    setPaginaCursos(1)
  }
  async function ejecutar(ruta: string, metodo: string, datos: unknown, mensaje: string): Promise<boolean> {
    setError(''); setExito('')
    try { await api(ruta, metodo, datos); await cargar(); setExito(mensaje); return true }
    catch (e) { setError((e as Error).message); return false }
  }
  async function guardarSeccion(letra: string, seccion: Fila | undefined, capacidad: string): Promise<boolean> {
    if (capacidad && (!Number.isInteger(Number(capacidad)) || Number(capacidad) < 1)) { setError('La capacidad debe ser un número entero mayor que cero.'); return false }
    const cupo = capacidad ? Number(capacidad) : null
    if (seccion) return ejecutar('/secciones/' + valor(seccion.id), 'PUT', { habilitada: seccion.habilitada, capacidad: cupo }, 'Capacidad de la sección ' + letra + ' actualizada.')
    if (!periodoActual || !gradoActual) { setError('Selecciona un periodo y un grado.'); return false }
    return ejecutar('/secciones', 'POST', { periodoId: periodoActual, gradoId: gradoActual, nombre: letra, capacidad: cupo, habilitada: true }, 'Sección ' + letra + ' creada.')
  }
  async function alternarSeccion(seccion: Fila) {
    await ejecutar('/secciones/' + valor(seccion.id), 'PUT', { habilitada: !seccion.habilitada, capacidad: seccion.capacidad || null }, 'Estado de la sección actualizado.')
  }
  async function crearCurso(e: FormEvent) {
    e.preventDefault()
    if (await ejecutar('/ofertas/grado', 'POST', { periodoId: cursoNuevo.periodoId, gradoId: cursoNuevo.gradoId, asignaturaId: cursoNuevo.asignaturaId }, 'Curso aplicado a las secciones habilitadas y sincronizado con Aula Virtual.')) {
      setCursoNuevo({ periodoId: '', nivelId: '', gradoId: '', asignaturaId: '' }); setMostrarCrearCurso(false)
    }
  }

  return <div className="pagina oferta-pagina-principal">
    <PageHeading title="Oferta académica" metricLabel="CURSOS POR GRADO" metricValue={String(cursosPorGrado.length).padStart(2, '0')} metricCaption="CURSOS DISPONIBLES" />
    <Aviso error={error || errorCat} exito={exito} />
    <div className="oferta-tabs" role="tablist" aria-label="Secciones de oferta académica">
      {([['cursos', '01', 'Cursos'], ['secciones', '02', 'Secciones'], ['estructura', '03', 'Periodos y grados']] as const).map(([id, numero, titulo]) =>
        <button key={id} role="tab" aria-selected={vista === id} className={vista === id ? 'activo' : ''} onClick={() => { setVista(id); setError(''); setExito('') }}><span>{numero}</span>{titulo}</button>)}
    </div>

    {vista === 'cursos' && <div className="oferta-contenido" role="tabpanel">
      <div className="oferta-titulo-seccion"><div><p className="eyebrow">01 / CURSOS POR GRADO</p><h2>Cursos disponibles</h2><p>Cada curso del grado se aplica a sus secciones habilitadas.</p></div>{puedeEditar && <button className="boton primario" onClick={() => setMostrarCrearCurso(v => !v)}>{mostrarCrearCurso ? 'Cerrar formulario' : '+ Crear curso'}</button>}</div>
      {mostrarCrearCurso && puedeEditar && <Panel titulo="Crear curso" subtitulo="Elige el grado y una asignatura de su malla; se aplicará a todas sus secciones habilitadas."><form className="formulario" onSubmit={e => void crearCurso(e)}><div className="campos-4"><Selector etiqueta="Periodo" valorActual={cursoNuevo.periodoId} cambio={v => setCursoNuevo(prev => ({ ...prev, periodoId: v, asignaturaId: '' }))} opciones={opciones(cat.periodos.filter(p => p.habilitado), p => valor(p.nombre))} requerido /><Selector etiqueta="Nivel" valorActual={cursoNuevo.nivelId} cambio={v => setCursoNuevo(prev => ({ ...prev, nivelId: v, gradoId: '', asignaturaId: '' }))} opciones={opciones(cat.niveles.filter(n => n.habilitado), n => valor(n.nombre))} requerido /><Selector etiqueta="Grado" valorActual={cursoNuevo.gradoId} cambio={v => setCursoNuevo(prev => ({ ...prev, gradoId: v, asignaturaId: '' }))} opciones={opciones(gradosCursoNuevo.filter(g => g.habilitado), g => valor(g.nombre))} requerido /><Selector etiqueta="Asignatura" valorActual={cursoNuevo.asignaturaId} cambio={v => setCursoNuevo(prev => ({ ...prev, asignaturaId: v }))} opciones={opciones(asignaturasDisponibles, a => valor(a.codigo) + ' · ' + valor(a.nombre))} requerido /></div>{cursoNuevo.gradoId && !asignaturasDisponibles.length && <p className="oferta-ayuda">Este grado no tiene asignaturas activas en la malla curricular. <Link to="/asignaturas">Revisar malla →</Link></p>}<button className="boton primario">Crear curso para el grado</button></form></Panel>}
      <section className="oferta-filtros" aria-label="Filtrar cursos"><div className="oferta-filtros-cabecera"><strong>Buscar y filtrar</strong><button className="link-button" onClick={() => { setFiltros(filtrosIniciales); setPaginaCursos(1) }}>Limpiar filtros</button></div><div className="oferta-filtros-grid">
        <Campo etiqueta="Nombre o código" valorActual={filtros.buscar} cambio={v => cambiarFiltro('buscar', v)} placeholder="Buscar asignatura" />
        <Selector etiqueta="Periodo" valorActual={filtros.periodo} cambio={v => cambiarFiltro('periodo', v)} opciones={opciones(cat.periodos, p => valor(p.nombre))} vacio="Todos" />
        <Selector etiqueta="Nivel" valorActual={filtros.nivel} cambio={v => cambiarFiltro('nivel', v)} opciones={opciones(cat.niveles, n => valor(n.nombre))} vacio="Todos" />
        <Selector etiqueta="Grado" valorActual={filtros.grado} cambio={v => cambiarFiltro('grado', v)} opciones={opciones(gradosFiltrables, g => filtros.nivel ? valor(g.nombre) : valor(g.nivel) + ' · ' + valor(g.nombre))} vacio="Todos" />
        <Selector etiqueta="Estado" valorActual={filtros.estado} cambio={v => cambiarFiltro('estado', v)} opciones={[...new Set(cursosPorGrado.map(o => valor(o.estado)))].filter(Boolean).map(x => ({ valor: x, texto: x.replaceAll('_', ' ') }))} vacio="Todos" />
      </div></section>
      <section className="oferta-listado" aria-label="Resultados de cursos"><div className="oferta-listado-titulo"><div><p className="eyebrow">RESULTADOS</p><h3>{cursosFiltrados.length} {cursosFiltrados.length === 1 ? 'curso' : 'cursos'}</h3></div><span>{cursosFiltrados.length ? 'Mostrando ' + ((paginaCursosActual - 1) * porPagina + 1) + '–' + Math.min(paginaCursosActual * porPagina, cursosFiltrados.length) : 'Sin resultados'}</span></div>
        {cursosPagina.length ? <div className="tabla-wrap"><table className="oferta-tabla"><thead><tr><th>Asignatura</th><th>Grado</th><th>Periodo</th><th>Estado</th></tr></thead><tbody>{cursosPagina.map(o => <tr key={[valor(o.periodo_id), valor(o.grado_id), valor(o.asignatura_id)].join(':')}><td><strong>{valor(o.asignatura)}</strong><small>{valor(o.codigo_asignatura)}</small></td><td>{valor(o.nivel)} · {valor(o.grado)}</td><td>{valor(o.periodo)}</td><td><Etiqueta estado={o.estado} /></td></tr>)}</tbody></table></div> : <Vacio texto={cat.ofertas.length ? 'No hay cursos con estos filtros. Prueba con otros criterios.' : 'Todavía no hay cursos. Configura la malla, habilita una sección y crea un curso.'} />}
        <Paginacion pagina={paginaCursosActual} paginas={paginasCursos} total={cursosFiltrados.length} cambio={setPaginaCursos} />
      </section>
    </div>}

    {vista === 'secciones' && <div className="oferta-contenido" role="tabpanel">
      <div className="oferta-titulo-seccion"><div><p className="eyebrow">02 / ESPACIOS DE MATRÍCULA</p><h2>Secciones del grado</h2><p>Las secciones habilitadas permiten matricular estudiantes y ofrecer cursos.</p></div></div>
      <section className="oferta-filtros" aria-label="Elegir grado y periodo"><div className="oferta-filtros-grid secciones"><Selector etiqueta="Periodo" valorActual={periodoActual} cambio={v => setPeriodoSecciones(v)} opciones={opciones(cat.periodos, p => valor(p.nombre))} requerido /><Selector etiqueta="Nivel" valorActual={nivelActual} cambio={v => { setNivelSecciones(v); setGradoSecciones('') }} opciones={opciones(cat.niveles, n => valor(n.nombre))} requerido /><Selector etiqueta="Grado" valorActual={gradoActual} cambio={setGradoSecciones} opciones={opciones(gradosDelNivel, g => valor(g.nombre))} requerido /></div></section>
      <div className="oferta-grado-encabezado"><div><span>PERIODO {valor(periodoSeleccionado?.nombre) || '—'}</span><h3>{valor(gradoSeleccionado?.nivel)} · {valor(gradoSeleccionado?.nombre) || 'Selecciona un grado'}</h3></div><strong>{seccionesGrado.filter(s => s.habilitada).length} de 4 habilitadas</strong></div>
      {gradoActual && periodoActual ? <div className="oferta-secciones-grid">{letras.map(letra => <SeccionTarjeta key={periodoActual + gradoActual + letra} letra={letra} seccion={seccionesGrado.find(s => valor(s.nombre) === letra)} puedeEditar={puedeEditar} guardar={guardarSeccion} alternar={alternarSeccion} />)}</div> : <Vacio texto="Crea primero un periodo y un grado para configurar secciones." />}
      <p className="oferta-ayuda">Cada grado puede tener hasta cuatro secciones (A, B, C y D). La capacidad es opcional.</p>
    </div>}

    {vista === 'estructura' && <div className="oferta-contenido" role="tabpanel">
      <div className="oferta-titulo-seccion"><div><p className="eyebrow">03 / BASE INSTITUCIONAL</p><h2>Periodos y grados</h2><p>Define la estructura que comparten matrículas, secciones y cursos.</p></div><Link className="boton" to="/asignaturas">Ver malla curricular →</Link></div>
      <div className="oferta-estructura-grid"><Panel titulo="Periodos escolares" subtitulo="Años en los que se organizan matrículas y cursos.">
        {periodosPagina.length ? periodosPagina.map(p => <div className="oferta-estructura-fila" key={valor(p.id)}><div><strong>{valor(p.nombre)}</strong><small>{valor(p.inicio)} a {valor(p.fin)}</small></div><Etiqueta estado={p.habilitado ? 'ACTIVO' : 'INACTIVO'} />{puedeEditar && <button className="link-button" onClick={() => void ejecutar('/periodos/' + p.id + '/estado', 'PUT', { habilitado: !p.habilitado }, 'Periodo actualizado.')}>{p.habilitado ? 'Inactivar' : 'Habilitar'}</button>}</div>) : <Vacio texto="Aún no hay periodos escolares." />}
        {cat.periodos.length > 8 && <div className="oferta-mini-paginacion"><button className="boton" disabled={paginaPeriodosActual === 1} onClick={() => setPaginaPeriodos(paginaPeriodosActual - 1)}>← Anterior</button><span>{paginaPeriodosActual} / {paginasPeriodos}</span><button className="boton" disabled={paginaPeriodosActual === paginasPeriodos} onClick={() => setPaginaPeriodos(paginaPeriodosActual + 1)}>Siguiente →</button></div>}
        {puedeEditar && <details className="oferta-crear"><summary>+ Nuevo periodo</summary><form className="formulario" onSubmit={e => { e.preventDefault(); void ejecutar('/periodos', 'POST', { ...periodoNuevo, habilitado: true }, 'Periodo creado.') }}><Campo etiqueta="Nombre" valorActual={periodoNuevo.nombre} cambio={v => setPeriodoNuevo({ ...periodoNuevo, nombre: v })} placeholder="2027" requerido /><div className="campos-2"><Campo etiqueta="Inicio" tipo="date" valorActual={periodoNuevo.inicio} cambio={v => setPeriodoNuevo({ ...periodoNuevo, inicio: v })} requerido /><Campo etiqueta="Fin" tipo="date" valorActual={periodoNuevo.fin} cambio={v => setPeriodoNuevo({ ...periodoNuevo, fin: v })} requerido /></div><button className="boton primario">Crear periodo</button></form></details>}
      </Panel><Panel titulo="Niveles y grados" subtitulo="Selecciona un nivel para consultar sus grados.">
        <Selector etiqueta="Nivel" valorActual={nivelSeleccionado} cambio={setNivelEstructura} opciones={opciones(cat.niveles, n => valor(n.nombre))} />
        {cat.niveles.filter(n => valor(n.id) === nivelSeleccionado).map(n => <div className="oferta-nivel-fila" key={valor(n.id)}><strong>{valor(n.nombre)}</strong><Etiqueta estado={n.habilitado ? 'ACTIVO' : 'INACTIVO'} />{puedeEditar && <button className="link-button" onClick={() => void ejecutar('/niveles/' + n.id + '/estado', 'PUT', { habilitado: !n.habilitado }, 'Nivel actualizado.')}>{n.habilitado ? 'Inactivar' : 'Habilitar'}</button>}</div>)}
        {gradosEnOrden(cat.grados.filter(g => valor(g.nivel_id) === nivelSeleccionado)).map(g => <div className="oferta-estructura-fila" key={valor(g.id)}><strong>{valor(g.nombre)}</strong><Etiqueta estado={g.habilitado ? 'ACTIVO' : 'INACTIVO'} />{puedeEditar && <button className="link-button" onClick={() => void ejecutar('/grados/' + g.id + '/estado', 'PUT', { habilitado: !g.habilitado }, 'Grado actualizado.')}>{g.habilitado ? 'Inactivar' : 'Habilitar'}</button>}</div>)}
        {puedeEditar && <div className="oferta-crear-grupo"><details className="oferta-crear"><summary>+ Agregar nivel</summary><form className="formulario" onSubmit={e => { e.preventDefault(); void ejecutar('/niveles', 'POST', { nombre: nivelNuevo }, 'Nivel creado.') }}><Campo etiqueta="Nombre" valorActual={nivelNuevo} cambio={setNivelNuevo} requerido /><button className="boton primario">Crear nivel</button></form></details><details className="oferta-crear"><summary>+ Agregar grado</summary><form className="formulario" onSubmit={e => { e.preventDefault(); void ejecutar('/grados', 'POST', gradoNuevo, 'Grado creado.') }}><Selector etiqueta="Nivel" valorActual={gradoNuevo.nivelId} cambio={v => setGradoNuevo({ ...gradoNuevo, nivelId: v })} opciones={opciones(cat.niveles.filter(n => n.habilitado), n => valor(n.nombre))} requerido /><Campo etiqueta="Grado" valorActual={gradoNuevo.nombre} cambio={v => setGradoNuevo({ ...gradoNuevo, nombre: v })} requerido /><button className="boton primario">Crear grado</button></form></details></div>}
      </Panel></div>
    </div>}
  </div>
}
