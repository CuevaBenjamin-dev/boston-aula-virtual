package pe.boston.erp.asistencia;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="spring.task.scheduling.enabled=false")
@Import(AsistenciaFlujoTest.Reloj.class)
@AutoConfigureMockMvc
class AsistenciaFlujoTest {
  static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6-alpine");
  static { postgres.start(); }
  @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",postgres::getJdbcUrl);
    r.add("spring.datasource.username",postgres::getUsername);
    r.add("spring.datasource.password",postgres::getPassword);
    r.add("spring.security.oauth2.client.registration.boston-erp.client-secret",()->"test-local");
  }
  @TestConfiguration static class Reloj {
    @Bean @Primary Clock fijo(){return Clock.fixed(Instant.parse("2027-03-03T16:00:00Z"),AsistenciaService.LIMA);}
  }
  @Autowired JdbcTemplate db;
  @Autowired AsistenciaService ast;
  @Autowired MockMvc mvc;
  UUID seccion,otra,docente,uno,dos,tres;
  final LocalDate dia=LocalDate.of(2027,3,2);
  final AsistenciaService.Actor coord=new AsistenciaService.Actor("coord","COORDINACION",null);
  @BeforeEach void preparar() {
    db.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(2027,'test')");
    db.update("INSERT INTO ast_calendarios(anio,inicio,fin,dias_semana,habilitado_desde) VALUES(2027,'2027-03-01','2027-12-15',ARRAY[1,2,3,4,5],'2027-03-01')");
    seccion=UUID.randomUUID();otra=UUID.randomUUID();
    db.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,2027,'PRIMARIA',3,'A'),(?,2027,'PRIMARIA',3,'B')",seccion,otra);
    docente=persona("Docente","Prueba",1,"DOCENTE");
    uno=persona("Ana","Prueba",2,"ESTUDIANTE");dos=persona("Beto","Prueba",3,"ESTUDIANTE");tres=persona("Celia","Prueba",4,"ESTUDIANTE");
    for(UUID p:List.of(uno,dos,tres)) db.update("""
      INSERT INTO mat_matriculas(id,persona_id,anio,seccion_id,estado,creado_por,confirmado_en)
      VALUES(gen_random_uuid(),?,2027,?,'VIGENTE','test',now())
      """,p,seccion);
  }
  private UUID persona(String nombre,String apellido,int numero,String tipo) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO per_personas(id,nombres,apellidos,fecha_nacimiento,sexo,nacionalidad,direccion,telefono) VALUES(?,?,?,'2015-01-01','X','PE','Prueba','999999999')",id,nombre,apellido);
    db.update("INSERT INTO per_condiciones(id,persona_id,tipo) VALUES(gen_random_uuid(),?,?)",id,tipo);
    String documento=String.format("%08d",numero);
    db.update("INSERT INTO per_documentos(id,persona_id,tipo_codigo,pais_emisor,numero,numero_normalizado,principal) VALUES(gen_random_uuid(),?,'DNI','PE',?,?,true)",id,documento,documento);
    return id;
  }
  @Test @Transactional void avancePrivadoVersionPublicacionYCierreRecuperado() {
    var j=ast.jornada(seccion,dia,true);assertEquals(3,j.alumnos().size());
    var a=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(0,"avance-1",List.of(new AsistenciaService.Marca(uno,"PRESENTE"))));
    assertEquals("PENDIENTE",ast.jornada(seccion,dia,false).alumnos().getFirst().visible());
    assertThrows(AsistenciaService.Conflicto.class,()->ast.avance(coord,seccion,dia,
      new AsistenciaService.Lote(0,"avance-conflicto",List.of(new AsistenciaService.Marca(dos,"PRESENTE")))));
    assertEquals(a.version(),ast.avance(coord,seccion,dia,
      new AsistenciaService.Lote(0,"avance-1",List.of(new AsistenciaService.Marca(uno,"PRESENTE")))).version());
    ast.cerrar(seccion,dia);
    var cerrado=ast.jornada(seccion,dia,false);
    assertTrue(cerrado.cerrada());assertEquals(3,cerrado.alumnos().stream().filter(x->x.marca()!=null).count());
    assertEquals(2,db.queryForObject("SELECT count(*) FROM ast_registros WHERE fecha=? AND origen='SISTEMA'",Long.class,dia));
    ast.cerrar(seccion,dia);
    assertEquals(3,db.queryForObject("SELECT count(*) FROM ast_registros WHERE fecha=?",Long.class,dia));
    assertThrows(SecurityException.class,()->ast.avance(new AsistenciaService.Actor("doc","DOCENTE",docente),seccion,dia,
      new AsistenciaService.Lote(cerrado.version(),"ayer",List.of(new AsistenciaService.Marca(dos,"PRESENTE")))));
  }
  @Test @Transactional void calendarioInvalidaYJustificacionSalidaConservaHecho() {
    var etapa=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(0,"falta-1",List.of(new AsistenciaService.Marca(uno,"FALTA"))));
    ast.publicar(coord,seccion,dia,etapa.version(),"publicar-1",false,"Regularización test");
    UUID registro=db.queryForObject("SELECT id FROM ast_registros WHERE persona_id=? AND fecha=?",UUID.class,uno,dia);
    var estudiante=new AsistenciaService.Actor("alumno","ESTUDIANTE",uno);
    assertThrows(SecurityException.class,()->ast.solicitar(new AsistenciaService.Actor("otro","ESTUDIANTE",dos),registro,"Motivo","ajena"));
    var solicitud=ast.solicitar(estudiante,registro,"Motivo de prueba","sol-1");
    assertThrows(IllegalArgumentException.class,()->ast.adjuntar(estudiante,solicitud.id(),"falso.pdf",new byte[]{1,2,3,4}));
    assertThrows(SecurityException.class,()->ast.adjuntos(new AsistenciaService.Actor("otro","ESTUDIANTE",dos),solicitud.id()));
    assertEquals(solicitud.id(),ast.solicitar(estudiante,registro,"Motivo de prueba","sol-1").id());
    assertThrows(AsistenciaService.Conflicto.class,()->ast.solicitar(estudiante,registro,"Otro","sol-2"));
    ast.complementar(estudiante,solicitud.id(),"Más contexto",solicitud.version());
    assertThrows(SecurityException.class,()->ast.resolver(estudiante,solicitud.id(),true,"",2));
    ast.resolver(coord,solicitud.id(),true,"",2);
    assertEquals("JUSTIFICADA",ast.historial(uno,2027).getFirst().visible());
    assertEquals("FALTA",db.queryForObject("SELECT marca FROM ast_registros WHERE id=?",String.class,registro));
    var salida=ast.salida(coord,uno,"NO_AUTORIZADA","Salió sin autorización","salida-1");
    assertEquals(salida.id(),ast.salida(coord,uno,"NO_AUTORIZADA","Salió sin autorización","salida-1").id());
    ast.excepcion(coord,new AsistenciaService.Excepcion(dia,seccion,false,"Suspensión de clases"));
    assertFalse(ast.historial(uno,2027).getFirst().valido());
    assertEquals(0,ast.jornada(seccion,dia,false).alumnos().size());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM ast_salidas WHERE persona_id=?",Long.class,uno));
    ast.excepcion(coord,new AsistenciaService.Excepcion(dia,seccion,true,"Jornada reabierta"));
    var nuevo=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(ast.jornada(seccion,dia,true).version(),
      "reabierta",List.of(new AsistenciaService.Marca(uno,"PRESENTE"))));
    ast.publicar(coord,seccion,dia,nuevo.version(),"publicar-reabierta",false,"Regularización tras reapertura");
    assertEquals(2,db.queryForObject("SELECT count(*) FROM ast_registros WHERE persona_id=? AND fecha=?",Long.class,uno,dia));
    assertEquals("PRESENTE",ast.jornada(seccion,dia,false).alumnos().stream()
      .filter(a->a.personaId().equals(uno)).findFirst().orElseThrow().visible());
  }
  @Test @Transactional void padrónTemporalYDiaSinMarcas() {
    assertEquals(3,ast.jornada(seccion,dia,false).alumnos().size());
    ast.cerrar(seccion,dia);
    assertTrue(ast.jornada(seccion,dia,false).alertaSinMarcas());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM ast_registros WHERE fecha=?",Long.class,dia));
    UUID matricula=db.queryForObject("SELECT id FROM mat_matriculas WHERE persona_id=?",UUID.class,uno);
    db.update("UPDATE mat_matriculas SET seccion_id=? WHERE id=?",otra,matricula);
    assertEquals(2,ast.jornada(seccion,dia,false).alumnos().size());
    assertEquals(1,ast.jornada(otra,dia,false).alumnos().size());
    db.update("UPDATE mat_matriculas SET estado='RETIRADA' WHERE id=?",matricula);
    assertEquals(0,ast.jornada(otra,dia,false).alumnos().size());
    db.update("UPDATE mat_matriculas SET estado='VIGENTE' WHERE id=?",matricula);
    assertEquals(1,ast.jornada(otra,dia,false).alumnos().size());
  }
  @Test @Transactional void docenteActivoSinAsignacionSoloRegistraHoyYFinalizacionCierraTramo() {
    var profesor=new AsistenciaService.Actor("docente","DOCENTE",docente);
    LocalDate hoy=ast.hoy();
    assertEquals(0,db.queryForObject("SELECT count(*) FROM asi_asignaciones WHERE docente_id=?",Long.class,docente));
    var actual=ast.avance(profesor,seccion,hoy,new AsistenciaService.Lote(0,"doc-hoy",
      List.of(new AsistenciaService.Marca(uno,"PRESENTE"))));
    assertEquals(1,actual.version());
    assertThrows(SecurityException.class,()->ast.avance(profesor,seccion,dia,
      new AsistenciaService.Lote(0,"doc-ayer",List.of(new AsistenciaService.Marca(uno,"FALTA")))));
    assertThrows(SecurityException.class,()->ast.avance(profesor,seccion,hoy.plusDays(1),
      new AsistenciaService.Lote(0,"doc-manana",List.of(new AsistenciaService.Marca(uno,"FALTA")))));
    db.update("UPDATE per_condiciones SET vigente_desde=now()-interval '2 days',vigente_hasta=now()-interval '1 day' WHERE persona_id=? AND tipo='DOCENTE'",docente);
    assertThrows(SecurityException.class,()->ast.avance(profesor,seccion,hoy,
      new AsistenciaService.Lote(0,"doc-inactivo",List.of(new AsistenciaService.Marca(uno,"FALTA")))));
    UUID matricula=db.queryForObject("SELECT id FROM mat_matriculas WHERE persona_id=?",UUID.class,dos);
    db.update("UPDATE mat_matriculas SET estado='FINALIZADA' WHERE id=?",matricula);
    assertFalse(ast.jornada(seccion,hoy,false).alumnos().stream().anyMatch(a->a.personaId().equals(dos)));
  }
  @Test @Transactional void salidasIndependientesNoAlteranAsistencia() {
    var primero=ast.salida(coord,uno,"AUTORIZADA","Retiro comunicado","salida-autorizada");
    var segundo=ast.salida(coord,uno,"NO_AUTORIZADA","Salida sin autorización","salida-no-autorizada");
    assertNotEquals(primero.id(),segundo.id());
    assertEquals(2,ast.salidas(uno).size());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM ast_registros WHERE persona_id=?",Long.class,uno));
    assertThrows(SecurityException.class,()->ast.salida(new AsistenciaService.Actor("alumno","ESTUDIANTE",uno),
      uno,"AUTORIZADA","Motivo","salida-ajena"));
  }
  @Test @Transactional void tardanzaRechazoReaperturaYExcepcionParcial() {
    var avance=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(0,"tardanza",
      List.of(new AsistenciaService.Marca(uno,"TARDANZA"))));
    ast.publicar(coord,seccion,dia,avance.version(),"publicar-tardanza",false,"Corrección respaldada");
    UUID registro=db.queryForObject("SELECT id FROM ast_registros WHERE persona_id=? AND fecha=?",UUID.class,uno,dia);
    var alumno=new AsistenciaService.Actor("alumno","ESTUDIANTE",uno);
    var solicitud=ast.solicitar(alumno,registro,"Llegada documentada","sol-tardanza");
    assertThrows(IllegalArgumentException.class,()->ast.resolver(coord,solicitud.id(),false,"",solicitud.version()));
    ast.resolver(coord,solicitud.id(),false,"Sin sustento suficiente",solicitud.version());
    var segunda=ast.solicitar(alumno,registro,"Sustento ampliado","sol-tardanza-2");
    ast.resolver(coord,segunda.id(),true,"",segunda.version());
    assertEquals("TARDANZA_JUSTIFICADA",ast.historial(uno,2027).getFirst().visible());
    assertTrue(ast.estadisticas(uno,2027).tardanzaJustificada()>0);
    ast.excepcion(coord,new AsistenciaService.Excepcion(dia,seccion,false,"Suspensión sección A"));
    assertFalse(ast.historial(uno,2027).getFirst().valido());
    assertTrue(ast.lectivo(otra,dia));
    LocalDate sabado=LocalDate.of(2027,3,6);
    assertFalse(ast.lectivo(seccion,sabado));
    ast.excepcion(coord,new AsistenciaService.Excepcion(sabado,otra,true,"Jornada extraordinaria"));
    assertTrue(ast.lectivo(otra,sabado));
    assertFalse(ast.lectivo(seccion,sabado));
  }
  @Test @Transactional void fronteraServicioRechazaTokenYActorNoAutorizado() throws Exception {
    String ruta="/api/erp/v1/integracion/asistencia/secciones";
    mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    mvc.perform(get(ruta).with(jwt().jwt(j->j.subject("otro"))
      .authorities(new SimpleGrantedAuthority("SCOPE_erp.asistencia")))).andExpect(status().isForbidden());
    mvc.perform(get(ruta).with(jwt().jwt(j->j.subject("aula-servicio"))
      .authorities(new SimpleGrantedAuthority("SCOPE_erp.read")))).andExpect(status().isForbidden());
    mvc.perform(get(ruta).with(jwt().jwt(j->j.subject("aula-servicio"))
      .authorities(new SimpleGrantedAuthority("SCOPE_erp.asistencia")))
      .header("X-Ast-Sub","otro").header("X-Ast-Rol","ESTUDIANTE")
      .header("X-Ast-Persona",uno)).andExpect(status().isForbidden());
    mvc.perform(get(ruta).with(jwt().jwt(j->j.subject("aula-servicio"))
      .authorities(new SimpleGrantedAuthority("SCOPE_erp.asistencia")))
      .header("X-Ast-Sub","docente").header("X-Ast-Rol","DOCENTE")
      .header("X-Ast-Persona",docente)).andExpect(status().isOk());
  }
  @Test @Transactional void recordatorioUnicoPorSeccionYDocenteSeRetiraAlCompletar() {
    for(String codigo:List.of("MAT","COM")) {
      UUID asignatura=UUID.randomUUID();
      db.update("INSERT INTO ofe_asignaturas(id,anio,nivel,grado,codigo,nombre) VALUES(?,2027,'PRIMARIA',3,?,?)",
        asignatura,"PR-03-"+codigo+"-27",codigo);
      db.update("""
        INSERT INTO asi_asignaciones(id,docente_id,seccion_id,asignatura_id,tipo,desde,estado,actor_sub)
        VALUES(gen_random_uuid(),?,?,?,'TITULAR','2027-03-01','ACTIVA','test')
        """,docente,seccion,asignatura);
    }
    ast.configurarHora(coord,2027,"10:00");
    ast.generarRecordatorios();ast.generarRecordatorios();
    assertEquals(1,db.queryForObject("SELECT count(*) FROM ast_avisos_pendientes WHERE tipo='DOCENTE'",Long.class));
    LocalDate hoy=LocalDate.of(2027,3,3);
    var lote=ast.avance(coord,seccion,hoy,new AsistenciaService.Lote(0,"hoy-completo",
      List.of(new AsistenciaService.Marca(uno,"PRESENTE"),new AsistenciaService.Marca(dos,"PRESENTE"),
        new AsistenciaService.Marca(tres,"PRESENTE"))));
    ast.publicar(coord,seccion,hoy,lote.version(),"hoy-publicar",true,null);
    assertTrue(ast.avisosPendientes().isEmpty());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM ast_avisos_pendientes WHERE enviado_en IS NULL",Long.class));
  }
  @Test @Transactional void correccionRetiraSolicitudObsoletaSinPerderHistorial() {
    var alumno=new AsistenciaService.Actor("alumno","ESTUDIANTE",uno);
    var primero=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(0,"primera-falta",
      List.of(new AsistenciaService.Marca(uno,"FALTA"))));
    ast.publicar(coord,seccion,dia,primero.version(),"primera-publicacion",false,"Registro inicial");
    UUID registro=db.queryForObject("SELECT id FROM ast_registros WHERE persona_id=?",UUID.class,uno);
    var solicitud=ast.solicitar(alumno,registro,"Motivo inicial","sol-inicial");
    var corregido=ast.avance(coord,seccion,dia,new AsistenciaService.Lote(ast.jornada(seccion,dia,true).version(),
      "correccion-tardanza",List.of(new AsistenciaService.Marca(uno,"TARDANZA"))));
    ast.publicar(coord,seccion,dia,corregido.version(),"publicar-correccion",false,"Corrección de hecho");
    assertEquals("OBSOLETA",ast.misJustificaciones(uno).getFirst().estado());
    assertTrue(ast.justificaciones(2027,true).isEmpty());
    assertThrows(AsistenciaService.Conflicto.class,()->ast.resolver(coord,solicitud.id(),true,"",solicitud.version()));
    assertEquals("TARDANZA",ast.solicitar(alumno,registro,"Motivo actualizado","sol-actualizada").marca());
  }
}
