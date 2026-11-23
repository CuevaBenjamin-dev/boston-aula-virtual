import {Link} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,post,type NotificacionClase} from '../api';
import {fecha,errorDe} from './ClasesPresentacion';

export function Notificaciones(){
  const qc=useQueryClient();const {data:avisos=[],isLoading,error}=useQuery<NotificacionClase[]>({queryKey:['notificaciones'],queryFn:()=>api('/notificaciones'),refetchInterval:30000});
  const leer=useMutation({mutationFn:(id:string)=>post(`/notificaciones/${id}/leer`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['notificaciones']})});
  return <><div className="page-head"><div><span className="eyebrow">AULA VIRTUAL</span><h1>Notificaciones</h1><p>Avisos de tus clases virtuales.</p></div></div>{isLoading?<p className="muted">Cargando avisos…</p>:error?<div className="alert">{errorDe(error)}</div>:!avisos.length?<div className="empty">No tienes notificaciones.</div>:<div className="list">{avisos.map(n=><div className={'list-row stack-mobile '+(!n.leida_en?'unread':'')} key={n.id}><div><strong>{n.mensaje}</strong><span>{n.asignatura} · {n.grado} {n.seccion} · {fecha(n.creada_en)}</span></div><div className="row-actions"><Link className="button secondary" to={`/clases/${n.clase_id}?return=${encodeURIComponent('/notificaciones')}`}>Ver clase</Link>{!n.leida_en&&<button className="button secondary" disabled={leer.isPending} onClick={()=>leer.mutate(n.id)}>Marcar como leída</button>}</div></div>)}</div>}{leer.error&&<p className="error">{errorDe(leer.error)}</p>}</>
}
