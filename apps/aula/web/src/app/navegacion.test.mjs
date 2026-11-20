import assert from 'node:assert/strict';
import test from 'node:test';
import {menuVisible,rutasAplicacion,tituloRuta} from './navegacion.ts';

const perfil=(tipo,developer=false)=>({tipo,developer});
const rutas=(tipo,modulos,developer)=>menuVisible(perfil(tipo,tipo==='ADMINISTRADOR'),modulos,developer).map(([ruta])=>ruta);

test('Estudiante y Docente conservan sus menús y no reciben administración heredada',()=>{
  const estudiante=rutas('ESTUDIANTE');
  const docente=rutas('DOCENTE');
  assert.ok(estudiante.includes('/pagos'));
  assert.ok(estudiante.includes('/tareas'));
  assert.ok(docente.includes('/tareas'));
  assert.ok(docente.includes('/notas'));
  assert.ok(!docente.includes('/pagos'));
  for(const ruta of ['/usuarios','/modulos','/integraciones','/almacenamiento']) {
    assert.ok(!estudiante.includes(ruta));
    assert.ok(!docente.includes(ruta));
  }
});

test('Módulo de clases y menú Developer conservan sus filtros anteriores',()=>{
  for(const tipo of ['ESTUDIANTE','DOCENTE','COORDINACION','ADMINISTRADOR']) {
    const visibles=rutas(tipo,{clasesYGrabaciones:false});
    assert.ok(!visibles.includes('/clases'));
    assert.ok(!visibles.includes('/grabaciones'));
  }
  assert.ok(!rutas('ADMINISTRADOR',undefined,false).includes('/integraciones'));
  assert.ok(rutas('ADMINISTRADOR',undefined,true).includes('/integraciones'));
  assert.ok(!menuVisible(perfil('DOCENTE',true),undefined,true).some(([ruta])=>ruta==='/integraciones'));
});

test('Las 17 rutas públicas y sus destinos conservan el contrato anterior',()=>{
  assert.deepEqual(rutasAplicacion.map(({path})=>path),[
    '/','/cuenta','/pagos','/modulos','/integraciones','/almacenamiento','/seguridad','/estado',
    '/usuarios','/docentes','/reportes','/cursos/:cursoId','/tareas/:tareaId','/notas/:notaId',
    '/clases/:claseId','/:pagina','*'
  ]);
  assert.deepEqual(rutasAplicacion.filter(({vista})=>vista==='operacion').map(({pagina})=>pagina),
    ['cuenta','pagos','modulos','integraciones','almacenamiento','seguridad','estado','usuarios']);
  assert.equal(tituloRuta('/cursos/123','ESTUDIANTE'),'Curso');
  assert.equal(tituloRuta('/tareas','DOCENTE'),'Tareas y entregas');
  assert.equal(tituloRuta('/usuarios','ESTUDIANTE'),'Aula Virtual');
});
