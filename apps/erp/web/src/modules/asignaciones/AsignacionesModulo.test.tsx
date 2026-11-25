// @vitest-environment jsdom
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest'
import { cleanup,fireEvent,render,screen,waitFor,within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AsignacionesModulo } from './AsignacionesModulo'
import { erp } from '../../shared/api/erp'
import type { Sesion } from '../../shared/api/sesion'
vi.mock('../../shared/api/erp',()=>({erp:vi.fn()}))
const sesion:Sesion={autenticado:true,usuario:'prueba',rol:'COORDINACION',csrf:'csrf-test'}
const api=vi.mocked(erp)
const docente={id:'d1',nombres:'Ana',apellidos:'Ensayo',documento:'00000011'}
const secciones=['A','B','C'].map(nombre=>({id:nombre,anio:2027,nivel:'PRIMARIA',grado:1,nombre}))
const materia={id:'m1',anio:2027,nivel:'PRIMARIA',grado:1,nombre:'Matemática',codigo:'PR-01-MAT-27'}
function fila(s:string,id=s){return {id,docenteId:docente.id,docente:'Ana Ensayo',seccionId:s,seccion:`PRIMARIA 1° ${s}`,asignaturaId:materia.id,asignatura:materia.nombre,anio:2027,tipo:'TITULAR',desde:'2027-03-08',hasta:null,horario:null,estado:'ACTIVA',coberturaPendiente:false,aulaEstado:'CONFIRMADA'}}
let filas:ReturnType<typeof fila>[]=[]
beforeEach(()=>{
  if(!HTMLDialogElement.prototype.showModal)HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')}
  if(!HTMLDialogElement.prototype.close)HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}
  filas=[]
  api.mockImplementation(async <T,>(ruta:string,_sesion:Sesion,metodo='GET',cuerpo?:unknown):Promise<T>=>{
    if(metodo==='POST'&&ruta==='/asignaciones/lote'){const v=cuerpo as {seccionIds:string[]};const nuevas=v.seccionIds.map(s=>({...fila(s),aulaEstado:'PENDIENTE_NO_CONECTADO'}));filas=[...filas,...nuevas];return nuevas as T}
    if(metodo==='POST'&&ruta.endsWith('/cerrar')){const id=ruta.split('/')[2];filas=filas.map(f=>f.id===id?{...f,estado:'CERRADA',aulaEstado:'PENDIENTE_NO_CONECTADO'}:f);return filas.find(f=>f.id===id) as T}
    if(ruta==='/oferta/anios')return [2027,2026] as T
    if(ruta==='/oferta/secciones?anio=2027')return secciones as T
    if(ruta==='/oferta/asignaturas?anio=2027')return [materia] as T
    if(ruta==='/asignaciones?anio=2027')return filas as T
    if(ruta.startsWith('/personas?'))return {resultados:[docente]} as T
    if(ruta.startsWith('/integracion/estado?'))return [{id:'e1',sistema:'AULA',accion:'ASIGNAR_DOCENTE',estado:'CONFIRMADA',motivo:'Confirmada por el destino'}] as T
    return [] as T
  })
})
afterEach(()=>{cleanup();vi.resetAllMocks()})
async function abrir(){render(<AsignacionesModulo sesion={sesion}/>);return screen.findByRole('button',{name:'Asignar Matemática a sección A'})}
async function completar(){await userEvent.click(await abrir());const d=screen.getByRole('dialog',{name:'Nueva asignación'});await userEvent.type(within(d).getByRole('textbox',{name:'Buscar docente'}),'Ana');await userEvent.click(await within(d).findByRole('button',{name:/Ana Ensayo/}));await userEvent.click(within(d).getByRole('button',{name:'Todas'}));fireEvent.change(within(d).getByLabelText('Desde'),{target:{value:'2027-03-08'}});return d}
describe('Asignaciones docentes',()=>{
  it('registra tres secciones con una solicitud y conserva el estado externo real',async()=>{const d=await completar();await userEvent.click(within(d).getByRole('button',{name:/Registrar 3 asignación/}));await waitFor(()=>expect(api).toHaveBeenCalledWith('/asignaciones/lote',sesion,'POST',{anio:2027,docenteId:'d1',asignaturaId:'m1',seccionIds:['A','B','C'],tipo:'TITULAR',desde:'2027-03-08',hasta:null,horario:null}));await screen.findByText('3/3',{selector:'strong'});await userEvent.click(screen.getByRole('button',{name:'Registro de asignaciones'}));expect(screen.getAllByText('Pendiente',{exact:true})).toHaveLength(3)})
  it('mantiene el diálogo y los valores si el servidor rechaza el lote',async()=>{const d=await completar();api.mockImplementationOnce(async()=>{throw new Error('Asignación duplicada')});await userEvent.click(within(d).getByRole('button',{name:/Registrar 3 asignación/}));expect((await within(d).findByRole('alert')).textContent).toContain('Asignación duplicada');expect(within(d).getAllByRole('checkbox',{checked:true})).toHaveLength(3)})
  it('consulta historial, cierra explícitamente y no confunde cierre con eliminación',async()=>{filas=[fila('A')];await abrir();await userEvent.click(screen.getByRole('button',{name:'Registro de asignaciones'}));await userEvent.click(screen.getByRole('button',{name:'Ana Ensayo'}));const d=screen.getByRole('dialog',{name:'Detalle de asignación'});await userEvent.click(within(d).getByRole('button',{name:'Cerrar asignación'}));expect(api.mock.calls.filter(c=>c[2]==='POST')).toHaveLength(0);await userEvent.click(within(d).getByRole('button',{name:'Confirmar cierre'}));await screen.findByText('Cerrada',{selector:'td'});expect(api).toHaveBeenCalledWith('/asignaciones/A/cerrar',sesion,'POST')})
  it('no presenta resultados del año anterior al cambiar el año',async()=>{filas=[fila('A')];await abrir();await userEvent.selectOptions(screen.getByRole('combobox',{name:'Año escolar'}),'2026');await screen.findByText('Configura secciones y asignaturas en Oferta académica.');expect(screen.queryByText('Ana Ensayo')).toBeNull()})
})
