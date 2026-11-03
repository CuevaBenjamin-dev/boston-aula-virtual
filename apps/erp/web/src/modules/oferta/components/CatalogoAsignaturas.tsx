import { useState, type FormEvent } from 'react'
import { type Fila, valor } from '../../../shared/api/client'
import { Campo, Etiqueta, Selector, Vacio } from '../../../shared/ui/index'

const TAMANO_PAGINA = 15
type Ejecutar = (ruta: string, metodo: string, datos: unknown, mensaje: string) => Promise<boolean>

export function CatalogoAsignaturas({ asignaturas, puedeEditar, ejecutar }: {
  asignaturas: Fila[]; puedeEditar: boolean; ejecutar: Ejecutar
}) {
  const [abierto, setAbierto] = useState(false)
  const [buscar, setBuscar] = useState('')
  const [estado, setEstado] = useState('')
  const [pagina, setPagina] = useState(1)
  const [creando, setCreando] = useState(false)
  const [nuevo, setNuevo] = useState({ codigo: '', nombre: '' })
  const [editando, setEditando] = useState<{ id: string; codigo: string; nombre: string; activa: boolean } | null>(null)
  const [eliminando, setEliminando] = useState('')

  const texto = buscar.trim().toLocaleLowerCase('es')
  const filtradas = asignaturas.filter(a =>
    (!texto || [a.codigo, a.nombre].some(v => valor(v).toLocaleLowerCase('es').includes(texto))) &&
    (!estado || (estado === 'activas' ? a.activa === true : a.activa !== true))
  )
  const paginas = Math.max(1, Math.ceil(filtradas.length / TAMANO_PAGINA))
  const paginaActual = Math.min(pagina, paginas)
  const visibles = filtradas.slice((paginaActual - 1) * TAMANO_PAGINA, paginaActual * TAMANO_PAGINA)

  async function crear(e: FormEvent) {
    e.preventDefault()
    if (await ejecutar('/asignaturas', 'POST', { ...nuevo, activa: true }, 'Asignatura creada en el catálogo. Asígnala a un grado para ofrecerla.')) {
      setNuevo({ codigo: '', nombre: '' })
      setCreando(false)
      setBuscar(nuevo.codigo.trim())
      setEstado('')
      setPagina(1)
    }
  }
  async function guardar(e: FormEvent) {
    e.preventDefault()
    if (!editando) return
    if (await ejecutar('/asignaturas/' + editando.id, 'PUT', {
      codigo: editando.codigo, nombre: editando.nombre, activa: editando.activa
    }, 'Asignatura actualizada. Los cursos vinculados reflejarán el cambio.')) setEditando(null)
  }
  async function eliminar(id: string) {
    if (await ejecutar('/asignaturas/' + id, 'DELETE', undefined, 'Asignatura eliminada del catálogo.')) setEliminando('')
  }

  return <section className="panel catalogo-asignaturas">
    <button type="button" className="catalogo-desplegar" aria-expanded={abierto} aria-controls={abierto ? 'catalogo-asignaturas-contenido' : undefined} onClick={() => setAbierto(v => !v)}>
      <span><small>GESTIÓN DEL CATÁLOGO</small><strong>Catálogo de asignaturas</strong><em>{asignaturas.length} registradas · Abre para buscar y administrar</em></span>
      <b aria-hidden="true">{abierto ? '−' : '+'}</b>
    </button>
    {abierto && <div id="catalogo-asignaturas-contenido" className="catalogo-cuerpo">
      <p className="catalogo-ayuda">El código identifica una asignatura. Puede tener el mismo nombre que otra de un grado distinto, pero cada código es único. Las asignaturas con vínculos se desactivan para conservar su historial; las que nunca se usaron pueden eliminarse.</p>
      <div className="catalogo-controles">
        <Campo etiqueta="Buscar por nombre o código" valorActual={buscar} cambio={v => { setBuscar(v); setPagina(1) }} placeholder="Ej. Matemática o PRI-01-MAT" />
        <Selector etiqueta="Estado" valorActual={estado} cambio={v => { setEstado(v); setPagina(1) }} opciones={[{ valor: 'activas', texto: 'Activas' }, { valor: 'inactivas', texto: 'Inactivas' }]} vacio="Todas" />
        {puedeEditar && <button type="button" className="boton primario" onClick={() => { setCreando(v => !v); setEditando(null); setEliminando('') }}>{creando ? 'Cerrar formulario' : '+ Nueva asignatura'}</button>}
      </div>
      {creando && puedeEditar && <form className="catalogo-form" onSubmit={e => void crear(e)}>
        <h3>Agregar al catálogo</h3>
        <div className="catalogo-form-campos"><Campo etiqueta="Código único" valorActual={nuevo.codigo} cambio={v => setNuevo({ ...nuevo, codigo: v })} placeholder="PRI-01-OTRO" requerido /><Campo etiqueta="Nombre" valorActual={nuevo.nombre} cambio={v => setNuevo({ ...nuevo, nombre: v })} requerido /><button className="boton primario" type="submit">Guardar asignatura</button></div>
        <p>Crear una asignatura no la añade todavía a la malla de un grado.</p>
      </form>}
      <div className="catalogo-resultados"><strong>{filtradas.length} {filtradas.length === 1 ? 'resultado' : 'resultados'}</strong><span>Mostrando {filtradas.length ? (paginaActual - 1) * TAMANO_PAGINA + 1 : 0}–{Math.min(paginaActual * TAMANO_PAGINA, filtradas.length)} · 15 por página</span></div>
      {visibles.length ? <div className="catalogo-lista">{visibles.map(a => {
        const id = valor(a.id)
        const enUso = Number(a.relaciones_count || 0) > 0 || Number(a.ofertas_count || 0) > 0
        return <div key={id} className="catalogo-fila">
          {editando?.id === id ? <form className="catalogo-edicion" onSubmit={e => void guardar(e)}>
            <div className="catalogo-form-campos"><Campo etiqueta="Código" valorActual={editando.codigo} cambio={v => setEditando({ ...editando, codigo: v })} requerido /><Campo etiqueta="Nombre" valorActual={editando.nombre} cambio={v => setEditando({ ...editando, nombre: v })} requerido /></div>
            <div className="catalogo-acciones"><button className="boton primario" type="submit">Guardar cambios</button><button className="boton" type="button" onClick={() => setEditando(null)}>Cancelar</button></div>
          </form> : <>
            <div className="catalogo-identidad"><strong>{valor(a.nombre)}</strong><small>{valor(a.codigo)} · {enUso ? 'Vinculada a la malla o a cursos' : 'Aún sin vínculos'}</small></div>
            <Etiqueta estado={a.activa ? 'ACTIVO' : 'INACTIVO'} />
            {puedeEditar && <div className="catalogo-acciones"><button className="link-button" onClick={() => { setEditando({ id, codigo: valor(a.codigo), nombre: valor(a.nombre), activa: a.activa === true }); setEliminando(''); setCreando(false) }}>Editar</button><button className="link-button" onClick={() => void ejecutar('/asignaturas/' + id, 'PUT', { codigo: a.codigo, nombre: a.nombre, activa: !a.activa }, a.activa ? 'Asignatura desactivada.' : 'Asignatura reactivada.')}>{a.activa ? 'Desactivar' : 'Reactivar'}</button><button className="link-button" disabled={enUso} title={enUso ? 'Tiene vínculos: desactívala para conservar su historial' : 'Eliminar definitivamente'} onClick={() => setEliminando(id)}>Eliminar</button></div>}
          </>}
          {eliminando === id && <div className="catalogo-confirmacion" role="group" aria-label="Confirmar eliminación"><span>¿Eliminar definitivamente «{valor(a.nombre)}»? Esta acción no se puede deshacer.</span><div className="catalogo-acciones"><button className="boton primario" onClick={() => void eliminar(id)}>Sí, eliminar</button><button className="boton" onClick={() => setEliminando('')}>Cancelar</button></div></div>}
        </div>
      })}</div> : <Vacio texto="No hay asignaturas con estos filtros." />}
      {paginas > 1 && <nav className="catalogo-paginacion" aria-label="Páginas del catálogo"><span>Página {paginaActual} de {paginas}</span><div><button className="boton" disabled={paginaActual === 1} onClick={() => setPagina(paginaActual - 1)}>← Anterior</button><button className="boton" disabled={paginaActual === paginas} onClick={() => setPagina(paginaActual + 1)}>Siguiente →</button></div></nav>}
    </div>}
  </section>
}
