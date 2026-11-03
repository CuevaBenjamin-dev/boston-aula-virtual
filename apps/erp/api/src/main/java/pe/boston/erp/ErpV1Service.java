package pe.boston.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.boston.erp.plataforma.AccesoAcademico;

@Service
public class ErpV1Service {
  private final JdbcTemplate db;
  private final Institucional eventos;
  private final DirectorioIdentidad identidad;
  private final ObjectMapper json;
  public ErpV1Service(JdbcTemplate db,Institucional eventos,DirectorioIdentidad identidad) { this.db=db;this.eventos=eventos;this.identidad=identidad;this.json=new ObjectMapper(); }

  public static void lectura(Jwt actor) {
    AccesoAcademico.lectura(actor);
  }
  public static void escritura(Jwt actor) { AccesoAcademico.escritura(actor); }
  public static void direccion(Jwt actor) { AccesoAcademico.direccion(actor); }
  private String texto(String valor,int max,String campo) {
    if(valor==null||valor.isBlank()||valor.trim().length()>max) throw new IllegalArgumentException(campo+" requerido o inválido");
    return valor.trim();
  }
  private String opcional(String valor,int max,String campo) {
    if(valor==null||valor.isBlank()) return null;
    if(valor.trim().length()>max) throw new IllegalArgumentException(campo+" demasiado largo");
    return valor.trim();
  }
  private String json(Object dato) {
    try { return json.writeValueAsString(dato); }
    catch(JsonProcessingException e) { throw new IllegalArgumentException("Datos inválidos"); }
  }
  private void auditar(Jwt actor,String accion,UUID id,Object detalle) {
    db.update("INSERT INTO auditoria_erp(id,actor,accion,entidad_id,detalle) VALUES (?,?,?,?,?::jsonb)",
      UUID.randomUUID(),actor.getSubject(),accion,id,json(detalle));
  }
  private void historial(UUID matricula,Jwt actor,String accion,Object antes,Object despues) {
    db.update("INSERT INTO historial_matriculas(id,matricula_id,actor,accion,antes,despues) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
      UUID.randomUUID(),matricula,actor.getSubject(),accion,antes==null?null:json(antes),despues==null?null:json(despues));
    auditar(actor,"MATRICULA_"+accion,matricula,despues==null?Map.of():despues);
  }

  public record EstudianteDato(String nombres,String apellidos,LocalDate fechaNacimiento,String tipoDocumento,String documento,
    String nacionalidad,String estado) {}
  @Transactional public UUID crearEstudiante(EstudianteDato d,Jwt actor) {
    escritura(actor);
    String nombres=texto(d.nombres(),120,"Nombres"), apellidos=texto(d.apellidos(),120,"Apellidos");
    String tipo=opcional(d.tipoDocumento(),20,"Tipo de documento");
    String documento=opcional(d.documento(),30,"Documento");
    if((tipo==null)!=(documento==null)) throw new IllegalArgumentException("Indica tipo y número de documento juntos");
    if(documento!=null && db.queryForObject("SELECT count(*) FROM personas WHERE tipo_documento=? AND documento=?",Long.class,tipo,documento)>0)
      throw new IllegalArgumentException("El documento ya está registrado; busca la persona existente");
    UUID persona=UUID.randomUUID(), estudiante=UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos,fecha_nacimiento,tipo_documento,documento,nacionalidad) VALUES (?,?,?,?,?,?,?)",
      persona,nombres,apellidos,d.fechaNacimiento(),tipo,documento,opcional(d.nacionalidad(),80,"Nacionalidad"));
    db.update("INSERT INTO estudiantes(id,persona_id,estado) VALUES (?,?,'ACTIVO')",estudiante,persona);
    auditar(actor,"FICHA_PRELIMINAR_CREADA",estudiante,Map.of());
    return estudiante;
  }
  public List<Map<String,Object>> candidatosMatricula(String buscar,Jwt actor) {
    lectura(actor);
    if(buscar==null||buscar.trim().length()<2) return List.of();
    String q="%"+buscar.trim()+"%";
    return db.queryForList("""
      SELECT e.id,e.codigo,p.nombres,p.apellidos,p.tipo_documento,p.documento,
        EXISTS(SELECT 1 FROM matriculas m WHERE m.estudiante_id=e.id AND m.estado='MATRICULADO') AS matriculado,
        (SELECT m.id FROM matriculas m WHERE m.estudiante_id=e.id
          AND m.estado IN ('PROCESO_PENDIENTE','VACANTE_RESERVADA') ORDER BY m.creado_en DESC LIMIT 1) AS proceso_activo_id,
        (SELECT m.estado FROM matriculas m WHERE m.estudiante_id=e.id ORDER BY m.creado_en DESC LIMIT 1) AS ultimo_estado
      FROM estudiantes e JOIN personas p ON p.id=e.persona_id
      WHERE e.codigo ILIKE ? OR p.nombres ILIKE ? OR p.apellidos ILIKE ? OR p.documento ILIKE ?
      ORDER BY CASE WHEN p.documento=? OR e.codigo=? THEN 0 ELSE 1 END,p.apellidos,p.nombres LIMIT 50
      """,q,q,q,q,buscar.trim(),buscar.trim());
  }
  public Map<String,Object> verificarEstudiante(String tipo,String documento,Jwt actor) {
    lectura(actor);
    if(tipo==null||tipo.isBlank()||documento==null||documento.isBlank())
      throw new IllegalArgumentException("Indica tipo y número de documento para verificar");
    var ids=db.query("""
      SELECT e.id FROM estudiantes e JOIN personas p ON p.id=e.persona_id
      WHERE p.tipo_documento=? AND p.documento=?
      """,(rs,n)->(UUID)rs.getObject(1),tipo.trim(),documento.trim());
    return ids.isEmpty()?Map.of("encontrado",false):resumenVerificacion(ids.getFirst(),actor);
  }
  public Map<String,Object> verificarEstudiante(UUID id,Jwt actor) {
    lectura(actor);
    return resumenVerificacion(id,actor);
  }
  private Map<String,Object> resumenVerificacion(UUID id,Jwt actor) {
    var estudiante=db.queryForMap("""
      SELECT e.id,e.codigo,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.fecha_nacimiento
      FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=?
      """,id);
    LocalDate hoy=LocalDate.now(ZoneId.of("America/Lima"));
    LocalDate nacimiento=fechaSql(estudiante.get("fecha_nacimiento"));
    estudiante.put("edad",nacimiento==null||nacimiento.isAfter(hoy)?null:java.time.Period.between(nacimiento,hoy).getYears());
    var matriculas=db.queryForList("""
      SELECT m.id,m.procedencia,m.confirmado_en,m.actualizado_en,m.vigente,m.cierre_motivo,
        m.contactos_snapshot::text AS contactos,per.id AS periodo_id,per.nombre AS periodo,
        per.inicio AS ciclo_inicio,per.fin AS ciclo_fin,g.nombre AS grado,s.nombre AS seccion
      FROM matriculas m JOIN periodos_academicos per ON per.id=m.periodo_id
      LEFT JOIN grados g ON g.id=m.grado_id LEFT JOIN secciones s ON s.id=m.seccion_id
      WHERE m.estudiante_id=? AND m.estado='MATRICULADO'
      ORDER BY per.inicio DESC,m.confirmado_en DESC
      """,id);
    List<Map<String,Object>> historial=new ArrayList<>();
    long dias=0;
    for(var m:matriculas) {
      LocalDate inicio=fechaSql(m.get("ciclo_inicio")),fin=fechaSql(m.get("ciclo_fin"));
      LocalDate confirmado=fechaSql(m.get("confirmado_en"));
      LocalDate desde=confirmado==null?inicio:(confirmado.isAfter(inicio)?confirmado:inicio);
      LocalDate hasta=fin.isBefore(hoy)?fin:hoy;
      if(Boolean.FALSE.equals(m.get("vigente"))) {
        LocalDate cierre=fechaSql(m.get("actualizado_en"));
        if(cierre!=null&&cierre.isBefore(hasta)) hasta=cierre;
      }
      if(hasta.isBefore(desde)) hasta=desde;
      boolean completo=!fin.isAfter(hoy)&&!desde.isAfter(inicio)
        &&(Boolean.TRUE.equals(m.get("vigente"))||!fechaSql(m.get("actualizado_en")).isBefore(fin));
      dias+=ChronoUnit.DAYS.between(desde,hasta)+1;
      Map<String,Object> fila=new LinkedHashMap<>();
      fila.put("periodo",m.get("periodo"));fila.put("grado",m.get("grado"));fila.put("seccion",m.get("seccion"));
      fila.put("desde",desde);fila.put("hasta",hasta);fila.put("cicloCompleto",completo);
      fila.put("enCurso",fin.isAfter(hoy)&&Boolean.TRUE.equals(m.get("vigente")));
      historial.add(fila);
    }
    Map<String,Object> apoderado=null;
    if(!matriculas.isEmpty()) {
      try {
        @SuppressWarnings("unchecked") List<Map<String,Object>> snapshot=json.readValue((String)matriculas.getFirst().get("contactos"),List.class);
        apoderado=snapshot.stream().filter(x->"PRINCIPAL".equals(x.get("rol"))).findFirst()
          .orElse(snapshot.isEmpty()?null:snapshot.getFirst());
      } catch(JsonProcessingException e) { throw new IllegalArgumentException("No se pudo leer el historial del apoderado"); }
    }
    if(apoderado==null) {
      var actuales=apoderados(id,actor);
      if(!actuales.isEmpty()) apoderado=actuales.getFirst();
    }
    var procesos=db.queryForList("""
      SELECT m.id,m.estado,m.periodo_id,per.nombre AS periodo FROM matriculas m
      JOIN periodos_academicos per ON per.id=m.periodo_id
      WHERE m.estudiante_id=? AND m.estado IN ('PROCESO_PENDIENTE','VACANTE_RESERVADA')
      ORDER BY m.creado_en DESC LIMIT 1
      """,id);
    Map<String,Object> respuesta=new LinkedHashMap<>();
    respuesta.put("encontrado",true);respuesta.put("estudiante",estudiante);
    respuesta.put("matriculado",!matriculas.isEmpty());respuesta.put("historial",historial);
    respuesta.put("diasRegistrados",dias);respuesta.put("apoderado",apoderado);
    respuesta.put("procesoActivo",procesos.isEmpty()?null:procesos.getFirst());
    return respuesta;
  }
  private LocalDate fechaSql(Object dato) {
    if(dato==null) return null;
    if(dato instanceof LocalDate fecha) return fecha;
    if(dato instanceof java.sql.Timestamp instante) return instante.toInstant().atZone(ZoneId.of("America/Lima")).toLocalDate();
    if(dato instanceof java.time.OffsetDateTime instante) return instante.atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate();
    return LocalDate.parse(dato.toString().substring(0,10));
  }
  public Map<String,Object> estudiante(UUID id,Jwt actor) {
    lectura(actor);
    var ficha=db.queryForMap("""
      SELECT e.id,e.codigo,e.estado,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.fecha_nacimiento,
        p.nacionalidad,e.creado_en,e.actualizado_en FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=?
      """,id);
    ficha.put("matriculas",db.queryForList("""
      SELECT m.id,m.estado,m.procedencia,m.periodo_id,per.nombre AS periodo,g.nombre AS grado,s.nombre AS seccion,
        m.creado_en,m.confirmado_en,m.vigente FROM matriculas m JOIN periodos_academicos per ON per.id=m.periodo_id
      LEFT JOIN grados g ON g.id=m.grado_id LEFT JOIN secciones s ON s.id=m.seccion_id
      WHERE m.estudiante_id=? ORDER BY per.inicio DESC
      """,id));
    ficha.put("apoderados",apoderados(id,actor));
    return ficha;
  }
  @Transactional public void editarEstudiante(UUID id,EstudianteDato d,Jwt actor) {
    escritura(actor);
    var anterior=db.queryForMap("SELECT e.codigo,p.id AS persona_id,p.documento FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=? FOR UPDATE OF e,p",id);
    String tipo=opcional(d.tipoDocumento(),20,"Tipo de documento"),documento=opcional(d.documento(),30,"Documento");
    if((tipo==null)!=(documento==null)) throw new IllegalArgumentException("Indica tipo y número de documento juntos");
    db.update("""
      UPDATE personas SET nombres=?,apellidos=?,fecha_nacimiento=?,tipo_documento=?,documento=?,nacionalidad=?,actualizado_en=now()
      WHERE id=?
      """,texto(d.nombres(),120,"Nombres"),texto(d.apellidos(),120,"Apellidos"),d.fechaNacimiento(),tipo,documento,
      opcional(d.nacionalidad(),80,"Nacionalidad"),anterior.get("persona_id"));
    db.update("UPDATE estudiantes SET actualizado_en=now() WHERE id=?",id);
    auditar(actor,"ESTUDIANTE_EDITADO",id,Map.of("preliminar",anterior.get("codigo")==null));
  }

  public record PersonaDato(String nombres,String apellidos,String tipoDocumento,String documento,String telefono,String correo) {}
  public List<Map<String,Object>> personas(String tipo,String documento,Jwt actor) {
    lectura(actor);
    if(tipo==null||tipo.isBlank()||documento==null||documento.isBlank()) return List.of();
    return db.queryForList("SELECT p.id,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.telefono,p.correo,a.id AS apoderado_id FROM personas p LEFT JOIN apoderados a ON a.persona_id=p.id WHERE p.tipo_documento=? AND p.documento=?",
      tipo.trim(),documento.trim());
  }
  @Transactional public UUID crearPersona(PersonaDato d,Jwt actor) {
    escritura(actor);
    String tipo=texto(d.tipoDocumento(),20,"Tipo de documento"),doc=texto(d.documento(),30,"Documento");
    var existente=personas(tipo,doc,actor);
    if(!existente.isEmpty()) return (UUID)existente.getFirst().get("id");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO personas(id,nombres,apellidos,tipo_documento,documento,telefono,correo) VALUES (?,?,?,?,?,?,?)",
      id,texto(d.nombres(),120,"Nombres"),texto(d.apellidos(),120,"Apellidos"),tipo,doc,
      opcional(d.telefono(),40,"Teléfono"),opcional(d.correo(),160,"Correo"));
    auditar(actor,"PERSONA_CREADA",id,Map.of("tipoDocumento",tipo));
    return id;
  }
  public record VinculoDato(UUID personaId,String rol,String vinculo,boolean emergencia,String telefono,String correo) {}
  @Transactional public void vincular(UUID estudiante,VinculoDato d,Jwt actor) {
    escritura(actor);
    String rol=texto(d.rol(),24,"Rol");
    if(!List.of("PRINCIPAL","SEGUNDO","SUPLENTE").contains(rol)) throw new IllegalArgumentException("Rol inválido");
    UUID apoderado=db.queryForObject("""
      INSERT INTO apoderados(id,persona_id) VALUES (?,?)
      ON CONFLICT(persona_id) DO UPDATE SET persona_id=EXCLUDED.persona_id RETURNING id
      """,UUID.class,UUID.randomUUID(),d.personaId());
    if("PRINCIPAL".equals(rol)) db.update("UPDATE vinculos_estudiante_apoderado SET rol='SEGUNDO' WHERE estudiante_id=? AND rol='PRINCIPAL'",estudiante);
    db.update("""
      INSERT INTO vinculos_estudiante_apoderado(estudiante_id,apoderado_id,vinculo,rol,contacto_emergencia,telefono,correo)
      VALUES (?,?,?,?,?,?,?) ON CONFLICT(estudiante_id,apoderado_id) DO UPDATE SET
      vinculo=EXCLUDED.vinculo,rol=EXCLUDED.rol,contacto_emergencia=EXCLUDED.contacto_emergencia,
      telefono=EXCLUDED.telefono,correo=EXCLUDED.correo,actualizado_en=now()
      """,estudiante,apoderado,texto(d.vinculo(),40,"Vínculo"),rol,d.emergencia(),
      opcional(d.telefono(),40,"Teléfono"),opcional(d.correo(),160,"Correo"));
    auditar(actor,"RESPONSABLE_VINCULADO",estudiante,Map.of("personaId",d.personaId(),"rol",rol));
  }
  public List<Map<String,Object>> apoderados(UUID estudiante,Jwt actor) {
    lectura(actor);
    return db.queryForList("""
      SELECT p.id AS persona_id,a.id AS apoderado_id,p.nombres,p.apellidos,p.tipo_documento,p.documento,
        v.rol,v.vinculo,v.contacto_emergencia,coalesce(v.telefono,p.telefono) AS telefono,
        coalesce(v.correo,p.correo) AS correo
      FROM vinculos_estudiante_apoderado v JOIN apoderados a ON a.id=v.apoderado_id
      JOIN personas p ON p.id=a.persona_id WHERE v.estudiante_id=?
      ORDER BY CASE v.rol WHEN 'PRINCIPAL' THEN 0 WHEN 'SEGUNDO' THEN 1 ELSE 2 END,p.apellidos
      """,estudiante);
  }

  public record MatriculaDato(UUID estudianteId,UUID periodoId,UUID gradoId,UUID seccionId,String procedencia,String procedenciaDetalle) {
    public MatriculaDato(UUID estudianteId,UUID periodoId,UUID gradoId,String procedencia,String procedenciaDetalle) {
      this(estudianteId,periodoId,gradoId,null,procedencia,procedenciaDetalle);
    }
  }
  public record NuevaMatriculaDato(EstudianteDato estudiante,UUID periodoId,UUID gradoId,UUID seccionId,String procedenciaDetalle) {}
  @Transactional public UUID iniciarNuevo(NuevaMatriculaDato d,Jwt actor) {
    escritura(actor);
    if(d.estudiante()==null) throw new IllegalArgumentException("Datos del estudiante requeridos");
    if(d.periodoId()==null) throw new IllegalArgumentException("Periodo requerido");
    UUID estudiante=crearEstudiante(d.estudiante(),actor);
    return iniciarMatricula(new MatriculaDato(estudiante,d.periodoId(),d.gradoId(),d.seccionId(),"NUEVO",d.procedenciaDetalle()),actor);
  }
  @Transactional public UUID iniciarMatricula(MatriculaDato d,Jwt actor) {
    escritura(actor);
    if(d.estudianteId()==null||d.periodoId()==null) throw new IllegalArgumentException("Estudiante y periodo requeridos");
    String procedencia=texto(d.procedencia(),20,"Procedencia");
    if(!List.of("NUEVO","ANTIGUO","TRASLADO","REINCORPORACION").contains(procedencia))
      throw new IllegalArgumentException("Procedencia inválida");
    if(db.queryForObject("SELECT count(*) FROM estudiantes WHERE id=?",Long.class,d.estudianteId())==0)
      throw new IllegalArgumentException("Estudiante inexistente");
    if(db.queryForObject("SELECT count(*) FROM matriculas WHERE estudiante_id=? AND periodo_id=? AND estado<>'CANCELADA'",Long.class,d.estudianteId(),d.periodoId())>0)
      throw new IllegalArgumentException("Ya existe una matrícula activa o pendiente para este alumno y periodo. Ábrela o cancélala antes de iniciar otra.");
    if(db.queryForObject("SELECT count(*) FROM periodos_academicos WHERE id=? AND habilitado",Long.class,d.periodoId())==0)
      throw new IllegalArgumentException("Periodo no habilitado");
    if(d.gradoId()!=null && db.queryForObject("SELECT count(*) FROM grados WHERE id=? AND habilitado",Long.class,d.gradoId())==0)
      throw new IllegalArgumentException("Grado inválido");
    UUID grado=d.gradoId();
    if(d.seccionId()!=null) {
      var seccion=db.queryForMap("SELECT grado_id,periodo_id,habilitada FROM secciones WHERE id=?",d.seccionId());
      if(!d.periodoId().equals(seccion.get("periodo_id")) || !Boolean.TRUE.equals(seccion.get("habilitada")))
        throw new IllegalArgumentException("La sección no corresponde al periodo o está inactiva");
      if(grado!=null && !grado.equals(seccion.get("grado_id"))) throw new IllegalArgumentException("Grado y sección incompatibles");
      grado=(UUID)seccion.get("grado_id");
    }
    UUID id=UUID.randomUUID();
    db.update("""
      INSERT INTO matriculas(id,estudiante_id,periodo_id,grado_id,seccion_id,procedencia,procedencia_detalle,estado)
      VALUES (?,?,?,?,?,?,jsonb_build_object('detalle',?::text),'PROCESO_PENDIENTE')
      """,id,d.estudianteId(),d.periodoId(),grado,d.seccionId(),procedencia,opcional(d.procedenciaDetalle(),500,"Procedencia"));
    historial(id,actor,"CREADA",null,Map.of("estado","PROCESO_PENDIENTE","procedencia",procedencia));
    return id;
  }
  public List<Map<String,Object>> matriculas(UUID periodo,String estado,Jwt actor) {
    lectura(actor);
    return db.queryForList("""
      SELECT m.id,m.estudiante_id,e.codigo,p.nombres,p.apellidos,m.periodo_id,per.nombre AS periodo,
        m.grado_id,g.nombre AS grado,m.seccion_id,s.nombre AS seccion,m.estado,m.procedencia,
        m.creado_en,m.actualizado_en,m.confirmado_en,m.vigente
      FROM matriculas m JOIN estudiantes e ON e.id=m.estudiante_id JOIN personas p ON p.id=e.persona_id
      JOIN periodos_academicos per ON per.id=m.periodo_id LEFT JOIN grados g ON g.id=m.grado_id
      LEFT JOIN secciones s ON s.id=m.seccion_id
      WHERE (?::uuid IS NULL OR m.periodo_id=?) AND (?::text IS NULL OR m.estado=?)
      ORDER BY m.creado_en DESC LIMIT 300
      """,periodo,periodo,estado,estado);
  }
  public Map<String,Object> matricula(UUID id,Jwt actor) {
    lectura(actor);
    var dato=db.queryForMap("""
      SELECT m.id,m.estudiante_id,m.periodo_id,m.grado_id,m.seccion_id,m.responsable_financiero_id,
        m.estado,m.procedencia,m.procedencia_detalle->>'detalle' AS procedencia_detalle,
        m.version,m.creado_en,m.actualizado_en,m.confirmado_en,m.vigente,
        m.pago_estado,m.pago_medio,m.pago_monto,m.pago_registrado_en,m.pago_registrado_por,
        e.codigo,p.nombres,p.apellidos,p.tipo_documento,p.documento,per.nombre AS periodo,
        g.nombre AS grado,s.nombre AS seccion FROM matriculas m
      JOIN estudiantes e ON e.id=m.estudiante_id JOIN personas p ON p.id=e.persona_id
      JOIN periodos_academicos per ON per.id=m.periodo_id
      LEFT JOIN grados g ON g.id=m.grado_id LEFT JOIN secciones s ON s.id=m.seccion_id WHERE m.id=?
      """,id);
    dato.put("apoderados",apoderados((UUID)dato.get("estudiante_id"),actor));
    dato.put("historial",db.queryForList("SELECT actor,accion,antes,despues,instante FROM historial_matriculas WHERE matricula_id=? ORDER BY instante,id",id));
    return dato;
  }
  public record CambioMatricula(UUID gradoId,UUID seccionId,UUID responsableFinancieroId,String procedenciaDetalle) {}
  @Transactional public void editarMatricula(UUID id,CambioMatricula d,Jwt actor) {
    escritura(actor);
    var antes=db.queryForMap("SELECT id,estudiante_id,periodo_id,grado_id,seccion_id,estado FROM matriculas WHERE id=? FOR UPDATE",id);
    if("CANCELADA".equals(antes.get("estado"))) throw new IllegalArgumentException("La matrícula cancelada no se puede editar; inicia una nueva");
    UUID grado=d.gradoId(),seccion=d.seccionId(),periodo=(UUID)antes.get("periodo_id");
    if(seccion!=null) {
      var datos=db.queryForMap("SELECT grado_id,periodo_id,habilitada FROM secciones WHERE id=?",seccion);
      if(!periodo.equals(datos.get("periodo_id"))||!Boolean.TRUE.equals(datos.get("habilitada")))
        throw new IllegalArgumentException("La sección no corresponde al periodo o está inactiva");
      if(grado!=null&&!grado.equals(datos.get("grado_id"))) throw new IllegalArgumentException("Grado y sección incompatibles");
      grado=(UUID)datos.get("grado_id");
    }
    if(grado!=null&&db.queryForObject("SELECT count(*) FROM grados WHERE id=? AND habilitado",Long.class,grado)==0)
      throw new IllegalArgumentException("Grado no habilitado");
    if("MATRICULADO".equals(antes.get("estado"))&&grado==null)
      throw new IllegalArgumentException("La matrícula confirmada requiere un grado");
    if(d.responsableFinancieroId()!=null&&db.queryForObject("SELECT count(*) FROM personas WHERE id=?",Long.class,d.responsableFinancieroId())==0)
      throw new IllegalArgumentException("Responsable financiero inexistente");
    db.update("""
      UPDATE matriculas SET grado_id=?,seccion_id=?,responsable_financiero_id=?,
        procedencia_detalle=jsonb_build_object('detalle',?::text),version=version+1,actualizado_en=now() WHERE id=?
      """,grado,seccion,d.responsableFinancieroId(),opcional(d.procedenciaDetalle(),500,"Procedencia"),id);
    historial(id,actor,"EDITADA",antes,Map.of("gradoId",String.valueOf(grado),"seccionId",String.valueOf(seccion)));
    UUID vieja=(UUID)antes.get("seccion_id");
    if("MATRICULADO".equals(antes.get("estado"))) {
      if(vieja!=null) eventos.publicarSeccion(vieja);
      if(seccion!=null&&!seccion.equals(vieja)) eventos.publicarSeccion(seccion);
    }
  }
  @Transactional public void reservar(UUID id,Jwt actor) {
    escritura(actor);
    var anterior=db.queryForMap("SELECT estado FROM matriculas WHERE id=? FOR UPDATE",id);
    if("VACANTE_RESERVADA".equals(anterior.get("estado"))) return;
    if(!"PROCESO_PENDIENTE".equals(anterior.get("estado"))) throw new IllegalArgumentException("Solo se puede reservar una matrícula pendiente");
    db.update("UPDATE matriculas SET estado='VACANTE_RESERVADA',version=version+1,actualizado_en=now() WHERE id=?",id);
    historial(id,actor,"VACANTE_RESERVADA",anterior,Map.of("estado","VACANTE_RESERVADA"));
  }
  @Transactional public void cancelar(UUID id,Jwt actor) {
    escritura(actor);
    var anterior=db.queryForMap("SELECT estado FROM matriculas WHERE id=? FOR UPDATE",id);
    if("CANCELADA".equals(anterior.get("estado"))) return;
    if(!List.of("PROCESO_PENDIENTE","VACANTE_RESERVADA").contains(anterior.get("estado")))
      throw new IllegalArgumentException("Solo se puede cancelar antes de confirmar la matrícula");
    db.update("""
      UPDATE matriculas SET estado='CANCELADA',vigente=FALSE,cierre_motivo='CANCELACION',
        version=version+1,actualizado_en=now() WHERE id=?
      """,id);
    historial(id,actor,"CANCELADA",anterior,Map.of("estado","CANCELADA"));
  }
  @Transactional public void confirmar(UUID id,boolean confirmacionExplicita,Jwt actor) {
    escritura(actor);
    if(!confirmacionExplicita) throw new IllegalArgumentException("Se requiere confirmación explícita");
    var m=db.queryForMap("""
      SELECT m.id,m.estudiante_id,m.periodo_id,m.grado_id,m.seccion_id,m.estado,e.estado AS estado_estudiante,
        p.nombres,p.apellidos FROM matriculas m JOIN estudiantes e ON e.id=m.estudiante_id
      JOIN personas p ON p.id=e.persona_id WHERE m.id=? FOR UPDATE OF m
      """,id);
    if("MATRICULADO".equals(m.get("estado"))) return;
    if(!List.of("PROCESO_PENDIENTE","VACANTE_RESERVADA").contains(m.get("estado")))
      throw new IllegalArgumentException("La matrícula cancelada no se puede confirmar; inicia una nueva");
    if(!"ACTIVO".equals(m.get("estado_estudiante"))||m.get("grado_id")==null
      ||m.get("nombres")==null||m.get("apellidos")==null)
      throw new IllegalArgumentException("Completa estudiante y grado antes de confirmar");
    var responsables=db.queryForList("""
      SELECT p.nombres,p.apellidos,p.tipo_documento,p.documento,v.rol,v.vinculo,v.contacto_emergencia,
        coalesce(v.telefono,p.telefono) AS telefono,coalesce(v.correo,p.correo) AS correo
      FROM vinculos_estudiante_apoderado v JOIN apoderados a ON a.id=v.apoderado_id
      JOIN personas p ON p.id=a.persona_id WHERE v.estudiante_id=?
      """,m.get("estudiante_id"));
    if(responsables.isEmpty()||responsables.stream().noneMatch(r -> r.get("documento")!=null && r.get("tipo_documento")!=null))
      throw new IllegalArgumentException("Se necesita al menos un responsable identificado");
    asignarCodigoSiFalta((UUID)m.get("estudiante_id"),actor);
    db.update("""
      UPDATE matriculas SET estado='MATRICULADO',contactos_snapshot=?::jsonb,confirmado_en=now(),
        confirmado_por=?,version=version+1,actualizado_en=now() WHERE id=?
      """,json(responsables),actor.getSubject(),id);
    historial(id,actor,"CONFIRMADA",m,Map.of("estado","MATRICULADO"));
    if(m.get("seccion_id")!=null) eventos.publicarSeccion((UUID)m.get("seccion_id"));
  }
  void asignarCodigoSiFalta(UUID estudiante,Jwt actor) {
    String actual=db.queryForObject("SELECT codigo FROM estudiantes WHERE id=? FOR UPDATE",String.class,estudiante);
    if(actual!=null) return;
    String codigo=db.queryForObject("SELECT 'BOS-'||lpad(nextval('codigo_estudiante_seq')::text,6,'0')",String.class);
    db.update("UPDATE estudiantes SET codigo=?,actualizado_en=now() WHERE id=?",codigo,estudiante);
    auditar(actor,"ESTUDIANTE_REGISTRADO",estudiante,Map.of("codigo",codigo));
  }
  @Transactional public void registrarPagoExterno(UUID matricula,String medio,java.math.BigDecimal monto,Jwt actor) {
    escritura(actor);
    if(medio==null||!List.of("EFECTIVO","TRANSFERENCIA","YAPE","PLIN","TARJETA","OTRO").contains(medio))
      throw new IllegalArgumentException("Selecciona el medio de pago externo");
    if(monto==null||monto.signum()<=0||monto.scale()>2||monto.precision()>12)
      throw new IllegalArgumentException("Indica un monto válido mayor que cero");
    var actual=db.queryForMap("SELECT estado,pago_estado FROM matriculas WHERE id=? FOR UPDATE",matricula);
    if(!"MATRICULADO".equals(actual.get("estado"))) throw new IllegalArgumentException("La matrícula debe estar confirmada");
    if(!"PENDIENTE".equals(actual.get("pago_estado"))) throw new IllegalArgumentException("El pago ya fue registrado");
    db.update("""
      UPDATE matriculas SET pago_estado='REGISTRADO',pago_medio=?,pago_monto=?,
        pago_registrado_en=now(),pago_registrado_por=?,version=version+1,actualizado_en=now() WHERE id=?
      """,medio,monto,actor.getSubject(),matricula);
    historial(matricula,actor,"PAGO_EXTERNO_REGISTRADO",actual,Map.of("medio",medio,"monto",monto));
  }
  public List<Map<String,Object>> resumen(Jwt actor) {
    lectura(actor);
    return db.queryForList("""
      SELECT p.id AS periodo_id,p.nombre AS periodo,n.nombre AS nivel,g.nombre AS grado,
        count(m.id) FILTER(WHERE m.estado='PROCESO_PENDIENTE') AS pendientes,
        count(m.id) FILTER(WHERE m.estado='VACANTE_RESERVADA') AS reservadas,
        count(m.id) FILTER(WHERE m.estado='MATRICULADO') AS confirmadas
      FROM periodos_academicos p LEFT JOIN matriculas m ON m.periodo_id=p.id
      LEFT JOIN grados g ON g.id=m.grado_id LEFT JOIN niveles_educativos n ON n.id=g.nivel_id
      GROUP BY p.id,p.nombre,n.nombre,g.nombre ORDER BY p.nombre DESC,n.nombre,g.nombre
      """);
  }
  public Map<String,Object> catalogos(Jwt actor) {
    lectura(actor);
    return Map.of(
      "periodos",db.queryForList("SELECT * FROM periodos_academicos ORDER BY inicio DESC"),
      "niveles",db.queryForList("SELECT * FROM niveles_educativos ORDER BY nombre"),
      "grados",db.queryForList("SELECT g.*,n.nombre AS nivel FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id ORDER BY n.nombre,g.nombre"),
      "secciones",db.queryForList("SELECT s.*,g.nombre AS grado,n.nombre AS nivel,p.nombre AS periodo FROM secciones s JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id JOIN periodos_academicos p ON p.id=s.periodo_id ORDER BY p.inicio DESC,n.nombre,g.nombre,s.nombre"),
      "asignaturas",db.queryForList("""
        SELECT a.*,
          (SELECT count(*) FROM asignaturas_grado ag WHERE ag.asignatura_id=a.id) AS relaciones_count,
          (SELECT count(*) FROM ofertas_academicas o WHERE o.asignatura_id=a.id) AS ofertas_count
        FROM asignaturas a ORDER BY a.codigo
        """),
      "relaciones",db.queryForList("SELECT asignatura_id,grado_id,periodo_id FROM asignaturas_grado WHERE activa"),
      "ofertas",db.queryForList("SELECT o.*,a.codigo AS codigo_asignatura,a.nombre AS asignatura,s.grado_id,s.periodo_id,s.nombre AS seccion,g.nombre AS grado,n.nombre AS nivel,p.nombre AS periodo FROM ofertas_academicas o JOIN asignaturas a ON a.id=o.asignatura_id JOIN secciones s ON s.id=o.seccion_id JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id JOIN periodos_academicos p ON p.id=s.periodo_id ORDER BY p.inicio DESC,n.nombre,g.nombre,a.codigo,s.nombre")
    );
  }
  public record PeriodoDato(String nombre,LocalDate inicio,LocalDate fin,boolean habilitado) {}
  @Transactional public UUID crearPeriodo(PeriodoDato d,Jwt actor) {
    direccion(actor);
    if(d.inicio()==null||d.fin()==null||d.fin().isBefore(d.inicio())) throw new IllegalArgumentException("Fechas inválidas");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO periodos_academicos(id,nombre,inicio,fin,habilitado) VALUES (?,?,?,?,?)",
      id,texto(d.nombre(),80,"Periodo"),d.inicio(),d.fin(),d.habilitado());
    auditar(actor,"PERIODO_CREADO",id,Map.of("nombre",d.nombre()));
    return id;
  }
  @Transactional public void estadoPeriodo(UUID id,boolean habilitado,Jwt actor) {
    direccion(actor);
    db.update("UPDATE periodos_academicos SET habilitado=? WHERE id=?",habilitado,id);
    auditar(actor,"PERIODO_ESTADO",id,Map.of("habilitado",habilitado));
  }
  @Transactional public UUID crearNivel(String nombre,Jwt actor) {
    direccion(actor);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO niveles_educativos(id,nombre) VALUES (?,?)",id,texto(nombre,80,"Nivel"));
    auditar(actor,"NIVEL_CREADO",id,Map.of("nombre",nombre));
    return id;
  }
  @Transactional public void estadoNivel(UUID id,boolean habilitado,Jwt actor) {
    direccion(actor);
    db.update("UPDATE niveles_educativos SET habilitado=? WHERE id=?",habilitado,id);
    auditar(actor,"NIVEL_ESTADO",id,Map.of("habilitado",habilitado));
  }
  @Transactional public UUID crearGrado(UUID nivel,String nombre,Jwt actor) {
    direccion(actor);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO grados(id,nivel_id,nombre) VALUES (?,?,?)",id,nivel,texto(nombre,80,"Grado"));
    auditar(actor,"GRADO_CREADO",id,Map.of("nombre",nombre));
    return id;
  }
  @Transactional public void estadoGrado(UUID id,boolean habilitado,Jwt actor) {
    direccion(actor);
    db.update("UPDATE grados SET habilitado=? WHERE id=?",habilitado,id);
    auditar(actor,"GRADO_ESTADO",id,Map.of("habilitado",habilitado));
  }
  public record SeccionDato(UUID gradoId,UUID periodoId,String nombre,boolean habilitada,Integer capacidad) {}
  @Transactional public UUID crearSeccion(SeccionDato d,Jwt actor) {
    direccion(actor);
    String nombre=texto(d.nombre(),1,"Sección").toUpperCase();
    if(!List.of("A","B","C","D").contains(nombre)) throw new IllegalArgumentException("Sección permitida: A a D");
    if(d.capacidad()!=null && d.capacidad()<1) throw new IllegalArgumentException("Capacidad inválida");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO secciones(id,grado_id,periodo_id,nombre,habilitada,capacidad) VALUES (?,?,?,?,?,?)",
      id,d.gradoId(),d.periodoId(),nombre,d.habilitada(),d.capacidad());
    if(d.habilitada()) sincronizarCursosSeccion(id,d.gradoId(),d.periodoId());
    auditar(actor,"SECCION_CREADA",id,Map.of("nombre",nombre));
    return id;
  }
  @Transactional public void estadoSeccion(UUID id,boolean habilitada,Integer capacidad,Jwt actor) {
    direccion(actor);
    if(capacidad!=null&&capacidad<1) throw new IllegalArgumentException("Capacidad inválida");
    db.update("UPDATE secciones SET habilitada=?,capacidad=? WHERE id=?",habilitada,capacidad,id);
    if(habilitada) {
      var seccion=db.queryForMap("SELECT grado_id,periodo_id FROM secciones WHERE id=?",id);
      sincronizarCursosSeccion(id,(UUID)seccion.get("grado_id"),(UUID)seccion.get("periodo_id"));
    }
    eventos.publicarSeccion(id);
    auditar(actor,"SECCION_EDITADA",id,Map.of("habilitada",habilitada));
  }
  private void publicarOfertaSeccion(UUID seccion,UUID asignatura) {
    UUID nueva=UUID.randomUUID();
    int creada=db.update("INSERT INTO ofertas_academicas(id,seccion_id,asignatura_id) VALUES (?,?,?) ON CONFLICT(seccion_id,asignatura_id) DO NOTHING",nueva,seccion,asignatura);
    UUID id=creada>0?nueva:db.queryForObject("SELECT id FROM ofertas_academicas WHERE seccion_id=? AND asignatura_id=?",UUID.class,seccion,asignatura);
    int reactivada=creada>0?0:db.update("UPDATE ofertas_academicas SET estado='ACTIVA' WHERE id=? AND estado<>'ACTIVA'",id);
    if(creada>0||reactivada>0) eventos.publicarOferta(id);
  }
  private void sincronizarCursosSeccion(UUID seccion,UUID grado,UUID periodo) {
    var asignaturas=db.queryForList("""
      SELECT DISTINCT ag.asignatura_id FROM asignaturas_grado ag
      JOIN asignaturas a ON a.id=ag.asignatura_id
      WHERE ag.grado_id=? AND ag.activa AND a.activa AND (ag.periodo_id IS NULL OR ag.periodo_id=?)
      """,UUID.class,grado,periodo);
    for(UUID asignatura:asignaturas) publicarOfertaSeccion(seccion,asignatura);
  }
  @Transactional public int crearOfertaGrado(UUID grado,UUID periodo,UUID asignatura,Jwt actor) {
    direccion(actor);
    if(grado==null||periodo==null||asignatura==null) throw new IllegalArgumentException("Selecciona periodo, grado y asignatura");
    if(db.queryForObject("""
      SELECT count(*) FROM asignaturas_grado ag JOIN asignaturas a ON a.id=ag.asignatura_id
      WHERE ag.grado_id=? AND ag.asignatura_id=? AND ag.activa AND a.activa
        AND (ag.periodo_id IS NULL OR ag.periodo_id=?)
      """,Long.class,grado,asignatura,periodo)==0)
      throw new IllegalArgumentException("La asignatura no corresponde a este grado y periodo");
    var secciones=db.queryForList("SELECT id FROM secciones WHERE grado_id=? AND periodo_id=? AND habilitada ORDER BY nombre",UUID.class,grado,periodo);
    if(secciones.isEmpty()) throw new IllegalArgumentException("Habilita una sección de este grado y periodo antes de crear el curso");
    for(UUID seccion:secciones) publicarOfertaSeccion(seccion,asignatura);
    auditar(actor,"OFERTA_GRADO_CREADA",grado,Map.of("periodoId",periodo,"asignaturaId",asignatura,"secciones",secciones.size()));
    return secciones.size();
  }
  @Transactional public UUID crearOferta(UUID seccion,UUID asignatura,Jwt actor) {
    direccion(actor);
    var datos=db.queryForMap("SELECT grado_id,periodo_id,habilitada FROM secciones WHERE id=?",seccion);
    if(!Boolean.TRUE.equals(datos.get("habilitada"))) throw new IllegalArgumentException("Sección no habilitada");
    if(db.queryForObject("SELECT count(*) FROM asignaturas WHERE id=? AND activa",Long.class,asignatura)==0)
      throw new IllegalArgumentException("Asignatura no activa");
    if(db.queryForObject("""
      SELECT count(*) FROM asignaturas_grado WHERE asignatura_id=? AND grado_id=? AND activa
      AND (periodo_id IS NULL OR periodo_id=?)
      """,Long.class,asignatura,datos.get("grado_id"),datos.get("periodo_id"))==0)
      throw new IllegalArgumentException("La asignatura no corresponde a este grado y periodo");
    crearOfertaGrado((UUID)datos.get("grado_id"),(UUID)datos.get("periodo_id"),asignatura,actor);
    return db.queryForObject("SELECT id FROM ofertas_academicas WHERE seccion_id=? AND asignatura_id=?",UUID.class,seccion,asignatura);
  }
  private String codigoAsignatura(String codigo) {
    if(codigo==null || !codigo.trim().toUpperCase().matches("[A-Z0-9-]{3,40}"))
      throw new IllegalArgumentException("Código de asignatura: 3 a 40 letras, números o guiones");
    return codigo.trim().toUpperCase();
  }
  @Transactional public UUID crearAsignatura(String codigo,String nombre,Jwt actor) {
    direccion(actor);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO asignaturas(id,codigo,nombre) VALUES (?,?,?)",id,codigoAsignatura(codigo),texto(nombre,120,"Asignatura"));
    auditar(actor,"ASIGNATURA_CREADA",id,Map.of("codigo",codigo,"nombre",nombre));
    return id;
  }
  @Transactional public void editarAsignatura(UUID id,String codigo,String nombre,boolean activa,Jwt actor) {
    direccion(actor);
    int actualizadas=db.update("UPDATE asignaturas SET codigo=?,nombre=?,activa=?,actualizado_en=now() WHERE id=?",codigoAsignatura(codigo),texto(nombre,120,"Asignatura"),activa,id);
    if(actualizadas==0) throw new IllegalArgumentException("Asignatura inexistente");
    sincronizarOfertasAsignatura(id,true);
    auditar(actor,"ASIGNATURA_EDITADA",id,Map.of("nombre",nombre,"activa",activa));
  }
  @Transactional public void eliminarAsignatura(UUID id,Jwt actor) {
    direccion(actor);
    var asignatura=db.queryForList("SELECT codigo,nombre FROM asignaturas WHERE id=? FOR UPDATE",id);
    if(asignatura.isEmpty()) throw new IllegalArgumentException("Asignatura inexistente");
    long relaciones=db.queryForObject("SELECT count(*) FROM asignaturas_grado WHERE asignatura_id=?",Long.class,id);
    long ofertas=db.queryForObject("SELECT count(*) FROM ofertas_academicas WHERE asignatura_id=?",Long.class,id);
    if(relaciones>0||ofertas>0)
      throw new IllegalArgumentException("Esta asignatura está vinculada a una malla o curso. Desactívala para conservar su historial.");
    db.update("DELETE FROM asignaturas WHERE id=?",id);
    auditar(actor,"ASIGNATURA_ELIMINADA",id,asignatura.getFirst());
  }
  private void sincronizarOfertasAsignatura(UUID asignatura,boolean publicarTodas) {
    var ofertas=db.queryForList("""
      SELECT o.id,s.grado_id,s.periodo_id FROM ofertas_academicas o
      JOIN secciones s ON s.id=o.seccion_id WHERE o.asignatura_id=?
      """,asignatura);
    for(var oferta:ofertas) {
      UUID id=(UUID)oferta.get("id");
      boolean activa=db.queryForObject("""
        SELECT count(*) FROM asignaturas_grado ag JOIN asignaturas a ON a.id=ag.asignatura_id
        WHERE ag.asignatura_id=? AND ag.grado_id=? AND ag.activa AND a.activa
          AND (ag.periodo_id IS NULL OR ag.periodo_id=?)
        """,Long.class,asignatura,oferta.get("grado_id"),oferta.get("periodo_id"))>0;
      String estado=activa?"ACTIVA":"INACTIVA";
      int cambio=db.update("UPDATE ofertas_academicas SET estado=? WHERE id=? AND estado<>?",estado,id,estado);
      if(cambio>0||publicarTodas) eventos.publicarOferta(id);
    }
    var seccionesActivas=db.queryForList("""
      SELECT DISTINCT s.id FROM secciones s
      JOIN asignaturas_grado ag ON ag.grado_id=s.grado_id AND ag.asignatura_id=? AND ag.activa
        AND (ag.periodo_id IS NULL OR ag.periodo_id=s.periodo_id)
      JOIN asignaturas a ON a.id=ag.asignatura_id AND a.activa
      WHERE s.habilitada
      """,UUID.class,asignatura);
    for(UUID seccion:seccionesActivas) publicarOfertaSeccion(seccion,asignatura);
  }
  public record AsignaturaGradoDato(UUID asignaturaId,UUID gradoId,UUID periodoId,int orden,boolean activa) {}
  @Transactional public UUID vincularAsignatura(AsignaturaGradoDato d,Jwt actor) {
    direccion(actor);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO asignaturas_grado(id,asignatura_id,grado_id,periodo_id,orden,activa) VALUES (?,?,?,?,?,?)",
      id,d.asignaturaId(),d.gradoId(),d.periodoId(),d.orden(),d.activa());
    sincronizarOfertasAsignatura(d.asignaturaId(),false);
    auditar(actor,"ASIGNATURA_GRADO_CREADA",id,Map.of("asignaturaId",d.asignaturaId()));
    return id;
  }
  @Transactional public void editarRelacion(UUID id,int orden,boolean activa,Jwt actor) {
    direccion(actor);
    UUID asignatura=db.queryForObject("SELECT asignatura_id FROM asignaturas_grado WHERE id=?",UUID.class,id);
    db.update("UPDATE asignaturas_grado SET orden=?,activa=?,actualizado_en=now() WHERE id=?",orden,activa,id);
    sincronizarOfertasAsignatura(asignatura,false);
    auditar(actor,"ASIGNATURA_GRADO_EDITADA",id,Map.of("orden",orden,"activa",activa));
  }
  public record DocenteDato(String nombres,String apellidos,String tipoDocumento,String documento,String correoInstitucional,String telefono,UUID cuentaIdentidadId) {}
  public List<Map<String,Object>> docentes(String buscar,Jwt actor) {
    lectura(actor);
    String q="%"+(buscar==null?"":buscar.trim())+"%";
    return db.queryForList("""
      SELECT d.id,d.codigo,d.estado,d.correo_institucional,d.telefono,d.cuenta_identidad_id,
        p.nombres,p.apellidos,p.tipo_documento,p.documento FROM docentes d JOIN personas p ON p.id=d.persona_id
      WHERE d.codigo ILIKE ? OR p.nombres ILIKE ? OR p.apellidos ILIKE ? OR p.documento ILIKE ?
      ORDER BY p.apellidos,p.nombres LIMIT 200
      """,q,q,q,q);
  }
  @Transactional public UUID crearDocente(DocenteDato d,Jwt actor) {
    direccion(actor);
    if(d.cuentaIdentidadId()!=null) throw new IllegalArgumentException("Registra primero al docente; vincula una cuenta de Identidad después");
    String doc=opcional(d.documento(),30,"Documento"),tipo=opcional(d.tipoDocumento(),20,"Tipo de documento");
    if((doc==null)!=(tipo==null)) throw new IllegalArgumentException("Indica tipo y documento juntos");
    if(doc!=null&&!personas(tipo,doc,actor).isEmpty()) throw new IllegalArgumentException("La persona ya existe");
    UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
    String codigo=db.queryForObject("SELECT 'DOC-'||lpad(nextval('codigo_docente_seq')::text,6,'0')",String.class);
    db.update("INSERT INTO personas(id,nombres,apellidos,tipo_documento,documento,telefono,correo) VALUES (?,?,?,?,?,?,?)",
      persona,texto(d.nombres(),120,"Nombres"),texto(d.apellidos(),120,"Apellidos"),tipo,doc,
      opcional(d.telefono(),40,"Teléfono"),opcional(d.correoInstitucional(),160,"Correo"));
    db.update("INSERT INTO docentes(id,persona_id,codigo,correo_institucional,telefono,cuenta_identidad_id) VALUES (?,?,?,?,?,?)",
      id,persona,codigo,opcional(d.correoInstitucional(),160,"Correo"),opcional(d.telefono(),40,"Teléfono"),d.cuentaIdentidadId());
    auditar(actor,"DOCENTE_CREADO",id,Map.of("codigo",codigo));
    return id;
  }
  @Transactional public UUID crearCuentaDocente(UUID id,String usuario,String clave,Jwt actor) {
    direccion(actor);
    var docente=db.queryForMap("SELECT cuenta_identidad_id,estado FROM docentes WHERE id=? FOR UPDATE",id);
    if(!"ACTIVO".equals(docente.get("estado"))) throw new IllegalArgumentException("Docente no activo");
    if(docente.get("cuenta_identidad_id")!=null) throw new IllegalArgumentException("El docente ya tiene cuenta vinculada");
    UUID cuenta=identidad.crearDocente(usuario,clave,id,actor);
    db.update("UPDATE docentes SET cuenta_identidad_id=?,actualizado_en=now() WHERE id=?",cuenta,id);
    auditar(actor,"CUENTA_DOCENTE_CREADA",id,Map.of("cuentaId",cuenta,"usuario",usuario));
    return cuenta;
  }
  @Transactional public void editarDocente(UUID id,DocenteDato d,String estado,Jwt actor) {
    direccion(actor);
    if(!List.of("ACTIVO","INACTIVO").contains(estado)) throw new IllegalArgumentException("Estado inválido");
    String tipo=opcional(d.tipoDocumento(),20,"Tipo"),documento=opcional(d.documento(),30,"Documento");
    if((tipo==null)!=(documento==null)) throw new IllegalArgumentException("Indica tipo y documento juntos");
    var anterior=db.queryForMap("SELECT persona_id,cuenta_identidad_id FROM docentes WHERE id=?",id);
    UUID persona=(UUID)anterior.get("persona_id");
    if(d.cuentaIdentidadId()!=null&&!d.cuentaIdentidadId().equals(anterior.get("cuenta_identidad_id")))
      identidad.verificarDocente(d.cuentaIdentidadId(),id,actor);
    db.update("UPDATE personas SET nombres=?,apellidos=?,tipo_documento=?,documento=?,telefono=?,correo=?,actualizado_en=now() WHERE id=?",
      texto(d.nombres(),120,"Nombres"),texto(d.apellidos(),120,"Apellidos"),
      tipo,documento,opcional(d.telefono(),40,"Teléfono"),opcional(d.correoInstitucional(),160,"Correo"),persona);
    db.update("UPDATE docentes SET correo_institucional=?,telefono=?,cuenta_identidad_id=?,estado=?,actualizado_en=now() WHERE id=?",
      opcional(d.correoInstitucional(),160,"Correo"),opcional(d.telefono(),40,"Teléfono"),d.cuentaIdentidadId(),estado,id);
    db.query("SELECT oferta_id FROM asignaciones_docentes WHERE docente_id=? AND estado='ACTIVA'",(rs,n)->(UUID)rs.getObject(1),id).forEach(eventos::publicarOferta);
    auditar(actor,"DOCENTE_EDITADO",id,Map.of("estado",estado));
  }
  @Transactional public void asignarDocente(UUID oferta,UUID docente,Jwt actor) {
    direccion(actor);
    if(db.queryForObject("SELECT count(*) FROM ofertas_academicas WHERE id=? AND estado='ACTIVA'",Long.class,oferta)==0)
      throw new IllegalArgumentException("Curso no activo");
    if(db.queryForObject("SELECT count(*) FROM docentes WHERE id=? AND estado='ACTIVO'",Long.class,docente)==0)
      throw new IllegalArgumentException("Docente no activo");
    int insertadas=db.update("""
      INSERT INTO asignaciones_docentes(id,oferta_id,docente_id,estado) VALUES (?,?,?,'ACTIVA')
      ON CONFLICT(oferta_id,docente_id) WHERE estado='ACTIVA' DO NOTHING
      """,UUID.randomUUID(),oferta,docente);
    if(insertadas>0) {
      eventos.publicarOferta(oferta);
      auditar(actor,"DOCENTE_ASIGNADO",oferta,Map.of("docenteId",docente));
    }
  }
  @Transactional public void asignarDocenteLote(UUID docente,List<UUID> ofertas,Jwt actor) {
    direccion(actor);
    if(ofertas==null || ofertas.isEmpty() || ofertas.size()>100 || ofertas.contains(null))
      throw new IllegalArgumentException("Selecciona entre 1 y 100 cursos");
    for(UUID oferta:new java.util.LinkedHashSet<>(ofertas)) asignarDocente(oferta,docente,actor);
  }
  @Transactional public void retirarDocente(UUID asignacion,Jwt actor) {
    direccion(actor);
    UUID oferta=db.queryForObject("UPDATE asignaciones_docentes SET estado='INACTIVA',actualizado_en=now() WHERE id=? RETURNING oferta_id",UUID.class,asignacion);
    eventos.publicarOferta(oferta);
    auditar(actor,"DOCENTE_RETIRADO",oferta,Map.of("asignacionId",asignacion));
  }
}
