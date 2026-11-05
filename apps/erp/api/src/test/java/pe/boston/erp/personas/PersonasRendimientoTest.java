package pe.boston.erp.personas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Escenario aislado con datos sintéticos: 30 000 registros, 30 clientes simultáneos. */
class PersonasRendimientoTest {
  static long p95(List<Long> valores) {
    var ordenados=new ArrayList<>(valores); Collections.sort(ordenados);
    return TimeUnit.NANOSECONDS.toMillis(ordenados.get((int)Math.ceil(ordenados.size()*.95)-1));
  }

  @Test void consultasYGuardadosConTreintaMilPersonas() throws Exception {
    try (var pg=new PostgreSQLContainer("postgres:18.6-alpine")) {
      pg.start();
      Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())
        .locations("classpath:db/migration").load().migrate();
      try (var db=DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
          var st=db.createStatement()) {
        // Solo en esta base efímera: se omiten triggers durante la carga sintética masiva.
        st.execute("SET session_replication_role=replica");
        st.execute("""
          INSERT INTO per_personas(id,nombres,apellidos,fecha_nacimiento,sexo,nacionalidad,direccion)
          SELECT md5('p'||i)::uuid,'Persona '||lpad(i::text,5,'0'),'Rendimiento',
            DATE '2012-01-01','F','PE','Domicilio sintético' FROM generate_series(1,30000) i
          """);
        st.execute("""
          INSERT INTO per_condiciones(id,persona_id,tipo)
          SELECT md5('c'||i)::uuid,md5('p'||i)::uuid,'ESTUDIANTE' FROM generate_series(1,30000) i
          """);
        st.execute("""
          INSERT INTO per_documentos(id,persona_id,tipo_codigo,pais_emisor,numero,numero_normalizado,principal)
          SELECT md5('d'||i)::uuid,md5('p'||i)::uuid,'DNI','PE',lpad(i::text,8,'0'),lpad(i::text,8,'0'),true
          FROM generate_series(1,30000) i
          """);
        st.execute("SET session_replication_role=origin");
        st.execute("ANALYZE per_personas"); st.execute("ANALYZE per_documentos");
        try (var rs=st.executeQuery("""
          EXPLAIN (ANALYZE, BUFFERS) SELECT p.id FROM per_personas p
          WHERE lower(p.nombres || ' ' || p.apellidos) LIKE '%persona 12345%'
          ORDER BY p.apellidos,p.nombres,p.id LIMIT 15
          """)) {
          System.out.println("PLAN BUSQUEDA NOMBRE:"); while(rs.next()) System.out.println(rs.getString(1));
        }
        try (var rs=st.executeQuery("""
          EXPLAIN (ANALYZE, BUFFERS) SELECT persona_id FROM per_documentos
          WHERE tipo_codigo='DNI' AND pais_emisor='PE' AND numero_normalizado='00012345' AND vigente_hasta IS NULL
          """)) {
          System.out.println("PLAN DOCUMENTO EXACTO:"); while(rs.next()) System.out.println(rs.getString(1));
        }
      }
      var busquedas=Collections.synchronizedList(new ArrayList<Long>());
      var guardados=Collections.synchronizedList(new ArrayList<Long>());
      var inicio=new CountDownLatch(1);
      try (var pool=Executors.newFixedThreadPool(30)) {
        var tareas=new ArrayList<java.util.concurrent.Future<?>>();
        for(int cliente=0;cliente<30;cliente++) {
          final int n=cliente+1;
          tareas.add(pool.submit(()->{
            try (var db=DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword())) {
              inicio.await();
              try (var buscar=db.prepareStatement("""
                  SELECT id FROM per_personas p WHERE lower(p.nombres || ' ' || p.apellidos) LIKE ?
                  ORDER BY apellidos,nombres,id LIMIT 15
                  """);
                  var exacto=db.prepareStatement("""
                  SELECT persona_id FROM per_documentos WHERE tipo_codigo='DNI' AND pais_emisor='PE'
                    AND numero_normalizado=? AND vigente_hasta IS NULL
                  """);
                  var guardar=db.prepareStatement("UPDATE per_personas SET telefono=? WHERE id=md5(?)::uuid")) {
                for(int vuelta=0;vuelta<5;vuelta++) {
                  buscar.setString(1,"%persona 12345%");
                  long t=System.nanoTime(); try(var rs=buscar.executeQuery()) { while(rs.next()) rs.getObject(1); }
                  busquedas.add(System.nanoTime()-t);
                  exacto.setString(1,String.format("%08d",n*100+vuelta));
                  t=System.nanoTime(); try(var rs=exacto.executeQuery()) { while(rs.next()) rs.getObject(1); }
                  busquedas.add(System.nanoTime()-t);
                }
                guardar.setString(1,"999000"+n); guardar.setString(2,"p"+n);
                long t=System.nanoTime(); guardar.executeUpdate(); guardados.add(System.nanoTime()-t);
              }
            } catch(Exception e) { throw new RuntimeException(e); }
          }));
        }
        inicio.countDown();
        for(var tarea:tareas) tarea.get();
      }
      long p95Busqueda=p95(busquedas), p95Guardado=p95(guardados);
      System.out.printf("RENDIMIENTO Personas: PostgreSQL 18.6, Docker Desktop, 30000 personas, 30 clientes; búsquedas n=%d p95=%d ms; guardados n=%d p95=%d ms%n",
        busquedas.size(),p95Busqueda,guardados.size(),p95Guardado);
      assertTrue(p95Busqueda<=2000,"Búsquedas p95: "+p95Busqueda+" ms");
      assertTrue(p95Guardado<=3000,"Guardados p95: "+p95Guardado+" ms");
    }
  }
}
