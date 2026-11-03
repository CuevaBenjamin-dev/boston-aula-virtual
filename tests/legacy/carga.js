import http from 'k6/http';
import {check, sleep} from 'k6';
import encoding from 'k6/encoding';

export const options={
  vus:5,
  duration:'30s',
  thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<1000']},
};

export function setup(){
  const auth=encoding.b64encode(`aula-servicio:${__ENV.ERP_CLIENT_SECRET}`);
  const r=http.post('http://identidad:9000/oauth2/token','grant_type=client_credentials&scope=erp.read%20erp.sync',{
    headers:{Authorization:`Basic ${auth}`,'Content-Type':'application/x-www-form-urlencoded'},
  });
  if(r.status!==200)throw new Error(`No se obtuvo token de servicio: HTTP ${r.status}`);
  return {token:r.json('access_token')};
}

export default function({token}){
  const headers={Authorization:`Bearer ${token}`};
  const respuestas=http.batch([
    ['GET','http://api-erp:8080/api/erp/eventos?despues=0&limite=20',null,{headers,tags:{escenario:'sincronizacion'}}],
    ['GET','http://api-erp:8080/api/erp/tesoreria/estudiantes/00000000-0000-0000-0000-000000000101',null,{headers,tags:{escenario:'tesoreria'}}],
    ['GET','http://api-aula:8080/actuator/health',null,{tags:{escenario:'salud_aula'}}],
    ['GET','http://proxy/',null,{tags:{escenario:'frontend'}}],
  ]);
  respuestas.forEach((r,i)=>check(r,{[`respuesta ${i+1} HTTP 200`]:x=>x.status===200}));
  sleep(1);
}
