import {useState} from 'react';
import {Link,useSearchParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,post,put,subirArchivo,type Curso,type Material,type Perfil} from '../api';
import {PageHeading} from '../PageHeading';
import {ArchivosDe,archivoPermitido} from './AdjuntosAcademicos';

const meses=['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
function gruposMateriales(materiales:Material[]){
  const grupos=new Map<string,{mes:number|null;semana:number|null;materiales:Material[]}>();
  for(const material of materiales){
    const clave=`${material.mes||0}-${material.semana||0}`;
    if(!grupos.has(clave))grupos.set(clave,{mes:material.mes,semana:material.semana,materiales:[]});
    grupos.get(clave)!.materiales.push(material);
  }
  return [...grupos.values()].sort((a,b)=>(b.mes||0)-(a.mes||0)||(b.semana||0)-(a.semana||0));
}
function tituloGrupo(mes:number|null,semana:number|null){return mes?`${meses[mes-1]}${semana?` · Semana ${semana}`:''}`:'Sin mes ni semana asignados'}
export function MaterialesCurso({curso,perfil}:{curso:string;perfil:Perfil}){
  const qc=useQueryClient(),[nuevo,setNuevo]=useState(false);
  const {data:materiales=[],error}=useQuery<Material[]>({queryKey:['materiales',curso],queryFn:()=>api(`/cursos/${curso}/materiales`)});
  const ocultar=useMutation({mutationFn:(id:string)=>post(`/materiales/${id}/ocultar`,{}),onSuccess:()=>qc.invalidateQueries({queryKey:['materiales']})});
  return <section className="section curso-resumen-seccion" id="curso-materiales"><div className="curso-seccion-cabecera"><div className="curso-seccion-titulos"><span className="curso-seccion-numero">02 / RECURSOS</span><h2>Materiales del curso</h2><p>Archivos y enlaces compartidos para esta sección.</p></div>{perfil.tipo==='DOCENTE'&&<button className="button secondary" onClick={()=>setNuevo(!nuevo)}>+ Añadir material</button>}</div>
    {nuevo&&<FormularioMaterial curso={curso} cerrar={()=>setNuevo(false)}/>}
    {error?<div className="alert">No se pudieron consultar los materiales. {String(error)}</div>:!materiales.length?<div className="empty">Todavía no hay materiales.</div>:<div className="curso-materiales-lista">{gruposMateriales(materiales).map(grupo=><div className="materiales-grupo" key={`${grupo.mes}-${grupo.semana}`}><h3>{tituloGrupo(grupo.mes,grupo.semana)}</h3>{grupo.materiales.map(m=><MaterialFila key={m.id} material={m} curso={curso} perfil={perfil} ocultar={()=>ocultar.mutate(m.id)}/>)}</div>)}</div>}
    {ocultar.error&&<p className="error">{String(ocultar.error)}</p>}
    <Link className="curso-seccion-pie" to={`/materiales?curso=${encodeURIComponent(curso)}`}>Ver todos los materiales <span aria-hidden="true">→</span></Link>
  </section>;
}

function MaterialFila({material,curso,perfil,ocultar,editable=true}:{material:Material;curso:string;perfil:Perfil;ocultar?:()=>void;editable?:boolean}){
  const [editar,setEditar]=useState(false);
  return <article className="panel curso-material"><div className="curso-material-cabecera"><div><h3>{material.nombre}</h3><p>{material.estado==='BORRADOR'?'Falta completar':material.estado==='OCULTO'?'Oculto':'Publicado'} · {material.total_archivos||0} {material.total_archivos===1?'archivo':'archivos'}</p></div>{editable&&perfil.tipo==='DOCENTE'&&<div className="row-actions"><button className="button secondary" onClick={()=>setEditar(!editar)}>Editar</button>{material.estado==='PUBLICADO'&&<button className="button secondary" onClick={ocultar}>Ocultar</button>}</div>}</div>
    {material.descripcion&&<p className="prewrap curso-material-descripcion">{material.descripcion}</p>}
    <div className="curso-material-recursos">{material.enlace&&<a className="text-link" href={material.enlace} target="_blank" rel="noopener noreferrer">Abrir enlace ↗</a>}<ArchivosDe padre="materiales" id={material.id} editable={editable&&perfil.tipo==='DOCENTE'}/></div>
    {editar&&<FormularioMaterial curso={curso} material={material} cerrar={()=>setEditar(false)}/>}
  </article>;
}

function FormularioMaterial({curso,material,cerrar}:{curso:string;material?:Material;cerrar:()=>void}){
  const qc=useQueryClient(),[nombre,setNombre]=useState(material?.nombre||''),[descripcion,setDescripcion]=useState(material?.descripcion||''),[enlace,setEnlace]=useState(material?.enlace||''),[mes,setMes]=useState(material?.mes?String(material.mes):''),[semana,setSemana]=useState(material?.semana?String(material.semana):''),[archivos,setArchivos]=useState<File[]>([]),[progreso,setProgreso]=useState(0);
  const guardar=useMutation({mutationFn:async(publicar:boolean)=>{
    if(!mes||!semana)throw new Error('Selecciona el mes y la semana del material.');
    const datos={nombre,descripcion,enlace,mes:Number(mes),semana:Number(semana),...(material?{}:{cursoIds:[curso]})};
    const id=material?.id||(await post<{id:string}>('/materiales',datos)).id;
    if(material)await put(`/materiales/${id}`,datos);
    for(const archivo of archivos)await subirArchivo('materiales',id,archivo,setProgreso);
    if(publicar)await post(`/materiales/${id}/publicar`,{});
    return id;
  },onSuccess:()=>{qc.invalidateQueries({queryKey:['materiales']});qc.invalidateQueries({queryKey:['archivos','materiales']});cerrar()}});
  return <form className="form compact" onSubmit={e=>{e.preventDefault();guardar.mutate(true)}}><h3>{material?'Editar material':'Nuevo material'}</h3><label>Nombre<input value={nombre} maxLength={200} onChange={e=>setNombre(e.target.value)} required/></label><div className="materiales-periodo-campos"><label>Mes<select value={mes} onChange={e=>setMes(e.target.value)} required><option value="">Seleccionar mes</option>{meses.map((m,i)=><option value={i+1} key={m}>{m}</option>)}</select></label><label>Semana<select value={semana} onChange={e=>setSemana(e.target.value)} required><option value="">Seleccionar semana</option>{[1,2,3,4,5].map(n=><option value={n} key={n}>Semana {n}</option>)}</select></label></div><label>Descripción<textarea value={descripcion} rows={3} onChange={e=>setDescripcion(e.target.value)}/></label><label>Enlace (opcional)<input type="url" value={enlace} onChange={e=>setEnlace(e.target.value)} placeholder="https://..."/></label>
    <label>Adjuntar archivos<input type="file" multiple accept={archivoPermitido} onChange={e=>setArchivos(Array.from(e.target.files||[]))}/></label>{archivos.length>0&&<small>{archivos.map(a=>a.name).join(', ')}</small>}{progreso>0&&<progress value={progreso} max="100"/>}{guardar.error&&<p className="error">{String(guardar.error)}</p>}
    <div className="row-actions"><button className="button primary" disabled={guardar.isPending}>Publicar</button><button type="button" className="button secondary" disabled={guardar.isPending} onClick={()=>guardar.mutate(false)}>Guardar como borrador</button><button type="button" className="text-link" onClick={cerrar}>Cancelar</button></div>
  </form>;
}

export function MaterialesPage({perfil}:{perfil:Perfil}){
  const [params,setParams]=useSearchParams();
  const {data:cursos=[],isLoading:cargandoCursos,error:errorCursos}=useQuery<Curso[]>({queryKey:['cursos'],queryFn:()=>api('/cursos')});
  const ids=cursos.map(c=>c.id).join(',');
  const {data:registros=[],isLoading:cargandoMateriales,error:errorMateriales}=useQuery<Array<{material:Material;cursos:Curso[]}>>({queryKey:['materiales','todos',ids],enabled:!!ids,queryFn:async()=>{
    const porCurso=await Promise.all(cursos.map(async curso=>({curso,materiales:await api<Material[]>(`/cursos/${curso.id}/materiales`)})));
    const unicos=new Map<string,{material:Material;cursos:Curso[]}>();
    for(const {curso,materiales} of porCurso)for(const material of materiales){
      if(!unicos.has(material.id))unicos.set(material.id,{material,cursos:[]});
      unicos.get(material.id)!.cursos.push(curso);
    }
    return [...unicos.values()];
  }});
  const cambiar=(clave:string,valor:string)=>{const siguientes=new URLSearchParams(params);if(valor)siguientes.set(clave,valor);else siguientes.delete(clave);setParams(siguientes)};
  const docentes=[...new Set(cursos.flatMap(c=>c.docentes?.split(', ').map(n=>n.trim()).filter(Boolean)||[]))].sort((a,b)=>a.localeCompare(b,'es'));
  const visibles=registros.filter(r=>(!params.get('curso')||r.cursos.some(c=>c.id===params.get('curso')))
    &&(!params.get('mes')||r.material.mes===Number(params.get('mes')))
    &&(!params.get('semana')||r.material.semana===Number(params.get('semana')))
    &&(!params.get('docente')||r.cursos.some(c=>c.docentes?.split(', ').includes(params.get('docente')!))));
  const porId=new Map(visibles.map(r=>[r.material.id,r]));
  return <main className="materiales-editorial"><PageHeading title="Mis materiales" metricLabel="MATERIALES DISPONIBLES" metricValue={String(visibles.length).padStart(2,'0')} metricCaption="EN ESTA VISTA"/>
    <header className="cursos-portada"><div className="cursos-portada-principal"><span className="cursos-sobretitulo">RECURSOS ACADÉMICOS</span><h1>Mis materiales<i>.</i></h1><p>Consulta los materiales de estudio de tus cursos.</p></div><div className="cursos-portada-lateral"><span>MATERIALES DISPONIBLES</span><strong>{String(visibles.length).padStart(2,'0')}</strong><small>EN ESTA VISTA</small></div></header>
    <section className="materiales-filtros" aria-label="Filtrar materiales"><h2>Buscar materiales</h2><div className="materiales-filtros-campos">
      <label>Curso<select value={params.get('curso')||''} onChange={e=>cambiar('curso',e.target.value)}><option value="">Todos los cursos</option>{cursos.map(c=><option key={c.id} value={c.id}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</option>)}</select></label>
      <label>Mes<select value={params.get('mes')||''} onChange={e=>cambiar('mes',e.target.value)}><option value="">Todos los meses</option>{meses.map((m,i)=><option key={m} value={i+1}>{m}</option>)}</select></label>
      <label>Semana<select value={params.get('semana')||''} onChange={e=>cambiar('semana',e.target.value)}><option value="">Todas las semanas</option>{[1,2,3,4,5].map(n=><option key={n} value={n}>Semana {n}</option>)}</select></label>
      <label>Docente<select value={params.get('docente')||''} onChange={e=>cambiar('docente',e.target.value)}><option value="">Todos los docentes</option>{docentes.map(n=><option key={n} value={n}>{n}</option>)}</select></label>
    </div><button type="button" className="button secondary" onClick={()=>setParams(new URLSearchParams())}>Limpiar filtros</button></section>
    {errorCursos||errorMateriales?<div className="alert">No se pudieron consultar los materiales. {String(errorCursos||errorMateriales)}</div>:cargandoCursos||cargandoMateriales?<div className="empty">Cargando materiales…</div>:!visibles.length?<div className="empty">No hay materiales para estos filtros.</div>:<section className="materiales-resultados" aria-label="Materiales encontrados">{gruposMateriales(visibles.map(r=>r.material)).map(grupo=><div className="materiales-grupo" key={`${grupo.mes}-${grupo.semana}`}><h2>{tituloGrupo(grupo.mes,grupo.semana)}</h2><div className="materiales-grupo-tarjetas">{grupo.materiales.map(material=>{const registro=porId.get(material.id)!;const curso=registro.cursos.find(c=>c.id===params.get('curso'))||registro.cursos[0];return <div key={material.id} className="materiales-resultado"><div className="materiales-resultado-contexto">{registro.cursos.map(c=><Link key={c.id} to={`/cursos/${c.id}?vista=materiales`}>{c.asignatura} · {c.nivel} {c.grado} {c.seccion}</Link>)}<span>DOCENTE: {curso.docentes||'Sin docente asignado'}</span></div><MaterialFila material={material} curso={curso.id} perfil={perfil} editable={false}/></div>})}</div></div>)}</section>}
  </main>;
}
