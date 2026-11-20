package pe.boston.erp.plataforma;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Concilia el estado ERP, entrega una solicitud cada vez y confirma solo tras HTTP 2xx del destino. */
@Component
public class IntegracionInstitucional {
  public record Docente(UUID personaId,String nombre) {}
  public record Curso(int version,UUID cursoId,UUID seccionId,UUID asignaturaId,int anio,
    String nivel,int grado,String seccion,String asignatura,String codigo,
    List<UUID> estudiantes,List<UUID> finalizados,List<Docente> docentes) {}
  public record Perfil(int version,UUID personaId,String tipo,String nombre) {}
  private record Solicitud(UUID id,String sistema,String entidadTipo,UUID entidadId,String accion,int intentos) {}
  private record Persona(UUID id,String nombres,String apellidos,LocalDate nacimiento,String documento) {}
  private record Par(UUID seccion,UUID asignatura) {}
  private final JdbcTemplate db;
  private final ObjectMapper json=new ObjectMapper();
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String tokenUri,identidadUrl,aulaUrl,secret;
  private String token;
  private Instant vencimiento=Instant.EPOCH;

  public IntegracionInstitucional(JdbcTemplate db,
      @Value("${BOSTON_TOKEN_URI:http://localhost:9000/oauth2/token}") String tokenUri,
      @Value("${BOSTON_IDENTIDAD_URL:http://localhost:9000}") String identidadUrl,
      @Value("${BOSTON_AULA_URL:http://localhost:8080}") String aulaUrl,
      @Value("${BOSTON_INTEGRACION_SECRET:cambiar-local-erp}") String secret) {
    this.db=db;this.tokenUri=tokenUri;this.identidadUrl=identidadUrl;
    this.aulaUrl=aulaUrl;this.secret=secret;
  }

  @Scheduled(fixedDelay=15000,initialDelay=8000)
  public void ejecutar() {
    try {
      reconciliar();
      db.update("UPDATE erp_solicitudes_externas SET estado='ERROR',motivo='Entrega interrumpida; se reintentará',actualizado_en=now() WHERE estado='PROCESANDO' AND actualizado_en<now()-interval '2 minutes'");
      for (int n=0;n<60;n++) {
        List<Solicitud> filas=db.query("""
          SELECT id,sistema,entidad_tipo,entidad_id,accion,intentos FROM erp_solicitudes_externas
          WHERE estado IN ('PENDIENTE_NO_CONECTADO','ERROR') AND (proximo_intento IS NULL OR proximo_intento<=now())
          ORDER BY creado_en,id LIMIT 1
          """,(r,i)->new Solicitud(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
            r.getObject(4,UUID.class),r.getString(5),r.getInt(6)));
        if (filas.isEmpty()) break;
        Solicitud s=filas.getFirst();
        if (db.update("UPDATE erp_solicitudes_externas SET estado='PROCESANDO',actualizado_en=now() WHERE id=? AND estado IN ('PENDIENTE_NO_CONECTADO','ERROR')",s.id())!=1) continue;
        entregar(s);
      }
    } catch(Exception error) {
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("Integración institucional pendiente: {}",resumen(error));
    }
  }

  /** Reconciliación por huella: la misma foto no crea solicitudes nuevas. */
  public void reconciliar() throws Exception {
    List<UUID> docentes=db.query("SELECT DISTINCT persona_id FROM per_condiciones WHERE tipo='DOCENTE'",
      (r,n)->r.getObject(1,UUID.class));
    for(UUID id:docentes) {
      solicitar("IDENTIDAD","PERSONA",id,"DOCENTE",huellaCuenta(id,"DOCENTE"));
      solicitar("AULA","PERFIL",id,"DOCENTE",perfil(id,"DOCENTE"));
    }
    List<UUID> estudiantes=db.query("SELECT DISTINCT persona_id FROM mat_matriculas WHERE persona_id IS NOT NULL AND estado<>'BORRADOR'",
      (r,n)->r.getObject(1,UUID.class));
    for(UUID id:estudiantes) {
      solicitar("IDENTIDAD","PERSONA",id,"ESTUDIANTE",huellaCuenta(id,"ESTUDIANTE"));
      solicitar("AULA","PERFIL",id,"ESTUDIANTE",perfil(id,"ESTUDIANTE"));
    }
    List<Par> pares=db.query("""
      SELECT s.id,a.id FROM ofe_secciones s JOIN ofe_asignaturas a
      ON a.anio=s.anio AND a.nivel=s.nivel AND a.grado=s.grado ORDER BY s.id,a.id
      """,(r,n)->new Par(r.getObject(1,UUID.class),r.getObject(2,UUID.class)));
    for(Par par:pares) {
      Curso curso=curso(par.seccion(),par.asignatura());
      solicitar("AULA","CURSO",curso.cursoId(),"PROYECTAR",curso);
    }
  }

  private void solicitar(String sistema,String tipo,UUID entidad,String accion,Object foto) throws Exception {
    String huella=UUID.nameUUIDFromBytes(json.writeValueAsBytes(foto)).toString();
    String clave="aula02:v1:"+sistema+":"+tipo+":"+entidad+":"+accion+":"+huella;
    db.update("""
      INSERT INTO erp_solicitudes_externas(id,clave_idempotencia,sistema,entidad_tipo,entidad_id,accion,motivo)
      VALUES(?,?,?,?,?,?,?) ON CONFLICT(clave_idempotencia) DO NOTHING
      """,UUID.randomUUID(),clave,sistema,tipo,entidad,accion,"Pendiente de confirmación del destino");
  }
  private Object huellaCuenta(UUID id,String tipo) {
    try { return cuenta(id,tipo); }
    catch(IllegalStateException error) { return tipo+":"+id+":"+resumen(error); }
  }

  private void entregar(Solicitud s) {
    try {
      int confirmaciones=0;
      if ("IDENTIDAD".equals(s.sistema())) {
        UUID persona="MATRICULA".equals(s.entidadTipo())
          ?db.queryForObject("SELECT persona_id FROM mat_matriculas WHERE id=?",UUID.class,s.entidadId())
          :s.entidadId();
        if (persona==null) throw new IllegalStateException("Matrícula sin persona confirmada");
        if ("MATRICULA".equals(s.entidadTipo())) { enviarCuenta(s,persona,"ESTUDIANTE"); confirmaciones++; }
        else {
          long docente=db.queryForObject("SELECT count(*) FROM per_condiciones WHERE persona_id=? AND tipo='DOCENTE'",Long.class,persona);
          long alumno=db.queryForObject("SELECT count(*) FROM mat_matriculas WHERE persona_id=? AND estado<>'BORRADOR'",Long.class,persona);
          if (docente>0) { enviarCuenta(s,persona,"DOCENTE"); confirmaciones++; }
          if (alumno>0) { enviarCuenta(s,persona,"ESTUDIANTE"); confirmaciones++; }
        }
      } else if ("AULA".equals(s.sistema())) {
        if ("PERFIL".equals(s.entidadTipo())) {
          enviarPerfil(s); confirmaciones++;
        } else if ("CURSO".equals(s.entidadTipo())) {
          Par par=parDeCurso(s.entidadId());
          if (par==null) throw new IllegalStateException("Curso sin par institucional");
          enviarCurso(s,par); confirmaciones++;
        } else if ("ASIGNACION".equals(s.entidadTipo())) {
          Par par=db.query("SELECT seccion_id,asignatura_id FROM asi_asignaciones WHERE id=?",
            (r,n)->new Par(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),s.entidadId()).getFirst();
          enviarCurso(s,par); confirmaciones++;
        } else if ("MATRICULA".equals(s.entidadTipo())) {
          List<Par> pares=db.query("""
            SELECT s.id,a.id FROM mat_matriculas m JOIN ofe_secciones s ON s.id=m.seccion_id
            JOIN ofe_asignaturas a ON a.anio=s.anio AND a.nivel=s.nivel AND a.grado=s.grado
            WHERE m.id=?
            """,(r,n)->new Par(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),s.entidadId());
          for(Par par:pares) { enviarCurso(s,par); confirmaciones++; }
        }
      }
      if (confirmaciones==0) throw new IllegalStateException("No hay proyección vigente que confirmar en el destino");
      db.update("UPDATE erp_solicitudes_externas SET estado='CONFIRMADA',motivo='Confirmada por el destino',intentos=intentos+1,proximo_intento=NULL,actualizado_en=now() WHERE id=?",s.id());
    } catch(Exception error) {
      boolean intervencion=error instanceof DestinoInvalido || error instanceof IllegalStateException;
      int demora=Math.min(900,5*(1<<Math.min(s.intentos(),7)));
      db.update("""
        UPDATE erp_solicitudes_externas SET estado=?,motivo=?,intentos=intentos+1,
        proximo_intento=CASE WHEN ? THEN NULL ELSE now()+(? * interval '1 second') END,actualizado_en=now()
        WHERE id=?
        """,intervencion?"INTERVENCION":"ERROR",resumen(error),intervencion,demora,s.id());
    }
  }

  private void enviarCuenta(Solicitud s,UUID persona,String tipo) throws Exception {
    var foto=cuenta(persona,tipo);
    String ruta=identidadUrl+"/api/identidad/integracion/v1/cuentas/"+persona+"/"+tipo;
    enviar(ruta,foto,s.id());
  }
  private void enviarCurso(Solicitud s,Par par) throws Exception {
    Curso foto=curso(par.seccion(),par.asignatura());
    enviar(aulaUrl+"/api/aula/integracion/v1/cursos/"+foto.cursoId(),foto,s.id());
  }

  private void enviarPerfil(Solicitud s) throws Exception {
    if (!List.of("DOCENTE","ESTUDIANTE").contains(s.accion()))
      throw new IllegalStateException("Tipo de perfil académico inválido");
    Perfil foto=perfil(s.entidadId(),s.accion());
    enviar(aulaUrl+"/api/aula/integracion/v1/perfiles/"+foto.personaId()+"/"+foto.tipo(),foto,s.id());
  }

  private Perfil perfil(UUID id,String tipo) {
    Persona p=persona(id);
    return new Perfil(1,id,tipo,(p.nombres()+" "+p.apellidos()).trim());
  }

  private record Cuenta(UUID personaId,String tipo,String usuario,boolean activa) {}
  private Cuenta cuenta(UUID id,String tipo) {
    Persona p=persona(id);
    String usuario=p.documento();
    if (usuario==null && "ESTUDIANTE".equals(tipo)) {
      if (p.nacimiento()==null) throw new IllegalStateException("Estudiante sin nacimiento para acceso provisional");
      usuario=normalizar(p.nombres().split(" ")[0])+normalizar(p.apellidos().split(" ")[0])+
        String.format("%02d",p.nacimiento().getDayOfMonth());
    }
    if (usuario==null) throw new IllegalStateException("Documento requerido para la cuenta "+tipo);
    boolean activa="DOCENTE".equals(tipo)
      ?db.queryForObject("SELECT EXISTS(SELECT 1 FROM per_condiciones WHERE persona_id=? AND tipo='DOCENTE' AND periodo @> now())",Boolean.class,id)
      :db.queryForObject("SELECT EXISTS(SELECT 1 FROM mat_matriculas WHERE persona_id=? AND estado IN ('VIGENTE','FINALIZADA'))",Boolean.class,id);
    return new Cuenta(id,tipo,usuario,activa);
  }
  private static String normalizar(String palabra) {
    return Normalizer.normalize(palabra,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase()
      .replaceAll("[^a-z0-9]","");
  }
  private Persona persona(UUID id) {
    var filas=db.query("""
      SELECT p.id,p.nombres,p.apellidos,p.fecha_nacimiento,d.numero_normalizado
      FROM per_personas p LEFT JOIN LATERAL (
        SELECT numero_normalizado FROM per_documentos WHERE persona_id=p.id AND principal AND periodo @> now()
        ORDER BY vigente_desde DESC LIMIT 1) d ON true WHERE p.id=?
      """,(r,n)->new Persona(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
        r.getObject(4,LocalDate.class),r.getString(5)),id);
    if (filas.isEmpty()) throw new IllegalStateException("Persona ya no disponible en ERP");
    return filas.getFirst();
  }
  private Par parDeCurso(UUID id) {
    var pares=db.query("""
      SELECT s.id,a.id FROM ofe_secciones s JOIN ofe_asignaturas a
      ON a.anio=s.anio AND a.nivel=s.nivel AND a.grado=s.grado
      """,(r,n)->new Par(r.getObject(1,UUID.class),r.getObject(2,UUID.class)));
    return pares.stream().filter(p->id.equals(cursoId(p.seccion(),p.asignatura()))).findFirst().orElse(null);
  }
  private static UUID cursoId(UUID seccion,UUID asignatura) {
    return UUID.nameUUIDFromBytes(("bbs:aula:curso:v1:"+seccion+":"+asignatura).getBytes(StandardCharsets.UTF_8));
  }
  private Curso curso(UUID seccion,UUID asignatura) {
    var datos=db.query("""
      SELECT s.anio,s.nivel,s.grado,s.nombre,a.codigo,a.nombre
      FROM ofe_secciones s JOIN ofe_asignaturas a ON a.anio=s.anio AND a.nivel=s.nivel AND a.grado=s.grado
      WHERE s.id=? AND a.id=?
      """,(r,n)->new Object[]{r.getInt(1),r.getString(2),r.getInt(3),r.getString(4),r.getString(5),r.getString(6)},seccion,asignatura);
    if (datos.isEmpty()) throw new IllegalStateException("Oferta incompleta para curso");
    Object[] d=datos.getFirst();
    List<UUID> alumnos=db.query("""
      SELECT DISTINCT persona_id FROM mat_matriculas WHERE seccion_id=? AND estado='VIGENTE'
      ORDER BY persona_id
      """,(r,n)->r.getObject(1,UUID.class),seccion);
    List<UUID> finalizados=db.query("""
      SELECT DISTINCT persona_id FROM mat_matriculas WHERE seccion_id=? AND estado='FINALIZADA'
      ORDER BY persona_id
      """,(r,n)->r.getObject(1,UUID.class),seccion);
    List<Docente> docentes=db.query("""
      SELECT DISTINCT p.id,p.nombres||' '||p.apellidos FROM asi_asignaciones a
      JOIN per_personas p ON p.id=a.docente_id
      WHERE a.seccion_id=? AND a.asignatura_id=? AND a.estado='ACTIVA'
      AND (a.hasta IS NULL OR a.hasta>=current_date)
      AND EXISTS(SELECT 1 FROM per_condiciones c WHERE c.persona_id=a.docente_id
        AND c.tipo='DOCENTE' AND c.periodo @> now())
      ORDER BY p.id
      """,(r,n)->new Docente(r.getObject(1,UUID.class),r.getString(2)),seccion,asignatura);
    return new Curso(2,cursoId(seccion,asignatura),seccion,asignatura,(int)d[0],(String)d[1],
      (int)d[2],(String)d[3],(String)d[5],(String)d[4],alumnos,finalizados,docentes);
  }

  private synchronized String token() throws Exception {
    if (token!=null && Instant.now().isBefore(vencimiento)) return token;
    String basic=Base64.getEncoder().encodeToString(("erp-integracion:"+secret).getBytes(StandardCharsets.UTF_8));
    var solicitud=HttpRequest.newBuilder(URI.create(tokenUri)).timeout(Duration.ofSeconds(8))
      .header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=identidad.provision%20aula.proyectar")).build();
    var respuesta=http.send(solicitud,HttpResponse.BodyHandlers.ofString());
    if (respuesta.statusCode()!=200) throw new Exception("Token de integración: HTTP "+respuesta.statusCode());
    var datos=json.readTree(respuesta.body());
    token=datos.path("access_token").asText();
    vencimiento=Instant.now().plusSeconds(Math.max(10,datos.path("expires_in").asLong(300)-30));
    return token;
  }
  private void enviar(String destino,Object foto,UUID operacion) throws Exception {
    var solicitud=HttpRequest.newBuilder(URI.create(destino)).timeout(Duration.ofSeconds(12))
      .header("Authorization","Bearer "+token()).header("Content-Type","application/json")
      .header("X-Operacion-Id",operacion.toString())
      .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(foto))).build();
    var respuesta=http.send(solicitud,HttpResponse.BodyHandlers.ofString());
    if (respuesta.statusCode()==401) { token=null; throw new Exception("Token de integración rechazado"); }
    if (respuesta.statusCode()>=400 && respuesta.statusCode()<500)
      throw new DestinoInvalido("Destino rechazó la proyección: HTTP "+respuesta.statusCode());
    if (respuesta.statusCode()<200 || respuesta.statusCode()>=300)
      throw new Exception("Destino temporalmente no disponible: HTTP "+respuesta.statusCode());
    var confirmado=json.readTree(respuesta.body());
    if (!confirmado.hasNonNull("estado") || !List.of("CONFIRMADA","CREADA","REUTILIZADA","SIN_CUENTA").contains(confirmado.get("estado").asText()))
      throw new Exception("Destino no confirmó la operación");
  }
  private static String resumen(Exception error) {
    String texto=error.getMessage();
    String limpio=(texto==null?error.getClass().getSimpleName():texto).replaceAll("[\\r\\n]"," ");
    return limpio.substring(0,Math.min(180,limpio.length()));
  }
  private static final class DestinoInvalido extends Exception {
    DestinoInvalido(String mensaje) { super(mensaje); }
  }
}
