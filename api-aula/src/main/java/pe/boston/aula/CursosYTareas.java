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
             count(*) FILTER (WHERE e.id IS NOT NULL AND (n.id IS NULL OR n.entrega_version<e.version)) AS pendientes_revision
      FROM inscripciones_curso i
      LEFT JOIN LATERAL (
        SELECT id,estado,version FROM entregas WHERE tarea_id=t.id AND estudiante_erp_id=i.estudiante_erp_id
        ORDER BY version DESC LIMIT 1
      ) e ON TRUE
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=t.id AND n.estudiante_erp_id=i.estudiante_erp_id
      WHERE i.curso_id=t.curso_id AND i.activo
    ) k ON TRUE
    """;
  private final JdbcTemplate db; private final UsuarioActual usuario; private final ErpCliente erp; private final AvisosTarea avisos;
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
        (SELECT e.estado FROM entregas e WHERE e.tarea_id=t.id AND e.estudiante_erp_id=? ORDER BY e.version DESC LIMIT 1) AS ultima_entrega_estado
      FROM tareas t WHERE t.curso_id=? AND t.estado<>'FALTA_COMPLETAR'
        AND NOT EXISTS (SELECT 1 FROM tareas siguiente WHERE siguiente.tarea_anterior_id=t.id)
      ORDER BY t.publicado_en DESC NULLS LAST,t.fecha_limite
      """,usuario.referencia(a),usuario.referencia(a),curso);
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
  @Transactional public UUID entregar(UUID tarea,String respuesta,Authentication a) {
    usuario.estudiante(a); tarea(tarea,a);
    var t=db.queryForMap("SELECT * FROM tareas WHERE id=? FOR UPDATE",tarea); UUID curso=(UUID)t.get("curso_id"); usuario.estudianteCurso(a,curso);
    if(!"PUBLICADA".equals(t.get("estado"))) throw new IllegalArgumentException("La tarea está cerrada");
    var recepcion=java.time.Instant.now();
    var plazo=((java.sql.Timestamp)t.get("fecha_limite")).toInstant();
    boolean tarde=recepcion.isAfter(plazo);
    int version=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,usuario.referencia(a));
    if(version>0 && !Boolean.TRUE.equals(t.get("permite_reentrega"))) throw new IllegalArgumentException("Reentrega no permitida");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO entregas(id,tarea_id,estudiante_erp_id,version,respuesta,estado,entregado_en,fecha_limite_al_entregar) VALUES (?,?,?,?,?,?,?,?)",
      id,tarea,usuario.referencia(a),version+1,respuesta,tarde?"TARDIA":"ENVIADA",java.sql.Timestamp.from(recepcion),java.sql.Timestamp.from(plazo));
    avisos.entrega(id,tarea,curso,(String)t.get("titulo"));
    auditar(a,"ENTREGA",id); return id;
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
      LEFT JOIN calificaciones_tarea n ON n.tarea_id=? AND n.estudiante_erp_id=i.estudiante_erp_id
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
      case "REVISION" -> e.get("entrega_id")!=null && (e.get("nota_id")==null || ((Number)e.get("version_calificada")).intValue()<((Number)e.get("entrega_version")).intValue());
      default -> true;
    }).toList();
  }
  @Transactional public Map<String,Object> calificar(UUID tarea,UUID estudiante,BigDecimal valor,String comentario,Integer version,Authentication a) {
    var t=tarea(tarea,a); usuario.docente(a,(UUID)t.get("curso_id"));
    if(valor==null || valor.signum()<0 || valor.compareTo((BigDecimal)t.get("escala_maxima"))>0) throw new IllegalArgumentException("Calificación fuera de escala");
    int ultimaEntrega=db.queryForObject("SELECT COALESCE(max(version),0) FROM entregas WHERE tarea_id=? AND estudiante_erp_id=?",Integer.class,tarea,estudiante);
    if(ultimaEntrega==0) throw new IllegalArgumentException("No existe entrega");
    var anteriores=db.queryForList("SELECT * FROM calificaciones_tarea WHERE tarea_id=? AND estudiante_erp_id=? FOR UPDATE",tarea,estudiante);
    UUID id; BigDecimal antes=null;
    if(anteriores.isEmpty()) {
      if(version!=null && version!=0) throw new IllegalStateException("Versión obsoleta");
      id=UUID.randomUUID(); db.update("INSERT INTO calificaciones_tarea(id,tarea_id,estudiante_erp_id,valor,comentario,entrega_version,actualizado_por) VALUES (?,?,?,?,?,?,?)",id,tarea,estudiante,valor,comentario,ultimaEntrega,usuario.cuenta(a));
    } else {
      var anterior=anteriores.getFirst(); id=(UUID)anterior.get("id"); antes=(BigDecimal)anterior.get("valor");
      if(version==null || version!=((Number)anterior.get("version")).intValue()) throw new IllegalStateException("Versión obsoleta");
      db.update("UPDATE calificaciones_tarea SET valor=?,comentario=?,entrega_version=?,version=version+1,actualizado_en=now(),actualizado_por=? WHERE id=?",valor,comentario,ultimaEntrega,usuario.cuenta(a),id);
    }
    db.update("INSERT INTO historial_calificaciones(id,calificacion_id,valor_anterior,valor_nuevo,comentario,actor_id) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,antes,valor,comentario,usuario.cuenta(a));
    auditar(a,"CALIFICACION",id);
    return db.queryForMap("SELECT * FROM calificaciones_tarea WHERE id=?",id);
  }
  public List<Map<String,Object>> notas(UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    if("ESTUDIANTE".equals(usuario.tipo(a))) return db.queryForList("SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? AND c.estudiante_erp_id=? ORDER BY c.actualizado_en DESC",curso,usuario.referencia(a));
    return db.queryForList("SELECT c.*,t.titulo,t.fecha_limite,t.escala_maxima,(SELECT max(e.version) FROM entregas e WHERE e.tarea_id=c.tarea_id AND e.estudiante_erp_id=c.estudiante_erp_id) AS ultima_entrega_version FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE t.curso_id=? ORDER BY c.actualizado_en DESC",curso);
  }
  public Map<String,Object> nota(UUID id,Authentication a) {
    var n=db.queryForMap("SELECT c.*,t.curso_id,t.titulo,t.instrucciones,t.fecha_limite,t.escala_maxima FROM calificaciones_tarea c JOIN tareas t ON t.id=c.tarea_id WHERE c.id=?",id);
    usuario.leerCurso(a,(UUID)n.get("curso_id"));
    if("ESTUDIANTE".equals(usuario.tipo(a))&&!usuario.referencia(a).equals(n.get("estudiante_erp_id"))) throw new AccessDeniedException("Nota ajena");
    return n;
  }
  private void auditar(Authentication a,String accion,UUID id) { db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id); }
}
