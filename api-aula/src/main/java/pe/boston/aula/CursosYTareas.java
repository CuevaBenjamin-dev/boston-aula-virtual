package pe.boston.aula;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class CursosYTareas {
  private static final String CURSOS_CON_DOCENTES="SELECT c.*,(SELECT string_agg(dc.nombre_completo, ', ' ORDER BY dc.nombre_completo) FROM docentes_curso dc WHERE dc.curso_id=c.id AND dc.activo AND dc.nombre_completo IS NOT NULL) AS docentes FROM cursos c ";
  private static final String INDICADORES="""
    LEFT JOIN LATERAL (
      SELECT count(*) AS total_estudiantes,
             count(e.id) AS entregados,
             count(*) FILTER (WHERE e.estado='TARDIA') AS tardias,
             count(*) FILTER (WHERE e.id IS NOT NULL AND (n.id IS NULL OR n.estado='BORRADOR')) AS pendientes_revision
      FROM inscripciones_curso i
      LEFT JOIN LATERAL (
        SELECT id,estado,version FROM entregas WHERE tarea_id=t.id AND estudiante_erp_id=i.estudiante_erp_id
        ORDER BY version DESC LIMIT 1
      ) e ON TRUE
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=t.id AND n.estudiante_erp_id=i.estudiante_erp_id AND n.entrega_version=e.version
      WHERE i.curso_id=t.curso_id AND i.activo
    ) k ON TRUE
    """;
  private final JdbcTemplate db; private final UsuarioActual usuario; private final ErpCliente erp; private final AvisosTarea avisos;
  @Value("${boston.calificaciones.minimo:0}") private BigDecimal notaMinima=new BigDecimal("0");
  @Value("${boston.calificaciones.maximo:20}") private BigDecimal notaMaxima=new BigDecimal("20");
  public CursosYTareas(JdbcTemplate db,UsuarioActual usuario,ErpCliente erp,AvisosTarea avisos) { this.db=db;this.usuario=usuario;this.erp=erp;this.avisos=avisos; }
  public List<Map<String,Object>> cursos(Authentication a) {
    return switch(usuario.tipo(a)) {
      case "ESTUDIANTE" -> db.queryForList(CURSOS_CON_DOCENTES+"JOIN inscripciones_curso i ON i.curso_id=c.id WHERE i.estudiante_erp_id=? AND i.activo=TRUE AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) ORDER BY c.asignatura",usuario.referencia(a));
      case "DOCENTE" -> db.queryForList(CURSOS_CON_DOCENTES+"JOIN docentes_curso d ON d.curso_id=c.id WHERE d.docente_erp_id=? AND d.activo=TRUE AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) ORDER BY c.asignatura",usuario.referencia(a));
      case "COORDINACION" -> db.queryForList(CURSOS_CON_DOCENTES+"JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE x.cuenta_id=? AND x.confirmado_erp AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE) ORDER BY c.asignatura",usuario.cuenta(a));
      default -> throw new AccessDeniedException("Sin cursos académicos");
    };
  }
  public List<Map<String,Object>> tareas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,EXISTS(SELECT 1 FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?) AS presentada,
        (SELECT e.estado FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=? ORDER BY e.version DESC LIMIT 1) AS ultima_entrega_estado,
        (SELECT max(e.version) FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=?) AS ultima_entrega_version
      FROM tareas t WHERE t.curso_id=? AND t.estado<>'FALTA_COMPLETAR'
        AND NOT EXISTS (SELECT 1 FROM tareas siguiente WHERE siguiente.tarea_anterior_id=t.id)
      ORDER BY t.publicado_en DESC NULLS LAST,t.fecha_limite
      """,usuario.referencia(a),usuario.referencia(a),usuario.referencia(a),curso);
    if("DOCENTE".equals(usuario.tipo(a))) return db.queryForList("""
      SELECT t.*,k.total_estudiantes,k.entregados,k.tardias,
        (k.total_estudiantes-k.entregados) AS pendientes,k.pendientes_revision,
        EXISTS(SELECT 1 FROM tareas sucesora WHERE sucesora.tarea_anterior_id=t.id) AS es_historica
      FROM tareas t
      """+INDICADORES+" WHERE t.curso_id=? ORDER BY t.creado_en DESC",curso);
    return db.queryForList("SELECT * FROM tareas WHERE curso_id=? AND estado<>'FALTA_COMPLETAR' ORDER BY fecha_limite",curso);
  }
  public Map<String,Object> tarea(UUID id,Authentication a) {
    var t=new HashMap<>(db.queryForMap("SELECT * FROM tareas WHERE id=?",id)); UUID curso=(UUID)t.get("curso_id");
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a)) && "FALTA_COMPLETAR".equals(t.get("estado"))) throw new AccessDeniedException("Tarea no publicada");
    t.put("tarea_sucesora_id",db.queryForList("SELECT id FROM tareas WHERE tarea_anterior_id=?",UUID.class,id).stream().findFirst().orElse(null));
    if("DOCENTE".equals(usuario.tipo(a))) { var contadores=db.queryForList("""
      SELECT k.total_estudiantes,k.entregados,k.tardias,
             (k.total_estudiantes-k.entregados) AS pendientes,k.pendientes_revision
      FROM tareas t
      """+INDICADORES+" WHERE t.id=?",id); if(!contadores.isEmpty()) t.putAll(contadores.getFirst()); }
    return t;
  }
  @Transactional public UUID crear(UUID curso,String titulo,String instrucciones,OffsetDateTime limite,Authentication a) {
    usuario.docente(a,curso); validar(titulo,instrucciones,limite);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,estado,escala_maxima,creado_por) VALUES (?,?,?,?,?,'FALTA_COMPLETAR',20,?)",id,curso,titulo.trim(),instrucciones.trim(),limite,usuario.cuenta(a));
    auditar(a,"TAREA_CREADA",id); return id;
  }
  @Transactional public Map<String,Object> crearLote(List<UUID> cursos,String titulo,String instrucciones,OffsetDateTime limite,UUID operacion,Authentication a) {
    validar(titulo,instrucciones,limite);
    if(operacion==null||cursos==null||cursos.size()<2||cursos.contains(null))
      throw new IllegalArgumentException("Selecciona varias secciones y una operación de creación");
    var unicos=new LinkedHashSet<>(cursos);
    if(unicos.size()!=cursos.size()) throw new IllegalArgumentException("No repitas una sección");
    for(UUID curso:unicos) usuario.docente(a,curso);
    var ordenados=unicos.stream().map(UUID::toString).sorted().toList();
    String firma=firma(String.join("|",ordenados)+"|"+titulo.trim()+"|"+instrucciones.trim()+"|"+limite.toInstant());
    int creado=db.update("INSERT INTO lotes_tarea(id,creado_por,solicitud_hash) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING",operacion,usuario.cuenta(a),firma);
    if(creado==0) {
      var previo=db.queryForMap("SELECT creado_por,solicitud_hash FROM lotes_tarea WHERE id=?",operacion);
      if(!usuario.cuenta(a).equals(previo.get("creado_por"))||!firma.equals(previo.get("solicitud_hash")))
        throw new IllegalArgumentException("Esta operación pertenece a otra solicitud");
    } else for(UUID curso:unicos) {
      UUID id=UUID.randomUUID();
      db.update("INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,estado,escala_maxima,creado_por,lote_id) VALUES (?,?,?,?,?,'FALTA_COMPLETAR',20,?,?)",
        id,curso,titulo.trim(),instrucciones.trim(),limite,usuario.cuenta(a),operacion);
      auditar(a,"TAREA_CREADA",id);
    }
    return Map.of("operacionId",operacion,"tareas",lote(operacion,a));
  }
  public List<Map<String,Object>> lote(UUID lote,Authentication a) {
    var meta=db.queryForMap("SELECT creado_por FROM lotes_tarea WHERE id=?",lote);
    if(!usuario.cuenta(a).equals(meta.get("creado_por"))) throw new AccessDeniedException("Lote ajeno");
    var filas=db.queryForList("SELECT t.*,c.asignatura,c.grado,c.seccion,c.nivel FROM tareas t JOIN cursos c ON c.id=t.curso_id WHERE t.lote_id=? ORDER BY t.creado_en,t.id",lote);
    for(var fila:filas) usuario.docente(a,(UUID)fila.get("curso_id"));
    return filas;
  }
  @Transactional public List<Map<String,Object>> publicarLote(UUID lote,Authentication a) {
    lote(lote,a);
    db.queryForList("SELECT id FROM tareas WHERE lote_id=? ORDER BY id FOR UPDATE",UUID.class,lote);
    var filas=lote(lote,a);
    if(filas.isEmpty()) throw new IllegalArgumentException("Lote vacío");
    if(filas.stream().allMatch(f->"PUBLICADA".equals(f.get("estado")))) return filas;
    copiarArchivosLote(filas);
    for(var fila:filas) {
      UUID destino=(UUID)fila.get("id");
      if(!List.of("FALTA_COMPLETAR","PUBLICADA").contains(fila.get("estado")))
        throw new IllegalArgumentException("Una tarea del lote ya no puede publicarse");
      if("FALTA_COMPLETAR".equals(fila.get("estado"))) estado(destino,"PUBLICADA",a);
    }
    return lote(lote,a);
  }
  @Transactional public List<Map<String,Object>> compartirArchivosLote(UUID lote,Authentication a) {
    lote(lote,a);
    db.queryForList("SELECT id FROM tareas WHERE lote_id=? ORDER BY id FOR UPDATE",UUID.class,lote);
    var filas=lote(lote,a); copiarArchivosLote(filas); return filas;
  }
  private void copiarArchivosLote(List<Map<String,Object>> filas) {
    if(filas.isEmpty()) throw new IllegalArgumentException("Lote vacío");
    if(filas.stream().anyMatch(f->!"FALTA_COMPLETAR".equals(f.get("estado"))))
      throw new IllegalArgumentException("Los archivos compartidos solo se preparan mientras todas las tareas son borradores");
    UUID fuente=(UUID)filas.getFirst().get("id");
    for(var fila:filas) {
      UUID destino=(UUID)fila.get("id");
      if(fuente.equals(destino)) continue;
      db.update("""
        INSERT INTO archivos_tarea(id,tarea_id,nombre,clave_objeto,tamano,tipo,estado,activo,creado_en)
        SELECT gen_random_uuid(),?,f.nombre,f.clave_objeto,f.tamano,f.tipo,'DISPONIBLE',TRUE,f.creado_en
        FROM archivos_tarea f WHERE f.tarea_id=? AND f.estado='DISPONIBLE' AND f.activo
          AND NOT EXISTS (SELECT 1 FROM archivos_tarea x WHERE x.tarea_id=? AND x.clave_objeto=f.clave_objeto AND x.activo)
        """,destino,fuente,destino);
    }
  }
  private String firma(String contenido) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8))); }
    catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  @Transactional public void estado(UUID tarea,String estado,Authentication a) {
    if(!List.of("PUBLICADA","CERRADA","CANCELADA").contains(estado)) throw new IllegalArgumentException("Estado inválido");
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=? FOR UPDATE",tarea);
    UUID curso=(UUID)t.get("curso_id"); usuario.docente(a,curso);
    String anterior=(String)t.get("estado");
    if(estado.equals(anterior)) return;
    if(db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,tarea)>0)
      throw new IllegalArgumentException("Una versión sustituida no puede volver a estar vigente");
    if(("PUBLICADA".equals(estado) && !List.of("FALTA_COMPLETAR","CERRADA").contains(anterior)) ||
       ("CERRADA".equals(estado) && !"PUBLICADA".equals(anterior)) ||
       ("CANCELADA".equals(estado) && !List.of("PUBLICADA","CERRADA").contains(anterior)))
      throw new IllegalArgumentException("Transición de estado inválida");
    int version=((Number)t.get("version")).intValue()+1;
    db.update("UPDATE tareas SET estado=?,publicado_en=CASE WHEN ?='PUBLICADA' THEN COALESCE(publicado_en,now()) ELSE publicado_en END,version=?,cambio_en=now() WHERE id=?",estado,estado,version,tarea);
    auditar(a,"TAREA_"+estado,tarea);
    String aviso="PUBLICADA".equals(estado)&&"CERRADA".equals(anterior)?"REABIERTA":estado;
    avisos.estudiantes(tarea,curso,aviso,(String)t.get("titulo"),version);
  }
  @Transactional public Map<String,Object> editar(UUID id,String titulo,String instrucciones,OffsetDateTime limite,int version,UUID destino,boolean confirmar,UUID operacion,String tipoCambio,Authentication a) {
    validar(titulo,instrucciones,limite);
    String cambio=tipoCambio==null?"SUSTANCIAL":tipoCambio;
    if(!List.of("MENOR","SUSTANCIAL").contains(cambio)) throw new IllegalArgumentException("Tipo de cambio inválido");
    if(operacion!=null) {
      var repetida=db.queryForList("SELECT * FROM tareas WHERE clave_operacion=?",operacion);
      if(!repetida.isEmpty()) { if(!id.equals(repetida.getFirst().get("tarea_anterior_id"))) throw new IllegalArgumentException("Operación ajena"); usuario.docente(a,(UUID)db.queryForMap("SELECT curso_id FROM tareas WHERE id=?",id).get("curso_id")); return repetida.getFirst(); }
      var menorPrevio=db.queryForList("SELECT entidad_id,actor FROM registros_auditoria WHERE operacion_id=? AND accion='TAREA_EDITADA_MENOR'",operacion);
      if(!menorPrevio.isEmpty()) {
        if(!id.equals(menorPrevio.getFirst().get("entidad_id"))||!usuario.oidc(a).getSubject().equals(menorPrevio.getFirst().get("actor"))) throw new IllegalArgumentException("Operación ajena");
        var anterior=db.queryForMap("SELECT curso_id FROM tareas WHERE id=?",id); usuario.docente(a,(UUID)anterior.get("curso_id"));
        return db.queryForMap("SELECT * FROM tareas WHERE id=?",id);
      }
    }
    tarea(id,a);
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=? FOR UPDATE",id);
    if(operacion!=null) {
      var repetida=db.queryForList("SELECT * FROM tareas WHERE clave_operacion=?",operacion);
      if(!repetida.isEmpty()) { if(!id.equals(repetida.getFirst().get("tarea_anterior_id"))) throw new IllegalArgumentException("Operación ajena"); return repetida.getFirst(); }
      var menorPrevio=db.queryForList("SELECT entidad_id,actor FROM registros_auditoria WHERE operacion_id=? AND accion='TAREA_EDITADA_MENOR'",operacion);
      if(!menorPrevio.isEmpty()) {
        if(!id.equals(menorPrevio.getFirst().get("entidad_id"))||!usuario.oidc(a).getSubject().equals(menorPrevio.getFirst().get("actor"))) throw new IllegalArgumentException("Operación ajena");
        return t;
      }
    }
    UUID origen=(UUID)t.get("curso_id"); usuario.docente(a,origen);
    if(db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,id)>0) throw new IllegalStateException("Esta versión ya tiene una sucesora");
    if("CANCELADA".equals(t.get("estado"))) throw new IllegalArgumentException("Una tarea cancelada no puede modificarse");
    if(((Number)t.get("version")).intValue()!=version) throw new IllegalStateException("Versión obsoleta");
    UUID nuevoCurso=destino==null?origen:destino; usuario.docente(a,nuevoCurso);
    boolean cambioCurso=!origen.equals(nuevoCurso);
    var plazoAnterior=((java.sql.Timestamp)t.get("fecha_limite")).toInstant();
    boolean ampliado=limite.toInstant().isAfter(plazoAnterior);
    boolean contenidoIgual=titulo.trim().equals(t.get("titulo"))&&instrucciones.trim().equals(t.get("instrucciones"));
    if(!cambioCurso&&contenidoIgual&&limite.toInstant().equals(plazoAnterior)) return t;
    if("MENOR".equals(cambio) && (cambioCurso||limite.toInstant().isBefore(plazoAnterior)))
      throw new IllegalArgumentException("Una corrección menor no cambia de sección ni acorta el plazo");
    int entregas=db.queryForObject("SELECT count(*) FROM entregas WHERE tarea_id=?",Integer.class,id);
    if(entregas>0 && !"MENOR".equals(cambio)) {
      if(!confirmar) throw new IllegalArgumentException("Confirma que la nueva versión reiniciará las entregas");
      if(operacion==null) throw new IllegalArgumentException("Identificador de operación obligatorio");
      UUID sucesora=UUID.randomUUID();
      String nuevoEstado="FALTA_COMPLETAR".equals(t.get("estado"))?"FALTA_COMPLETAR":"PUBLICADA";
      db.update("""
        INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,acepta_tarde,permite_reentrega,
                           estado,escala_maxima,version,creado_por,publicado_en,tarea_anterior_id,clave_operacion,cambio_en)
        VALUES (?,?,?,?,?,true,false,?,20,0,?,CASE WHEN ?='PUBLICADA' THEN now() ELSE NULL END,?,?,now())
        """,sucesora,nuevoCurso,titulo.trim(),instrucciones.trim(),limite,nuevoEstado,usuario.cuenta(a),nuevoEstado,id,operacion);
      db.update("""
        INSERT INTO archivos_tarea(id,tarea_id,nombre,clave_objeto,tamano,tipo,estado,activo,creado_en)
        SELECT gen_random_uuid(),?,nombre,clave_objeto,tamano,tipo,'DISPONIBLE',TRUE,creado_en
        FROM archivos_tarea WHERE tarea_id=? AND estado='DISPONIBLE' AND activo
        """,sucesora,id);
      db.update("UPDATE tareas SET estado='CERRADA',version=version+1,cambio_en=now() WHERE id=?",id);
      if("PUBLICADA".equals(nuevoEstado)) {
        if(cambioCurso) avisos.traslado(id,origen,(String)t.get("titulo"),version+1);
        avisos.estudiantes(sucesora,nuevoCurso,"REINICIADA",titulo.trim(),0);
      }
      db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id,detalle) VALUES (?,?,?,?,?)",
        UUID.randomUUID(),usuario.oidc(a).getSubject(),"TAREA_VERSIONADA",sucesora,"Versión anterior: "+id+"; curso anterior: "+origen+"; curso nuevo: "+nuevoCurso);
      return db.queryForMap("SELECT * FROM tareas WHERE id=?",sucesora);
    }
    int actualizados=db.update("UPDATE tareas SET curso_id=?,titulo=?,instrucciones=?,fecha_limite=?,version=version+1,cambio_en=now() WHERE id=? AND version=?",nuevoCurso,titulo.trim(),instrucciones.trim(),limite,id,version);
    if(actualizados!=1) throw new IllegalStateException("Versión obsoleta");
    if("PUBLICADA".equals(t.get("estado"))) {
      if(cambioCurso) avisos.traslado(id,origen,(String)t.get("titulo"),version+1);
      avisos.estudiantes(id,nuevoCurso,ampliado&&"MENOR".equals(cambio)?"AMPLIADA":"ACTUALIZADA",titulo.trim(),version+1);
    }
    if("MENOR".equals(cambio)) db.update("""
      INSERT INTO registros_auditoria(id,actor,accion,entidad_id,detalle,operacion_id)
      VALUES (?,?,?,?,?,?)
      """,UUID.randomUUID(),usuario.oidc(a).getSubject(),"TAREA_EDITADA_MENOR",id,
      "Plazo anterior: "+plazoAnterior+"; plazo nuevo: "+limite.toInstant()+"; título anterior: "+t.get("titulo")+"; título nuevo: "+titulo.trim()+"; instrucciones anteriores: "+t.get("instrucciones")+"; instrucciones nuevas: "+instrucciones.trim(),operacion);
    else auditar(a,"TAREA_EDITADA",id);
    return db.queryForMap("SELECT * FROM tareas WHERE id=?",id);
  }
  private void validar(String titulo,String instrucciones,OffsetDateTime limite) {
    if(titulo==null||titulo.isBlank()||titulo.length()>200||instrucciones==null||instrucciones.isBlank()||limite==null) throw new IllegalArgumentException("Título, instrucciones y fecha límite son obligatorios");
  }
  private Map<String,Object> tareaAbierta(UUID id,Authentication a) {
    usuario.estudiante(a);
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=? FOR UPDATE",id);
    usuario.estudianteCurso(a,(UUID)t.get("curso_id"));
    if(!"PUBLICADA".equals(t.get("estado")) || db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,id)>0)
      throw new IllegalArgumentException("La tarea no recibe entregas");
    return t;
  }
  private int ultimoIntento(UUID tarea,UUID estudiante) {
    return db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,estudiante);
  }
  private boolean puedeReentregar(UUID tarea,UUID estudiante,int intento,java.time.Instant plazo,java.time.Instant ahora) {
    if(intento<=1||!ahora.isAfter(plazo)) return true;
    return db.queryForObject("SELECT count(*) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=? AND version>1 AND estado='TARDIA'",Long.class,tarea,estudiante)==0;
  }
  public Map<String,Object> estadoPreparacion(UUID tarea,Authentication a) {
    usuario.estudiante(a); var t=tarea(tarea,a);
    UUID estudiante=usuario.referencia(a);
    int siguiente=ultimoIntento(tarea,estudiante)+1;
    var resultado=new HashMap<String,Object>();
    var plazo=((java.sql.Timestamp)t.get("fecha_limite")).toInstant();
    resultado.put("puede_iniciar","PUBLICADA".equals(t.get("estado"))&&t.get("tarea_sucesora_id")==null&&puedeReentregar(tarea,estudiante,siguiente,plazo,java.time.Instant.now()));
    resultado.put("siguiente_intento",siguiente);
    var filas=db.queryForList("SELECT * FROM preparaciones_entrega WHERE tarea_id=? AND estudiante_erp_id=? AND estado='ACTIVA'",tarea,estudiante);
    if(!filas.isEmpty()) resultado.putAll(filas.getFirst());
    return resultado;
  }
  @Transactional public Map<String,Object> preparar(UUID tarea,int version,Authentication a) {
    var t=tareaAbierta(tarea,a);
    if(((Number)t.get("version")).intValue()!=version) throw new IllegalStateException("La tarea cambió; vuelve a abrirla");
    UUID estudiante=usuario.referencia(a);
    int intento=ultimoIntento(tarea,estudiante)+1;
    if(!puedeReentregar(tarea,estudiante,intento,((java.sql.Timestamp)t.get("fecha_limite")).toInstant(),java.time.Instant.now()))
      throw new IllegalArgumentException("Ya se realizó la única reentrega fuera de plazo");
    var existente=db.queryForList("SELECT * FROM preparaciones_entrega WHERE tarea_id=? AND estudiante_erp_id=? AND estado='ACTIVA' FOR UPDATE",tarea,estudiante);
    if(!existente.isEmpty()) return existente.getFirst();
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO preparaciones_entrega(id,tarea_id,estudiante_erp_id,tarea_version,intento) VALUES (?,?,?,?,?)",id,tarea,estudiante,version,intento);
    return db.queryForMap("SELECT * FROM preparaciones_entrega WHERE id=?",id);
  }
  @Transactional public Map<String,Object> guardarPreparacion(UUID id,String respuesta,int version,Authentication a) {
    usuario.estudiante(a);
    var previa=db.queryForMap("SELECT tarea_id,estudiante_erp_id FROM preparaciones_entrega WHERE id=?",id);
    if(!usuario.referencia(a).equals(previa.get("estudiante_erp_id"))) throw new AccessDeniedException("Preparación ajena");
    var t=tareaAbierta((UUID)previa.get("tarea_id"),a);
    var p=db.queryForMap("SELECT * FROM preparaciones_entrega WHERE id=? FOR UPDATE",id);
    if(!"ACTIVA".equals(p.get("estado"))) throw new IllegalArgumentException("Preparación ya confirmada");
    if(((Number)t.get("version")).intValue()!=version) throw new IllegalStateException("La tarea cambió; vuelve a abrirla");
    if(respuesta!=null&&respuesta.length()>20000) throw new IllegalArgumentException("Respuesta demasiado larga");
    db.update("UPDATE preparaciones_entrega SET respuesta=?,tarea_version=?,actualizada_en=now() WHERE id=?",respuesta==null?"":respuesta,version,id);
    return db.queryForMap("SELECT * FROM preparaciones_entrega WHERE id=?",id);
  }
  @Transactional public Map<String,Object> confirmarPreparacion(UUID id,UUID operacion,Authentication a) {
    if(operacion==null) throw new IllegalArgumentException("Identificador de operación obligatorio");
    usuario.estudiante(a);
    var previa=db.queryForMap("SELECT * FROM preparaciones_entrega WHERE id=?",id);
    if(!usuario.referencia(a).equals(previa.get("estudiante_erp_id"))) throw new AccessDeniedException("Preparación ajena");
    UUID tarea=(UUID)previa.get("tarea_id");
    if("CONFIRMADA".equals(previa.get("estado"))) {
      if(!operacion.equals(previa.get("clave_confirmacion"))) throw new IllegalStateException("La preparación ya fue confirmada");
      return db.queryForMap("SELECT * FROM entregas WHERE id=?",previa.get("entrega_id"));
    }
    var t=tareaAbierta(tarea,a);
    var p=db.queryForMap("SELECT * FROM preparaciones_entrega WHERE id=? FOR UPDATE",id);
    if("CONFIRMADA".equals(p.get("estado"))) {
      if(!operacion.equals(p.get("clave_confirmacion"))) throw new IllegalStateException("La preparación ya fue confirmada");
      return db.queryForMap("SELECT * FROM entregas WHERE id=?",p.get("entrega_id"));
    }
    if(((Number)t.get("version")).intValue()!=((Number)p.get("tarea_version")).intValue()) throw new IllegalStateException("La tarea cambió; guarda la preparación antes de confirmar");
    UUID estudiante=usuario.referencia(a);
    int intento=ultimoIntento(tarea,estudiante)+1;
    if(((Number)p.get("intento")).intValue()!=intento) throw new IllegalStateException("Intento desactualizado");
    long incompletos=db.queryForObject("SELECT count(*) FROM archivos_entrega WHERE preparacion_id=? AND activo AND estado<>'DISPONIBLE'",Long.class,id);
    if(incompletos>0) throw new IllegalStateException("Hay archivos sin cargar completamente");
    var uso=db.queryForMap("SELECT count(*) AS cantidad,COALESCE(sum(tamano),0) AS bytes,COALESCE(max(tamano),0) AS mayor FROM archivos_entrega WHERE preparacion_id=? AND activo AND estado='DISPONIBLE'",id);
    long archivos=((Number)uso.get("cantidad")).longValue();
    if(archivos>4||((Number)uso.get("bytes")).longValue()>100L*1024*1024||((Number)uso.get("mayor")).longValue()>25L*1024*1024)
      throw new IllegalStateException("Los archivos superan los límites de entrega");
    String respuesta=(String)p.get("respuesta");
    if((respuesta==null||respuesta.isBlank())&&archivos==0) throw new IllegalArgumentException("Escribe una respuesta o adjunta un archivo");
    var recepcion=java.time.Instant.now();
    var plazo=((java.sql.Timestamp)t.get("fecha_limite")).toInstant();
    if(!puedeReentregar(tarea,estudiante,intento,plazo,recepcion)) throw new IllegalArgumentException("Ya se realizó la única reentrega fuera de plazo");
    UUID entrega=UUID.randomUUID();
    db.update("INSERT INTO entregas(id,tarea_id,estudiante_erp_id,version,respuesta,estado,entregado_en,fecha_limite_al_entregar) VALUES (?,?,?,?,?,?,?,?)",
      entrega,tarea,estudiante,intento,respuesta,recepcion.isAfter(plazo)?"TARDIA":"ENVIADA",java.sql.Timestamp.from(recepcion),java.sql.Timestamp.from(plazo));
    db.update("UPDATE archivos_entrega SET entrega_id=?,preparacion_id=NULL WHERE preparacion_id=? AND activo AND estado='DISPONIBLE'",entrega,id);
    db.update("UPDATE preparaciones_entrega SET estado='CONFIRMADA',entrega_id=?,clave_confirmacion=?,actualizada_en=now() WHERE id=?",entrega,operacion,id);
    avisos.entrega(entrega,tarea,(UUID)t.get("curso_id"),(String)t.get("titulo"));
    auditar(a,"ENTREGA",entrega);
    return db.queryForMap("SELECT * FROM entregas WHERE id=?",entrega);
  }
  @Transactional public UUID entregar(UUID tarea,String respuesta,Authentication a) {
    var t=tareaAbierta(tarea,a);
    UUID estudiante=usuario.referencia(a);
    int intento=ultimoIntento(tarea,estudiante)+1;
    if(!puedeReentregar(tarea,estudiante,intento,((java.sql.Timestamp)t.get("fecha_limite")).toInstant(),java.time.Instant.now()))
      throw new IllegalArgumentException("Ya se realizó la única reentrega fuera de plazo");
    var p=preparar(tarea,((Number)t.get("version")).intValue(),a);
    guardarPreparacion((UUID)p.get("id"),respuesta,((Number)t.get("version")).intValue(),a);
    return (UUID)confirmarPreparacion((UUID)p.get("id"),UUID.randomUUID(),a).get("id");
  }
  public List<Map<String,Object>> entregas(UUID tarea,Authentication a) {
    var t=tarea(tarea,a);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("SELECT * FROM entregas WHERE tarea_id=? AND estudiante_erp_id=? ORDER BY version DESC",tarea,usuario.referencia(a));
    usuario.docente(a,(UUID)t.get("curso_id"));
    var filas=db.queryForList("SELECT * FROM entregas WHERE tarea_id=? ORDER BY entregado_en DESC",tarea);
    if(filas.isEmpty()) return filas;
    Map<String,String> nombres=new HashMap<>();
    try { for(var e:erp.get("/api/erp/cursos/"+t.get("curso_id")+"/estudiantes/resumen")) nombres.put(e.get("id").asText(),e.get("nombre").asText()); }
    catch(Exception e) { /* Las entregas siguen disponibles si ERP no responde. */ }
    for(var e:filas) e.put("estudiante",nombres.getOrDefault(e.get("estudiante_erp_id").toString(),"Estudiante no identificado"));
    return filas;
  }
  public List<Map<String,Object>> seguimiento(UUID tarea,String grupo,Authentication a) {
    if(!List.of("TODOS","ENTREGARON","TARDIAS","PENDIENTES","REVISION").contains(grupo))
      throw new IllegalArgumentException("Filtro inválido");
    var t=tarea(tarea,a); UUID curso=(UUID)t.get("curso_id"); usuario.docente(a,curso);
    var filas=db.queryForList("""
      SELECT i.estudiante_erp_id,e.id AS entrega_id,e.estado AS estado_entrega,e.entregado_en,
             e.version AS entrega_version,e.fecha_limite_al_entregar,
             n.id AS nota_id,n.entrega_version AS version_calificada
      FROM inscripciones_curso i
      LEFT JOIN LATERAL (
        SELECT id,estado,entregado_en,version,fecha_limite_al_entregar
        FROM entregas WHERE tarea_id=? AND estudiante_erp_id=i.estudiante_erp_id
        ORDER BY version DESC LIMIT 1
      ) e ON TRUE
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=? AND n.estudiante_erp_id=i.estudiante_erp_id AND n.entrega_version=e.version AND n.estado='PUBLICADA'
      WHERE i.curso_id=? AND i.activo ORDER BY i.estudiante_erp_id
      """,tarea,tarea,curso);
    Map<String,String> nombres=new HashMap<>();
    try { for(var e:erp.get("/api/erp/cursos/"+curso+"/estudiantes/resumen")) nombres.put(e.get("id").asText(),e.get("nombre").asText()); }
    catch(Exception e) { /* El seguimiento numérico sigue disponible sin ERP. */ }
    for(var e:filas) e.put("estudiante",nombres.getOrDefault(e.get("estudiante_erp_id").toString(),"Estudiante "+e.get("estudiante_erp_id")));
    return filas.stream().filter(e->switch(grupo) {
      case "ENTREGARON" -> e.get("entrega_id")!=null;
      case "TARDIAS" -> "TARDIA".equals(e.get("estado_entrega"));
      case "PENDIENTES" -> e.get("entrega_id")==null;
      case "REVISION" -> e.get("entrega_id")!=null && e.get("nota_id")==null;
      default -> true;
    }).toList();
  }
  @Transactional public Map<String,Object> calificar(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Authentication a) {
    return calificar(tarea,estudiante,valor,comentario,version,null,a);
  }
  @Transactional public Map<String,Object> calificar(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Integer entregaEsperada,Authentication a) {
    var borrador=guardarBorrador(tarea,estudiante,valor,comentario,version,entregaEsperada,a);
    return publicarCalificacion(tarea,estudiante,entregaEsperada,((Number)borrador.get("version")).intValue(),UUID.randomUUID(),a);
  }
  private Map<String,Object> contextoCalificacion(UUID tarea,Authentication a) {
    var t=tarea(tarea,a);
    var bloqueada=db.queryForMap("SELECT estado FROM tareas WHERE id=? FOR UPDATE",tarea);
    usuario.docente(a,(UUID)t.get("curso_id"));
    if("CANCELADA".equals(bloqueada.get("estado")) || "FALTA_COMPLETAR".equals(bloqueada.get("estado")) ||
       db.queryForObject("SELECT count(*) FROM tareas WHERE tarea_anterior_id=?",Long.class,tarea)>0)
      throw new IllegalStateException("Esta versión de la tarea no admite nuevas calificaciones");
    return t;
  }
  private int entregaCalificable(UUID tarea,UUID estudiante,Integer entregaEsperada) {
    int ultima=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,estudiante);
    if(ultima==0) throw new IllegalArgumentException("No existe entrega");
    if(entregaEsperada!=null && entregaEsperada!=ultima) throw new IllegalStateException("Llegó un intento más reciente; vuelve a abrir la entrega");
    return ultima;
  }
  @Transactional public Map<String,Object> guardarBorrador(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Integer entregaEsperada,Authentication a) {
    var t=contextoCalificacion(tarea,a);
    if(valor==null || valor.scale()>2 || valor.compareTo(notaMinima)<0 || valor.compareTo(notaMaxima)>0 || valor.compareTo((BigDecimal)t.get("escala_maxima"))>0)
      throw new IllegalArgumentException("La calificación debe estar entre 0 y 20, con hasta dos decimales");
    int intento=entregaCalificable(tarea,estudiante,entregaEsperada);
    var anteriores=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,intento);
    UUID id;
    if(anteriores.isEmpty()) {
      if(version!=null && version!=0) throw new IllegalStateException("Versión obsoleta");
      id=UUID.randomUUID();
      db.update("INSERT INTO calificaciones_tarea(id,tarea_id,estudiante_erp_id,valor,comentario,entrega_version,actualizado_por,estado) VALUES (?,?,?,?,?,?,?,'BORRADOR')",id,tarea,estudiante,valor,comentario,intento,usuario.cuenta(a));
    } else {
      var anterior=anteriores.getFirst(); id=(UUID)anterior.get("id");
      if(version==null || version!=((Number)anterior.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
      if("PUBLICADA".equals(anterior.get("estado")))
        db.update("UPDATE calificaciones_tarea SET valor_borrador=?,comentario_borrador=?,borrador_actualizado_en=now(),borrador_actualizado_por=?,version=version+1 WHERE id=?",valor,comentario,usuario.cuenta(a),id);
      else
        db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,actualizado_en=now(),actualizado_por=?,version=version+1 WHERE id=?",valor,comentario,usuario.cuenta(a),id);
    }
    auditar(a,"BORRADOR_CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  @Transactional public Map<String,Object> publicarCalificacion(UUID tarea,UUID estudiante,Integer entregaEsperada,Integer version,UUID operacionId,Authentication a) {
    var t=contextoCalificacion(tarea,a);
    if(operacionId==null) throw new IllegalArgumentException("Falta identificador de operación");
    var repetida=db.queryForList("SELECT c.* FROM historial_calificaciones h JOIN calificaciones_tarea c ON c.id=h.calificacion_id WHERE h.operacion_id=?",operacionId);
    if(!repetida.isEmpty()) {
      if(!tarea.equals(repetida.getFirst().get("tarea_id")) || !estudiante.equals(repetida.getFirst().get("estudiante_erp_id")))
        throw new IllegalStateException("Identificador de publicación utilizado en otra evaluación");
      return repetida.getFirst();
    }
    int intento=entregaCalificable(tarea,estudiante,entregaEsperada);
    var actuales=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? AND entrega_version=? FOR UPDATE",tarea,estudiante,intento);
    if(actuales.isEmpty()) throw new IllegalStateException("Guarda primero el borrador de la calificación");
    var actual=actuales.getFirst(); UUID id=(UUID)actual.get("id");
    if(version==null || version!=((Number)actual.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
    boolean correccion="PUBLICADA".equals(actual.get("estado"));
    if(correccion && actual.get("valor_borrador")==null) return actual;
    BigDecimal nuevo=(BigDecimal)(correccion?actual.get("valor_borrador"):actual.get("valor"));
    BigDecimal anterior=correccion?(BigDecimal)actual.get("valor"):null;
    String comentarioNuevo=(String)(correccion?actual.get("comentario_borrador"):actual.get("comentario"));
    db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,estado='PUBLICADA',publicada_en=now(),publicada_por=?,valor_borrador=NULL,comentario_borrador=NULL,borrador_actualizado_en=NULL,borrador_actualizado_por=NULL,actualizado_en=now(),actualizado_por=?,version=version+1 WHERE id=?",nuevo,comentarioNuevo,usuario.cuenta(a),usuario.cuenta(a),id);
    db.update("INSERT INTO historial_calificaciones(id,calificacion_id,valor_anterior,valor_nuevo,comentario_anterior,comentario,actor_id,estado_anterior,estado_nuevo,operacion_id) VALUES (?,?,?,?,?,?,?,?,'PUBLICADA',?)",UUID.randomUUID(),id,anterior,nuevo,actual.get("comentario"),comentarioNuevo,usuario.cuenta(a),actual.get("estado"),operacionId);
    int nuevaVersion=((Number)actual.get("version")).intValue()+1;
    avisos.calificacion(tarea,(UUID)t.get("curso_id"),estudiante,(String)t.get("titulo"),id,nuevaVersion,correccion);
    auditar(a,correccion?"CORRECCION_CALIFICACION":"PUBLICACION_CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  public List<Map<String,Object>> notas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    String sql="SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=?";
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList(sql+" AND c.estudiante_erp_id=? AND c.estado='PUBLICADA' ORDER BY c.publicada_en DESC",curso,usuario.referencia(a)).stream().map(this::sinBorrador).toList();
    if("COORDINACION".equals(usuario.tipo(a))) return db.queryForList(sql+" AND c.estado='PUBLICADA' ORDER BY c.publicada_en DESC",curso).stream().map(this::sinBorrador).toList();
    return db.queryForList(sql+" ORDER BY c.actualizado_en DESC",curso);
  }
  public Map<String,Object> nota(UUID id,Authentication a) {
    var n=db.queryForMap("SELECT c.*,t.curso_id,t.titulo,t.instrucciones,t.fecha_limite,t.escala_maxima FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)n.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a)) && !usuario.referencia(a).equals(n.get("estudiante_erp_id"))) throw new AccessDeniedException("Nota ajena");
    if(!"DOCENTE".equals(usuario.tipo(a))) {
      if(!"PUBLICADA".equals(n.get("estado"))) throw new AccessDeniedException("Calificación sin publicar");
      return sinBorrador(n);
    }
    return n;
  }
  private Map<String,Object> sinBorrador(Map<String,Object> fila) {
    var visible=new HashMap<>(fila);
    visible.remove("valor_borrador"); visible.remove("comentario_borrador");
    visible.remove("borrador_actualizado_en"); visible.remove("borrador_actualizado_por");
    visible.remove("version");
    return visible;
  }
  public List<Map<String,Object>> historialNota(UUID id,Authentication a) {
    nota(id,a);
    return db.queryForList("SELECT id,valor_anterior,valor_nuevo,comentario_anterior,comentario,actor_id,instante,estado_anterior,estado_nuevo FROM historial_calificaciones WHERE calificacion_id=? ORDER BY instante,id",id);
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
