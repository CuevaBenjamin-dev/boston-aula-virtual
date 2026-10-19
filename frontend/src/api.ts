export type Perfil = {usuario:string;tipo:'ESTUDIANTE'|'DOCENTE'|'COORDINACION'|'ADMINISTRADOR';developer:boolean;csrfToken:string};
export type Curso = {id:string;asignatura:string;grado:string;nivel:string|null;seccion:string;periodo:string;docentes:string|null};
export type Tarea = {id:string;curso_id:string;titulo:string;instrucciones:string;fecha_limite:string;estado:'FALTA_COMPLETAR'|'PUBLICADA'|'CERRADA'|'CANCELADA';escala_maxima:number;version:number;creado_en:string;publicado_en:string|null;tarea_anterior_id:string|null;tarea_sucesora_id?:string|null;lote_id?:string|null;presentada?:boolean;ultima_entrega_estado?:'ENVIADA'|'TARDIA'|null;ultima_entrega_version?:number|null;entregados?:number;total_estudiantes?:number;tardias?:number;pendientes?:number;pendientes_revision?:number;es_historica?:boolean};
export type Material = {id:string;curso_id?:string;curso_ids?:string[];nombre:string;descripcion:string;enlace:string|null;estado:'BORRADOR'|'PUBLICADO'|'OCULTO';total_archivos?:number;actualizado_en:string};
export type Clase = {id:string;curso_id:string;tema:string;inicio:string;fin:string;estado:'PROGRAMADA'|'CANCELADA';modificado_en:string|null;enlace_actual:string|null;enlace_actualizado:string|null;asignatura?:string;grado?:string;seccion?:string;periodo?:string};
export type Entrega = {id:string;tarea_id:string;estudiante_erp_id:string;estudiante?:string;version:number;respuesta:string;estado:'ENVIADA'|'TARDIA';entregado_en:string;fecha_limite_al_entregar:string};
export type PreparacionEntrega = {id?:string;tarea_id?:string;intento?:number;tarea_version?:number;respuesta?:string;puede_iniciar:boolean;siguiente_intento:number};
export type Seguimiento = {estudiante_erp_id:string;estudiante:string;entrega_id:string|null;estado_entrega:'ENVIADA'|'TARDIA'|null;entregado_en:string|null;entrega_version:number|null;fecha_limite_al_entregar:string|null;nota_id:string|null;version_calificada:number|null};
export type Nota = {id:string;tarea_id:string;estudiante_erp_id:string;valor:number|null;comentario:string|null;version?:number;titulo:string;entrega_version:number;ultima_entrega_version:number;escala_maxima:number;fecha_limite:string;actualizado_en:string;estado:'BORRADOR'|'PUBLICADA';valor_borrador?:number|null;comentario_borrador?:string|null;publicada_en?:string|null};
export type Grabacion = {id:string;clase_id:string;curso_id:string;orden:number;youtube_id:string;url:string;estado:'PUBLICADA'|'OCULTA';tema:string;inicio:string;fin:string;estado_clase:string;asignatura:string;grado:string;seccion:string;periodo:string;docente:string;creado_en:string;actualizado_en:string};
export type NotificacionClase = {id:string;clase_id:string|null;tarea_id?:string|null;nota_id?:string|null;puede_abrir?:boolean;tipo:string;mensaje:string;creada_en:string;leida_en:string|null;nuevas_entregas?:number;tema:string;inicio:string|null;asignatura:string;grado:string;seccion:string};
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
export const del=<T>(path:string)=>api<T>(path,{method:'DELETE'});

export async function subirArchivo(destino:'tareas'|'entregas'|'materiales'|'preparaciones',padre:string,archivo:File,progreso:(n:number)=>void,sustituyeId?:string){
  const conocido={'pdf':'application/pdf','png':'image/png','jpg':'image/jpeg','jpeg':'image/jpeg','docx':'application/vnd.openxmlformats-officedocument.wordprocessingml.document','pptx':'application/vnd.openxmlformats-officedocument.presentationml.presentation','xlsx':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','txt':'text/plain'}[archivo.name.split('.').pop()?.toLowerCase()||'']||'';
  const tipo=destino==='preparaciones'?conocido:archivo.type||conocido;
  const inicio=await post<{id:string;tamanoParte:number}>(`/${destino}/${padre}/archivos`,{nombre:archivo.name,tipo,tamano:archivo.size,sustituyeId});
  try {
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
  } catch(error) {
    if(destino==='preparaciones')await del(`/archivos/${inicio.id}`).catch(()=>{});
    throw error;
  }
}

