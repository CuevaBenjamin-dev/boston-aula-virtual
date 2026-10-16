export type Perfil = {usuario:string;tipo:'ESTUDIANTE'|'DOCENTE'|'COORDINACION'|'ADMINISTRADOR';developer:boolean;csrfToken:string};
export type Curso = {id:string;asignatura:string;grado:string;seccion:string;periodo:string;docentes:string|null};
export type Tarea = {id:string;curso_id:string;titulo:string;instrucciones:string;fecha_limite:string;estado:'FALTA_COMPLETAR'|'PUBLICADA'|'CERRADA';escala_maxima:number;version:number;creado_en:string;publicado_en:string|null;presentada?:boolean;entregados?:number;total_estudiantes?:number};
export type Clase = {id:string;curso_id:string;tema:string;inicio:string;fin:string;estado:'PROGRAMADA'|'CANCELADA';modificado_en:string|null;enlace_actual:string|null;enlace_actualizado:string|null;asignatura?:string;grado?:string;seccion?:string;periodo?:string};
export type Entrega = {id:string;tarea_id:string;estudiante_erp_id:string;estudiante?:string;version:number;respuesta:string;estado:string;entregado_en:string};
export type Nota = {id:string;tarea_id:string;estudiante_erp_id:string;valor:number;comentario:string;version:number;titulo:string;entrega_version:number;ultima_entrega_version:number;escala_maxima:number;fecha_limite:string;actualizado_en:string};
export type Grabacion = {id:string;clase_id:string;curso_id:string;orden:number;youtube_id:string;url:string;estado:'PUBLICADA'|'OCULTA';tema:string;inicio:string;fin:string;estado_clase:string;asignatura:string;grado:string;seccion:string;periodo:string;docente:string;creado_en:string;actualizado_en:string};
export type NotificacionClase = {id:string;clase_id:string;tipo:'PROGRAMADA'|'REPROGRAMADA'|'CANCELADA'|'CREADA'|'EDITADA';mensaje:string;creada_en:string;leida_en:string|null;tema:string;inicio:string;asignatura:string;grado:string;seccion:string};
export type Archivo = {id:string;nombre:string;tamano:number;tipo:string};

function csrf(){const match=document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);return match?decodeURIComponent(match[1]):''}
let redirigiendoAlInicio=false;
export async function api<T>(path:string, options:RequestInit={}):Promise<T>{
  const headers:Record<string,string>={...options.headers as Record<string,string>};
  if(options.body && !(options.body instanceof FormData)) headers['Content-Type']='application/json';
  if(options.method && !['GET','HEAD'].includes(options.method.toUpperCase())) headers['X-XSRF-TOKEN']=csrf();
  const response=await fetch('/api/aula'+path,{...options,headers,credentials:'same-origin'});
  if(response.status===401&&!redirigiendoAlInicio){redirigiendoAlInicio=true;window.location.assign('/');}
  if(!response.ok){const body=await response.json().catch(()=>({error:response.statusText}));throw new Error(body.error||`Error HTTP ${response.status}`)}
  if(response.status===204)return undefined as T;
  const body=await response.text();return body?JSON.parse(body) as T:undefined as T;
}
export const post=<T>(path:string,body:unknown)=>api<T>(path,{method:'POST',body:JSON.stringify(body)});
export const put=<T>(path:string,body:unknown)=>api<T>(path,{method:'PUT',body:JSON.stringify(body)});

export async function subirArchivo(destino:'tareas'|'entregas',padre:string,archivo:File,progreso:(n:number)=>void){
  const tipo=archivo.type||({'pdf':'application/pdf','png':'image/png','jpg':'image/jpeg','jpeg':'image/jpeg','docx':'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}[archivo.name.split('.').pop()?.toLowerCase()||'']||'');
  const inicio=await post<{id:string;tamanoParte:number}>(`/${destino}/${padre}/archivos`,{nombre:archivo.name,tipo,tamano:archivo.size});
  const partes:Array<{numero:number;etag:string}>=[];const total=Math.ceil(archivo.size/inicio.tamanoParte);
  for(let i=0;i<total;i++){
    const {url}=await api<{url:string}>(`/archivos/${inicio.id}/partes/${i+1}`);
    let respuesta:Response|undefined;
    for(let intento=0;intento<3;intento++){respuesta=await fetch(url,{method:'PUT',body:archivo.slice(i*inicio.tamanoParte,Math.min((i+1)*inicio.tamanoParte,archivo.size))}).catch(()=>undefined);if(respuesta?.ok)break}
    if(!respuesta?.ok)throw new Error(`Falló la parte ${i+1}`);
    const etag=respuesta.headers.get('ETag');if(!etag)throw new Error('El almacenamiento no expuso ETag');
    partes.push({numero:i+1,etag});progreso(Math.round((i+1)/total*100));
  }
  await post(`/archivos/${inicio.id}/completar`,{partes});
  return inicio.id;
}

