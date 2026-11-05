package pe.boston.erp.personas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PersonasEsquemaTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
  static final AtomicInteger documentos = new AtomicInteger(10000000);

  @BeforeAll static void migrar() {
    postgres.start();
    Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .locations("classpath:db/migration").load().migrate();
  }

  static Connection conexion() throws SQLException {
    Connection db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    db.setAutoCommit(false);
    return db;
  }

  static void ejecutar(Connection db, String sql, Object... args) throws SQLException {
    try (PreparedStatement ps = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
      ps.executeUpdate();
    }
  }

  static void persona(Connection db, UUID id) throws SQLException {
    ejecutar(db, """
      INSERT INTO per_personas(id,nombres,apellidos,fecha_nacimiento,sexo,nacionalidad,telefono,correo,direccion)
      VALUES (?,'Ana','Prueba',DATE '2010-01-01','F','PE','999888777','ana@example.test','Calle Uno')
      """, id);
  }

  static void condicion(Connection db, UUID id, String tipo) throws SQLException {
    ejecutar(db, "INSERT INTO per_condiciones(id,persona_id,tipo) VALUES (?,?,?)", UUID.randomUUID(), id, tipo);
  }

  static void dni(Connection db, UUID id, String numero) throws SQLException {
    ejecutar(db, """
      INSERT INTO per_documentos(id,persona_id,tipo_codigo,pais_emisor,numero,numero_normalizado,principal)
      VALUES (?,?,'DNI','PE',?,?,true)
      """, UUID.randomUUID(), id, numero, numero);
  }

  static UUID estudiante(Connection db) throws SQLException {
    UUID id = UUID.randomUUID();
    persona(db, id);
    condicion(db, id, "ESTUDIANTE");
    dni(db, id, Integer.toString(documentos.incrementAndGet()));
    return id;
  }

  @Test void exigeCondicionYDocumentoEnLaMismaTransaccion() throws Exception {
    try (Connection db = conexion()) {
      persona(db, UUID.randomUUID());
      assertThrows(SQLException.class, db::commit);
      db.rollback();
    }
    try (Connection db = conexion()) {
      estudiante(db);
      db.commit();
    }
  }

  @Test void documentoVigenteEsUnicoYNoSeSolapanVigencias() throws Exception {
    String numero = Integer.toString(documentos.incrementAndGet());
    try (Connection db = conexion()) {
      UUID primero = UUID.randomUUID();
      persona(db, primero); condicion(db, primero, "ESTUDIANTE"); dni(db, primero, numero);
      db.commit();
    }
    try (Connection db = conexion()) {
      UUID segundo = UUID.randomUUID();
      persona(db, segundo); condicion(db, segundo, "ESTUDIANTE");
      assertThrows(SQLException.class, () -> dni(db, segundo, numero));
      db.rollback();
    }
    try (Connection db = conexion()) {
      UUID id = estudiante(db);
      assertThrows(SQLException.class, () -> condicion(db, id, "ESTUDIANTE"));
      db.rollback();
    }
  }

  @Test void provisionalSeRegularizaSinCambiarLaPersona() throws Exception {
    UUID id = UUID.randomUUID();
    UUID provisional = UUID.randomUUID();
    try (Connection db = conexion()) {
      persona(db, id);
      condicion(db, id, "ESTUDIANTE");
      ejecutar(db, """
        INSERT INTO per_identificaciones_provisionales(id,persona_id,motivo,sustento_tipo,sustento_referencia)
        VALUES (?,?,'Documento en trámite','DECLARACION_JURADA','DJ-PRUEBA')
        """, provisional, id);
      db.commit();
    }
    try (Connection db = conexion()) {
      dni(db, id, Integer.toString(documentos.incrementAndGet()));
      ejecutar(db, "UPDATE per_identificaciones_provisionales SET regularizada_en=now() WHERE id=?", provisional);
      db.commit();
      try (var ps = db.prepareStatement("SELECT count(*) FROM per_personas WHERE id=?")) {
        ps.setObject(1, id);
        try (var rs = ps.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
      }
    }
  }

  @Test void apoderamientosMultiplesSeDesactivanPorSeparado() throws Exception {
    UUID estudiante;
    UUID primero = UUID.randomUUID();
    UUID segundo = UUID.randomUUID();
    try (Connection db = conexion()) {
      estudiante = estudiante(db);
      for (UUID apoderado : new UUID[]{primero, segundo}) {
        persona(db, apoderado);
        dni(db, apoderado, Integer.toString(documentos.incrementAndGet()));
        ejecutar(db, """
          INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco,
            autorizador,verificador_sub,medio_verificacion,verificado_en,sustento_tipo,sustento_referencia)
          VALUES (?,?,?,true,'Responsable','coordinacion.prueba','TELÉFONO',now(),'VERBAL','Llamada verificada')
          """, UUID.randomUUID(), estudiante, apoderado);
      }
      db.commit();
    }
    try (Connection db = conexion()) {
      ejecutar(db, "UPDATE per_apoderamientos SET vigente_hasta=now() WHERE estudiante_id=? AND apoderado_id=?",
        estudiante, primero);
      db.commit();
      try (var ps = db.prepareStatement("SELECT count(*) FROM per_apoderamientos WHERE estudiante_id=? AND periodo @> now()")) {
        ps.setObject(1, estudiante);
        try (var rs = ps.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
      }
    }
  }

  @Test void altaConcurrenteDelMismoDocumentoSoloPermiteUna() throws Exception {
    String numero = Integer.toString(documentos.incrementAndGet());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tareas = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
        try (Connection db = conexion()) {
          UUID id = UUID.randomUUID();
          persona(db, id); condicion(db, id, "ESTUDIANTE"); dni(db, id, numero);
          db.commit();
          return true;
        } catch (SQLException error) { return false; }
      })).toList();
      int exitos = 0;
      for (var tarea : tareas) if (tarea.get()) exitos++;
      assertEquals(1, exitos);
    }
  }

  @Test void rechazaDniInvalidoYAutorizacionIncompleta() throws Exception {
    try (Connection db = conexion()) {
      UUID id = UUID.randomUUID();
      persona(db, id); condicion(db, id, "ESTUDIANTE");
      assertThrows(SQLException.class, () -> dni(db, id, "ABC12345"));
      db.rollback();
    }
    try (Connection db = conexion()) {
      UUID estudiante = estudiante(db);
      UUID apoderado = UUID.randomUUID();
      persona(db, apoderado); dni(db, apoderado, Integer.toString(documentos.incrementAndGet()));
      assertThrows(SQLException.class, () -> ejecutar(db, """
        INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco)
        VALUES (?,?,?,true)
        """, UUID.randomUUID(), estudiante, apoderado));
      db.rollback();
    }
    assertTrue(postgres.isRunning());
  }

  @Test void correccionDocumentalConservaUuidEHistorial() throws Exception {
    UUID id;
    try (Connection db=conexion()) { id=estudiante(db); db.commit(); }
    String nuevo=Integer.toString(documentos.incrementAndGet());
    try (Connection db=conexion()) {
      ejecutar(db,"UPDATE per_documentos SET vigente_hasta=now() WHERE persona_id=? AND vigente_hasta IS NULL",id);
      dni(db,id,nuevo);
      db.commit();
      try (var ps=db.prepareStatement("SELECT count(*),count(*) FILTER (WHERE vigente_hasta IS NULL) FROM per_documentos WHERE persona_id=?")) {
        ps.setObject(1,id);
        try (var rs=ps.executeQuery()) { rs.next(); assertEquals(2,rs.getInt(1)); assertEquals(1,rs.getInt(2)); }
      }
    }
  }

  @Test void condicionesMultiplesYReactivacionHistorica() throws Exception {
    UUID id;
    try (Connection db=conexion()) { id=estudiante(db); condicion(db,id,"DOCENTE"); db.commit(); }
    try (Connection db=conexion()) {
      ejecutar(db,"UPDATE per_condiciones SET vigente_hasta=now(),cierre_manual=true,motivo_desactivacion='Cese' WHERE persona_id=? AND tipo='DOCENTE' AND vigente_hasta IS NULL",id);
      db.commit();
    }
    try (Connection db=conexion()) {
      condicion(db,id,"DOCENTE"); db.commit();
      try (var ps=db.prepareStatement("SELECT count(*) FROM per_condiciones WHERE persona_id=? AND tipo='DOCENTE'")) {
        ps.setObject(1,id);
        try (var rs=ps.executeQuery()) { rs.next(); assertEquals(2,rs.getInt(1)); }
      }
    }
  }

  @Test void rollbackDeAltaIncompletaNoDejaPersona() throws Exception {
    UUID id=UUID.randomUUID();
    try (Connection db=conexion()) {
      persona(db,id);
      assertThrows(SQLException.class,db::commit);
      db.rollback();
    }
    try (Connection db=conexion(); var ps=db.prepareStatement("SELECT count(*) FROM per_personas WHERE id=?")) {
      ps.setObject(1,id);
      try (var rs=ps.executeQuery()) { rs.next(); assertEquals(0,rs.getInt(1)); }
    }
  }

  @Test void exigeParentescoParaVinculoFamiliar() throws Exception {
    UUID estudiante;
    UUID familiar=UUID.randomUUID();
    try (Connection db=conexion()) {
      estudiante=estudiante(db); persona(db,familiar); condicion(db,familiar,"PERSONAL_INSTITUCIONAL"); dni(db,familiar,Integer.toString(documentos.incrementAndGet()));
      db.commit();
    }
    try (Connection db=conexion()) {
      ejecutar(db,"INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco) VALUES(?,?,?,false)",UUID.randomUUID(),estudiante,familiar);
      assertThrows(SQLException.class,db::commit); db.rollback();
    }
    try (Connection db=conexion()) {
      ejecutar(db,"INSERT INTO per_parentescos(id,estudiante_id,familiar_id,tipo) VALUES(?,?,?,'MADRE')",UUID.randomUUID(),estudiante,familiar);
      ejecutar(db,"INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco) VALUES(?,?,?,false)",UUID.randomUUID(),estudiante,familiar);
      db.commit();
    }
  }

  @Test void concurrenciaDelMismoVinculoSoloPermiteUno() throws Exception {
    UUID estudiante;
    UUID apoderado=UUID.randomUUID();
    try (Connection db=conexion()) {
      estudiante=estudiante(db); persona(db,apoderado); condicion(db,apoderado,"PERSONAL_INSTITUCIONAL"); dni(db,apoderado,Integer.toString(documentos.incrementAndGet()));
      db.commit();
    }
    try (var pool=Executors.newFixedThreadPool(2)) {
      var tareas=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{
        try (Connection db=conexion()) {
          ejecutar(db,"""
            INSERT INTO per_apoderamientos(id,estudiante_id,apoderado_id,sin_parentesco,
              autorizador,verificador_sub,medio_verificacion,verificado_en,sustento_tipo,sustento_referencia)
            VALUES(?,?,?,true,'Responsable','verificador','TELÉFONO',now(),'VERBAL','Registro de prueba')
            """,UUID.randomUUID(),estudiante,apoderado);
          db.commit(); return true;
        } catch(SQLException e) { return false; }
      })).toList();
      int exitos=0; for (var tarea:tareas) if(tarea.get()) exitos++;
      assertEquals(1,exitos);
    }
  }
}
