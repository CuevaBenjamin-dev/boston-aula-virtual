import React, {Suspense, lazy, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter, Link, Navigate, Route, Routes, useLocation} from 'react-router-dom';
import {QueryClient, QueryClientProvider, useQuery} from '@tanstack/react-query';
import {api,type NotificacionClase, type Perfil} from './api';
import {PageHeadingContext,type HeadingMeta} from './PageHeading';
import './style.scss';

const Academico=lazy(()=>import('./pages/Academico'));
const Operacion=lazy(()=>import('./pages/Operacion'));
const queryClient=new QueryClient({defaultOptions:{queries:{staleTime:15000,retry:1}}});
const menu:Record<Perfil['tipo'],Array<[string,string]>>={
  ESTUDIANTE:[['/','Inicio'],['/cursos','Mis cursos'],['/materiales','Mis materiales'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/tareas','Mis tareas'],['/notas','Mis notas'],['/pagos','Mis pagos'],['/cuenta','Mi cuenta']],
  DOCENTE:[['/','Inicio'],['/cursos','Mis cursos'],['/materiales','Mis materiales'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/tareas','Tareas y entregas'],['/notas','Calificaciones'],['/cuenta','Mi cuenta']],
  COORDINACION:[['/','Inicio'],['/cursos','Cursos y secciones'],['/materiales','Mis materiales'],['/historial','Historial académico'],['/docentes','Docentes'],['/reportes','Seguimiento y reportes'],['/tareas','Tareas y evaluaciones'],['/clases','Clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/cuenta','Mi cuenta']],
  ADMINISTRADOR:[['/','Inicio'],['/usuarios','Usuarios y accesos'],['/historial','Historial académico'],['/modulos','Módulos del aula'],['/clases','Control de clases virtuales'],['/grabaciones','Grabaciones'],['/notificaciones','Bandeja de mensajes'],['/cuenta','Mi cuenta']]
};

function SidebarSession({perfil,nombre}:{perfil:Perfil,nombre?:string}){
  return <div className="sidebar-bottom"><div className="sidebar-account"><div className="sidebar-user"><span title={nombre||perfil.usuario}>{nombre||perfil.usuario}</span><small>{perfil.tipo.toLowerCase()}</small></div><form action="/logout" method="post"><input type="hidden" name="_csrf" value={perfil.csrfToken}/><button className="sidebar-logout" type="submit" title="Cerrar sesión" aria-label="Cerrar sesión"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg></button></form></div></div>;
}

function MenuIcono({ruta}:{ruta:string}){
  const trazos:Record<string,React.ReactNode>={
    '/':<><path d="m3 10 9-7 9 7v10H4V10"/><path d="M9 20v-7h6v7"/></>,
    '/cursos':<><path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1z"/><path d="M12 5v15"/></>,
    '/materiales':<><path d="M6 3h9l4 4v14H6zM15 3v5h4M9 12h7M9 16h7"/></>,
    '/clases':<><rect x="3" y="4" width="18" height="14" rx="1"/><path d="M8 21h8M12 18v3"/></>,
    '/grabaciones':<><circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4z"/></>,
    '/notificaciones':<path d="M4 5h16v12H9l-5 4z"/>,
    '/tareas':<><rect x="5" y="4" width="14" height="17" rx="1"/><path d="M9 4V2h6v2M8 9h8M8 13h8M8 17h5"/></>,
    '/notas':<><path d="M5 2h9l5 5v15H5zM14 2v5h5M8 12h8M8 16h8"/></>,
    '/pagos':<><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/></>,
    '/cuenta':<><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></>
  };
  return <svg className="menu-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{trazos[ruta]||<circle cx="12" cy="12" r="8"/>}</svg>;
}

function tituloRuta(path:string,perfil:Perfil){
  if(path.startsWith('/cursos/'))return 'Curso';
  if(path.startsWith('/clases/'))return 'Clase virtual';
  if(path.startsWith('/tareas/'))return 'Tarea';
  if(path.startsWith('/notas/'))return 'Nota';
  return menu[perfil.tipo].find(([ruta])=>ruta===path)?.[1]||'Aula Virtual';
}

function Shell(){
  const {data:perfil,isLoading,error}=useQuery<Perfil>({queryKey:['sesion'],queryFn:async()=>{const r=await fetch('/api/sesion',{credentials:'same-origin'});if(!r.ok)throw new Error('sin sesión');return r.json()}});
  const {data:modulos}=useQuery<{clasesYGrabaciones:boolean}>({queryKey:['modulos'],queryFn:async()=>{const r=await fetch('/api/aula/modulos');return r.json()},enabled:!!perfil});
  const {data:avisos=[]}=useQuery<NotificacionClase[]>({queryKey:['notificaciones'],queryFn:()=>api('/notificaciones'),enabled:!!perfil,refetchInterval:30000});
  const {data:mensajes=[]}=useQuery<Array<{leida_en:string|null}>>({queryKey:['mensajes','recibidos'],queryFn:()=>api('/mensajes?carpeta=recibidos'),enabled:!!perfil,refetchInterval:30000});
  const {data:cuenta}=useQuery<{fotoActualizada:string|null,datosInstitucionales?:{nombre?:string}}>({queryKey:['perfil-aula'],queryFn:()=>api('/perfil'),enabled:!!perfil});
  const [developer,setDeveloper]=useState(false);const [mobile,setMobile]=useState(false);const [heading,setHeading]=useState<HeadingMeta|null>(null);const location=useLocation();
  if(isLoading)return <div className="center"><div className="spinner"/>Preparando tu aula…</div>;
  if(error||!perfil)return <main className="auth-page">
    <section className="auth-story" aria-label="Boston Bilingual School">
      <div className="auth-logo" aria-label="Boston Bilingual School, Aula Virtual"><div className="auth-logo-letters" aria-hidden="true"><span>B</span><span>B</span><span>S</span></div><div><strong>Boston Bilingual School</strong><small>AULA VIRTUAL</small></div></div>
      <div className="auth-story-copy"><span className="auth-kicker">BIENVENIDO A TU ESPACIO DE APRENDIZAJE</span><h1>APRENDER<br/>ES CRECER<span>.</span></h1><p>Tu comunidad escolar, tus clases y tus logros en un solo lugar.</p></div>
      <div className="auth-story-footer"><span>BOSTON BILINGUAL SCHOOL</span><span>TRUJILLO · PERÚ</span></div>
    </section>
    <section className="auth-entry" aria-labelledby="auth-title"><div className="auth-entry-inner"><span className="auth-step">01 / ACCESO AL AULA</span><h2 id="auth-title">Tu aula te espera<span>.</span></h2><p>Ingresa con tu cuenta institucional para continuar al Aula Virtual.</p><a className="auth-submit" href="/oauth2/authorization/boston"><span>Iniciar sesión</span><span aria-hidden="true">→</span></a><div className="auth-entry-note">Acceso para estudiantes, docentes y equipo institucional.</div></div><div className="auth-entry-footer">AULA VIRTUAL BOSTON</div></section>
  </main>;
  const notificacionesPendientes=avisos.filter(a=>!a.leida_en).length;
  const mensajesPendientes=mensajes.filter(m=>!m.leida_en).length;
  const pendientes=notificacionesPendientes+mensajesPendientes;
  const items=menu[perfil.tipo].filter(([,label])=>modulos?.clasesYGrabaciones!==false||!['Clases virtuales','Grabaciones','Control de clases virtuales'].includes(label));
  if(perfil.tipo==='ADMINISTRADOR'&&perfil.developer&&developer)items.push(['/almacenamiento','Almacenamiento'],['/integraciones','Integraciones'],['/seguridad','Seguridad y auditoría'],['/estado','Estado del sistema']);
  const inicio=location.pathname==='/';
  const pageHeading=heading?.path===location.pathname?heading:null;
  const enCursos=location.pathname==='/cursos'||location.pathname.startsWith('/cursos/');
  const enClases=perfil.tipo!=='ADMINISTRADOR'&&(location.pathname==='/clases'||location.pathname.startsWith('/clases/'));
  const contenidoEditorial=inicio||enCursos||enClases||location.pathname==='/materiales'||location.pathname==='/grabaciones'||location.pathname==='/notificaciones'||location.pathname==='/tareas'||location.pathname.startsWith('/tareas/')||location.pathname==='/notas'||location.pathname.startsWith('/notas/')||location.pathname==='/pagos'||location.pathname==='/cuenta';
  return <PageHeadingContext.Provider value={setHeading}><div className={'layout editorial-layout'+(contenidoEditorial?' editorial-page':'')+(!inicio?' compact-page':'')}>
    <aside className={mobile?'sidebar open':'sidebar'}>
      <div className="brand"><div className="inicio-marca"><div className="inicio-marca-letras"><span>B</span><span>B</span><span>S</span></div><strong>Boston Bilingual School</strong><small>AULA VIRTUAL</small></div></div>
      <nav aria-label="Menú principal">{items.map(([path,label])=><Link key={path} to={path} className={location.pathname===path||(path!=='/'&&location.pathname.startsWith(path+'/'))?'active':''} onClick={()=>setMobile(false)}><MenuIcono ruta={path}/><span>{label}</span>{path==='/notificaciones'&&pendientes>0&&<span className="nav-count">{pendientes}</span>}</Link>)}</nav>
      <SidebarSession perfil={perfil} nombre={cuenta?.datosInstitucionales?.nombre?.trim()}/>
    </aside>
    <div className="main"><header className="topbar"><button className="menu-button" onClick={()=>setMobile(!mobile)} aria-label="Abrir menú">☰</button><div className="topbar-heading"><div className="top-title">Boston Bilingual School <span> / Aula Virtual</span></div>{!inicio&&<h1 className="topbar-page-title" title={pageHeading?.title||tituloRuta(location.pathname,perfil)}>{pageHeading?.title||tituloRuta(location.pathname,perfil)}<i aria-hidden="true">.</i></h1>}</div>{!inicio&&pageHeading?.metricValue&&<div className="topbar-metric"><span>{pageHeading.metricLabel}</span><strong>{pageHeading.metricValue}</strong><small>{pageHeading.metricCaption}</small></div>}<div className="top-actions">{perfil.tipo==='ADMINISTRADOR'&&perfil.developer&&<label className="switch-label"><input type="checkbox" checked={developer} onChange={e=>setDeveloper(e.target.checked)}/> Modo Developer</label>}<Link className="header-icon-link" to="/notificaciones?tab=mensajes" aria-label={`Mensajes${mensajesPendientes?`, ${mensajesPendientes} sin leer`:''}`} title="Mensajes"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="m3 7 9 7 9-7"/></svg>{mensajesPendientes>0&&<span className="nav-count">{mensajesPendientes}</span>}</Link><Link className="header-icon-link" to="/notificaciones?tab=notificaciones" aria-label={`Notificaciones${notificacionesPendientes?`, ${notificacionesPendientes} sin leer`:''}`} title="Notificaciones"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 9h18c0-1-3-2-3-9Z"/><path d="M10 21h4"/></svg>{notificacionesPendientes>0&&<span className="nav-count">{notificacionesPendientes}</span>}</Link><Link to="/cuenta" className="avatar" aria-label="Mi cuenta">{cuenta?.fotoActualizada?<img src={'/api/aula/perfil/foto?v='+encodeURIComponent(cuenta.fotoActualizada)} alt="Foto de perfil"/>:perfil.usuario[0].toUpperCase()}</Link></div></header>
      <div className={'content'+(contenidoEditorial?' editorial-content':'')}><Suspense fallback={<div className="center"><div className="spinner"/>Cargando módulo…</div>}><Routes><Route path="/" element={<Academico perfil={perfil} pagina="inicio" modulos={modulos}/>}/><Route path="/cuenta" element={<Operacion perfil={perfil} pagina="cuenta"/>}/><Route path="/pagos" element={<Operacion perfil={perfil} pagina="pagos"/>}/><Route path="/modulos" element={<Operacion perfil={perfil} pagina="modulos"/>}/><Route path="/integraciones" element={<Operacion perfil={perfil} pagina="integraciones"/>}/><Route path="/almacenamiento" element={<Operacion perfil={perfil} pagina="almacenamiento"/>}/><Route path="/seguridad" element={<Operacion perfil={perfil} pagina="seguridad"/>}/><Route path="/estado" element={<Operacion perfil={perfil} pagina="estado"/>}/><Route path="/usuarios" element={<Operacion perfil={perfil} pagina="usuarios"/>}/><Route path="/docentes" element={<Academico perfil={perfil} pagina="docentes" modulos={modulos}/>}/><Route path="/reportes" element={<Academico perfil={perfil} pagina="reportes" modulos={modulos}/>}/><Route path="/cursos/:cursoId" element={<Academico perfil={perfil} pagina="curso" modulos={modulos}/>}/><Route path="/tareas/:tareaId" element={<Academico perfil={perfil} pagina="tarea" modulos={modulos}/>}/><Route path="/notas/:notaId" element={<Academico perfil={perfil} pagina="nota" modulos={modulos}/>}/><Route path="/clases/:claseId" element={<Academico perfil={perfil} pagina="clase" modulos={modulos}/>}/><Route path="/:pagina" element={<Academico perfil={perfil} modulos={modulos}/>}/><Route path="*" element={<Navigate to="/"/>}/></Routes></Suspense></div>
    </div>
  </div></PageHeadingContext.Provider>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={queryClient}><BrowserRouter><Shell/></BrowserRouter></QueryClientProvider></React.StrictMode>);
