import {useId, useState} from 'react';

export const normalizarBusqueda=(valor:string)=>valor.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').replace(/\s+/g,' ').trim();
export const coincideBusqueda=(valor:string,consulta:string)=>normalizarBusqueda(valor).includes(normalizarBusqueda(consulta));

export function sugerenciasBusqueda(valor:string,candidatos:string[]){
  const consulta=normalizarBusqueda(valor);
  if(!consulta)return [];
  const rango=(texto:string)=>{const normal=normalizarBusqueda(texto);return normal.startsWith(consulta)?0:normal.split(' ').some(p=>p.startsWith(consulta))?1:2};
  return [...new Set(candidatos.filter(Boolean))].filter(x=>coincideBusqueda(x,valor))
    .sort((a,b)=>rango(a)-rango(b)||a.localeCompare(b,'es',{sensitivity:'base'})).slice(0,6);
}

export function BusquedaConSugerencias({etiqueta,valor,alCambiar,candidatos,placeholder}:{etiqueta:string;valor:string;alCambiar:(valor:string)=>void;candidatos:string[];placeholder:string}){
  const id=useId(),[abierto,setAbierto]=useState(false),[activo,setActivo]=useState(-1);
  const opciones=sugerenciasBusqueda(valor,candidatos),visible=abierto&&opciones.length>0;
  const elegir=(opcion:string)=>{alCambiar(opcion);setAbierto(false);setActivo(-1)};
  return <div className="busqueda-sugerencias" onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget))setAbierto(false)}}>
    <label htmlFor={id}>{etiqueta}</label><input id={id} value={valor} placeholder={placeholder} role="combobox" aria-autocomplete="list" aria-expanded={visible}
      aria-controls={visible?id+'-lista':undefined} aria-activedescendant={visible&&activo>=0?id+'-opcion-'+activo:undefined}
      onFocus={()=>setAbierto(true)} onChange={e=>{alCambiar(e.target.value);setAbierto(true);setActivo(-1)}}
      onKeyDown={e=>{if(e.key==='Escape'){setAbierto(false);setActivo(-1)}else if(e.key==='ArrowDown'&&opciones.length){e.preventDefault();setAbierto(true);setActivo(i=>(i+1)%opciones.length)}else if(e.key==='ArrowUp'&&opciones.length){e.preventDefault();setAbierto(true);setActivo(i=>i<=0?opciones.length-1:i-1)}else if(e.key==='Enter'&&visible&&activo>=0){e.preventDefault();elegir(opciones[activo])}}}/>
    {visible&&<div className="busqueda-sugerencias-lista" id={id+'-lista'} role="listbox">{opciones.map((opcion,i)=><button type="button" role="option" aria-selected={i===activo} id={id+'-opcion-'+i} key={opcion} onClick={()=>elegir(opcion)}>{opcion}</button>)}</div>}
  </div>;
}
