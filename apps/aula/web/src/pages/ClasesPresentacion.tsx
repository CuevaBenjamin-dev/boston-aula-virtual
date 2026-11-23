import {useSearchParams} from 'react-router-dom';
import {BusquedaConSugerencias} from '../components/BusquedaConSugerencias';

export const fecha=(s:string)=>new Date(s).toLocaleString('es-PE',{dateStyle:'medium',timeStyle:'short',timeZone:'America/Lima'});
export const hora=(s:string)=>new Date(s).toLocaleTimeString('es-PE',{timeStyle:'short',timeZone:'America/Lima'});
export const horario=(inicio:string,fin:string)=>`${new Date(inicio).toLocaleDateString('es-PE',{dateStyle:'medium',timeZone:'America/Lima'})} · ${new Date(inicio).toLocaleTimeString('es-PE',{timeStyle:'short',timeZone:'America/Lima'})} a ${new Date(fin).toLocaleTimeString('es-PE',{timeStyle:'short',timeZone:'America/Lima'})}`;
export const fechaAgenda=(s:string)=>{const d=new Date(s);return {dia:new Intl.DateTimeFormat('es-PE',{timeZone:'America/Lima',day:'2-digit'}).format(d),mes:new Intl.DateTimeFormat('es-PE',{timeZone:'America/Lima',month:'short'}).format(d).replace('.','').toUpperCase()}};
export const local=(s:string)=>new Intl.DateTimeFormat('sv-SE',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(s)).replace(' ','T');
export const aIsoLima=(s:string)=>new Date(`${s}:00-05:00`).toISOString();
export const paginaTamano=10;
export const unico=(xs:string[])=>[...new Set(xs)].sort((a,b)=>a.localeCompare(b,'es'));
export const errorDe=(error:unknown)=>error instanceof Error?error.message:String(error);

export function normalizarYoutube(valor:string):string|null {
  try {
    const u=new URL(valor.trim());
    if(!['https:','http:'].includes(u.protocol)||u.username||u.password||!u.hostname||valor.length>2048)return null;
    return valor.trim();
  } catch {return null}
}

export function useFiltros(){
  const [params,setParams]=useSearchParams();
  function cambiar(clave:string,valor:string){const p=new URLSearchParams(params);if(valor)p.set(clave,valor);else p.delete(clave);if(clave!=='p')p.delete('p');setParams(p)}
  return {params,cambiar,limpiar:()=>setParams(new URLSearchParams())};
}
export function Filtros({params,cambiar,limpiar,asignaturas,grados,secciones,estados,sugerencias=[],conTitulo=false}:{params:URLSearchParams;cambiar:(k:string,v:string)=>void;limpiar:()=>void;asignaturas:string[];grados:string[];secciones:string[];estados?:string[];sugerencias?:string[];conTitulo?:boolean}){
  return <div className="panel form filters"><div className="filter-grid">
    <BusquedaConSugerencias etiqueta={conTitulo?'Buscar título':'Buscar tema'} valor={params.get('q')||''} alCambiar={v=>cambiar('q',v)} candidatos={sugerencias} placeholder="Escribe para buscar"/>
    <label>Curso<select value={params.get('asignatura')||''} onChange={e=>cambiar('asignatura',e.target.value)}><option value="">Todos</option>{asignaturas.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Grado<select value={params.get('grado')||''} onChange={e=>cambiar('grado',e.target.value)}><option value="">Todos</option>{grados.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Sección<select value={params.get('seccion')||''} onChange={e=>cambiar('seccion',e.target.value)}><option value="">Todas</option>{secciones.map(x=><option key={x}>{x}</option>)}</select></label>
    <label>Fecha<input type="date" value={params.get('fecha')||''} onChange={e=>cambiar('fecha',e.target.value)}/></label>
    {estados&&<label>Estado<select value={params.get('estado')||''} onChange={e=>cambiar('estado',e.target.value)}><option value="">Todos</option>{estados.map(x=><option key={x} value={x}>{x}</option>)}</select></label>}
  </div><button type="button" className="button secondary" onClick={limpiar}>Limpiar filtros</button></div>
}
export function Paginacion({total,params,cambiar}:{total:number;params:URLSearchParams;cambiar:(k:string,v:string)=>void}){
  const max=Math.max(1,Math.ceil(total/paginaTamano)),actual=Math.min(max,Math.max(1,Number(params.get('p'))||1));
  if(max<2)return null;
  return <div className="pagination"><button className="button secondary" disabled={actual<=1} onClick={()=>cambiar('p',String(actual-1))}>← Anterior</button><span>Página {actual} de {max}</span><button className="button secondary" disabled={actual>=max} onClick={()=>cambiar('p',String(actual+1))}>Siguiente →</button></div>
}
export function paginaFilas<T>(filas:T[],params:URLSearchParams){const max=Math.max(1,Math.ceil(filas.length/paginaTamano));const p=Math.min(max,Math.max(1,Number(params.get('p'))||1));return filas.slice((p-1)*paginaTamano,p*paginaTamano)}
