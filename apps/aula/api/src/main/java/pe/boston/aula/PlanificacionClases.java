package pe.boston.aula;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service class PlanificacionClases {
  static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));
  private final JdbcTemplate db;
  PlanificacionClases(JdbcTemplate db) { this.db=db; }
  void validar(String tema,OffsetDateTime inicio,OffsetDateTime fin) {
    if(tema==null||tema.isBlank()||tema.length()>200) throw new IllegalArgumentException("Tema inválido");
    if(inicio==null||fin==null||!fin.isAfter(inicio)) throw new IllegalArgumentException("Horario inválido");
  }
  String validarEnlace(String enlace) {
    if(enlace==null||enlace.isBlank()) throw new IllegalArgumentException("Ingresa un enlace");
    return enlace.trim();
  }
  String huella(List<UUID> cursos,String tema,OffsetDateTime inicio,OffsetDateTime fin,String enlace) {
    String datos=cursos+"|"+tema.trim()+"|"+inicio.toInstant()+"|"+fin.toInstant()+"|"+enlace;
    return resumen(datos);
  }
  String huellaProgramaciones(List<Clases.Programacion> planes,boolean grupal) {
    StringBuilder datos=new StringBuilder("programaciones|"+grupal);
    for(Clases.Programacion p:planes) datos.append('|').append(p.cursoId()).append('|').append(p.tema()).append('|')
      .append(p.inicio().toInstant()).append('|').append(p.fin().toInstant()).append('|').append(p.enlace());
    return resumen(datos.toString());
  }
  String resumen(String datos) {
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8))); }
    catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  void bloquear(List<String> claves) {
    claves.stream().distinct().sorted().forEach(clave->db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",clave));
  }
  List<String> claves(UUID operacion,UUID docente,List<UUID> cursos) {
    var claves=new ArrayList<String>(); claves.add("operacion:"+operacion); claves.add("docente:"+docente);
    for(UUID curso:cursos) claves.add("seccion:"+db.queryForObject("SELECT seccion_erp_id FROM cursos WHERE id=?",UUID.class,curso));
    return claves;
  }
  List<Map<String,Object>> conflictos(List<UUID> cursos,OffsetDateTime inicio,OffsetDateTime fin,UUID excluir,UUID docente) {
    String plazas=String.join(",",java.util.Collections.nCopies(cursos.size(),"?"));
    String sql="""
      SELECT DISTINCT c.id,c.tema,c.inicio,c.fin,curso.asignatura,curso.grado,curso.seccion
      FROM clases_academicas c JOIN cursos curso ON curso.id=c.curso_id
      WHERE c.estado<>'CANCELADA' AND c.inicio<? AND c.fin>?
        AND (curso.seccion_erp_id IN (SELECT seccion_erp_id FROM cursos WHERE id IN (%s))
          OR c.docente_erp_id=?
          OR EXISTS (SELECT 1 FROM docentes_curso d WHERE d.curso_id=c.curso_id AND d.docente_erp_id=? AND d.activo))
      """.formatted(plazas);
    var parametros=new ArrayList<Object>(); parametros.add(fin); parametros.add(inicio); parametros.addAll(cursos); parametros.add(docente); parametros.add(docente);
    if(excluir!=null) { sql+=" AND c.id<>?"; parametros.add(excluir); }
    return db.queryForList(sql+" ORDER BY c.inicio",parametros.toArray());
  }
  void exigirConfirmacion(List<Map<String,Object>> conflictos,int secciones,boolean confirmado) {
    if(!confirmado&&(!conflictos.isEmpty()||secciones>1)) {
      String detalle=conflictos.isEmpty()?"el mismo docente dicta en varias secciones a esa hora":
        conflictos.stream().distinct().limit(3).map(c->"«"+c.get("tema")+"» · "+c.get("asignatura")+" "+c.get("grado")+" "+c.get("seccion")+
          " · "+FECHA.format(((Timestamp)c.get("inicio")).toInstant())+" a "+FECHA.format(((Timestamp)c.get("fin")).toInstant()))
          .collect(java.util.stream.Collectors.joining("; "));
      throw new IllegalStateException("CONFLICTO_HORARIO: Coincide con "+detalle+". ¿Guardar de todos modos?");
    }
  }
}
