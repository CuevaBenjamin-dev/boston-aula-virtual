import {useState} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {api,del,subirArchivo,type Archivo} from '../api';

export const archivoPermitido='.pdf,.png,.jpg,.jpeg,.docx,.pptx';
export function ArchivosDe({padre,id,editable=false}:{padre:'tareas'|'entregas'|'materiales';id:string;editable?:boolean}){
  const qc=useQueryClient();
  const {data=[]}=useQuery<Archivo[]>({queryKey:['archivos',padre,id],queryFn:()=>api(`/${padre}/${id}/archivos`),enabled:!!id});const [error,setError]=useState('');
  async function abrir(archivo:Archivo){try{const {url}=await api<{url:string}>(`/archivos/${archivo.id}/url`);window.open(url,'_blank','noopener,noreferrer')}catch(e){setError(String(e))}}
  async function retirar(archivo:Archivo){try{await del(`/archivos/${archivo.id}`);qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  async function reemplazar(archivo:Archivo,nuevo:File){try{await subirArchivo(padre,id,nuevo,()=>{},archivo.id);qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  async function agregar(nuevos:FileList|null){if(!nuevos)return;try{for(const nuevo of Array.from(nuevos))await subirArchivo(padre,id,nuevo,()=>{});qc.invalidateQueries({queryKey:['archivos',padre,id]})}catch(e){setError(String(e))}}
  return <>{data.map(a=><div className="row-actions" key={a.id}><button className="text-link" onClick={()=>abrir(a)}>↓ {a.nombre}</button>{editable&&<><label className="text-link">Sustituir<input type="file" accept={archivoPermitido} style={{display:'none'}} onChange={e=>{const f=e.target.files?.[0];if(f)reemplazar(a,f)}}/></label><button className="text-link" onClick={()=>retirar(a)}>Retirar</button></>}</div>)}{editable&&<label className="text-link">+ Agregar archivos<input type="file" multiple accept={archivoPermitido} style={{display:'none'}} onChange={e=>agregar(e.target.files)}/></label>}{error&&<small className="error">{error}</small>}</>;
}
