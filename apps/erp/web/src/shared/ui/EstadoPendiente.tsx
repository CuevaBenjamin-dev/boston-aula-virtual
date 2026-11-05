import { Link } from 'react-router-dom'
import type { Modulo } from '../lib/modulos'

export function EstadoPendiente({ modulo }: { modulo: Modulo }) {
  return <section className="estado-pendiente" aria-labelledby="estado-titulo">
    <p className="etiqueta">MÓDULO {modulo.nombre.toUpperCase()}</p>
    <h2 id="estado-titulo">Pendiente de implementación<span className="punto">.</span></h2>
    <p>Este módulo se habilitará cuando sus requisitos y reglas institucionales estén aprobados.</p>
    <Link to="/" className="enlace-volver">← Ver todos los módulos</Link>
  </section>
}
