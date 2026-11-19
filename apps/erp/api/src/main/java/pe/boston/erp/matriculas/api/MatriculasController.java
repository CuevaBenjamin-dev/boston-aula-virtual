package pe.boston.erp.matriculas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Period;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.oferta.OfertaConsulta;
import pe.boston.erp.personas.PersonasParaMatriculas;
import pe.boston.erp.plataforma.AccesoErp;

@RestController
@RequestMapping("/api/erp/v1/matriculas")
public class MatriculasController {
  public record BorradorEntrada(Map<String,Object> datos) {}
  public record Matricula(UUID id, UUID personaId, Integer anio, UUID seccionId,
    String estado, Map<String,Object> borrador, String creadoPor, String creadoEn, String confirmadoEn) {}
  public record ResultadoAnio(int anio, int finalizadas) {}
  private final JdbcTemplate jdbc;
  private final ObjectMapper json=new ObjectMapper();
  private final PersonasParaMatriculas personas;
  private final OfertaConsulta oferta;
  public MatriculasController(JdbcTemplate jdbc,PersonasParaMatriculas personas,OfertaConsulta oferta) {
    this.jdbc=jdbc;this.personas=personas;this.oferta=oferta;
  }

  @GetMapping public List<Matricula> listar(Authentication auth) {
    AccesoErp.actor(auth);
    return jdbc.query("SELECT id,persona_id,anio,seccion_id,estado,borrador::text,creado_por,creado_en::text,confirmado_en::text FROM mat_matriculas ORDER BY creado_en DESC LIMIT 100",
      (r,n)->convertir(r));
  }
  @GetMapping("/{id}") public Matricula ficha(@PathVariable UUID id,Authentication auth) {
    AccesoErp.actor(auth); return obtener(id);
  }
  @PostMapping @Transactional public Matricula crear(@RequestBody BorradorEntrada entrada,Authentication auth) {
    String actor=AccesoErp.actor(auth);
    JsonNode datos=validarBorrador(entrada);
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(?,?::jsonb,?)",id,datos.toString(),actor);
    evento(id,"BORRADOR_CREADO",actor);
    return obtener(id);
  }
  @PutMapping("/{id}") @Transactional public Matricula guardar(@PathVariable UUID id,@RequestBody BorradorEntrada entrada,Authentication auth) {
    String actor=AccesoErp.actor(auth); Matricula actual=obtener(id);
    if (!"BORRADOR".equals(actual.estado())) throw new IllegalArgumentException("Solo se puede editar un borrador");
    JsonNode datos=validarBorrador(entrada);
    jdbc.update("UPDATE mat_matriculas SET borrador=?::jsonb,actualizado_en=now() WHERE id=?",datos.toString(),id);
    evento(id,"BORRADOR_GUARDADO",actor);
    return obtener(id);
  }
  private Matricula registrarEstudiante(
      @PathVariable UUID id,Authentication auth) {
    String actor=AccesoErp.actor(auth); Matricula actual=obtener(id);
    if (!"BORRADOR".equals(actual.estado()) || actual.personaId()!=null)
      throw new IllegalArgumentException("El borrador ya tiene estudiante o no está editable");
    JsonNode d=json.valueToTree(actual.borrador());
    String nombres=requerido(d,"nombres","Nombres");
    String apellidos=requerido(d,"apellidos","Apellidos");
    LocalDate nacimiento=fecha(requerido(d,"fechaNacimiento","Nacimiento"));
    String tipo=requerido(d,"tipoDocumento","Tipo de documento");
    String pais=requerido(d,"paisEmisor","País emisor");
    String numero=requerido(d,"numeroDocumento","Número de documento");
    if (personas.buscarPorDocumento(tipo,pais,numero,auth)!=null)
      throw new IllegalArgumentException("El documento ya pertenece a una persona. Selecciónala en el borrador.");
    UUID persona=personas.registrarEstudiante(new PersonasParaMatriculas.NuevaPersona(
      nombres,apellidos,nacimiento,texto(d,"sexo"),texto(d,"nacionalidad"),texto(d,"telefono"),
      texto(d,"correo"),texto(d,"direccion"),tipo,pais,numero,null,null,null),auth);
    jdbc.update("UPDATE mat_matriculas SET persona_id=?,actualizado_en=now() WHERE id=?",persona,id);
    evento(id,"ESTUDIANTE_REGISTRADO",actor);
    return obtener(id);
  }
  @PostMapping("/{id}/confirmar") @Transactional public Matricula confirmar(@PathVariable UUID id,Authentication auth) {
    String actor=AccesoErp.actor(auth); Matricula m=obtener(id);
    if (!"BORRADOR".equals(m.estado())) throw new IllegalArgumentException("Solo un borrador puede confirmarse");
    JsonNode d=json.valueToTree(m.borrador());
    UUID seccionId=uuid(requerido(d,"seccionId","Sección"));
    OfertaConsulta.Seccion seccion=oferta.seccion(seccionId);
    if (seccion==null) throw new IllegalArgumentException("La sección no existe en Oferta académica");
    UUID persona=m.personaId()!=null?m.personaId():texto(d,"personaId")!=null
      ?uuid(texto(d,"personaId")):registrarEstudiante(id,auth).personaId();
    if (!personas.esEstudianteActivo(persona,auth) || !personas.tieneIdentificacionValida(persona,auth))
      throw new IllegalArgumentException("La persona requiere condición Estudiante e identificación vigente");
    LocalDate nacimiento=personas.fechaNacimiento(persona,auth);
    if (nacimiento==null) throw new IllegalArgumentException("Falta fecha real de nacimiento");
    JsonNode g=d.path("apoderado");
    if (g.isObject() && g.size()>0) {
      boolean sinParentesco=g.path("sinParentesco").asBoolean(false);
      PersonasParaMatriculas.Autorizacion autorizacion=sinParentesco
        ?new PersonasParaMatriculas.Autorizacion(requerido(g,"autorizador","Persona que autorizó"),
          requerido(g,"medio","Medio de verificación"),Instant.now(),requerido(g,"referencia","Referencia del sustento"))
        :null;
      String existente=texto(g,"personaId");
      if (existente!=null) {
        UUID apoderado=uuid(existente);
        if (!personas.apoderadosVigentes(persona,auth).contains(apoderado)) {
          if (!sinParentesco) personas.registrarParentesco(persona,apoderado,requerido(g,"parentesco","Parentesco"),auth);
          personas.vincularApoderado(persona,apoderado,sinParentesco,autorizacion,auth);
        }
      } else {
        personas.registrarApoderado(persona,new PersonasParaMatriculas.NuevoApoderado(
          requerido(g,"nombres","Nombres del apoderado"),requerido(g,"apellidos","Apellidos del apoderado"),
          requerido(g,"telefono","Teléfono del apoderado"),texto(g,"correo"),
          requerido(g,"tipoDocumento","Documento del apoderado"),requerido(g,"paisEmisor","País del documento"),
          requerido(g,"numeroDocumento","Número del documento"),sinParentesco,texto(g,"parentesco"),autorizacion),auth);
      }
    }
    if (Period.between(nacimiento,LocalDate.now()).getYears()<18 && personas.apoderadosVigentes(persona,auth).isEmpty())
      throw new IllegalArgumentException("El menor requiere un apoderado autorizado vigente con teléfono");
    if (jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE persona_id=? AND anio=? AND id<>? AND estado IN ('VIGENTE','RETIRADA','FINALIZADA')",
      Long.class,persona,seccion.anio(),id)>0)
      throw new IllegalArgumentException("El estudiante ya tiene matrícula en este año. Consulta su expediente para continuarla.");
    jdbc.update("UPDATE mat_matriculas SET persona_id=?,anio=?,seccion_id=?,estado='VIGENTE',confirmado_en=now(),actualizado_en=now() WHERE id=?",
      persona,seccion.anio(),seccionId,id);
    evento(id,"CONFIRMADA",actor);
    solicitud(id,"IDENTIDAD","CUENTA_ESTUDIANTE");
    solicitud(id,"AULA","ACCESO_MATRICULA");
    return obtener(id);
  }
  @PostMapping("/{id}/retirar") @Transactional public Matricula retirar(@PathVariable UUID id,Authentication auth) {
    cambiarEstado(id,auth,"VIGENTE","RETIRADA","RETIRADA"); solicitud(id,"AULA","RETIRAR_ACCESO"); return obtener(id);
  }
  @PostMapping("/{id}/reincorporar") @Transactional public Matricula reincorporar(@PathVariable UUID id,Authentication auth) {
    Matricula m=obtener(id);
    if (jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE persona_id=? AND anio=? AND estado='VIGENTE' AND id<>?",
      Long.class,m.personaId(),m.anio(),id)>0) throw new IllegalArgumentException("Ya existe otra matrícula vigente en el año");
    cambiarEstado(id,auth,"RETIRADA","VIGENTE","REINCORPORADA"); solicitud(id,"AULA","REINCORPORAR_ACCESO"); return obtener(id);
  }
  @PostMapping("/finalizar-anio/{anio}") @Transactional public ResultadoAnio finalizar(@PathVariable int anio,Authentication auth) {
    String actor=AccesoErp.actor(auth);
    if (anio>=LocalDate.now().getYear()) throw new IllegalArgumentException("Solo se puede finalizar un año escolar concluido");
    var ids=jdbc.query("SELECT id FROM mat_matriculas WHERE anio=? AND estado='VIGENTE' FOR UPDATE",
      (r,n)->r.getObject(1,UUID.class),anio);
    for (UUID id:ids) {
      jdbc.update("UPDATE mat_matriculas SET estado='FINALIZADA',actualizado_en=now() WHERE id=?",id);
      evento(id,"FINALIZADA",actor); solicitud(id,"AULA","ACCESO_HISTORICO");
    }
    return new ResultadoAnio(anio,ids.size());
  }
  private void cambiarEstado(UUID id,Authentication auth,String origen,String destino,String accion) {
    String actor=AccesoErp.actor(auth); Matricula m=obtener(id);
    if (!origen.equals(m.estado())) throw new IllegalArgumentException("La matrícula debe estar en estado "+origen);
    jdbc.update("UPDATE mat_matriculas SET estado=?,actualizado_en=now() WHERE id=?",destino,id);
    evento(id,accion,actor);
  }
  private void evento(UUID id,String accion,String actor) {
    jdbc.update("INSERT INTO mat_eventos(id,matricula_id,accion,actor_sub) VALUES(?,?,?,?)",UUID.randomUUID(),id,accion,actor);
  }
  private void solicitud(UUID id,String sistema,String accion) {
    UUID solicitudId=UUID.randomUUID();
    jdbc.update("INSERT INTO erp_solicitudes_externas(id,clave_idempotencia,sistema,entidad_tipo,entidad_id,accion,motivo) VALUES(?,?,?,?,?,?,?) ON CONFLICT (clave_idempotencia) DO NOTHING",
      solicitudId,"matricula:"+id+":"+accion+":"+solicitudId,sistema,"MATRICULA",id,accion,"Contrato remoto de negocio aún no conectado");
  }
  private Matricula obtener(UUID id) {
    var filas=jdbc.query("SELECT id,persona_id,anio,seccion_id,estado,borrador::text,creado_por,creado_en::text,confirmado_en::text FROM mat_matriculas WHERE id=?",
      (r,n)->convertir(r),id);
    if (filas.isEmpty()) throw new IllegalArgumentException("Matrícula no encontrada");
    return filas.getFirst();
  }
  private Matricula convertir(java.sql.ResultSet r) throws java.sql.SQLException {
    try { return new Matricula(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,Integer.class),
      r.getObject(4,UUID.class),r.getString(5),json.readValue(r.getString(6),
        new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}),r.getString(7),r.getString(8),r.getString(9)); }
    catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
  }
  private JsonNode validarBorrador(BorradorEntrada entrada) {
    if (entrada==null || entrada.datos()==null)
      throw new IllegalArgumentException("Los datos del borrador deben ser un objeto");
    return json.valueToTree(entrada.datos());
  }
  private static String texto(JsonNode n,String campo) { String v=n.path(campo).asText("").trim(); return v.isEmpty()?null:v; }
  private static String requerido(JsonNode n,String campo,String etiqueta) {
    String v=texto(n,campo); if (v==null) throw new IllegalArgumentException(etiqueta+" es obligatorio"); return v;
  }
  private static UUID uuid(String valor) {
    try { return UUID.fromString(valor); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Identificador inválido"); }
  }
  private static LocalDate fecha(String valor) {
    try { return LocalDate.parse(valor); } catch (Exception e) { throw new IllegalArgumentException("Fecha de nacimiento inválida"); }
  }
}
