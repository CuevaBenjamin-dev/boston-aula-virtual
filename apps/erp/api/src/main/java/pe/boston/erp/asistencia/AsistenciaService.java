package pe.boston.erp.asistencia;

import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import pe.boston.erp.matriculas.MatriculasParaAsistencia;
import pe.boston.erp.asignaciones.AsignacionesParaAsistencia;
import pe.boston.erp.oferta.OfertaConsulta;

/** Hechos oficiales, avance privado y calendario de Asistencia. */
@Service
public class AsistenciaService {
  public static final ZoneId LIMA=ZoneId.of("America/Lima");
  public record Actor(String sub,String rol,UUID personaId) {}
  public record Marca(UUID personaId,String valor) {}
  public record Lote(int version,String clave,List<Marca> marcas) {}
  public record Calendario(int anio,LocalDate inicio,LocalDate fin,List<Integer> diasSemana,
    LocalDate habilitadoDesde,String recordatorioHora,int version) {}
  public record Excepcion(LocalDate fecha,UUID seccionId,boolean lectivo,String motivo) {}
  public record Alumno(UUID personaId,UUID matriculaId,String nombre,String marca,String borrador,
    String visible,boolean justificada,String origen,boolean valido) {}
  public record Jornada(UUID seccionId,LocalDate fecha,boolean lectivo,int version,boolean confirmada,
    boolean cerrada,boolean alertaSinMarcas,List<Alumno> alumnos) {}
  public record Resumen(UUID seccionId,String seccion,LocalDate fecha,boolean lectivo,int total,int marcados,
    boolean confirmada,boolean cerrada,boolean alertaSinMarcas) {}
  public record Historial(UUID personaId,LocalDate fecha,UUID seccionId,String seccion,String marca,
    String visible,String origen,boolean valido,UUID registroId,boolean puedeJustificar) {}
  public record Estadisticas(int presente,int tardanza,int tardanzaJustificada,int falta,
    int justificada,int pendiente,int marcasValidas,int diasElegibles,Double presenciaFisica,Double cobertura) {}
  public record Justificacion(UUID id,UUID registroId,UUID estudianteId,String nombre,String marca,
    LocalDate fecha,String estado,String motivo,int version,String comentario) {}
  public record Salida(UUID id,UUID personaId,String nombre,String seccion,String estado,String motivo,
    String ocurridoEn,String actor) {}
  private final JdbcTemplate db;
  private final MatriculasParaAsistencia matriculas;
  private final OfertaConsulta oferta;
  private final AsignacionesParaAsistencia asignaciones;
  private final Clock clock;
  private final AdjuntosAsistencia almacenamiento;
  private final int maxBytes;
  private final int maxAdjuntos;
  public AsistenciaService(JdbcTemplate db,MatriculasParaAsistencia matriculas,OfertaConsulta oferta,
      AsignacionesParaAsistencia asignaciones,Clock clock,AdjuntosAsistencia almacenamiento,
      @Value("${BOSTON_AST_ADJUNTO_MAX_BYTES:2000000}") int maxBytes,
      @Value("${BOSTON_AST_MAX_ADJUNTOS:5}") int maxAdjuntos) {
    this.db=db;this.matriculas=matriculas;this.oferta=oferta;this.asignaciones=asignaciones;this.clock=clock;
    this.almacenamiento=almacenamiento;
    this.maxBytes=Math.max(1,Math.min(2_000_000,maxBytes));this.maxAdjuntos=Math.max(1,maxAdjuntos);
  }
  public static class Conflicto extends RuntimeException { public Conflicto(String mensaje) { super(mensaje); } }
  public LocalDate hoy() { return LocalDate.now(clock.withZone(LIMA)); }
  public LocalTime hora() { return LocalTime.now(clock.withZone(LIMA)); }
  public static String requerido(String valor,String nombre) {
    if (valor==null || valor.isBlank()) throw new IllegalArgumentException(nombre+" es obligatorio");
    return valor.trim();
  }
  public static void rol(Actor actor,String... permitidos) {
    for (String permitido:permitidos) if (permitido.equals(actor.rol())) return;
    throw new SecurityException("Perfil sin autorización para Asistencia");
  }
  public void docenteActivo(Actor actor) {
    rol(actor,"DOCENTE");
    if (actor.personaId()==null || !Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM per_condiciones WHERE persona_id=? AND tipo='DOCENTE' AND periodo @> now())
      """,Boolean.class,actor.personaId()))) throw new SecurityException("Condición Docente inactiva");
  }
  public Calendario calendario(int anio) {
    var filas=db.query("SELECT anio,inicio,fin,dias_semana,habilitado_desde,recordatorio_hora,version FROM ast_calendarios WHERE anio=?",
      (r,n)->new Calendario(r.getInt(1),r.getDate(2).toLocalDate(),r.getDate(3).toLocalDate(),
        List.of((Integer[])r.getArray(4).getArray()),r.getDate(5).toLocalDate(),r.getTime(6).toLocalTime().toString(),r.getInt(7)),anio);
    return filas.isEmpty()?null:filas.getFirst();
  }
  @Transactional public Calendario configurarCalendario(Actor actor,Calendario c) {
    rol(actor,"COORDINACION","DIRECCION");
    if(c==null||c.inicio()==null||c.fin()==null||c.anio()<2000||c.anio()>2100
        ||c.inicio().getYear()!=c.anio()||c.fin().getYear()!=c.anio()||c.fin().isBefore(c.inicio())
        ||c.diasSemana()==null||c.diasSemana().isEmpty()||c.diasSemana().stream().anyMatch(d->d<1||d>7))
      throw new IllegalArgumentException("Calendario anual inválido");
    LocalDate habilitado=c.anio()>hoy().getYear()?c.inicio():hoy();
    if(calendario(c.anio())!=null) throw new Conflicto("El calendario existe; modifica excepciones sin reescribir la base anual");
    db.update("INSERT INTO ast_calendarios(anio,inicio,fin,dias_semana,habilitado_desde) VALUES(?,?,?,?,?)",
      c.anio(),c.inicio(),c.fin(),c.diasSemana().toArray(Integer[]::new),habilitado);
    return calendario(c.anio());
  }
  public List<Map<String,Object>> excepciones(int anio) {
    return db.queryForList("SELECT id,fecha,seccion_id,lectivo,motivo,actor_sub,creado_en FROM ast_excepciones WHERE extract(year from fecha)=? ORDER BY fecha,seccion_id",anio);
  }
  public boolean lectivo(UUID seccionId,LocalDate fecha) {
    OfertaConsulta.Seccion s=oferta.seccion(seccionId);
    if(s==null||s.anio()!=fecha.getYear()) return false;
    Calendario c=calendario(s.anio());
    if(c==null||fecha.isBefore(c.inicio())||fecha.isAfter(c.fin())||fecha.isBefore(c.habilitadoDesde())) return false;
    var excepcion=db.query("""
      SELECT lectivo FROM ast_excepciones WHERE fecha=? AND (seccion_id=? OR seccion_id IS NULL)
      ORDER BY seccion_id NULLS LAST LIMIT 1
      """,(r,n)->r.getBoolean(1),fecha,seccionId);
    return excepcion.isEmpty()?c.diasSemana().contains(fecha.getDayOfWeek().getValue()):excepcion.getFirst();
  }
  @Transactional public void excepcion(Actor actor,Excepcion e) {
    rol(actor,"COORDINACION");
    if(e==null||e.fecha()==null) throw new IllegalArgumentException("Fecha obligatoria");
    String motivo=requerido(e.motivo(),"Motivo");
    if(calendario(e.fecha().getYear())==null) throw new IllegalArgumentException("Configura primero el calendario anual");
    if(e.seccionId()!=null && (oferta.seccion(e.seccionId())==null||oferta.seccion(e.seccionId()).anio()!=e.fecha().getYear()))
      throw new IllegalArgumentException("Sección ajena al año");
    List<UUID> secciones=e.seccionId()==null?oferta.secciones(e.fecha().getYear()).stream()
      .map(OfertaConsulta.Seccion::id).toList():List.of(e.seccionId());
    for(UUID seccion:secciones) {
      asegurarJornada(seccion,e.fecha());
      db.queryForObject("SELECT version FROM ast_jornadas WHERE seccion_id=? AND fecha=? FOR UPDATE",
        Integer.class,seccion,e.fecha());
    }
    db.update("""
      INSERT INTO ast_excepciones(id,fecha,seccion_id,lectivo,motivo,actor_sub)
      VALUES(?,?,?,?,?,?) ON CONFLICT (fecha,seccion_id) DO UPDATE
      SET lectivo=excluded.lectivo,motivo=excluded.motivo,actor_sub=excluded.actor_sub,creado_en=now()
      """,UUID.randomUUID(),e.fecha(),e.seccionId(),e.lectivo(),motivo,actor.sub());
    if(!e.lectivo()) {
      for(UUID seccion:secciones) {
        if(lectivo(seccion,e.fecha())) continue;
        var afectados=db.query("SELECT id,persona_id,marca FROM ast_registros WHERE fecha=? AND seccion_id=? AND valido",
          (r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)},e.fecha(),seccion);
        for(Object[] r:afectados) {
          db.update("UPDATE ast_registros SET valido=false,version=version+1 WHERE id=?",r[0]);
          auditar((UUID)r[0],seccion,e.fecha(),(UUID)r[1],"INVALIDADA",(String)r[2],null,motivo,actor.sub());
        }
        db.update("DELETE FROM ast_borradores WHERE fecha=? AND seccion_id=?",e.fecha(),seccion);
        db.update("UPDATE ast_jornadas SET version=version+1,confirmada=false,alerta_sin_marcas=false,cerrada_en=COALESCE(cerrada_en,now()) WHERE fecha=? AND seccion_id=?",e.fecha(),seccion);
        db.update("DELETE FROM ast_avisos_pendientes WHERE fecha=? AND seccion_id=? AND enviado_en IS NULL",e.fecha(),seccion);
      }
    } else for(UUID seccion:secciones) if(lectivo(seccion,e.fecha())) {
      db.update("UPDATE ast_jornadas SET version=version+1,confirmada=false,cerrada_en=CASE WHEN fecha<? THEN COALESCE(cerrada_en,now()) ELSE NULL END WHERE fecha=? AND seccion_id=?",
        hoy(),e.fecha(),seccion);
    }
  }

  private void asegurarJornada(UUID seccion,LocalDate fecha) {
    db.update("INSERT INTO ast_jornadas(seccion_id,fecha) VALUES(?,?) ON CONFLICT DO NOTHING",seccion,fecha);
  }
  public Jornada jornada(UUID seccion,LocalDate fecha,boolean incluirBorrador) {
    OfertaConsulta.Seccion s=oferta.seccion(seccion);
    if(s==null) throw new IllegalArgumentException("Sección inexistente");
    boolean lectivo=lectivo(seccion,fecha);
    var meta=db.query("SELECT version,confirmada,cerrada_en IS NOT NULL,alerta_sin_marcas FROM ast_jornadas WHERE seccion_id=? AND fecha=?",
      (r,n)->new Object[]{r.getInt(1),r.getBoolean(2),r.getBoolean(3),r.getBoolean(4)},seccion,fecha);
    Object[] m=meta.isEmpty()?new Object[]{0,false,false,false}:meta.getFirst();
    var registros=db.query("""
      SELECT persona_id,id,marca,origen,valido FROM ast_registros WHERE seccion_id=? AND fecha=? ORDER BY valido
      """,(r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getBoolean(5)},seccion,fecha);
    Map<UUID,Object[]> porPersona=new HashMap<>();
    for(Object[] r:registros) porPersona.put((UUID)r[0],r);
    Map<UUID,String> borradores=new HashMap<>();
    if(incluirBorrador) db.query("SELECT persona_id,marca FROM ast_borradores WHERE seccion_id=? AND fecha=?",
      r->{borradores.put(r.getObject(1,UUID.class),r.getString(2));},seccion,fecha);
    var alumnos=new ArrayList<Alumno>();
    if(lectivo) for(var p:matriculas.padron(seccion,fecha)) {
      Object[] r=porPersona.get(p.personaId());
      String marca=r==null||!(Boolean)r[4]?null:(String)r[2];
      boolean justificada=r!=null&&marca!=null&&aprobada((UUID)r[1]);
      String visible=marca==null?"PENDIENTE":justificada?
        ("FALTA".equals(marca)?"JUSTIFICADA":"TARDANZA_JUSTIFICADA"):marca;
      alumnos.add(new Alumno(p.personaId(),p.matriculaId(),p.nombre(),marca,
        borradores.get(p.personaId()),visible,justificada,r==null?null:(String)r[3],r==null||(Boolean)r[4]));
    }
    return new Jornada(seccion,fecha,lectivo,(Integer)m[0],(Boolean)m[1],(Boolean)m[2],(Boolean)m[3],alumnos);
  }
  private boolean aprobada(UUID registro) {
    return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM ast_justificaciones WHERE registro_id=? AND estado='APROBADA')",
      Boolean.class,registro));
  }
  public List<Resumen> control(LocalDate fecha,int anio) {
    var salida=new ArrayList<Resumen>();
    for(var s:oferta.secciones(anio)) {
      Jornada j=jornada(s.id(),fecha,false);
      if(!j.lectivo()) continue;
      salida.add(new Resumen(s.id(),s.nivel()+" "+s.grado()+"° "+s.nombre(),fecha,true,j.alumnos().size(),
        (int)j.alumnos().stream().filter(a->a.marca()!=null).count(),j.confirmada(),j.cerrada(),j.alertaSinMarcas()));
    }
    return salida;
  }
  private void editable(Actor actor,UUID seccion,LocalDate fecha) {
    if("DOCENTE".equals(actor.rol())) {
      docenteActivo(actor);
      if(!fecha.equals(hoy())) throw new SecurityException("El docente solo puede registrar hoy en Lima");
    } else rol(actor,"COORDINACION","DIRECCION");
    if(fecha.isAfter(hoy())) throw new IllegalArgumentException("No se registra asistencia futura");
    if(!lectivo(seccion,fecha)) throw new Conflicto("La fecha no es lectiva para esta sección");
  }
  private void validarLote(Lote lote) {
    if(lote==null||lote.version()<0||lote.marcas()==null||lote.marcas().size()>1000)
      throw new IllegalArgumentException("Lote inválido");
    requerido(lote.clave(),"Clave de operación");
    if(lote.clave().length()>150) throw new IllegalArgumentException("Clave demasiado larga");
    Set<UUID> vistos=new HashSet<>();
    for(var marca:lote.marcas()) if(marca==null||marca.personaId()==null||!vistos.add(marca.personaId())
        ||!Set.of("PRESENTE","TARDANZA","FALTA").contains(marca.valor()))
      throw new IllegalArgumentException("Marca inválida o duplicada");
  }
  @Transactional public Jornada avance(Actor actor,UUID seccion,LocalDate fecha,Lote lote) {
    editable(actor,seccion,fecha);validarLote(lote);
    asegurarJornada(seccion,fecha);
    Integer version=db.queryForObject("SELECT version FROM ast_jornadas WHERE seccion_id=? AND fecha=? FOR UPDATE",
      Integer.class,seccion,fecha);
    if(!lectivo(seccion,fecha)) throw new Conflicto("La fecha dejó de ser lectiva");
    if(operacionExistente(lote.clave(),actor.sub())) return jornada(seccion,fecha,true);
    if(version!=lote.version()) throw new Conflicto("La lista cambió en otra sesión. Recarga antes de guardar.");
    Set<UUID> padron=new HashSet<>();
    for(var p:matriculas.padron(seccion,fecha)) padron.add(p.personaId());
    for(var marca:lote.marcas()) {
      if(!padron.contains(marca.personaId())) throw new SecurityException("Estudiante fuera del padrón efectivo");
      var protegida=db.query("SELECT id,marca FROM ast_registros WHERE persona_id=? AND fecha=? AND valido",
        (r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2)},marca.personaId(),fecha);
      if(!protegida.isEmpty()&&aprobada((UUID)protegida.getFirst()[0])
          &&!marca.valor().equals(protegida.getFirst()[1]))
        throw new Conflicto("La incidencia justificada está protegida");
      db.update("""
        INSERT INTO ast_borradores(seccion_id,fecha,persona_id,marca,actor_sub)
        VALUES(?,?,?,?,?) ON CONFLICT (seccion_id,fecha,persona_id) DO UPDATE
        SET marca=excluded.marca,actor_sub=excluded.actor_sub,recibido_en=now()
        """,seccion,fecha,marca.personaId(),marca.valor(),actor.sub());
      auditar(null,seccion,fecha,marca.personaId(),"AVANCE_RECIBIDO",null,marca.valor(),null,actor.sub());
    }
    db.update("UPDATE ast_jornadas SET version=version+1,confirmada=false WHERE seccion_id=? AND fecha=?",seccion,fecha);
    registrarOperacion(lote.clave(),actor.sub());
    return jornada(seccion,fecha,true);
  }
  @Transactional public Jornada publicar(Actor actor,UUID seccion,LocalDate fecha,int version,String clave,boolean confirmar,String motivo) {
    editable(actor,seccion,fecha);
    requerido(clave,"Clave de operación");
    if(fecha.isBefore(hoy()) && "DOCENTE".equals(actor.rol())) throw new SecurityException("Fecha docente vencida");
    if(fecha.isBefore(hoy())) requerido(motivo,"Motivo de regularización");
    asegurarJornada(seccion,fecha);
    Integer actual=db.queryForObject("SELECT version FROM ast_jornadas WHERE seccion_id=? AND fecha=? FOR UPDATE",
      Integer.class,seccion,fecha);
    if(!lectivo(seccion,fecha)) throw new Conflicto("La fecha dejó de ser lectiva");
    if(operacionExistente(clave,actor.sub())) return jornada(seccion,fecha,true);
    if(actual!=version) throw new Conflicto("La jornada cambió. Recarga antes de publicar.");
    publicarBorradores(seccion,fecha,actor.sub(),motivo);
    var j=jornada(seccion,fecha,false);
    if(j.alumnos().isEmpty()) throw new IllegalArgumentException("No hay estudiantes elegibles en el padrón de este día");
    if(confirmar && j.alumnos().stream().anyMatch(a->a.marca()==null))
      throw new IllegalArgumentException("Para confirmar marca primero a todo el padrón");
    db.update("UPDATE ast_jornadas SET version=version+1,confirmada=? WHERE seccion_id=? AND fecha=?",confirmar,seccion,fecha);
    registrarOperacion(clave,actor.sub());
    return jornada(seccion,fecha,false);
  }
  private void publicarBorradores(UUID seccion,LocalDate fecha,String actor,String motivo) {
    var padron=matriculas.padron(seccion,fecha);
    Map<UUID,UUID> matriculaPorPersona=new HashMap<>();
    for(var p:padron) matriculaPorPersona.put(p.personaId(),p.matriculaId());
    var borradores=db.query("SELECT persona_id,marca FROM ast_borradores WHERE seccion_id=? AND fecha=? ORDER BY recibido_en,persona_id",
      (r,n)->new Marca(r.getObject(1,UUID.class),r.getString(2)),seccion,fecha);
    for(var b:borradores) {
      UUID matricula=matriculaPorPersona.get(b.personaId());
      if(matricula==null) throw new Conflicto("Cambió el padrón. Conservar el avance y revisar la matrícula.");
      var previo=db.query("SELECT id,marca,valido,seccion_id FROM ast_registros WHERE persona_id=? AND fecha=? AND valido FOR UPDATE",
        (r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getObject(4,UUID.class)},b.personaId(),fecha);
      if(!previo.isEmpty()) {
        Object[] p=previo.getFirst();
        if(!seccion.equals(p[3])) throw new Conflicto("Existe una marca en otra sección. Requiere regularización.");
        if(aprobada((UUID)p[0]) && !b.valor().equals(p[1]))
          throw new Conflicto("Una incidencia justificada no se sobrescribe");
        if(!b.valor().equals(p[1])) {
          db.update("UPDATE ast_registros SET marca=?,origen='HUMANO',version=version+1,actor_sub=?,publicado_en=now(),seccion_id=?,matricula_id=? WHERE id=?",
            b.valor(),actor,seccion,matricula,p[0]);
          db.update("""
            INSERT INTO ast_justificacion_eventos(id,justificacion_id,accion,contenido,actor_sub)
            SELECT gen_random_uuid(),id,'OBSOLETA','La incidencia original fue corregida',?
            FROM ast_justificaciones WHERE registro_id=? AND estado='EN_REVISION'
            """,actor,p[0]);
          db.update("""
            UPDATE ast_justificaciones SET estado='OBSOLETA',version=version+1,
              comentario_resolucion='La incidencia original fue corregida',
              resuelto_por=?,resuelto_en=now()
            WHERE registro_id=? AND estado='EN_REVISION'
            """,actor,p[0]);
          auditar((UUID)p[0],seccion,fecha,b.personaId(),"CORREGIDA",(String)p[1],b.valor(),motivo,actor);
        }
      } else {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO ast_registros(id,persona_id,matricula_id,seccion_id,fecha,marca,origen,actor_sub) VALUES(?,?,?,?,?,?,'HUMANO',?)",
          id,b.personaId(),matricula,seccion,fecha,b.valor(),actor);
        auditar(id,seccion,fecha,b.personaId(),"PUBLICADA",null,b.valor(),motivo,actor);
      }
    }
    db.update("DELETE FROM ast_borradores WHERE seccion_id=? AND fecha=?",seccion,fecha);
  }
  private boolean operacionExistente(String clave,String actor) {
    var anteriores=db.query("SELECT actor_sub FROM ast_operaciones WHERE clave=?",(r,n)->r.getString(1),clave);
    if(anteriores.isEmpty()) return false;
    if(!actor.equals(anteriores.getFirst())) throw new Conflicto("Clave de operación ya usada por otra cuenta");
    return true;
  }
  private void registrarOperacion(String clave,String actor) {
    db.update("INSERT INTO ast_operaciones(clave,actor_sub,respuesta) VALUES(?,?,'{}'::jsonb)",clave,actor);
  }
  @Transactional public void cerrar(UUID seccion,LocalDate fecha) {
    if(!fecha.isBefore(hoy())||!lectivo(seccion,fecha)) return;
    asegurarJornada(seccion,fecha);
    db.queryForObject("SELECT version FROM ast_jornadas WHERE seccion_id=? AND fecha=? FOR UPDATE",Integer.class,seccion,fecha);
    if(!lectivo(seccion,fecha)) return;
    Boolean cerrado=db.queryForObject("SELECT cerrada_en IS NOT NULL FROM ast_jornadas WHERE seccion_id=? AND fecha=?",Boolean.class,seccion,fecha);
    if(Boolean.TRUE.equals(cerrado)) return;
    publicarBorradores(seccion,fecha,"SISTEMA_CIERRE",null);
    var padron=matriculas.padron(seccion,fecha);
    if(padron.isEmpty()) {
      db.update("UPDATE ast_jornadas SET cerrada_en=now(),version=version+1 WHERE seccion_id=? AND fecha=?",seccion,fecha);
      return;
    }
    long marcados=db.queryForObject("SELECT count(*) FROM ast_registros WHERE seccion_id=? AND fecha=? AND valido",
      Long.class,seccion,fecha);
    if(marcados==0) {
      db.update("UPDATE ast_jornadas SET cerrada_en=now(),alerta_sin_marcas=true,version=version+1 WHERE seccion_id=? AND fecha=?",seccion,fecha);
      return;
    }
    for(var p:padron) if(!Boolean.TRUE.equals(db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM ast_registros WHERE persona_id=? AND fecha=? AND valido)",Boolean.class,p.personaId(),fecha))) {
      UUID id=UUID.randomUUID();
      db.update("INSERT INTO ast_registros(id,persona_id,matricula_id,seccion_id,fecha,marca,origen,actor_sub) VALUES(?,?,?,?,?,'FALTA','SISTEMA','SISTEMA_CIERRE')",
        id,p.personaId(),p.matriculaId(),seccion,fecha);
      auditar(id,seccion,fecha,p.personaId(),"FALTA_AUTOMATICA",null,"FALTA",null,"SISTEMA_CIERRE");
    }
    db.update("UPDATE ast_jornadas SET cerrada_en=now(),version=version+1 WHERE seccion_id=? AND fecha=?",seccion,fecha);
  }
  public List<Historial> historial(UUID persona,int anio) {
    if(anio<2000||anio>2100) throw new IllegalArgumentException("Año inválido");
    return db.query("""
      SELECT r.persona_id,r.fecha,r.seccion_id,s.nivel||' '||s.grado||'° '||s.nombre,
        r.marca,CASE WHEN j.estado='APROBADA' AND r.valido THEN
          CASE r.marca WHEN 'FALTA' THEN 'JUSTIFICADA' WHEN 'TARDANZA' THEN 'TARDANZA_JUSTIFICADA' ELSE r.marca END
          ELSE r.marca END,r.origen,r.valido,r.id,r.matricula_id
      FROM ast_registros r JOIN ofe_secciones s ON s.id=r.seccion_id
      LEFT JOIN ast_justificaciones j ON j.registro_id=r.id AND j.estado='APROBADA'
      WHERE r.persona_id=? AND extract(year from r.fecha)=?
      ORDER BY r.fecha DESC,r.id
      """,(r,n)->new Historial(r.getObject(1,UUID.class),r.getDate(2).toLocalDate(),
        r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getString(6),
        r.getString(7),r.getBoolean(8),r.getObject(9,UUID.class),
        r.getBoolean(8) && Set.of("FALTA","TARDANZA").contains(r.getString(5))
          && matriculas.puedeSolicitar(persona,r.getObject(10,UUID.class))),persona,anio);
  }
  public List<Integer> anios(UUID persona) {
    return db.query("SELECT DISTINCT anio FROM mat_matriculas WHERE persona_id=? AND estado<>'BORRADOR' ORDER BY anio DESC",
      (r,n)->r.getInt(1),persona);
  }
  public Estadisticas estadisticas(UUID persona,int anio) {
    List<Historial> hechos=historial(persona,anio).stream().filter(Historial::valido).toList();
    int p=0,t=0,tj=0,f=0,j=0;
    for(var h:hechos) switch(h.visible()) {
      case "PRESENTE" -> p++; case "TARDANZA" -> t++;
      case "TARDANZA_JUSTIFICADA" -> tj++; case "FALTA" -> f++;
      case "JUSTIFICADA" -> j++; default -> { }
    }
    int elegibles=db.queryForObject("""
      SELECT count(DISTINCT dia::date) FROM ast_calendarios c
      JOIN mat_tramos_asistencia t ON t.persona_id=?
      JOIN ofe_secciones s ON s.id=t.seccion_id AND s.anio=c.anio
      CROSS JOIN LATERAL generate_series(greatest(c.inicio,c.habilitado_desde,t.desde),
        least(c.fin,?::date,coalesce(t.hasta,c.fin)),interval '1 day') dia
      WHERE c.anio=? AND coalesce((SELECT e.lectivo FROM ast_excepciones e
        WHERE e.fecha=dia::date AND (e.seccion_id=s.id OR e.seccion_id IS NULL)
        ORDER BY e.seccion_id NULLS LAST LIMIT 1),
        extract(isodow FROM dia)::int=ANY(c.dias_semana))
      """,Integer.class,persona,hoy(),anio);
    int total=p+t+tj+f+j;
    return new Estadisticas(p,t,tj,f,j,Math.max(0,elegibles-total),total,elegibles,
      total==0?null:(p+t+tj)*100.0/total,elegibles==0?null:total*100.0/elegibles);
  }
  public List<Map<String,Object>> pendientes(int anio) {
    var salida=new ArrayList<Map<String,Object>>();
    var filas=db.query("""
      WITH dias AS (
        SELECT s.id,s.nivel,s.grado,s.nombre,dia::date AS fecha
        FROM ast_calendarios c JOIN ofe_secciones s ON s.anio=c.anio
        CROSS JOIN LATERAL generate_series(greatest(c.inicio,c.habilitado_desde),
          least(c.fin,?::date - 1),interval '1 day') dia
        WHERE c.anio=? AND coalesce((SELECT e.lectivo FROM ast_excepciones e
          WHERE e.fecha=dia::date AND (e.seccion_id=s.id OR e.seccion_id IS NULL)
          ORDER BY e.seccion_id NULLS LAST LIMIT 1),
          extract(isodow FROM dia)::int=ANY(c.dias_semana))
      )
      SELECT d.fecha,d.id,d.nivel||' '||d.grado||'° '||d.nombre,
        count(t.persona_id)-count(r.id),coalesce(j.alerta_sin_marcas,false),j.cerrada_en IS NOT NULL
      FROM dias d JOIN mat_tramos_asistencia t ON t.seccion_id=d.id
        AND t.desde<=d.fecha AND (t.hasta IS NULL OR t.hasta>=d.fecha)
      LEFT JOIN ast_registros r ON r.persona_id=t.persona_id AND r.seccion_id=d.id
        AND r.fecha=d.fecha AND r.valido
      LEFT JOIN ast_jornadas j ON j.seccion_id=d.id AND j.fecha=d.fecha
      GROUP BY d.fecha,d.id,d.nivel,d.grado,d.nombre,j.alerta_sin_marcas,j.cerrada_en
      HAVING count(t.persona_id)>count(r.id)
      ORDER BY d.fecha DESC,d.nivel,d.grado,d.nombre
      """,(r,n)->new Object[]{r.getDate(1).toLocalDate(),r.getObject(2,UUID.class),r.getString(3),
        r.getLong(4),r.getBoolean(5),r.getBoolean(6)},hoy(),anio);
    for(Object[] r:filas) salida.add(Map.of("fecha",r[0],"seccionId",r[1],"seccion",r[2],
      "sinMarca",r[3],"causa",(Boolean)r[4]?"SIN_REGISTRO":(Boolean)r[5]?"REGULARIZACION_PENDIENTE":"JORNADA_ABIERTA",
      "accion","Revisar sección y regularizar en ERP"));
    return salida;
  }
  public List<Justificacion> justificaciones(Integer anio,boolean soloPendientes) {
    String sql="""
      SELECT j.id,j.registro_id,j.estudiante_id,p.nombres||' '||p.apellidos,j.marca_origen,r.fecha,
        j.estado,j.motivo,j.version,j.comentario_resolucion
      FROM ast_justificaciones j JOIN ast_registros r ON r.id=j.registro_id
      JOIN per_personas p ON p.id=j.estudiante_id
      WHERE (CAST(? AS integer) IS NULL OR extract(year from r.fecha)=?)
      """+(soloPendientes?" AND j.estado='EN_REVISION' AND r.valido AND r.marca=j.marca_origen":"")+
      " ORDER BY j.creado_en DESC,j.id DESC LIMIT 500";
    return db.query(sql,(r,n)->new Justificacion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
      r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getDate(6).toLocalDate(),
      r.getString(7),r.getString(8),r.getInt(9),r.getString(10)),anio,anio);
  }
  public List<Justificacion> misJustificaciones(UUID persona) {
    return db.query("""
      SELECT j.id,j.registro_id,j.estudiante_id,p.nombres||' '||p.apellidos,j.marca_origen,r.fecha,
        j.estado,j.motivo,j.version,j.comentario_resolucion
      FROM ast_justificaciones j JOIN ast_registros r ON r.id=j.registro_id
      JOIN per_personas p ON p.id=j.estudiante_id WHERE j.estudiante_id=?
      ORDER BY j.creado_en DESC,j.id DESC
      """,(r,n)->new Justificacion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
      r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getDate(6).toLocalDate(),
      r.getString(7),r.getString(8),r.getInt(9),r.getString(10)),persona);
  }
  @Transactional public Justificacion solicitar(Actor actor,UUID registro,String motivo,String clave) {
    rol(actor,"ESTUDIANTE"); requerido(clave,"Clave de operación");
    motivo=requerido(motivo,"Motivo");
    var filas=db.query("SELECT persona_id,matricula_id,fecha,marca,valido FROM ast_registros WHERE id=?",
      (r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),
        r.getDate(3).toLocalDate(),r.getString(4),r.getBoolean(5)},registro);
    if(filas.isEmpty()) throw new IllegalArgumentException("Incidencia no encontrada");
    Object[] r=filas.getFirst();
    if(!actor.personaId().equals(r[0])) throw new SecurityException("Incidencia ajena");
    var anterior=db.query("SELECT respuesta->>'id',actor_sub FROM ast_operaciones WHERE clave=?",
      (rs,n)->new String[]{rs.getString(1),rs.getString(2)},clave);
    if(!anterior.isEmpty()) {
      if(!actor.sub().equals(anterior.getFirst()[1])) throw new Conflicto("Clave usada por otra cuenta");
      return misJustificaciones(actor.personaId()).stream().filter(j->j.id().toString().equals(anterior.getFirst()[0])).findFirst()
        .orElseThrow(()->new Conflicto("La solicitud previa no está disponible"));
    }
    if(!(Boolean)r[4]||!Set.of("FALTA","TARDANZA").contains(r[3])
        ||!matriculas.puedeSolicitar(actor.personaId(),(UUID)r[1]))
      throw new Conflicto("La incidencia ya no admite una nueva justificación");
    var activas=db.query("SELECT id FROM ast_justificaciones WHERE registro_id=? AND estado IN ('EN_REVISION','APROBADA')",
      (rs,n)->rs.getObject(1,UUID.class),registro);
    if(!activas.isEmpty()) throw new Conflicto("Ya existe una justificación pendiente o aprobada");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO ast_justificaciones(id,registro_id,estudiante_id,marca_origen,estado,motivo) VALUES(?,?,?,?,'EN_REVISION',?)",
      id,registro,actor.personaId(),r[3],motivo);
    db.update("INSERT INTO ast_justificacion_eventos(id,justificacion_id,accion,contenido,actor_sub) VALUES(?,?,'SOLICITADA',?,?)",
      UUID.randomUUID(),id,motivo,actor.sub());
    db.update("INSERT INTO ast_operaciones(clave,actor_sub,respuesta) VALUES(?,?,jsonb_build_object('id',?::text))",
      clave,actor.sub(),id.toString());
    return misJustificaciones(actor.personaId()).stream().filter(j->j.id().equals(id)).findFirst().orElseThrow();
  }
  @Transactional public void complementar(Actor actor,UUID id,String texto,int version) {
    rol(actor,"ESTUDIANTE");texto=requerido(texto,"Complemento");
    int actualizado=db.update("UPDATE ast_justificaciones SET version=version+1 WHERE id=? AND estudiante_id=? AND estado='EN_REVISION' AND version=?",
      id,actor.personaId(),version);
    if(actualizado!=1) throw new Conflicto("La solicitud cambió o no está en revisión");
    db.update("INSERT INTO ast_justificacion_eventos(id,justificacion_id,accion,contenido,actor_sub) VALUES(?,?,'COMPLEMENTO',?,?)",
      UUID.randomUUID(),id,texto,actor.sub());
  }
  @Transactional public void resolver(Actor actor,UUID id,boolean aprobar,String comentario,int version) {
    rol(actor,"COORDINACION");
    if(!aprobar) comentario=requerido(comentario,"Motivo de rechazo");
    var estado=db.query("""
      SELECT r.valido,r.marca,j.estudiante_id,j.marca_origen FROM ast_justificaciones j
      JOIN ast_registros r ON r.id=j.registro_id WHERE j.id=? AND j.estado='EN_REVISION' AND j.version=?
      FOR UPDATE OF j
      """,(r,n)->new Object[]{r.getBoolean(1),r.getString(2),r.getObject(3,UUID.class),r.getString(4)},id,version);
    if(estado.isEmpty()) throw new Conflicto("La solicitud cambió o no está en revisión");
    if(!(Boolean)estado.getFirst()[0] || !estado.getFirst()[1].equals(estado.getFirst()[3]))
      throw new Conflicto("La incidencia ya no está vigente; conserva la solicitud en historial");
    db.update("UPDATE ast_justificaciones SET estado=?,comentario_resolucion=?,resuelto_por=?,resuelto_en=now(),version=version+1 WHERE id=?",
      aprobar?"APROBADA":"RECHAZADA",comentario,actor.sub(),id);
    db.update("INSERT INTO ast_justificacion_eventos(id,justificacion_id,accion,contenido,actor_sub) VALUES(?,?,?,?,?)",
      UUID.randomUUID(),id,aprobar?"APROBADA":"RECHAZADA",comentario,actor.sub());
    UUID estudiante=(UUID)estado.getFirst()[2];
    db.update("INSERT INTO ast_avisos_pendientes(clave,tipo,destinatario_id,mensaje) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",
      "justificacion:"+id,"ESTUDIANTE",estudiante,aprobar?"Tu justificación de asistencia fue aprobada":"Tu justificación de asistencia fue rechazada; revisa el motivo");
  }
  @Transactional public Salida salida(Actor actor,UUID persona,String estado,String motivo,String clave) {
    rol(actor,"COORDINACION","DIRECCION");
    requerido(clave,"Clave de operación"); motivo=requerido(motivo,"Motivo");
    if(!Set.of("AUTORIZADA","NO_AUTORIZADA").contains(estado)) throw new IllegalArgumentException("Estado de salida inválido");
    if(operacionExistente(clave,actor.sub())) {
      var existente=db.query("SELECT id FROM ast_salidas WHERE id::text=(SELECT respuesta->>'id' FROM ast_operaciones WHERE clave=?)",
        (r,n)->r.getObject(1,UUID.class),clave);
      if(!existente.isEmpty()) return salidas(persona).stream().filter(s->s.id().equals(existente.getFirst())).findFirst().orElseThrow();
      throw new Conflicto("Clave ya utilizada");
    }
    var matricula=db.query("SELECT id,seccion_id FROM mat_matriculas WHERE persona_id=? AND estado='VIGENTE' AND anio=? ORDER BY confirmado_en DESC LIMIT 1",
      (r,n)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)},persona,hoy().getYear());
    if(matricula.isEmpty()) throw new IllegalArgumentException("El estudiante no tiene matrícula vigente este año");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO ast_salidas(id,persona_id,matricula_id,seccion_id,estado,motivo,actor_sub) VALUES(?,?,?,?,?,?,?)",
      id,persona,matricula.getFirst()[0],matricula.getFirst()[1],estado,motivo,actor.sub());
    db.update("INSERT INTO ast_operaciones(clave,actor_sub,respuesta) VALUES(?,?,jsonb_build_object('id',?::text))",
      clave,actor.sub(),id.toString());
    return salidas(persona).stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();
  }
  public List<Salida> salidas(UUID persona) {
    String filtro=persona==null?"":" WHERE x.persona_id=?";
    return db.query("""
      SELECT x.id,x.persona_id,p.nombres||' '||p.apellidos,s.nivel||' '||s.grado||'° '||s.nombre,
        x.estado,x.motivo,x.ocurrido_en::text,x.actor_sub
      FROM ast_salidas x JOIN per_personas p ON p.id=x.persona_id JOIN ofe_secciones s ON s.id=x.seccion_id
      """+filtro+" ORDER BY x.ocurrido_en DESC LIMIT 500",
      (r,n)->new Salida(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),
        r.getString(5),r.getString(6),r.getString(7),r.getString(8)),persona==null?new Object[]{}:new Object[]{persona});
  }
  public record Adjunto(UUID id,UUID justificacionId,String nombre,String tipo,long tamano) {}
  @Transactional public Adjunto adjuntar(Actor actor,UUID justificacion,String nombre,byte[] datos) {
    rol(actor,"ESTUDIANTE");nombre=requerido(nombre,"Nombre de archivo");
    if(datos==null||datos.length==0||datos.length>maxBytes) throw new IllegalArgumentException("Archivo vacío o demasiado grande");
    String tipo;
    if(datos.length>=4&&datos[0]=='%'&&datos[1]=='P'&&datos[2]=='D'&&datos[3]=='F') tipo="application/pdf";
    else if(datos.length>=3&&(datos[0]&255)==0xff&&(datos[1]&255)==0xd8&&(datos[2]&255)==0xff) tipo="image/jpeg";
    else if(datos.length>=8&&(datos[0]&255)==0x89&&datos[1]=='P'&&datos[2]=='N'&&datos[3]=='G') tipo="image/png";
    else throw new IllegalArgumentException("Solo PDF, JPG o PNG válidos");
    var j=db.query("SELECT estado,estudiante_id,version FROM ast_justificaciones WHERE id=? FOR UPDATE",
      (r,n)->new Object[]{r.getString(1),r.getObject(2,UUID.class),r.getInt(3)},justificacion);
    if(j.isEmpty()||!actor.personaId().equals(j.getFirst()[1])) throw new SecurityException("Solicitud ajena");
    if(!"EN_REVISION".equals(j.getFirst()[0])) throw new Conflicto("La solicitud ya fue resuelta");
    long cantidad=db.queryForObject("SELECT count(*) FROM ast_adjuntos WHERE justificacion_id=?",Long.class,justificacion);
    if(cantidad>=maxAdjuntos) throw new IllegalArgumentException("Se alcanzó el límite de archivos por solicitud");
    UUID id=UUID.randomUUID();
    String clave="asistencia/"+justificacion+"/"+id;
    almacenamiento.guardar(clave,tipo,datos);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int estado) {
        if(estado!=STATUS_COMMITTED) almacenamiento.eliminar(clave);
      }
    });
    db.update("INSERT INTO ast_adjuntos(id,justificacion_id,nombre,tipo,objeto_key,tamano) VALUES(?,?,?,?,?,?)",
      id,justificacion,nombre.substring(0,Math.min(120,nombre.length())),tipo,clave,datos.length);
    db.update("UPDATE ast_justificaciones SET version=version+1 WHERE id=?",justificacion);
    db.update("INSERT INTO ast_justificacion_eventos(id,justificacion_id,accion,contenido,actor_sub) VALUES(?,?,'ADJUNTO',?,?)",
      UUID.randomUUID(),justificacion,nombre,actor.sub());
    return new Adjunto(id,justificacion,nombre,tipo,datos.length);
  }
  public List<Adjunto> adjuntos(Actor actor,UUID justificacion) {
    validarAdjunto(actor,justificacion);
    return db.query("SELECT id,justificacion_id,nombre,tipo,tamano FROM ast_adjuntos WHERE justificacion_id=? ORDER BY creado_en",
      (r,n)->new Adjunto(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getLong(5)),justificacion);
  }
  public byte[] descargar(Actor actor,UUID adjunto) {
    var j=db.query("SELECT justificacion_id FROM ast_adjuntos WHERE id=?",
      (r,n)->r.getObject(1,UUID.class),adjunto);
    if(j.isEmpty()) throw new IllegalArgumentException("Archivo inexistente");
    validarAdjunto(actor,j.getFirst());
    return almacenamiento.leer(db.queryForObject("SELECT objeto_key FROM ast_adjuntos WHERE id=?",String.class,adjunto));
  }
  private void validarAdjunto(Actor actor,UUID justificacion) {
    if("COORDINACION".equals(actor.rol())||"DIRECCION".equals(actor.rol())) return;
    rol(actor,"ESTUDIANTE");
    if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM ast_justificaciones WHERE id=? AND estudiante_id=?)",
        Boolean.class,justificacion,actor.personaId()))) throw new SecurityException("Archivo ajeno");
  }
  @Transactional public void configurarHora(Actor actor,int anio,String valor) {
    rol(actor,"COORDINACION");
    LocalTime hora;
    try { hora=LocalTime.parse(valor); } catch(Exception e) { throw new IllegalArgumentException("Hora inválida"); }
    if(db.update("UPDATE ast_calendarios SET recordatorio_hora=?,version=version+1 WHERE anio=?",hora,anio)!=1)
      throw new IllegalArgumentException("Calendario inexistente");
  }
  @Transactional public void generarRecordatorios() {
    LocalDate dia=hoy();
    Calendario c=calendario(dia.getYear());
    if(c==null||hora().isBefore(LocalTime.parse(c.recordatorioHora()))) return;
    for(var s:oferta.secciones(dia.getYear())) {
      if(!lectivo(s.id(),dia)) continue;
      Jornada j=jornada(s.id(),dia,false);
      if(j.confirmada()||j.alumnos().isEmpty()||j.alumnos().stream().allMatch(a->a.marca()!=null)) continue;
      for(UUID docente:asignaciones.docentes(s.id(),dia))
        db.update("""
          INSERT INTO ast_avisos_pendientes(clave,tipo,destinatario_id,seccion_id,fecha,mensaje)
          VALUES(?,'DOCENTE',?,?,?,?) ON CONFLICT DO NOTHING
          ""","recordatorio:"+dia+":"+s.id()+":"+docente,docente,s.id(),dia,
          "La asistencia de "+s.nivel()+" "+s.grado()+"° "+s.nombre()+" está pendiente");
    }
  }
  @Transactional public List<Map<String,Object>> avisosPendientes() {
    var pendientes=db.queryForList("""
      SELECT clave,tipo,destinatario_id,seccion_id,fecha,mensaje FROM ast_avisos_pendientes
      WHERE enviado_en IS NULL ORDER BY creado_en LIMIT 100
      """);
    var vigentes=new ArrayList<Map<String,Object>>();
    for(var aviso:pendientes) {
      UUID seccion=(UUID)aviso.get("seccion_id");
      if(seccion==null) { vigentes.add(aviso);continue; }
      LocalDate fecha=((Date)aviso.get("fecha")).toLocalDate();
      UUID destinatario=(UUID)aviso.get("destinatario_id");
      Jornada j=jornada(seccion,fecha,false);
      if(!fecha.equals(hoy())||!j.lectivo()||j.confirmada()||j.alumnos().isEmpty()
          ||j.alumnos().stream().allMatch(a->a.marca()!=null)
          ||!asignaciones.docentes(seccion,fecha).contains(destinatario)) {
        db.update("DELETE FROM ast_avisos_pendientes WHERE clave=? AND enviado_en IS NULL",aviso.get("clave"));
      } else vigentes.add(aviso);
    }
    return vigentes;
  }
  @Transactional public void confirmarAviso(String clave) {
    db.update("UPDATE ast_avisos_pendientes SET enviado_en=COALESCE(enviado_en,now()),intentos=intentos+1 WHERE clave=?",clave);
  }
  private void auditar(UUID registro,UUID seccion,LocalDate fecha,UUID persona,String accion,
      String anterior,String nuevo,String motivo,String actor) {
    db.update("INSERT INTO ast_auditoria(id,registro_id,seccion_id,fecha,persona_id,accion,anterior,nuevo,motivo,actor_sub) VALUES(?,?,?,?,?,?,?,?,?,?)",
      UUID.randomUUID(),registro,seccion,fecha,persona,accion,anterior,nuevo,motivo,actor);
  }
}
