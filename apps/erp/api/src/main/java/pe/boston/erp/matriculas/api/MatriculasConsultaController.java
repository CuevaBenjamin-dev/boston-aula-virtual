package pe.boston.erp.matriculas.api;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.personas.PersonasParaMatriculas;
import pe.boston.erp.plataforma.AccesoErp;

/** Consultas de apoyo a la atención de matrículas. No crean ni modifican registros. */
@RestController
@RequestMapping("/api/erp/v1/matriculas")
public class MatriculasConsultaController {
  public record Expediente(UUID id, String estado) {}
  public record Contexto(UUID personaId, boolean estudianteValido, Boolean menor,
      List<Expediente> expedientes, List<UUID> apoderados) {}
  private final JdbcTemplate jdbc;
  private final PersonasParaMatriculas personas;
  public MatriculasConsultaController(JdbcTemplate jdbc, PersonasParaMatriculas personas) {
    this.jdbc=jdbc; this.personas=personas;
  }
  @GetMapping("/resumen") public Map<String,Long> resumen(@RequestParam(required=false) Integer anio, Authentication auth) {
    AccesoErp.actor(auth); validarAnio(anio);
    var salida=new LinkedHashMap<String,Long>();
    for(String estado:List.of("BORRADOR","VIGENTE","RETIRADA","FINALIZADA")) salida.put(estado,0L);
    String sql="SELECT estado,count(*) FROM mat_matriculas";
    Object[] args=anio==null?new Object[]{}:new Object[]{anio.toString()};
    if(anio!=null) sql+=" WHERE COALESCE(anio::text,borrador->>'anio')=?";
    jdbc.query(sql+" GROUP BY estado",r->{salida.put(r.getString(1),r.getLong(2));},args);
    return salida;
  }
  @GetMapping("/contexto") public Contexto contexto(@RequestParam(required=false) UUID personaId,
      @RequestParam(required=false) String tipo,@RequestParam(required=false) String pais,
      @RequestParam(required=false) String numero,@RequestParam(required=false) Integer anio,Authentication auth) {
    AccesoErp.actor(auth); validarAnio(anio);
    boolean documentoCompleto=tipo!=null&&pais!=null&&numero!=null&&!numero.isBlank();
    if(personaId==null&&!documentoCompleto) throw new IllegalArgumentException("Completa el documento para consultar la identidad");
    if(documentoCompleto) {
      UUID hallada=personas.buscarPorDocumento(tipo,pais,numero,auth);
      if(personaId!=null&&!personaId.equals(hallada)) throw new IllegalArgumentException("El documento no corresponde a la persona seleccionada");
      personaId=hallada;
    }
    // La consulta documental sin año se usa al elegir un apoderado, no devuelve sus matrículas.
    if(anio==null) return new Contexto(personaId,false,null,List.of(),List.of());
    var expedientes=new java.util.ArrayList<Expediente>();
    if(personaId!=null) expedientes.addAll(jdbc.query("""
      SELECT id,estado FROM mat_matriculas
      WHERE COALESCE(anio::text,borrador->>'anio')=?
      AND COALESCE(persona_id::text,borrador->>'personaId')=?
      ORDER BY creado_en DESC,id DESC
      """,(r,n)->new Expediente(r.getObject(1,UUID.class),r.getString(2)),anio.toString(),personaId.toString()));
    // Borradores nuevos aún sin Persona: coincidencia documental exacta, no por nombre.
    if(documentoCompleto) {
      var borradores=jdbc.query("""
        SELECT id,estado FROM mat_matriculas WHERE estado='BORRADOR' AND persona_id IS NULL
        AND NULLIF(borrador->>'personaId','') IS NULL AND borrador->>'anio'=?
        AND trim(borrador->>'tipoDocumento')=trim(?)
        AND trim(borrador->>'paisEmisor')=trim(?)
        AND trim(borrador->>'numeroDocumento')=trim(?)
        ORDER BY creado_en DESC,id DESC
        """,(r,n)->new Expediente(r.getObject(1,UUID.class),r.getString(2)),anio.toString(),tipo,pais,numero);
      expedientes.addAll(borradores);
    }
    if(personaId==null) return new Contexto(null,false,null,expedientes,List.of());
    LocalDate nacimiento=personas.fechaNacimiento(personaId,auth);
    Boolean menor=nacimiento==null?null:Period.between(nacimiento,LocalDate.now()).getYears()<18;
    boolean valido=nacimiento!=null&&personas.esEstudianteActivo(personaId,auth)&&personas.tieneIdentificacionValida(personaId,auth);
    return new Contexto(personaId,valido,menor,expedientes,personas.apoderadosVigentes(personaId,auth));
  }
  private static void validarAnio(Integer anio) {
    if(anio!=null&&(anio<2000||anio>2100)) throw new IllegalArgumentException("Año escolar inválido");
  }
}
