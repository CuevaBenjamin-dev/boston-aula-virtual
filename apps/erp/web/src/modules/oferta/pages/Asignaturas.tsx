import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Etiqueta, opciones, Pagina, Panel, Selector, useCatalogos, useDatos, Vacio } from '../../../shared/ui/index'
import { CatalogoAsignaturas } from '../components/CatalogoAsignaturas'

export function Asignaturas({ puedeEditar }: { puedeEditar: boolean }) {
  const { datos: cat, error: errorCat, cargar } = useCatalogos()
  const [grado, setGrado] = useState('')
  const [periodo, setPeriodo] = useState('')
  const { datos: relaciones, cargar: cargarRelaciones } = useDatos<Fila[]>(grado ? '/asignaturas/grado/' + grado + (periodo ? '?periodo=' + periodo : '') : '', [])
  const [asignatura, setAsignatura] = useState('')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  async function ejecutar(ruta: string, metodo: string, datos: unknown, texto: string): Promise<boolean> { setError(''); setExito(''); try {
    await api(ruta, metodo, datos); await cargar(); await cargarRelaciones(); setExito(texto); return true
  } catch (e) { setError((e as Error).message); return false } }
  return <Pagina titulo="Malla curricular" intro="Cada grado tiene asignaturas con código propio; sus secciones comparten esa malla." accion={<Link className="boton" to="/oferta">← Oferta académica</Link>}>
    <Aviso error={error || errorCat} exito={exito} />
    <div className="asignaturas-grid"><Panel titulo="Niveles y grados" subtitulo="Selecciona el grado para organizar sus asignaturas.">
      <Selector etiqueta="Periodo (opcional)" valorActual={periodo} cambio={setPeriodo} opciones={opciones(cat.periodos, p => valor(p.nombre))} vacio="Relaciones generales" />
      {cat.niveles.map(n => <div className="grupo" key={valor(n.id)}><h3>{valor(n.nombre)}</h3>{cat.grados.filter(g => g.nivel_id === n.id).map(g => <button key={valor(g.id)} className={'grado-opcion ' + (grado === g.id ? 'seleccionado' : '')} onClick={() => setGrado(valor(g.id))}>{valor(g.nombre)} <span>→</span></button>)}</div>)}
    </Panel><Panel titulo={grado ? 'Asignaturas del grado' : 'Selecciona un grado'} subtitulo="La malla del grado es compartida por sus secciones.">
      {grado ? <><p className="malla-ayuda">Se muestran por posición de lectura. «Malla base» se aplica en todos los periodos; una relación de periodo específico se aplica solo en ese año. La posición no define horario ni importancia académica.</p>{relaciones.length ? relaciones.map((r, indice) => <div className="item item-linea" key={valor(r.id)}><div><strong>{valor(r.nombre)}</strong><small>{valor(r.codigo)} · {r.periodo_id ? 'Solo periodo ' + valor(cat.periodos.find(p => p.id === r.periodo_id)?.nombre) : 'Malla base · todos los periodos'}</small></div><span className="malla-posicion" title="Posición de lectura en esta lista">{indice + 1}.</span><Etiqueta estado={r.activa ? 'ACTIVO' : 'INACTIVO'} />{puedeEditar && <><button className="link-button" onClick={() => { const n = prompt('Posición de lectura (número entero, sin relación con horarios)', valor(r.orden)); if (n !== null && /^\d+$/.test(n.trim())) void ejecutar('/asignaturas/grados/' + r.id,'PUT',{ orden: Number(n), activa: r.activa },'Posición actualizada.') }}>Cambiar posición</button><button className="link-button" onClick={() => void ejecutar('/asignaturas/grados/' + r.id,'PUT',{ orden: r.orden, activa: !r.activa },'Relación actualizada.')}>{r.activa ? 'Retirar' : 'Reactivar'}</button></>}</div>) : <Vacio texto="Este grado no tiene asignaturas relacionadas." />}
        {puedeEditar && <form className="formulario" onSubmit={e => { e.preventDefault(); void ejecutar('/asignaturas/grados','POST',{ asignaturaId: asignatura, gradoId: grado, periodoId: periodo || null, orden: Math.max(0, ...relaciones.map(r => Number(r.orden) || 0)) + 1, activa: true },'Asignatura añadida al grado.'); setAsignatura('') }}><h3>Añadir al grado</h3><div className="form-linea"><Selector etiqueta="Asignatura" valorActual={asignatura} cambio={setAsignatura} opciones={opciones(cat.asignaturas.filter(a => a.activa && !relaciones.some(r => r.asignatura_id === a.id && (r.periodo_id || '') === (periodo || ''))), a => valor(a.codigo) + ' · ' + valor(a.nombre))} requerido /><button className="boton primario boton-alineado">Añadir al final</button></div><p className="malla-ayuda">El sistema asigna automáticamente la siguiente posición de lectura.</p></form>}</> : <Vacio texto="Selecciona un nivel y grado a la izquierda." />}
    </Panel></div>
    <CatalogoAsignaturas asignaturas={cat.asignaturas} puedeEditar={puedeEditar} ejecutar={ejecutar} />
  </Pagina>
}
