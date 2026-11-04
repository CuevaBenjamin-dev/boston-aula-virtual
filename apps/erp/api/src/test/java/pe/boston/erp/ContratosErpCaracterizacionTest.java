package pe.boston.erp;

import pe.boston.erp.plataforma.DirectorioIdentidad;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.flywaydb.core.Flyway;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.boston.erp.matriculas.BorradoresMatricula;
import pe.boston.erp.matriculas.ImportacionMatriculas;
import pe.boston.erp.personas.application.PersonasService;

/** Línea base SQL sobre PostgreSQL desechable, previa a extraer responsabilidades. */
class ContratosErpCaracterizacionTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate db;
  private static TransactionTemplate tx;
  private static ErpV1Service v1;
  private static Institucional legado;

  @BeforeAll static void iniciar() {
    postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    postgres.start();
    var fuente = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    Flyway.configure().dataSource(fuente).locations("classpath:db/migration").load().migrate();
    db = new JdbcTemplate(fuente);
    tx = new TransactionTemplate(new DataSourceTransactionManager(fuente));
    legado = new Institucional(db);
    var personas = new PersonasService(db);
    v1 = new ErpV1Service(db, legado, new DirectorioIdentidad("http://127.0.0.1:1"), personas, personas);
  }

  @AfterAll static void cerrar() { if (postgres != null) postgres.close(); }

  private static <T> T aislado(Function<TransactionStatus,T> caso) {
    return tx.execute(estado -> {
      try { return caso.apply(estado); }
      finally { estado.setRollbackOnly(); }
    });
  }

  private static Jwt actor(String tipo) {
    return Jwt.withTokenValue("prueba-local").header("alg", "none")
      .subject("caracterizacion").claim("tipo", tipo).claim("scope", "erp.read erp.sync").build();
  }

  private static UUID id(String consulta, Object... parametros) {
    return db.queryForObject(consulta, UUID.class, parametros);
  }

  private static ErpV1Service.EstudianteDato alumno(String documento) {
    return new ErpV1Service.EstudianteDato("Ana", "Caracterización", LocalDate.now(ZoneId.of("America/Lima")).minusYears(11),
      documento == null ? null : "DNI", documento, "Peruana", "ACTIVO");
  }

  private static UUID alumnoConApoderado(Jwt actor) {
    UUID estudiante = v1.crearEstudiante(alumno("T" + UUID.randomUUID().toString().substring(0, 12)), actor);
    UUID persona = v1.crearPersona(new ErpV1Service.PersonaDato("María", "Responsable", "DNI",
      "P" + UUID.randomUUID().toString().substring(0, 12), null, null), actor);
    v1.vincular(estudiante, new ErpV1Service.VinculoDato(persona, "PRINCIPAL", "Madre", true, null, null), actor);
    return estudiante;
  }

  @Test void c01_c02_altaBusquedaVerificacionYDocumentoUnico() {
    aislado(estado -> {
      Jwt actor = actor("DIRECCION");
      String documento = "A" + UUID.randomUUID().toString().substring(0, 12);
      UUID estudiante = v1.crearEstudiante(alumno(documento), actor);
      assertNull(db.queryForObject("SELECT codigo FROM estudiantes WHERE id=?", String.class, estudiante));
      assertEquals(estudiante, v1.candidatosMatricula(documento, actor).getFirst().get("id"));
      Map<String,Object> verificacion = v1.verificarEstudiante("DNI", documento, actor);
      assertEquals(true, verificacion.get("encontrado"));
      assertEquals(11, verificacion.get("estudiante") instanceof Map<?,?> ficha ? ficha.get("edad") : null);
      assertEquals(false, verificacion.get("matriculado"));
      assertThrows(IllegalArgumentException.class, () -> v1.crearEstudiante(alumno(documento), actor));
      assertEquals(1L, db.queryForObject("SELECT count(*) FROM auditoria_erp WHERE entidad_id=?", Long.class, estudiante));
      var cambio = new ErpV1Service.EstudianteDato("Ana María", "Corregida", null, "DNI", documento,
        "Peruana", "ACTIVO");
      v1.editarEstudiante(estudiante, cambio, actor);
      assertEquals("Ana María", db.queryForObject("""
        SELECT p.nombres FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=?
        """, String.class, estudiante));
      assertEquals(2L, db.queryForObject("SELECT count(*) FROM auditoria_erp WHERE entidad_id=?", Long.class, estudiante));
      assertThrows(IllegalArgumentException.class, () -> v1.editarEstudiante(estudiante,
        new ErpV1Service.EstudianteDato("Ana", "Corregida", null, "DNI", null, null, "ACTIVO"), actor));
      assertEquals("Ana María", db.queryForObject("""
        SELECT p.nombres FROM estudiantes e JOIN personas p ON p.id=e.persona_id WHERE e.id=?
        """, String.class, estudiante));
      return null;
    });
  }

  @Test void c03_personaIdempotenteYUnSoloResponsablePrincipal() {
    aislado(estado -> {
      Jwt actor = actor("COORDINACION");
      UUID estudiante = v1.crearEstudiante(alumno(null), actor);
      var dato = new ErpV1Service.PersonaDato("Rosa", "Apoderada", "DNI", "P" + UUID.randomUUID().toString().substring(0,12), null, null);
      UUID principal = v1.crearPersona(dato, actor);
      assertEquals(principal, v1.crearPersona(dato, actor));
      v1.vincular(estudiante, new ErpV1Service.VinculoDato(principal,"PRINCIPAL","Madre",true,null,null), actor);
      UUID segundo = v1.crearPersona(new ErpV1Service.PersonaDato("Luis","Apoderado","DNI","Q" + UUID.randomUUID().toString().substring(0,12),null,null),actor);
      v1.vincular(estudiante, new ErpV1Service.VinculoDato(segundo,"PRINCIPAL","Padre",false,null,null), actor);
      List<Map<String,Object>> responsables = v1.apoderados(estudiante, actor);
      assertEquals(2, responsables.size());
      assertEquals(segundo, responsables.getFirst().get("persona_id"));
      assertEquals("SEGUNDO", responsables.get(1).get("rol"));
      return null;
    });
  }

  @Test void c04_borradorPrivadoReanudableYFinalizacionIdempotente() {
    aislado(estado -> {
      Jwt actor = actor("COORDINACION");
      Jwt ajeno = Jwt.withTokenValue("prueba-local").header("alg", "none")
        .subject("otro-operador").claim("tipo", "COORDINACION").build();
      var borradores = new BorradoresMatricula(db, v1);
      UUID seccion = id("SELECT id FROM secciones WHERE habilitada LIMIT 1");
      UUID periodo = id("SELECT periodo_id FROM secciones WHERE id=?", seccion);
      UUID grado = id("SELECT grado_id FROM secciones WHERE id=?", seccion);
      UUID borrador = borradores.crear(new BorradoresMatricula.Guardado(1, Map.of("procedencia", "NUEVO")), actor);
      assertEquals(1, borradores.ver(borrador, actor).get("paso"));
      assertThrows(EmptyResultDataAccessException.class, () -> borradores.ver(borrador, ajeno));
      assertThrows(EmptyResultDataAccessException.class,
        () -> borradores.guardar(borrador, new BorradoresMatricula.Guardado(2, Map.of()), ajeno));
      Map<String,Object> datos = Map.of(
        "procedencia", "NUEVO", "periodoId", periodo.toString(), "gradoId", grado.toString(),
        "seccionId", seccion.toString(), "vinculo", "Madre", "pagoEstado", "PENDIENTE",
        "estudiante", Map.of("nombres", "Alumna", "apellidos", "Borrador", "tipoDocumento", "DNI",
          "documento", "B" + UUID.randomUUID().toString().substring(0, 12)),
        "apoderado", Map.of("nombres", "Rosa", "apellidos", "Responsable", "tipoDocumento", "DNI",
          "documento", "R" + UUID.randomUUID().toString().substring(0, 12)));
      borradores.guardar(borrador, new BorradoresMatricula.Guardado(3, datos), actor);
      assertEquals(3, borradores.ver(borrador, actor).get("paso"));
      UUID matricula = borradores.finalizar(borrador, actor);
      assertEquals(matricula, borradores.finalizar(borrador, actor));
      assertEquals("MATRICULADO", db.queryForObject("SELECT estado FROM matriculas WHERE id=?", String.class, matricula));
      assertEquals("PENDIENTE", db.queryForObject("SELECT pago_estado FROM matriculas WHERE id=?", String.class, matricula));
      assertEquals("{}", db.queryForObject("SELECT datos::text FROM borradores_matricula WHERE id=?", String.class, borrador));
      assertThrows(IllegalArgumentException.class,
        () -> borradores.guardar(borrador, new BorradoresMatricula.Guardado(4, datos), actor));
      return null;
    });
  }

  @Test void c05_c06_c07_matriculaCanceladaConfirmacionYPagoExterno() {
    aislado(estado -> {
      Jwt actor = actor("DIRECCION");
      UUID estudiante = alumnoConApoderado(actor);
      UUID seccion = id("SELECT id FROM secciones WHERE habilitada ORDER BY nombre LIMIT 1");
      UUID periodo = id("SELECT periodo_id FROM secciones WHERE id=?", seccion);
      UUID grado = id("SELECT grado_id FROM secciones WHERE id=?", seccion);
      var solicitud = new ErpV1Service.MatriculaDato(estudiante, periodo, grado, seccion, "NUEVO", null);
      UUID cancelada = v1.iniciarMatricula(solicitud, actor);
      assertThrows(IllegalArgumentException.class, () -> v1.iniciarMatricula(solicitud, actor));
      v1.cancelar(cancelada, actor);
      assertEquals("CANCELADA", db.queryForObject("SELECT estado FROM matriculas WHERE id=?", String.class, cancelada));
      UUID nueva = v1.iniciarMatricula(solicitud, actor);
      assertThrows(IllegalArgumentException.class, () -> v1.confirmar(nueva, false, actor));
      v1.confirmar(nueva, true, actor);
      assertNotNull(db.queryForObject("SELECT codigo FROM estudiantes WHERE id=?", String.class, estudiante));
      long eventos = db.queryForObject("SELECT count(*) FROM eventos_pendientes", Long.class);
      v1.confirmar(nueva, true, actor);
      assertEquals(eventos, db.queryForObject("SELECT count(*) FROM eventos_pendientes", Long.class));
      v1.registrarPagoExterno(nueva, "TRANSFERENCIA", new BigDecimal("120.50"), actor);
      assertEquals("REGISTRADO", db.queryForObject("SELECT pago_estado FROM matriculas WHERE id=?", String.class, nueva));
      assertThrows(IllegalArgumentException.class, () -> v1.registrarPagoExterno(nueva, "TRANSFERENCIA", new BigDecimal("120.50"), actor));
      assertEquals(3L, db.queryForObject("SELECT count(*) FROM historial_matriculas WHERE matricula_id=?", Long.class, nueva));
      return null;
    });
  }

  @Test void c08_c11_mallaPropagaOfertaYOutboxVersionado() {
    aislado(estado -> {
      Jwt actor = actor("DIRECCION");
      UUID grado = id("SELECT grado_id FROM secciones WHERE habilitada GROUP BY grado_id HAVING count(*)>1 LIMIT 1");
      UUID asignatura = v1.crearAsignatura("T-" + UUID.randomUUID().toString().substring(0, 8), "Taller caracterización", actor);
      v1.vincularAsignatura(new ErpV1Service.AsignaturaGradoDato(asignatura, grado, null, 99, true), actor);
      long secciones = db.queryForObject("SELECT count(*) FROM secciones WHERE grado_id=? AND habilitada", Long.class, grado);
      assertEquals(secciones, db.queryForObject("SELECT count(*) FROM ofertas_academicas o JOIN secciones s ON s.id=o.seccion_id WHERE o.asignatura_id=? AND s.grado_id=?", Long.class, asignatura, grado));
      UUID oferta = id("SELECT id FROM ofertas_academicas WHERE asignatura_id=? LIMIT 1", asignatura);
      legado.publicarOferta(oferta);
      legado.publicarOferta(oferta);
      List<Map<String,Object>> eventos = legado.eventos(0, 200);
      assertTrue(eventos.stream().filter(e -> oferta.equals(e.get("entidad_id"))).count() >= 3);
      assertEquals(3, db.queryForObject("SELECT version FROM versiones_eventos WHERE entidad_id=?", Integer.class, oferta));
      return null;
    });
  }

  @Test void c11_rollbackNoPublicaYReintentoConservaSecuencia() {
    UUID oferta = id("SELECT id FROM ofertas_academicas WHERE estado='ACTIVA' LIMIT 1");
    long antes = db.queryForObject("SELECT count(*) FROM eventos_pendientes WHERE entidad_id=?", Long.class, oferta);
    tx.execute(estado -> {
      legado.publicarOferta(oferta);
      estado.setRollbackOnly();
      return null;
    });
    assertEquals(antes, db.queryForObject("SELECT count(*) FROM eventos_pendientes WHERE entidad_id=?", Long.class, oferta));
    tx.execute(estado -> { legado.publicarOferta(oferta); return null; });
    tx.execute(estado -> { legado.publicarOferta(oferta); return null; });
    var eventos = db.queryForList("SELECT secuencia,version,datos::text AS datos FROM eventos_pendientes WHERE entidad_id=? ORDER BY secuencia DESC LIMIT 2", oferta);
    assertEquals(2, eventos.size());
    assertTrue(((Number)eventos.get(0).get("secuencia")).longValue() > ((Number)eventos.get(1).get("secuencia")).longValue());
    assertEquals(((Number)eventos.get(1).get("version")).intValue() + 1,
      ((Number)eventos.get(0).get("version")).intValue());
    assertTrue(eventos.get(0).get("datos").toString().contains(oferta.toString()));
  }

  @Test void c09_c10_c12_c14_rolesAsignacionesYContratosLegados() {
    aislado(estado -> {
      Jwt direccion = actor("DIRECCION");
      Jwt coordinacion = actor("COORDINACION");
      Jwt admin = actor("ADMINISTRADOR");
      assertThrows(AccessDeniedException.class, () -> v1.crearDocente(
        new ErpV1Service.DocenteDato("Eva","Prueba",null,null,null,null,null), admin));
      UUID docente = v1.crearDocente(new ErpV1Service.DocenteDato("Eva","Prueba",null,null,null,null,null), direccion);
      UUID oferta = id("SELECT id FROM ofertas_academicas WHERE estado='ACTIVA' LIMIT 1");
      v1.asignarDocente(oferta, docente, direccion);
      v1.asignarDocenteLote(docente, List.of(oferta, oferta), direccion);
      assertThrows(IllegalArgumentException.class, () -> v1.asignarDocenteLote(docente, java.util.Arrays.asList((UUID)null), direccion));
      assertThrows(AccessDeniedException.class, () -> legado.catalogo(coordinacion));
      assertTrue(legado.catalogo(admin).containsKey("docentes"));
      assertTrue(v1.catalogos(direccion).containsKey("asignaturas"));
      assertEquals(1L, db.queryForObject("SELECT count(*) FROM asignaciones_docentes WHERE oferta_id=? AND docente_id=?", Long.class, oferta, docente));
      return null;
    });
  }

  @Test void c13_previsualizacionConfirmacionYReintentoNoDuplicanImportacion() {
    aislado(estado -> {
      try {
        Jwt actor = actor("COORDINACION");
        var importador = new ImportacionMatriculas(db, v1);
        var ubicacion = db.queryForMap("""
          SELECT p.nombre AS periodo,n.nombre AS nivel,g.nombre AS grado,s.nombre AS seccion
          FROM secciones s JOIN periodos_academicos p ON p.id=s.periodo_id
          JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id
          WHERE s.habilitada AND p.habilitado LIMIT 1
          """);
        byte[] archivo;
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(importador.plantilla(actor)));
             var salida = new ByteArrayOutputStream()) {
          var fila = libro.getSheetAt(0).getRow(1);
          fila.getCell(2).setCellValue("I" + UUID.randomUUID().toString().substring(0, 12));
          fila.getCell(6).setCellValue((String) ubicacion.get("periodo"));
          fila.getCell(7).setCellValue((String) ubicacion.get("nivel"));
          fila.getCell(8).setCellValue((String) ubicacion.get("grado"));
          fila.getCell(9).setCellValue((String) ubicacion.get("seccion"));
          fila.getCell(13).setCellValue("P" + UUID.randomUUID().toString().substring(0, 12));
          libro.write(salida);
          archivo = salida.toByteArray();
        }
        var carga = new MockMultipartFile("archivo", "matriculas.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo);
        var previa = importador.previsualizar(carga, actor);
        UUID importacion = (UUID) previa.get("id");
        assertEquals(1L, ((Number)((Map<?,?>)previa.get("reporte")).get("validas")).longValue());
        assertThrows(IllegalArgumentException.class, () -> importador.confirmar(importacion, false, actor));
        var confirmada = importador.confirmar(importacion, true, actor);
        assertEquals(1, ((List<?>) confirmada.get("resultados")).size());
        var reintento = importador.confirmar(importacion, true, actor);
        var resultado = (Map<?,?>) ((List<?>) confirmada.get("resultados")).getFirst();
        var resultadoReintento = (Map<?,?>) ((List<?>) reintento.get("resultados")).getFirst();
        assertEquals(String.valueOf(resultado.get("estudianteId")), String.valueOf(resultadoReintento.get("estudianteId")));
        assertEquals(String.valueOf(resultado.get("matriculaId")), String.valueOf(resultadoReintento.get("matriculaId")));
        assertEquals("CONFIRMADA", importador.previsualizar(carga, actor).get("estado"));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM importaciones_matriculas WHERE id=?", Long.class, importacion));
        assertThrows(IllegalArgumentException.class, () -> importador.previsualizar(
          new MockMultipartFile("archivo", "mal.xlsx", "application/octet-stream", new byte[]{1,2,3}), actor));
        return null;
      } catch (java.io.IOException e) { throw new AssertionError(e); }
    });
  }
}
