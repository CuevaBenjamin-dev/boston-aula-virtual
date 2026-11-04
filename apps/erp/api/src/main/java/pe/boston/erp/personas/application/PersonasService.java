package pe.boston.erp.personas.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.boston.erp.plataforma.AccesoAcademico;
import pe.boston.erp.personas.Responsables;
import pe.boston.erp.personas.Estudiantes;

/** Escritura de alumnos, identidad civil y vínculos de responsables. */
@Service
public class PersonasService implements Responsables, Estudiantes {
  private final JdbcTemplate db;
  private final ObjectMapper json = new ObjectMapper();
  public PersonasService(JdbcTemplate db) { this.db = db; }

  private String texto(String valor, int max, String campo) {
    if (valor == null || valor.isBlank() || valor.trim().length() > max)
      throw new IllegalArgumentException(campo + " requerido o inválido");
    return valor.trim();
  }

  private String opcional(String valor, int max, String campo) {
    if (valor == null || valor.isBlank()) return null;
    if (valor.trim().length() > max) throw new IllegalArgumentException(campo + " demasiado largo");
    return valor.trim();
  }

  private void auditar(Jwt actor, String accion, UUID id, Object detalle) {
    try {
      db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id,detalle) VALUES (?,?,?,?,?::jsonb)",
        UUID.randomUUID(), actor.getSubject(), accion, id, json.writeValueAsString(detalle));
    } catch (JsonProcessingException e) { throw new IllegalArgumentException("Datos inválidos", e); }
  }

  @Transactional public UUID crear(String nombresDato, String apellidosDato, LocalDate fechaNacimiento,
      String tipoDocumento, String documentoDato, String nacionalidad, Jwt actor) {
    AccesoAcademico.escritura(actor);
    String nombres = texto(nombresDato, 120, "Nombres"), apellidos = texto(apellidosDato, 120, "Apellidos");
    String tipo = opcional(tipoDocumento, 20, "Tipo de documento");
    String documento = opcional(documentoDato, 30, "Documento");
    if ((tipo == null) != (documento == null))
      throw new IllegalArgumentException("Indica tipo y número de documento juntos");
    if (documento != null && db.queryForObject(
        "SELECT count(*) FROM personas WHERE tipo_documento=? AND documento=?", Long.class, tipo, documento) > 0)
      throw new IllegalArgumentException("El documento ya está registrado; busca la persona existente");
    UUID persona = UUID.randomUUID(), estudiante = UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos,fecha_nacimiento,tipo_documento,documento,nacionalidad) VALUES (?,?,?,?,?,?,?)",
      persona, nombres, apellidos, fechaNacimiento, tipo, documento, opcional(nacionalidad, 80, "Nacionalidad"));
    db.update("INSERT INTO estudiantes(id,persona_id,estado) VALUES (?,?,'ACTIVO')", estudiante, persona);
    auditar(actor, "FICHA_PRELIMINAR_CREADA", estudiante, Map.of());
    return estudiante;
  }

  @Transactional public void editar(UUID estudiante, String nombres, String apellidos,
      LocalDate fechaNacimiento, String tipoDocumento, String documentoDato, String nacionalidad, Jwt actor) {
    AccesoAcademico.escritura(actor);
    var anterior = db.queryForMap("SELECT e.codigo,p.id AS persona_id,p.documento FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=? FOR UPDATE OF e,p", estudiante);
    String tipo = opcional(tipoDocumento, 20, "Tipo de documento"), documento = opcional(documentoDato, 30, "Documento");
    if ((tipo == null) != (documento == null))
      throw new IllegalArgumentException("Indica tipo y número de documento juntos");
    db.update("""
      UPDATE personas SET nombres=?,apellidos=?,fecha_nacimiento=?,tipo_documento=?,documento=?,nacionalidad=?,actualizado_en=now()
      WHERE id=?
      """, texto(nombres, 120, "Nombres"), texto(apellidos, 120, "Apellidos"), fechaNacimiento,
      tipo, documento, opcional(nacionalidad, 80, "Nacionalidad"), anterior.get("persona_id"));
    db.update("UPDATE estudiantes SET actualizado_en=now() WHERE id=?", estudiante);
    auditar(actor, "ESTUDIANTE_EDITADO", estudiante, Map.of("preliminar", anterior.get("codigo") == null));
  }

  public List<Map<String,Object>> personas(String tipo, String documento, Jwt actor) {
    AccesoAcademico.lectura(actor);
    if (tipo == null || tipo.isBlank() || documento == null || documento.isBlank()) return List.of();
    return db.queryForList("SELECT p.id,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.telefono,p.correo,a.id AS apoderado_id FROM personas p LEFT JOIN apoderados a ON a.persona_id=p.id WHERE p.tipo_documento=? AND p.documento=?",
      tipo.trim(), documento.trim());
  }

  @Transactional public UUID crearPersona(String nombres, String apellidos, String tipoDocumento,
      String documento, String telefono, String correo, Jwt actor) {
    AccesoAcademico.escritura(actor);
    String tipo = texto(tipoDocumento, 20, "Tipo de documento"), doc = texto(documento, 30, "Documento");
    var existente = personas(tipo, doc, actor);
    if (!existente.isEmpty()) return (UUID) existente.getFirst().get("id");
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos,tipo_documento,documento,telefono,correo) VALUES (?,?,?,?,?,?,?)",
      id, texto(nombres, 120, "Nombres"), texto(apellidos, 120, "Apellidos"), tipo, doc,
      opcional(telefono, 40, "Teléfono"), opcional(correo, 160, "Correo"));
    auditar(actor, "PERSONA_CREADA", id, Map.of("tipoDocumento", tipo));
    return id;
  }

  @Transactional public void vincular(UUID estudiante, UUID persona, String rolDato, String vinculo,
      boolean emergencia, String telefono, String correo, Jwt actor) {
    AccesoAcademico.escritura(actor);
    String rol = texto(rolDato, 24, "Rol");
    if (!List.of("PRINCIPAL", "SEGUNDO", "SUPLENTE").contains(rol))
      throw new IllegalArgumentException("Rol inválido");
    UUID apoderado = db.queryForObject("""
      INSERT INTO apoderados(id,persona_id) VALUES (?,?)
      ON CONFLICT(persona_id) DO UPDATE SET persona_id=EXCLUDED.persona_id RETURNING id
      """, UUID.class, UUID.randomUUID(), persona);
    if ("PRINCIPAL".equals(rol))
      db.update("UPDATE vinculos_estudiante_apoderado SET rol='SEGUNDO' WHERE estudiante_id=? AND rol='PRINCIPAL'", estudiante);
    db.update("""
      INSERT INTO vinculos_estudiante_apoderado(estudiante_id,apoderado_id,vinculo,rol,contacto_emergencia,telefono,correo)
      VALUES (?,?,?,?,?,?,?) ON CONFLICT(estudiante_id,apoderado_id) DO UPDATE SET
      vinculo=EXCLUDED.vinculo,rol=EXCLUDED.rol,contacto_emergencia=EXCLUDED.contacto_emergencia,
      telefono=EXCLUDED.telefono,correo=EXCLUDED.correo,actualizado_en=now()
      """, estudiante, apoderado, texto(vinculo, 40, "Vínculo"), rol, emergencia,
      opcional(telefono, 40, "Teléfono"), opcional(correo, 160, "Correo"));
    auditar(actor, "RESPONSABLE_VINCULADO", estudiante, Map.of("personaId", persona, "rol", rol));
  }

  public List<Map<String,Object>> apoderados(UUID estudiante, Jwt actor) {
    AccesoAcademico.lectura(actor);
    return db.queryForList("""
      SELECT p.id AS persona_id,a.id AS apoderado_id,p.nombres,p.apellidos,p.tipo_documento,p.documento,
        v.rol,v.vinculo,v.contacto_emergencia,coalesce(v.telefono,p.telefono) AS telefono,
        coalesce(v.correo,p.correo) AS correo
      FROM vinculos_estudiante_apoderado v JOIN apoderados a ON a.id=v.apoderado_id
      JOIN personas p ON p.id=a.persona_id WHERE v.estudiante_id=?
      ORDER BY CASE v.rol WHEN 'PRINCIPAL' THEN 0 WHEN 'SEGUNDO' THEN 1 ELSE 2 END,p.apellidos
      """, estudiante);
  }
}
