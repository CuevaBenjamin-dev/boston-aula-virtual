package pe.boston.erp.personas.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.boston.erp.personas.api.PersonaDto;
import pe.boston.erp.personas.api.PersonaDto.*;

@Repository
public class PersonasRepositorio {
  private final JdbcTemplate jdbc;
  public PersonasRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Instant momento() { return jdbc.queryForObject("SELECT now()", java.sql.Timestamp.class).toInstant(); }

  private static Instant instante(ResultSet rs, String campo) throws SQLException {
    var valor = rs.getTimestamp(campo);
    return valor == null ? null : valor.toInstant();
  }

  private static final RowMapper<Resumen> RESUMEN = (rs, n) -> new Resumen(
    rs.getObject("id", UUID.class), rs.getString("nombres"), rs.getString("apellidos"),
    rs.getString("documento"), rs.getString("condiciones") == null ? List.of()
      : List.of(rs.getString("condiciones").split(",")));

  public boolean existe(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM per_personas WHERE id=?)", Boolean.class, id));
  }

  public void bloquear(UUID id) { jdbc.queryForObject("SELECT id FROM per_personas WHERE id=? FOR UPDATE", UUID.class, id); }

  public boolean documentoExiste(String tipo, String pais, String numero) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM per_documentos WHERE tipo_codigo=? AND pais_emisor=? AND numero_normalizado=? AND vigente_hasta IS NULL)",
      Boolean.class, tipo, pais, numero));
  }

  public UUID personaPorDocumento(String tipo,String pais,String numero) {
    var filas=jdbc.query("SELECT persona_id FROM per_documentos WHERE tipo_codigo=? AND pais_emisor=? AND numero_normalizado=? AND vigente_hasta IS NULL LIMIT 1",
      (rs,n)->rs.getObject(1,UUID.class),tipo,pais,numero);
    return filas.isEmpty()?null:filas.getFirst();
  }

  public List<UUID> apoderadosVigentes(UUID estudiante) {
    return jdbc.query("SELECT apoderado_id FROM per_apoderamientos WHERE estudiante_id=? AND periodo @> now() ORDER BY vigente_desde DESC",
      (rs,n)->rs.getObject(1,UUID.class),estudiante);
  }

  public boolean identificacionValida(UUID persona) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM per_documentos WHERE persona_id=? AND periodo @> now())
        OR EXISTS(SELECT 1 FROM per_identificaciones_provisionales i
          JOIN per_condiciones c ON c.persona_id=i.persona_id AND c.tipo='ESTUDIANTE' AND c.periodo @> now()
          WHERE i.persona_id=? AND i.regularizada_en IS NULL)
      """,Boolean.class,persona,persona));
  }

  public boolean tipoDocumentoActivo(String tipo) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM per_tipos_documento WHERE codigo=? AND activo)", Boolean.class, tipo));
  }

  public TipoDocumento tipoDocumento(String codigo) {
    var filas=jdbc.query("SELECT codigo,nombre,patron,activo FROM per_tipos_documento WHERE codigo=?",
      (rs,n)->new TipoDocumento(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),codigo);
    return filas.isEmpty()?null:filas.getFirst();
  }

  public List<TipoDocumento> tiposDocumento() {
    return jdbc.query("SELECT codigo,nombre,patron,activo FROM per_tipos_documento ORDER BY codigo",
      (rs,n) -> new TipoDocumento(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)));
  }

  public void crearTipo(TipoDocumento t) {
    jdbc.update("INSERT INTO per_tipos_documento(codigo,nombre,patron,activo) VALUES(?,?,?,?)",
      t.codigo(),t.nombre(),t.patron(),t.activo());
  }

  public Pagina buscar(String texto, String condicion, Boolean vigente, int pagina, int tamano) {
    var donde = new StringBuilder(" WHERE 1=1");
    var params = new ArrayList<Object>();
    if (texto != null && !texto.isBlank()) {
      donde.append(" AND (lower(p.nombres || ' ' || p.apellidos) LIKE ? OR EXISTS (SELECT 1 FROM per_documentos d WHERE d.persona_id=p.id AND d.vigente_hasta IS NULL AND d.numero_normalizado=?))");
      params.add("%" + texto.trim().toLowerCase() + "%"); params.add(texto.trim());
    }
    if (condicion != null && !condicion.isBlank()) {
      if ("APODERADO".equals(condicion)) {
        donde.append(" AND EXISTS (SELECT 1 FROM per_apoderamientos a WHERE a.apoderado_id=p.id");
        if (Boolean.TRUE.equals(vigente)) donde.append(" AND a.periodo @> now()");
        if (Boolean.FALSE.equals(vigente)) donde.append(" AND NOT a.periodo @> now()");
        donde.append(")");
      } else {
        donde.append(" AND EXISTS (SELECT 1 FROM per_condiciones c WHERE c.persona_id=p.id AND c.tipo=?");
        params.add(condicion);
        if (Boolean.TRUE.equals(vigente)) donde.append(" AND c.periodo @> now()");
        if (Boolean.FALSE.equals(vigente)) donde.append(" AND NOT c.periodo @> now()");
        donde.append(")");
      }
    }
    long total = jdbc.queryForObject("SELECT count(*) FROM per_personas p" + donde, Long.class, params.toArray());
    var select = "SELECT p.id,p.nombres,p.apellidos,(SELECT d.numero_normalizado FROM per_documentos d WHERE d.persona_id=p.id AND d.principal AND d.vigente_hasta IS NULL LIMIT 1) documento, "
      + "(SELECT string_agg(DISTINCT x.tipo, ',') FROM (SELECT c.tipo FROM per_condiciones c WHERE c.persona_id=p.id AND c.periodo @> now() UNION ALL SELECT 'APODERADO' FROM per_apoderamientos a WHERE a.apoderado_id=p.id AND a.periodo @> now()) x) condiciones "
      + "FROM per_personas p" + donde + " ORDER BY p.apellidos,p.nombres,p.id LIMIT ? OFFSET ?";
    params.add(tamano); params.add(pagina * tamano);
    return new Pagina(jdbc.query(select, RESUMEN, params.toArray()), total, pagina, tamano);
  }

  public Persona ficha(UUID id) {
    var filas = jdbc.query("SELECT * FROM per_personas WHERE id=?", (rs,n) -> new Object[]{
      rs.getString("nombres"),rs.getString("apellidos"),rs.getObject("fecha_nacimiento", LocalDate.class),
      rs.getString("sexo"),rs.getString("nacionalidad"),rs.getString("telefono"),rs.getString("correo"),rs.getString("direccion")}, id);
    if (filas.isEmpty()) return null;
    var p = filas.getFirst();
    var docs = jdbc.query("SELECT * FROM per_documentos WHERE persona_id=? ORDER BY vigente_desde DESC",
      (rs,n) -> new Documento(rs.getObject("id",UUID.class),rs.getString("tipo_codigo"),
        rs.getString("pais_emisor").trim(),rs.getString("numero"),rs.getBoolean("principal"),
        instante(rs,"vigente_desde"),instante(rs,"vigente_hasta")), id);
    var condiciones = jdbc.query("SELECT * FROM per_condiciones WHERE persona_id=? ORDER BY vigente_desde DESC",
      (rs,n) -> new Condicion(rs.getObject("id",UUID.class),rs.getString("tipo"),
        instante(rs,"vigente_desde"),instante(rs,"vigente_hasta"),rs.getString("motivo_desactivacion")), id);
    var provisionales = jdbc.query("SELECT * FROM per_identificaciones_provisionales WHERE persona_id=? ORDER BY registrada_en DESC LIMIT 1",
      (rs,n) -> new Provisional(rs.getString("motivo"),rs.getString("sustento_tipo"),
        rs.getString("sustento_referencia"),instante(rs,"registrada_en"),instante(rs,"regularizada_en")), id);
    var parentescos = jdbc.query("SELECT * FROM per_parentescos WHERE estudiante_id=? OR familiar_id=? ORDER BY vigente_desde DESC",
      (rs,n) -> new Parentesco(rs.getObject("id",UUID.class),rs.getObject("estudiante_id",UUID.class),
        rs.getObject("familiar_id",UUID.class),rs.getString("tipo"),instante(rs,"vigente_desde"),instante(rs,"vigente_hasta")), id,id);
    var apoderamientos = jdbc.query("SELECT * FROM per_apoderamientos WHERE estudiante_id=? OR apoderado_id=? ORDER BY vigente_desde DESC",
      (rs,n) -> new Apoderamiento(rs.getObject("id",UUID.class),rs.getObject("estudiante_id",UUID.class),
        rs.getObject("apoderado_id",UUID.class),rs.getBoolean("sin_parentesco"),rs.getString("autorizador"),
        rs.getString("medio_verificacion"),instante(rs,"verificado_en"),
        instante(rs,"vigente_desde"),instante(rs,"vigente_hasta")), id,id);
    return new Persona(id,(String)p[0],(String)p[1],(LocalDate)p[2],(String)p[3],(String)p[4],
      (String)p[5],(String)p[6],(String)p[7],docs,condiciones,
      provisionales.isEmpty()?null:provisionales.getFirst(),parentescos,apoderamientos);
  }

  public void crearPersona(UUID id, CrearPersona p) {
    jdbc.update("INSERT INTO per_personas(id,nombres,apellidos,fecha_nacimiento,sexo,nacionalidad,telefono,correo,direccion) VALUES(?,?,?,?,?,?,?,?,?)",
      id,p.nombres().trim(),p.apellidos().trim(),p.fechaNacimiento(),p.sexo().trim(),p.nacionalidad().trim(),
      p.telefono(),p.correo(),p.direccion().trim());
  }

  public void editar(UUID id, EditarPersona p) {
    jdbc.update("UPDATE per_personas SET nombres=?,apellidos=?,fecha_nacimiento=?,sexo=?,nacionalidad=?,telefono=?,correo=?,direccion=?,actualizado_en=now() WHERE id=?",
      p.nombres(),p.apellidos(),p.fechaNacimiento(),p.sexo(),p.nacionalidad(),p.telefono(),p.correo(),p.direccion(),id);
  }

  public void contacto(UUID id, Contacto p) {
    jdbc.update("UPDATE per_personas SET telefono=?,correo=?,direccion=?,actualizado_en=now() WHERE id=?",
      p.telefono(),p.correo(),p.direccion(),id);
  }

  public void documento(UUID persona, DocumentoEntrada d, String normalizado, Instant desde) {
    jdbc.update("INSERT INTO per_documentos(id,persona_id,tipo_codigo,pais_emisor,numero,numero_normalizado,principal,vigente_desde) VALUES(?,?,?,?,?,?,true,?)",
      UUID.randomUUID(),persona,d.tipo(),d.pais(),normalizado,normalizado,java.sql.Timestamp.from(desde));
  }

  public void cerrarDocumentoPrincipal(UUID persona, Instant hasta) {
    jdbc.update("UPDATE per_documentos SET vigente_hasta=? WHERE persona_id=? AND principal AND vigente_hasta IS NULL",
      java.sql.Timestamp.from(hasta),persona);
  }

  public void provisional(UUID persona, ProvisionalEntrada p) {
    jdbc.update("INSERT INTO per_identificaciones_provisionales(id,persona_id,motivo,sustento_tipo,sustento_referencia) VALUES(?,?,?,?,?)",
      UUID.randomUUID(),persona,p.motivo(),p.sustentoTipo(),p.sustentoReferencia());
  }

  public void regularizar(UUID persona, Instant instante) {
    jdbc.update("UPDATE per_identificaciones_provisionales SET regularizada_en=? WHERE persona_id=? AND regularizada_en IS NULL",
      java.sql.Timestamp.from(instante),persona);
  }

  public void condicion(UUID persona, String tipo, Instant desde) {
    jdbc.update("INSERT INTO per_condiciones(id,persona_id,tipo,vigente_desde) VALUES(?,?,?,?)",
      UUID.randomUUID(),persona,tipo,java.sql.Timestamp.from(desde));
  }

  public boolean condicionActiva(UUID persona, String tipo) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM per_condiciones WHERE persona_id=? AND tipo=? AND periodo @> now())",
      Boolean.class,persona,tipo));
  }

  public Condicion condicion(UUID persona, UUID id) {
    var filas = jdbc.query("SELECT id,tipo,vigente_desde,vigente_hasta,motivo_desactivacion FROM per_condiciones WHERE id=? AND persona_id=?",
      (rs,n)-> new Condicion(rs.getObject(1,UUID.class),rs.getString(2),instante(rs,"vigente_desde"),
        instante(rs,"vigente_hasta"),rs.getString(5)),id,persona);
    return filas.isEmpty()?null:filas.getFirst();
  }

  public void cerrarCondicion(UUID id, String motivo, Instant hasta) {
    jdbc.update("UPDATE per_condiciones SET vigente_hasta=?,cierre_manual=true,motivo_desactivacion=? WHERE id=? AND vigente_hasta IS NULL",
      java.sql.Timestamp.from(hasta),motivo,id);
  }

  public Parentesco parentesco(UUID estudiante, UUID familiar, String tipo) {
    var filas=jdbc.query("SELECT * FROM per_parentescos WHERE estudiante_id=? AND familiar_id=? AND tipo=? AND vigente_hasta IS NULL",
      (rs,n)->new Parentesco(rs.getObject("id",UUID.class),estudiante,familiar,tipo,
        instante(rs,"vigente_desde"),instante(rs,"vigente_hasta")),estudiante,familiar,tipo);
    return filas.isEmpty()?null:filas.getFirst();
  }

  public void parentesco(UUID id, ParentescoEntrada p, Instant desde) {
    jdbc.update("INSERT INTO per_parentescos(id,estudiante_id,familiar_id,tipo,vigente_desde) VALUES(?,?,?,?,?)",
      id,p.estudianteId(),p.familiarId(),p.tipo(),java.sql.Timestamp.from(desde));
  }

  public void cerrarParentesco(UUID id, Instant hasta) {
    jdbc.update("UPDATE per_parentescos SET vigente_hasta=? WHERE id=? AND vigente_hasta IS NULL",java.sql.Timestamp.from(hasta),id);
  }

  public void apoderamiento(UUID id, ApoderamientoEntrada p, String verificador, Instant desde) {
    var a=p.autorizacion();
    jdbc.update("INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco,autorizador,verificador_sub,medio_verificacion,verificado_en,sustento_tipo,sustento_referencia,vigente_desde) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
      id,p.estudianteId(),p.apoderadoId(),p.sinParentesco(),a==null?null:a.autorizador(),
      p.sinParentesco()?verificador:null,a==null?null:a.medio(),
      a==null||a.fecha()==null?null:java.sql.Timestamp.from(a.fecha()),
      p.sinParentesco()?(a.sustentoTipo()==null?"VERBAL":a.sustentoTipo().trim()):null,
      a==null?null:a.sustentoReferencia(),java.sql.Timestamp.from(desde));
  }

  public boolean apoderamientoActivo(UUID estudiante, UUID apoderado) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM per_apoderamientos WHERE estudiante_id=? AND apoderado_id=? AND vigente_hasta IS NULL)",Boolean.class,estudiante,apoderado));
  }

  public void cerrarApoderamiento(UUID id, UUID persona, String motivo, Instant hasta) {
    jdbc.update("UPDATE per_apoderamientos SET vigente_hasta=?,motivo_desactivacion=? WHERE id=? AND (estudiante_id=? OR apoderado_id=?) AND vigente_hasta IS NULL",
      java.sql.Timestamp.from(hasta),motivo,id,persona,persona);
  }

  public void evento(UUID persona, UUID recurso, String accion, String actor) {
    jdbc.update("INSERT INTO per_eventos(id,persona_id,recurso_id,accion,actor_sub) VALUES(?,?,?,?,?)",
      UUID.randomUUID(),persona,recurso,accion,actor);
  }
}
