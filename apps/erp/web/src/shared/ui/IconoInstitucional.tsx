import type { ReactNode } from 'react'
import './iconos-institucionales.css'

const trazos = {
  inicio: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/></>,
  personas: <><circle cx="9" cy="7" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 4a3 3 0 0 1 0 6m5 10v-2a6 6 0 0 0-4-5.7"/></>,
  oferta: <><path d="M12 6C9 3 5 3 3 4v15c3-1 6-1 9 2 3-3 6-3 9-2V4c-2-1-6-1-9 2v15"/></>,
  matriculas: <><rect x="5" y="5" width="14" height="16" rx="2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="m8 12 2 2 4-4m-6 7h8"/></>,
  asignaciones: <><path d="m2 9 10-5 10 5-10 5-10-5Zm4 2v6c3 3 9 3 12 0v-6m4-2v7"/></>,
  asistencia: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 10h18m-13 5 3 3 5-5"/></>,
  documentos: <><path d="M6 3h8l4 4v14H6zM14 3v5h4M9 12h6m-6 4h6"/></>,
  atencion: <><path d="M4 13v-1a8 8 0 0 1 16 0v1m-1 6c0 2-2 3-6 3"/><rect x="3" y="12" width="4" height="6" rx="1"/><rect x="17" y="12" width="4" height="6" rx="1"/></>,
  integracion: <><path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5"/></>,
  identidad: <><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></>,
  aula: <><rect x="3" y="4" width="18" height="13" rx="1.5"/><path d="m8 21 4-4 4 4"/></>,
  confirmado: <><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></>,
  pendiente: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  alerta: <><circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/></>,
  informacion: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/></>,
} satisfies Record<string, ReactNode>

export function IconoInstitucional({ nombre }: { nombre: keyof typeof trazos }) {
  return <svg className="bbs-icono-ui" width="24" height="24" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true" focusable="false">{trazos[nombre]}</svg>
}
