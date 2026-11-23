import {useMemo} from 'react';
import {coincideBusqueda} from '../components/BusquedaConSugerencias';
import {Link} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {api,type Clase,type Grabacion,type Perfil} from '../api';
import {PageHeading} from '../PageHeading';
import {fecha,fechaAgenda,local,unico,errorDe,useFiltros,Filtros,Paginacion,paginaFilas} from './ClasesPresentacion';
import {RegistrarVideo,Video} from './GrabacionesVideo';

function GrabacionesPortada({cantidad,descripcion,etiqueta}:{cantidad:number;descripcion:string;etiqueta:string}){
  const anio=new Intl.DateTimeFormat('en-US',{timeZone:'America/Lima',year:'numeric'}).format(new Date());
  return <header className="cursos-portada grabaciones-portada"><div className="cursos-portada-principal"><span className="cursos-sobretitulo">AULA VIRTUAL — {anio}</span><h1>GRABACIONES<i>.</i></h1><p>{descripcion}</p></div><div className="cursos-portada-lateral"><span>ARCHIVO DE CLASES</span><strong>{String(cantidad).padStart(2,'0')}</strong><small>{etiqueta}</small></div></header>
}

function GrabacionTarjeta({partes,editable,verClase=false}:{partes:Grabacion[];editable:boolean;verClase?:boolean}){
  const g=partes[0],calendario=fechaAgenda(g.inicio);
  return <article className="grabaciones-tarjeta"><div className="grabaciones-tarjeta-cabecera"><time className="clases-tarjeta-fecha" dateTime={g.inicio}><strong>{calendario.dia}</strong><span>{calendario.mes}</span></time><div className="grabaciones-tarjeta-info"><span className="clases-tarjeta-curso">{g.asignatura} · {g.grado} {g.seccion}</span><h3>{g.tema}</h3><p>{fecha(g.inicio)} · Docente: {g.docente}</p></div><div className="grabaciones-tarjeta-acciones"><span>{partes.length} {partes.length===1?'video':'videos'}</span>{verClase&&<Link className="clases-detalle-link" to={`/clases/${g.clase_id}?return=${encodeURIComponent('/grabaciones'+window.location.search)}`}>Ver clase <span aria-hidden="true">→</span></Link>}</div></div><div className="grabaciones-partes">{partes.map(parte=><Video key={parte.id} g={parte} editable={editable}/>)}</div></article>
}

export function GrabacionesListado({perfil}:{perfil:Perfil}){
  const {params,cambiar,limpiar}=useFiltros();const {data:grabaciones=[],isLoading,error}=useQuery<Grabacion[]>({queryKey:['grabaciones-global'],queryFn:()=>api('/grabaciones'),refetchInterval:30000});
  const {data:clases=[]}=useQuery<Clase[]>({queryKey:['clases-global'],queryFn:()=>api('/clases'),enabled:perfil.tipo==='DOCENTE'});
  const grupos=useMemo(()=>{const m=new Map<string,Grabacion[]>();for(const g of grabaciones){if((params.get('q')&&!coincideBusqueda(`${g.tema} ${g.titulo}`,params.get('q')!))||(params.get('asignatura')&&g.asignatura!==params.get('asignatura'))||(params.get('grado')&&g.grado!==params.get('grado'))||(params.get('seccion')&&g.seccion!==params.get('seccion'))||(params.get('fecha')&&local(g.inicio).slice(0,10)!==params.get('fecha')))continue;const a=m.get(g.clase_id)||[];a.push(g);m.set(g.clase_id,a)}return [...m.values()]},[grabaciones,params]);
  return <main className="grabaciones-editorial"><PageHeading title="Grabaciones" metricLabel="ARCHIVO DE CLASES" metricValue={String(new Set(grabaciones.map(g=>g.clase_id)).size).padStart(2,'0')} metricCaption="CLASES CON GRABACIÓN"/><GrabacionesPortada cantidad={new Set(grabaciones.map(g=>g.clase_id)).size} descripcion={perfil.tipo==='DOCENTE'?'Registra y administra los videos de tus clases.':'Consulta las grabaciones disponibles de tus clases.'} etiqueta="CLASES CON GRABACIÓN"/>
    {perfil.tipo==='DOCENTE'&&<section className="grabaciones-registro" aria-label="Registrar grabación"><RegistrarVideo clases={clases}/></section>}
    <section className="grabaciones-busqueda" aria-labelledby="grabaciones-busqueda-titulo"><h2 id="grabaciones-busqueda-titulo">BUSCAR GRABACIONES</h2><Filtros params={params} cambiar={cambiar} limpiar={limpiar} asignaturas={unico(grabaciones.map(g=>g.asignatura))} grados={unico(grabaciones.map(g=>g.grado))} secciones={unico(grabaciones.map(g=>g.seccion))} sugerencias={grabaciones.flatMap(g=>[g.tema,g.titulo])} conTitulo/></section>
    <section className="grabaciones-resultados" aria-labelledby="grabaciones-resultados-titulo"><div className="clases-agenda-encabezado"><h2 id="grabaciones-resultados-titulo">CLASES GRABADAS</h2><span>{grupos.length} {grupos.length===1?'resultado':'resultados'}</span></div>
    {isLoading?<div className="grabaciones-sin-resultados">Cargando grabaciones…</div>:error?<div className="alert">{errorDe(error)}</div>:!grupos.length?<div className="grabaciones-sin-resultados"><span aria-hidden="true">▷</span><p>No hay grabaciones para estos filtros.</p></div>:<div className="grabaciones-lista">{paginaFilas(grupos,params).map(partes=><GrabacionTarjeta key={partes[0].clase_id} partes={partes} editable={perfil.tipo==='DOCENTE'} verClase/>)}</div>}
    </section>
    <Paginacion total={grupos.length} params={params} cambiar={cambiar}/>
  </main>
}

export function AdminGrabaciones(){
  const {data:grabaciones=[],isLoading,error}=useQuery<Grabacion[]>({queryKey:['grabaciones-admin'],queryFn:()=>api('/grabaciones/administracion')});
  const grupos=useMemo(()=>{const m=new Map<string,Grabacion[]>();for(const g of grabaciones){const a=m.get(g.clase_id)||[];a.push(g);m.set(g.clase_id,a)}return [...m.values()]},[grabaciones]);
  return <main className="grabaciones-editorial"><PageHeading title="Grabaciones" metricLabel="ARCHIVO DE CLASES" metricValue={String(grabaciones.length).padStart(2,'0')} metricCaption="VIDEOS REGISTRADOS"/><GrabacionesPortada cantidad={grabaciones.length} descripcion="Edita, publica u oculta las referencias de YouTube registradas." etiqueta="VIDEOS REGISTRADOS"/><section className="grabaciones-resultados" aria-labelledby="grabaciones-admin-titulo"><div className="clases-agenda-encabezado"><h2 id="grabaciones-admin-titulo">REFERENCIAS REGISTRADAS</h2><span>{grupos.length} {grupos.length===1?'clase':'clases'}</span></div>{isLoading?<div className="grabaciones-sin-resultados">Cargando grabaciones…</div>:error?<div className="alert">{errorDe(error)}</div>:!grupos.length?<div className="grabaciones-sin-resultados"><span aria-hidden="true">▷</span><p>No hay referencias de YouTube registradas.</p></div>:<div className="grabaciones-lista">{grupos.map(partes=><GrabacionTarjeta key={partes[0].clase_id} partes={partes} editable/>)}</div>}</section></main>
}
