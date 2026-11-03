import { useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, fecha, nombre, type Fila, valor } from '../../../shared/api/client'
import { Aviso, Campo, Etiqueta, opciones, Pagina, Panel, Selector, useCatalogos, useDatos, Vacio } from '../../../shared/ui/index'

export function FichaMatricula() {
  const { id = '' } = useParams()
  const { datos, error, cargar } = useDatos<Fila>('/matriculas/' + id, {})
  const { datos: cat } = useCatalogos()
  const [grado, setGrado] = useState('')
  const [seccion, setSeccion] = useState('')
  const [financiero, setFinanciero] = useState('')
  const [tipoFin, setTipoFin] = useState('DNI')
  const [docFin, setDocFin] = useState('')
  const [personasFin, setPersonasFin] = useState<Fila[]>([])
  const [detalle, setDetalle] = useState('')
  const [editar, setEditar] = useState(false)
  const [confirmar, setConfirmar] = useState(false)
  const [cancelar, setCancelar] = useState(false)
  const [medioPago, setMedioPago] = useState('')
  const [montoPago, setMontoPago] = useState('')
  const [mostrarPago, setMostrarPago] = useState(false)
  const [mensaje, setMensaje] = useState('')
  const [exito, setExito] = useState('')
  function comenzar() {
    setGrado(valor(datos.grado_id)); setSeccion(valor(datos.seccion_id)); setFinanciero(valor(datos.responsable_financiero_id))
    const procedencia = datos.procedencia_detalle
    setDetalle(typeof procedencia === 'string' ? procedencia : procedencia && typeof procedencia === 'object' ? valor((procedencia as Fila).detalle) : '')
    setEditar(true)
  }
  async function buscarFin() { try { setPersonasFin(await api<Fila[]>('/personas?tipo=' + encodeURIComponent(tipoFin) + '&documento=' + encodeURIComponent(docFin))) } catch (e) { setMensaje((e as Error).message) } }
  async function guardar(e: FormEvent) { e.preventDefault(); setMensaje(''); try {
    await api('/matriculas/' + id, 'PUT', { gradoId: grado || null, seccionId: seccion || null, responsableFinancieroId: financiero || null, procedenciaDetalle: detalle || null })
    setEditar(false); setExito('Datos de matrícula actualizados.'); await cargar()
  } catch (err) { setMensaje((err as Error).message) } }
  async function accion(tipo: 'reservar' | 'confirmar' | 'cancelar') { setMensaje(''); try {
    await api('/matriculas/' + id + '/' + tipo, 'POST', tipo === 'confirmar' ? { confirmacionExplicita: true } : {})
    setConfirmar(false); setCancelar(false)
    setExito(tipo === 'confirmar' ? 'Matrícula confirmada.' : tipo === 'cancelar' ? 'Proceso de matrícula cancelado.' : 'Vacante reservada.'); await cargar()
  } catch (err) { setMensaje((err as Error).message) } }
  async function registrarPago(e: FormEvent) { e.preventDefault(); setMensaje(''); try {
    await api('/matriculas/' + id + '/pago-externo', 'POST', { medio: medioPago, monto: montoPago })
    setMostrarPago(false); setExito('Pago externo registrado.'); await cargar()
  } catch (err) { setMensaje((err as Error).message) } }
  const responsables = (datos.apoderados || []) as Fila[]
  const historial = (datos.historial || []) as Fila[]
  const secciones = cat.secciones.filter(s => valor(s.periodo_id) === valor(datos.periodo_id) && valor(s.grado_id) === grado && s.habilitada)
  const estado = valor(datos.estado)
  const resumen = useMemo(() => [nombre(datos), valor(datos.codigo), valor(datos.periodo), valor(datos.grado) || 'Grado pendiente', valor(datos.seccion) || 'Sin sección'].filter(Boolean).join(' · '), [datos])
  return <Pagina titulo={nombre(datos) || 'Matrícula'} intro={'Periodo ' + valor(datos.periodo) + ' · ' + valor(datos.procedencia)} accion={<Link className="boton" to="/matriculas">← Matrículas</Link>}>
    <Aviso error={error || mensaje} exito={exito} />
    <div className="indicadores compactos"><div><small>ESTADO</small><Etiqueta estado={datos.estado} /></div><div><small>GRADO</small><strong className="mediano">{valor(datos.grado) || 'Pendiente'}</strong></div><div><small>SECCIÓN</small><strong className="mediano">{valor(datos.seccion) || 'Pendiente'}</strong></div></div>
    <div className="dos-columnas"><Panel titulo="Datos académicos" accion={estado !== 'CANCELADA' ? <button className="boton" onClick={comenzar}>Editar</button> : undefined}>
      {editar ? <form onSubmit={guardar} className="formulario"><div className="campos-2">
        <Selector etiqueta="Grado" valorActual={grado} cambio={v => { setGrado(v); setSeccion('') }} opciones={opciones(cat.grados.filter(g => g.habilitado), g => valor(g.nivel) + ' · ' + valor(g.nombre))} vacio="Sin grado" />
        <Selector etiqueta="Sección" valorActual={seccion} cambio={setSeccion} opciones={opciones(secciones, s => valor(s.grado) + ' · ' + valor(s.nombre))} vacio="Asignar después" />
      </div><div className="separador" /><div className="campos-2"><Selector etiqueta="Tipo de documento financiero" valorActual={tipoFin} cambio={setTipoFin} opciones={['DNI','CE','PASAPORTE'].map(x => ({ valor:x,texto:x }))} /><Campo etiqueta="Documento financiero" valorActual={docFin} cambio={setDocFin} /></div><button type="button" className="boton" onClick={() => void buscarFin()}>Buscar persona</button>
      {personasFin.length > 0 && <Selector etiqueta="Responsable financiero" valorActual={financiero} cambio={setFinanciero} opciones={opciones(personasFin, p => nombre(p) + ' · ' + valor(p.documento))} vacio="Sin designar" />}
      <Campo etiqueta="Antecedentes" valorActual={detalle} cambio={setDetalle} />
      <div className="acciones"><button className="boton primario">Guardar cambios</button><button type="button" className="boton" onClick={() => setEditar(false)}>Cancelar</button></div></form> :
      <dl className="ficha"><div><dt>Estudiante</dt><dd>{valor(datos.codigo) ? <Link to={'/estudiantes/' + datos.estudiante_id}>{resumen}</Link> : resumen + ' · Código por asignar'}</dd></div><div><dt>Confirmada por</dt><dd>{valor(datos.confirmado_por) || 'Aún no confirmada'}</dd></div><div><dt>Responsable financiero</dt><dd>{valor(datos.responsable_financiero_id) ? 'Registrado' : 'Sin designar'}</dd></div></dl>}
    </Panel><Panel titulo="Responsables y contactos" accion={<Link className="boton" to={'/apoderados?estudiante=' + datos.estudiante_id}>Gestionar →</Link>}>
      {responsables.length ? responsables.map(r => <div className="item" key={valor(r.persona_id)}><strong>{nombre(r)}</strong><small>{valor(r.rol)} · {valor(r.vinculo)} · {valor(r.tipo_documento)} {valor(r.documento)}</small></div>) : <Vacio texto="Se requiere un responsable identificado antes de confirmar." />}
    </Panel></div>
    {(estado === 'PROCESO_PENDIENTE' || estado === 'VACANTE_RESERVADA') && <Panel titulo="Estado del proceso" subtitulo="Solo Dirección y Coordinación Académica pueden confirmar. La sección es opcional hasta su asignación.">
      <div className="acciones">{estado === 'PROCESO_PENDIENTE' && <button className="boton" onClick={() => void accion('reservar')}>Reservar vacante</button>}<button className="boton primario" onClick={() => setConfirmar(true)}>Revisar y confirmar</button><button className="boton peligro" onClick={() => setCancelar(true)}>Cancelar matrícula</button></div>
    </Panel>}
    {estado === 'CANCELADA' && <Panel titulo="Proceso cancelado" subtitulo="Esta matrícula conserva su historial y ya no puede editarse ni confirmarse."><Link className="boton" to={valor(datos.codigo) ? '/matriculas/nueva?estudiante=' + datos.estudiante_id : '/matriculas/nueva'}>Iniciar nueva matrícula →</Link></Panel>}
    {estado === 'MATRICULADO' && <Panel titulo="Pago de matrícula" subtitulo="El pago se realiza fuera del ERP; aquí solo queda constancia de lo recibido." accion={valor(datos.pago_estado) === 'PENDIENTE' && !mostrarPago ? <button className="boton primario" onClick={() => setMostrarPago(true)}>Registrar pago externo</button> : undefined}>
      {valor(datos.pago_estado) === 'REGISTRADO' ? <dl className="ficha"><div><dt>Estado</dt><dd>Pago registrado</dd></div><div><dt>Medio</dt><dd>{valor(datos.pago_medio)}</dd></div><div><dt>Monto</dt><dd>S/ {Number(datos.pago_monto).toFixed(2)}</dd></div><div><dt>Registrado por</dt><dd>{valor(datos.pago_registrado_por)}</dd></div></dl> : mostrarPago ? <form className="formulario" onSubmit={registrarPago}><div className="campos-2"><Selector etiqueta="Medio de pago" valorActual={medioPago} cambio={setMedioPago} opciones={['EFECTIVO','TRANSFERENCIA','YAPE','PLIN','TARJETA','OTRO'].map(x => ({ valor: x, texto: x }))} requerido /><Campo etiqueta="Monto recibido (S/)" tipo="number" valorActual={montoPago} cambio={setMontoPago} requerido /></div><div className="acciones"><button className="boton primario">Confirmar pago recibido</button><button type="button" className="boton" onClick={() => setMostrarPago(false)}>Volver</button></div></form> : <p>Pago pendiente de registro. La matrícula académica ya está confirmada.</p>}
    </Panel>}
    <Panel titulo="Historial de operaciones" subtitulo="Responsable y momento de cada cambio de matrícula.">{historial.length ? historial.map((h, i) => <div className="item" key={i}><strong>{valor(h.accion).replaceAll('_',' ')}</strong><small>{valor(h.actor)} · {fecha(h.instante)}</small></div>) : <Vacio texto="Sin operaciones registradas." />}</Panel>
    {confirmar && <div className="modal-fondo" role="dialog" aria-modal="true" aria-label="Confirmar matrícula"><div className="modal"><p className="eyebrow">CONFIRMACIÓN INSTITUCIONAL</p><h2>Confirmar matrícula</h2><p>Verifica estos datos antes de continuar:</p><div className="resumen-confirmacion">{resumen}<br />{responsables.length} responsable(s) vinculado(s)</div><p>Esta acción registrará al estudiante como matriculado. Si tiene sección, se sincronizarán sus cursos con Aula.</p><div className="acciones"><button className="boton primario" onClick={() => void accion('confirmar')}>Sí, confirmar matrícula</button><button className="boton" onClick={() => setConfirmar(false)}>Volver</button></div></div></div>}
    {cancelar && <div className="modal-fondo" role="dialog" aria-modal="true" aria-label="Cancelar matrícula"><div className="modal"><p className="eyebrow">CANCELACIÓN DEL PROCESO</p><h2>Cancelar matrícula</h2><p>Se cancelará el proceso de {resumen}. Se conservará el historial y podrás iniciar otra matrícula para este estudiante y periodo.</p><div className="acciones"><button className="boton peligro" onClick={() => void accion('cancelar')}>Sí, cancelar matrícula</button><button className="boton" onClick={() => setCancelar(false)}>Volver</button></div></div></div>}
  </Pagina>
}
