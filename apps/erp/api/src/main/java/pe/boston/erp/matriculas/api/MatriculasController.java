package pe.boston.erp.matriculas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Period;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.oferta.OfertaConsulta;
import pe.boston.erp.personas.PersonasParaMatriculas;
import pe.boston.erp.plataforma.AccesoErp;
import pe.boston.erp.plataforma.BusquedaTexto;

@RestController
@RequestMapping("/api/erp/v1/matriculas")
public class MatriculasController {
  public record BorradorEntrada(Map<String,Object> datos) {}
  public record Matricula(UUID id, UUID personaId, Integer anio, UUID seccionId,
    String estado, Map<String,Object> borrador, String creadoPor, String creadoEn, String confirmadoEn) {}
  public record ResultadoAnio(int anio, int finalizadas) {}
  public record ResumenCierre(int anio, long vigentes) {}
  public record FilaMatricula(UUID id, String estudiante, String documento, Integer anio,
    String seccion, String estado, String creadoEn) {}
  public record PaginaMatriculas(List<FilaMatricula> resultados, long total, int pagina, int tamano) {}
  private static final String COLUMNAS = "id,persona_id,anio,seccion_id,estado,borrador::text,creado_por,creado_en::text,confirmado_en::text";
  private final JdbcTemplate jdbc;
  private final ObjectMapper json=new ObjectMapper();
  private final PersonasParaMatriculas personas;
  private final OfertaConsulta oferta;
  public MatriculasController(JdbcTemplate jdbc,PersonasParaMatriculas personas,OfertaConsulta oferta) {
    this.jdbc=jdbc;this.personas=personas;this.oferta=oferta;
  }

  @GetMapping public PaginaMatriculas listar(@RequestParam(defaultValue="0") int pagina,
      @RequestParam(defaultValue="15") int tamano, @RequestParam(required=false) Integer anio,
      @RequestParam(required=false) String estado, @RequestParam(required=false) UUID seccionId,
      @RequestParam(required=false) String nivel, @RequestParam(required=false) Integer grado,
      @RequestParam(required=false) String buscar, Authentication auth) {
    AccesoErp.actor(auth);
    if (nivel!=null && !List.of("PRIMARIA","SECUNDARIA").contains(nivel)) throw new IllegalArgumentException("Nivel inválido");
    if (pagina<0 || pagina>1_000_000 || tamano<1 || tamano>50)
      throw new IllegalArgumentException("La página o el tamaño solicitado no es válido");
    if (anio!=null && (anio<2000 || anio>2100)) throw new IllegalArgumentException("Año escolar inválido");
    if (estado!=null && !estado.isBlank() && !List.of("BORRADOR","VIGENTE","RETIRADA","FINALIZADA").contains(estado))
      throw new IllegalArgumentException("Estado de matrícula inválido");
    String termino=buscar==null?"":buscar.trim();
    if (termino.length()>120) throw new IllegalArgumentException("La búsqueda es demasiado larga");
    StringBuilder desde=new StringBuilder("""
      FROM mat_matriculas m
      LEFT JOIN per_personas p ON p.id::text=COALESCE(m.persona_id::text,m.borrador->>'personaId')
      LEFT JOIN per_documentos d ON d.persona_id=p.id AND d.principal AND d.vigente_hasta IS NULL
      LEFT JOIN ofe_secciones s ON s.id::text=COALESCE(m.seccion_id::text,m.borrador->>'seccionId')
      WHERE 1=1
      """);
    List<Object> parametros=new ArrayList<>();
    if (anio!=null) { desde.append(" AND COALESCE(m.anio::text,m.borrador->>'anio')=?"); parametros.add(anio.toString()); }
    if (estado!=null && !estado.isBlank()) { desde.append(" AND m.estado=?"); parametros.add(estado); }
    if (seccionId!=null) { desde.append(" AND COALESCE(m.seccion_id::text,m.borrador->>'seccionId')=?"); parametros.add(seccionId.toString()); }
    if (grado!=null && (grado<1 || grado>6)) throw new IllegalArgumentException("Grado inválido");
    if (nivel!=null) { desde.append(" AND s.nivel=?"); parametros.add(nivel); }
    if (grado!=null) { desde.append(" AND s.grado=?"); parametros.add(grado); }
    if (!termino.isEmpty()) {
      desde.append(" AND (position(" + BusquedaTexto.sql("?") + " in "
        + BusquedaTexto.sql("concat_ws(' ',p.nombres,p.apellidos)") + ")>0 OR position("
        + BusquedaTexto.sql("?") + " in " + BusquedaTexto.sql("concat_ws(' ',m.borrador->>'nombres',m.borrador->>'apellidos')")
        + ")>0 OR position(" + BusquedaTexto.sql("?") + " in " + BusquedaTexto.sql("d.numero_normalizado")
        + ")>0 OR position(" + BusquedaTexto.sql("?") + " in " + BusquedaTexto.sql("m.borrador->>'numeroDocumento'") + ")>0)");
      for (int i=0;i<4;i++) parametros.add(termino);
    }
    long total=jdbc.queryForObject("SELECT count(*) "+desde,Long.class,parametros.toArray());
    String orden=" ORDER BY m.creado_en DESC,m.id DESC";
    if (!termino.isEmpty()) {
      orden=" ORDER BY CASE WHEN starts_with(" + BusquedaTexto.sql("coalesce(p.nombres,m.borrador->>'nombres')")
        + "," + BusquedaTexto.sql("?") + ") THEN 0 WHEN starts_with("
        + BusquedaTexto.sql("coalesce(p.apellidos,m.borrador->>'apellidos')") + ","
        + BusquedaTexto.sql("?") + ") THEN 1 ELSE 2 END,m.creado_en DESC,m.id DESC";
      parametros.add(termino); parametros.add(termino);
    }
    parametros.add(tamano); parametros.add((long) pagina*tamano);
    var resultados=jdbc.query("""
      SELECT m.id,
        COALESCE(NULLIF(btrim(concat_ws(' ',p.nombres,p.apellidos)),''),
          NULLIF(btrim(concat_ws(' ',m.borrador->>'nombres',m.borrador->>'apellidos')),''),'Alumno por identificar'),
        COALESCE(d.numero_normalizado,m.borrador->>'numeroDocumento'),
        COALESCE(m.anio,s.anio,CASE WHEN m.borrador->>'anio' ~ '^[0-9]{4}$' THEN (m.borrador->>'anio')::integer END),
        CASE WHEN s.id IS NULL THEN NULL ELSE s.nivel||' · '||s.grado||'.º · '||s.nombre END,
        m.estado,m.creado_en::text
      """+desde+orden+" LIMIT ? OFFSET ?",
      (r,n)->new FilaMatricula(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
        r.getObject(4,Integer.class),r.getString(5),r.getString(6),r.getString(7)),parametros.toArray());
    return new PaginaMatriculas(resultados,total,pagina,tamano);
  }
  @GetMapping("/{id}") public Matricula ficha(@PathVariable UUID id,Authentication auth) {
    AccesoErp.actor(auth); return obtener(id);
  }
  @PostMapping @Transactional public Matricula crear(@RequestBody BorradorEntrada entrada,Authentication auth) {
    String actor=AccesoErp.actor(auth);
    JsonNode datos=validarBorrador(entrada);
    validarMatriculaAnualExistente(datos);
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(?,?::jsonb,?)",id,datos.toString(),actor);
    evento(id,"BORRADOR_CREADO",actor);
    return obtener(id);
  }
  @PutMapping("/{id}") @Transactional public Matricula guardar(@PathVariable UUID id,@RequestBody BorradorEntrada entrada,Authentication auth) {
    String actor=AccesoErp.actor(auth); Matricula actual=obtener(id,true);
    if (!"BORRADOR".equals(actual.estado())) throw new IllegalArgumentException("Solo se puede editar un borrador");
    JsonNode datos=validarBorrador(entrada);
    validarMatriculaAnualExistente(datos);
    jdbc.update("UPDATE mat_matriculas SET borrador=?::jsonb,actualizado_en=now() WHERE id=?",datos.toString(),id);
    evento(id,"BORRADOR_GUARDADO",actor);
    return obtener(id);
  }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void descartar(@PathVariable UUID id,Authentication auth) {
    String actor=AccesoErp.actor(auth);
    Matricula m=buscar(id,true);
    if (m==null) {
      if (Boolean.TRUE.equals(jdbc.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM mat_borradores_descartados WHERE matricula_id=?)",Boolean.class,id))) return;
      throw new IllegalArgumentException("Matrícula no encontrada");
    }
    if (!"BORRADOR".equals(m.estado())) throw new IllegalArgumentException("Solo se puede descartar un borrador");
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM erp_solicitudes_externas WHERE entidad_tipo='MATRICULA' AND entidad_id=?)",Boolean.class,id)))
      throw new IllegalArgumentException("Este borrador tiene solicitudes de integración y no puede descartarse");
    jdbc.update("INSERT INTO mat_borradores_descartados(matricula_id,descartado_por) VALUES(?,?)",id,actor);
    jdbc.update("DELETE FROM mat_eventos WHERE matricula_id=?",id);
    jdbc.update("DELETE FROM mat_matriculas WHERE id=? AND estado='BORRADOR'",id);
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
    String actor=AccesoErp.actor(auth); Matricula m=obtener(id,true);
    if (!"BORRADOR".equals(m.estado())) throw new IllegalArgumentException("Solo un borrador puede confirmarse");
    JsonNode d=json.valueToTree(m.borrador());
    UUID seccionId=uuid(requerido(d,"seccionId","Sección"));
    OfertaConsulta.Seccion seccion=oferta.seccion(seccionId);
    if (seccion==null) throw new IllegalArgumentException("La sección no existe en Oferta académica");
    UUID persona=m.personaId()!=null?m.personaId():texto(d,"personaId")!=null
      ?uuid(texto(d,"personaId")):registrarEstudiante(id,auth).personaId();
    validarMatriculaAnualExistente(persona,seccion.anio());
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
    try {
      jdbc.update("UPDATE mat_matriculas SET persona_id=?,anio=?,seccion_id=?,estado='VIGENTE',confirmado_en=now(),actualizado_en=now() WHERE id=?",
        persona,seccion.anio(),seccionId,id);
    } catch (DataIntegrityViolationException e) { throw conflictoVigente(e,seccion.anio()); }
    evento(id,"CONFIRMADA",actor);
    solicitud(id,"IDENTIDAD","CUENTA_ESTUDIANTE");
    solicitud(id,"AULA","ACCESO_MATRICULA");
    return obtener(id);
  }
  @PostMapping("/{id}/retirar") @Transactional public Matricula retirar(@PathVariable UUID id,Authentication auth) {
    cambiarEstado(id,auth,"VIGENTE","RETIRADA","RETIRADA"); solicitud(id,"AULA","RETIRAR_ACCESO"); return obtener(id);
  }
  @PostMapping("/{id}/reincorporar") @Transactional public Matricula reincorporar(@PathVariable UUID id,Authentication auth) {
    Matricula m=obtener(id,true);
    if (jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE persona_id=? AND anio=? AND estado='VIGENTE' AND id<>?",
      Long.class,m.personaId(),m.anio(),id)>0) throw new IllegalArgumentException("Ya existe otra matrícula vigente en el año");
    try { cambiarEstado(id,auth,"RETIRADA","VIGENTE","REINCORPORADA"); }
    catch (DataIntegrityViolationException e) { throw conflictoVigente(e,m.anio()); }
    solicitud(id,"AULA","REINCORPORAR_ACCESO"); return obtener(id);
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
  @GetMapping("/finalizar-anio/{anio}/resumen") public ResumenCierre resumenCierre(@PathVariable int anio,Authentication auth) {
    AccesoErp.actor(auth);
    return new ResumenCierre(anio,jdbc.queryForObject(
      "SELECT count(*) FROM mat_matriculas WHERE anio=? AND estado='VIGENTE'",Long.class,anio));
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
  private Matricula obtener(UUID id) { return obtener(id,false); }
  private Matricula obtener(UUID id,boolean bloquear) {
    Matricula m=buscar(id,bloquear);
    if (m==null) throw new IllegalArgumentException("Matrícula no encontrada");
    return m;
  }
  private Matricula buscar(UUID id,boolean bloquear) {
    var filas=jdbc.query("SELECT "+COLUMNAS+" FROM mat_matriculas WHERE id=?"+(bloquear?" FOR UPDATE":""),
      (r,n)->convertir(r),id);
    return filas.isEmpty()?null:filas.getFirst();
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
  private void validarMatriculaAnualExistente(JsonNode datos) {
    String personaId=texto(datos,"personaId");
    if (personaId==null) return;
    int anio=datos.path("anio").asInt(0);
    String seccionId=texto(datos,"seccionId");
    if (seccionId!=null) {
      OfertaConsulta.Seccion seccion=oferta.seccion(uuid(seccionId));
      if (seccion!=null) {
        if (anio>0 && anio!=seccion.anio()) throw new IllegalArgumentException("La sección no pertenece al año seleccionado.");
        anio=seccion.anio();
      }
    }
    if (anio>0) validarMatriculaAnualExistente(uuid(personaId),anio);
  }
  private void validarMatriculaAnualExistente(UUID persona,int anio) {
    if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM mat_matriculas WHERE persona_id=? AND anio=? AND estado IN ('VIGENTE','RETIRADA','FINALIZADA'))",
      Boolean.class,persona,anio)))
      throw new IllegalArgumentException("El estudiante ya tiene una matrícula en "+anio+". Consulta su expediente existente.");
  }
  private RuntimeException conflictoVigente(DataIntegrityViolationException e,int anio) {
    if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("mat_vigente_unico_anio"))
      return new MatriculaVigenteDuplicada("El estudiante ya tiene una matrícula vigente en "+anio+". Consulta su expediente existente.");
    return e;
  }
  private static class MatriculaVigenteDuplicada extends RuntimeException {
    MatriculaVigenteDuplicada(String mensaje) { super(mensaje); }
  }
  @ExceptionHandler(MatriculaVigenteDuplicada.class)
  ResponseEntity<Map<String,String>> duplicada(MatriculaVigenteDuplicada e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo","MATRICULA_VIGENTE_DUPLICADA","mensaje",e.getMessage()));
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
