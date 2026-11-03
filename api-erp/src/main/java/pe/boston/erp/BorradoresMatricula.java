package pe.boston.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorradoresMatricula {
  private final JdbcTemplate db;
  private final ErpV1Service matriculas;
  private final ObjectMapper json=new ObjectMapper();

  public BorradoresMatricula(JdbcTemplate db,ErpV1Service matriculas) {
    this.db=db;this.matriculas=matriculas;
  }

  public record Guardado(int paso,Map<String,Object> datos) {}

  @Transactional public UUID crear(Guardado guardado,Jwt actor) {
    ErpV1Service.escritura(actor);
    validarGuardado(guardado);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO borradores_matricula(id,actor,paso,datos) VALUES (?,?,?,?::jsonb)",
      id,actor.getSubject(),guardado.paso(),serializar(guardado.datos()));
    return id;
  }

  @Transactional public void guardar(UUID id,Guardado guardado,Jwt actor) {
    ErpV1Service.escritura(actor);
    validarGuardado(guardado);
    var borrador=bloquear(id,actor);
    if(borrador.get("matricula_id")!=null) throw new IllegalArgumentException("El borrador ya fue finalizado");
    db.update("UPDATE borradores_matricula SET paso=?,datos=?::jsonb,actualizado_en=now() WHERE id=?",
      guardado.paso(),serializar(guardado.datos()),id);
  }

  public Map<String,Object> ver(UUID id,Jwt actor) {
    ErpV1Service.lectura(actor);
    var borrador=db.queryForMap("""
      SELECT id,actor,paso,datos::text AS datos,matricula_id,actualizado_en
      FROM borradores_matricula WHERE id=? AND actor=?
      """,id,actor.getSubject());
    return Map.of("id",id,"paso",borrador.get("paso"),"datos",leer((String)borrador.get("datos")),
      "finalizado",borrador.get("matricula_id")!=null,
      "matriculaId",borrador.get("matricula_id")==null?"":borrador.get("matricula_id").toString());
  }

  @Transactional public UUID finalizar(UUID id,Jwt actor) {
    ErpV1Service.escritura(actor);
    var borrador=bloquear(id,actor);
    if(borrador.get("matricula_id")!=null) return (UUID)borrador.get("matricula_id");
    Map<String,Object> datos=leer((String)borrador.get("datos"));
    String procedencia=valor(datos,"procedencia");
    if(!List.of("NUEVO","ANTIGUO","TRASLADO","REINCORPORACION").contains(procedencia))
      throw new IllegalArgumentException("Selecciona la modalidad de matrícula");
    UUID periodo=id(datos,"periodoId",true),grado=id(datos,"gradoId",true),seccion=id(datos,"seccionId",false);
    UUID estudiante=Boolean.TRUE.equals(datos.get("usarExistente"))
      ? id(datos,"estudianteId",true) : crearORecuperarSolicitante(objeto(datos,"estudiante"),actor);
    Map<String,Object> responsable=objeto(datos,"apoderado");
    UUID persona=matriculas.crearPersona(new ErpV1Service.PersonaDato(
      valor(responsable,"nombres"),valor(responsable,"apellidos"),
      valor(responsable,"tipoDocumento"),valor(responsable,"documento"),
      valor(responsable,"telefono"),valor(responsable,"correo")),actor);
    String vinculo=valor(datos,"vinculo");
    if(vinculo.isBlank()) throw new IllegalArgumentException("Indica el vínculo del apoderado");
    UUID matricula=matriculas.iniciarMatricula(new ErpV1Service.MatriculaDato(
      estudiante,periodo,grado,seccion,procedencia,valor(datos,"detalle")),actor);
    matriculas.vincular(estudiante,new ErpV1Service.VinculoDato(persona,"PRINCIPAL",vinculo,
      Boolean.TRUE.equals(datos.get("emergencia")),valor(responsable,"telefono"),valor(responsable,"correo")),actor);
    UUID financiero=responsableFinanciero(datos,persona,actor);
    if(financiero!=null) matriculas.editarMatricula(matricula,
      new ErpV1Service.CambioMatricula(grado,seccion,financiero,valor(datos,"detalle")),actor);
    matriculas.confirmar(matricula,true,actor);
    if("REGISTRADO".equals(valor(datos,"pagoEstado"))) {
      BigDecimal monto;
      try { monto=new BigDecimal(valor(datos,"pagoMonto")); }
      catch(NumberFormatException e) { throw new IllegalArgumentException("Indica el monto del pago externo"); }
      matriculas.registrarPagoExterno(matricula,valor(datos,"pagoMedio"),monto,actor);
    } else if(!"PENDIENTE".equals(valor(datos,"pagoEstado")))
      throw new IllegalArgumentException("Selecciona si el pago externo está pendiente o realizado");
    db.update("""
      UPDATE borradores_matricula SET datos='{}'::jsonb,matricula_id=?,finalizado_en=now(),
        actualizado_en=now() WHERE id=?
      """,matricula,id);
    return matricula;
  }

  private UUID crearORecuperarSolicitante(Map<String,Object> alumno,Jwt actor) {
    String tipo=valor(alumno,"tipoDocumento"),documento=valor(alumno,"documento");
    if(tipo.isBlank()!=documento.isBlank()) throw new IllegalArgumentException("Indica tipo y número de documento del alumno juntos");
    LocalDate nacimiento=null;
    try { if(!valor(alumno,"fechaNacimiento").isBlank()) nacimiento=LocalDate.parse(valor(alumno,"fechaNacimiento")); }
    catch(DateTimeParseException e) { throw new IllegalArgumentException("Fecha de nacimiento inválida"); }
    ErpV1Service.EstudianteDato dato=new ErpV1Service.EstudianteDato(
      valor(alumno,"nombres"),valor(alumno,"apellidos"),nacimiento,
      tipo,documento,null,"ACTIVO");
    if(!documento.isBlank()) {
      var encontrados=db.queryForList("""
        SELECT p.id AS persona_id,e.id AS estudiante_id,e.codigo FROM personas p
        LEFT JOIN estudiantes e ON e.persona_id=p.id WHERE p.tipo_documento=? AND p.documento=?
        """,tipo,documento);
      if(!encontrados.isEmpty()) {
        var encontrado=encontrados.getFirst();
        if(encontrado.get("codigo")!=null)
          throw new IllegalArgumentException("El alumno ya está matriculado; selecciona estudiante registrado");
        UUID existente=(UUID)encontrado.get("estudiante_id");
        if(existente!=null) {
          matriculas.editarEstudiante(existente,dato,actor);
          return existente;
        }
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO estudiantes(id,persona_id,estado) VALUES (?,?,'ACTIVO')",id,encontrado.get("persona_id"));
        matriculas.editarEstudiante(id,dato,actor);
        return id;
      }
    }
    return matriculas.crearEstudiante(dato,actor);
  }

  private UUID responsableFinanciero(Map<String,Object> datos,UUID apoderado,Jwt actor) {
    String modo=valor(datos,"responsableFinanciero");
    if(modo.isBlank()||"SIN_DESIGNAR".equals(modo)) return null;
    if("MISMO_APODERADO".equals(modo)) return apoderado;
    if(!"OTRA_PERSONA".equals(modo)) throw new IllegalArgumentException("Responsable financiero inválido");
    Map<String,Object> d=objeto(datos,"financiero");
    return matriculas.crearPersona(new ErpV1Service.PersonaDato(
      valor(d,"nombres"),valor(d,"apellidos"),valor(d,"tipoDocumento"),valor(d,"documento"),
      valor(d,"telefono"),valor(d,"correo")),actor);
  }

  private Map<String,Object> bloquear(UUID id,Jwt actor) {
    return db.queryForMap("""
      SELECT actor,datos::text AS datos,matricula_id FROM borradores_matricula
      WHERE id=? AND actor=? FOR UPDATE
      """,id,actor.getSubject());
  }
  private void validarGuardado(Guardado guardado) {
    if(guardado==null||guardado.datos()==null||guardado.paso()<1||guardado.paso()>4)
      throw new IllegalArgumentException("Borrador inválido");
  }
  private String serializar(Map<String,Object> datos) {
    try { return json.writeValueAsString(datos); }
    catch(JsonProcessingException e) { throw new IllegalArgumentException("Borrador inválido"); }
  }
  @SuppressWarnings("unchecked") private Map<String,Object> leer(String datos) {
    try { return json.readValue(datos,Map.class); }
    catch(JsonProcessingException e) { throw new IllegalArgumentException("Borrador inválido"); }
  }
  @SuppressWarnings("unchecked") private Map<String,Object> objeto(Map<String,Object> datos,String clave) {
    Object valor=datos.get(clave);
    if(!(valor instanceof Map<?,?>)) throw new IllegalArgumentException("Completa los datos de "+clave);
    return (Map<String,Object>)valor;
  }
  private String valor(Map<String,Object> datos,String clave) {
    Object valor=datos.get(clave);
    return valor==null?"":valor.toString().trim();
  }
  private UUID id(Map<String,Object> datos,String clave,boolean requerido) {
    String texto=valor(datos,clave);
    if(texto.isBlank()) {
      if(requerido) throw new IllegalArgumentException("Falta "+clave);
      return null;
    }
    try { return UUID.fromString(texto); }
    catch(IllegalArgumentException e) { throw new IllegalArgumentException("Identificador inválido: "+clave); }
  }
}
