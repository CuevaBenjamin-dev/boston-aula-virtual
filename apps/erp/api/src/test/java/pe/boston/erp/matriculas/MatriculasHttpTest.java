package pe.boston.erp.matriculas;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class MatriculasHttpTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
  static { postgres.start(); }
  @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.security.oauth2.client.registration.boston-erp.client-secret", () -> "prueba-local");
  }
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  ObjectMapper json = new ObjectMapper();

  @Test void buscaBorradoresSinDistinguirTildesYCaja() throws Exception {
    String marca=UUID.randomUUID().toString().substring(0,8);
    jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(gen_random_uuid(),jsonb_build_object('nombres',?,'apellidos','Prueba'),'prueba-coordinacion')", "Sofía"+marca);
    mvc.perform(get("/api/erp/v1/matriculas?buscar=SOFIA"+marca).with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
      .andExpect(jsonPath("$.resultados[0].estudiante").value("Sofía"+marca+" Prueba"));
  }

  private OAuth2AuthenticationToken actor() {
    return actor("prueba-coordinacion");
  }
  private OAuth2AuthenticationToken actor(String sub) {
    var claims=Map.<String,Object>of("sub",sub,"tipo","COORDINACION");
    var token=new OidcIdToken("token-test",Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),claims);
    var usuario=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_COORDINACION")),token);
    return new OAuth2AuthenticationToken(usuario,usuario.getAuthorities(),"boston-erp");
  }

  @Test void rechazaDuplicadoAnualAntesDePasarAlApoderado() throws Exception {
    String numero=String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000);
    var persona=Map.of("nombres","Sofía","apellidos","Prueba","fechaNacimiento","2015-02-10",
      "condicion","ESTUDIANTE","documento",Map.of("tipo","DNI","pais","PE","numero",numero));
    String ubicacion=mvc.perform(post("/api/erp/v1/personas").with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(persona)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertNotNull(ubicacion);
    UUID personaId=UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/')+1));
    UUID seccionId=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(2027,'prueba-coordinacion')");
    jdbc.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,2027,'PRIMARIA',3,'B')",seccionId);
    UUID borradorId=UUID.randomUUID();
    jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(?,'{}'::jsonb,'prueba-coordinacion')",borradorId);
    UUID vigenteId=UUID.randomUUID();
    jdbc.update("INSERT INTO mat_matriculas(id,persona_id,anio,seccion_id,estado,borrador,creado_por,confirmado_en) VALUES(?,?,2027,?,'VIGENTE','{}'::jsonb,'prueba-coordinacion',now())",
      vigenteId,personaId,seccionId);

    mvc.perform(get("/api/erp/v1/matriculas/contexto").param("tipo","DNI").param("pais","PE")
        .param("numero",numero).param("anio","2027").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.personaId").value(personaId.toString()))
      .andExpect(jsonPath("$.expedientes[0].id").value(vigenteId.toString()))
      .andExpect(jsonPath("$.expedientes[0].estado").value("VIGENTE"));
    mvc.perform(get("/api/erp/v1/matriculas/resumen?anio=2027").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.VIGENTE").value(1));

    var datos=Map.of("personaId",personaId.toString(),"anio",2027,"seccionId",seccionId.toString());
    var cuerpo=json.writeValueAsBytes(Map.of("datos",datos));
    mvc.perform(post("/api/erp/v1/matriculas").with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("El estudiante ya tiene una matrícula en 2027. Consulta su expediente existente."));
    mvc.perform(put("/api/erp/v1/matriculas/"+borradorId).with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
      .andExpect(status().isBadRequest());
    assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE id IN (?,?)",Integer.class,borradorId,vigenteId));
    assertEquals("{}",jdbc.queryForObject("SELECT borrador::text FROM mat_matriculas WHERE id=?",String.class,borradorId));
  }

  @Test void paginaMasDeCienExpedientesYFiltraEnPostgres() throws Exception {
    String marca="Lote"+UUID.randomUUID().toString().replace("-","");
    String documento="DOC"+UUID.randomUUID().toString().substring(0,8);
    UUID seccion=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(2028,'prueba-coordinacion') ON CONFLICT DO NOTHING");
    jdbc.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,2028,'PRIMARIA',2,?)",seccion,"S"+seccion.toString().substring(0,5).toUpperCase());
    jdbc.update("""
      INSERT INTO mat_matriculas(id,borrador,creado_por)
      SELECT gen_random_uuid(),jsonb_build_object('nombres',?,'apellidos',n::text,'numeroDocumento',?,
        'anio',2028,'seccionId',?),'prueba-coordinacion' FROM generate_series(1,125) AS n
      """,marca,documento,seccion.toString());
    String ruta="/api/erp/v1/matriculas?buscar="+marca+"&anio=2028&estado=BORRADOR&seccionId="+seccion;
    String primera=mvc.perform(get(ruta+"&pagina=0&tamano=15").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(125))
      .andExpect(jsonPath("$.resultados.length()").value(15))
      .andExpect(jsonPath("$.resultados[0].estudiante").exists())
      .andReturn().getResponse().getContentAsString();
    String repetida=mvc.perform(get(ruta+"&pagina=0&tamano=15").with(authentication(actor())))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(json.readTree(primera).path("resultados").get(0).path("id"),
      json.readTree(repetida).path("resultados").get(0).path("id"));
    mvc.perform(get(ruta+"&pagina=8&tamano=15").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(125))
      .andExpect(jsonPath("$.resultados.length()").value(5));
    mvc.perform(get("/api/erp/v1/matriculas?buscar="+documento+"&anio=2028&tamano=50").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(125));
    mvc.perform(get("/api/erp/v1/matriculas?buscar="+marca+"&anio=2028&nivel=PRIMARIA&grado=2").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(125));
    mvc.perform(get("/api/erp/v1/matriculas?buscar="+marca+"&anio=2028&nivel=SECUNDARIA&grado=2").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/erp/v1/matriculas/resumen?anio=2028").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.BORRADOR").value(125));
    mvc.perform(get("/api/erp/v1/matriculas?tamano=51").with(authentication(actor())))
      .andExpect(status().isBadRequest());
  }

  @Test void descartaSoloBorradorYConservaConstanciaSinDatosPersonales() throws Exception {
    UUID borrador=UUID.randomUUID();
    UUID vigente=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(2029,'prueba-coordinacion') ON CONFLICT DO NOTHING");
    UUID seccion=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,2029,'PRIMARIA',1,?)",seccion,"S"+seccion.toString().substring(0,5).toUpperCase());
    jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(?,?::jsonb,'prueba-coordinacion')",
      borrador,"{\"nombres\":\"Erróneo\"}");
    jdbc.update("INSERT INTO mat_eventos(id,matricula_id,accion,actor_sub) VALUES(?,?,'BORRADOR_CREADO','prueba-coordinacion')",
      UUID.randomUUID(),borrador);
    String numero=String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000);
    var persona=Map.of("nombres","Adulto","apellidos","Prueba","fechaNacimiento","2000-02-10",
      "condicion","ESTUDIANTE","documento",Map.of("tipo","DNI","pais","PE","numero",numero));
    String ubicacion=mvc.perform(post("/api/erp/v1/personas").with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(persona)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    UUID personaId=UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/')+1));
    jdbc.update("INSERT INTO mat_matriculas(id,persona_id,anio,seccion_id,estado,borrador,creado_por,confirmado_en) VALUES(?,?,2029,?,'VIGENTE','{}'::jsonb,'prueba-coordinacion',now())",
      vigente,personaId,seccion);
    mvc.perform(delete("/api/erp/v1/matriculas/"+vigente).with(authentication(actor())).with(csrf()))
      .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/erp/v1/matriculas/"+borrador).with(authentication(actor())))
      .andExpect(status().isForbidden());
    mvc.perform(delete("/api/erp/v1/matriculas/"+borrador).with(authentication(actor())).with(csrf()))
      .andExpect(status().isNoContent());
    mvc.perform(delete("/api/erp/v1/matriculas/"+borrador).with(authentication(actor())).with(csrf()))
      .andExpect(status().isNoContent());
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE id=?",Integer.class,borrador));
    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE id=? AND estado='VIGENTE'",Integer.class,vigente));
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mat_eventos WHERE matricula_id=?",Integer.class,borrador));
    assertEquals("prueba-coordinacion",jdbc.queryForObject(
      "SELECT descartado_por FROM mat_borradores_descartados WHERE matricula_id=?",String.class,borrador));
  }

  @Test void dosOperadoresNoConfirmanDosVigentesDelMismoAnio() throws Exception {
    String numero=String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000);
    var persona=Map.of("nombres","Adulto","apellidos","Concurrente","fechaNacimiento","2000-02-10",
      "condicion","ESTUDIANTE","documento",Map.of("tipo","DNI","pais","PE","numero",numero));
    String ubicacion=mvc.perform(post("/api/erp/v1/personas").with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(persona)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    UUID personaId=UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/')+1));
    jdbc.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(2030,'prueba-coordinacion') ON CONFLICT DO NOTHING");
    UUID seccion=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,2030,'PRIMARIA',1,?)",seccion,"S"+seccion.toString().substring(0,5).toUpperCase());
    UUID primero=UUID.randomUUID(), segundo=UUID.randomUUID();
    String borrador=json.writeValueAsString(Map.of("personaId",personaId.toString(),"anio",2030,"seccionId",seccion.toString()));
    for (UUID id:List.of(primero,segundo))
      jdbc.update("INSERT INTO mat_matriculas(id,borrador,creado_por) VALUES(?,?::jsonb,'prueba-coordinacion')",id,borrador);
    var inicio=new CountDownLatch(1);
    try (var ejecutor=Executors.newFixedThreadPool(2)) {
      var tareas=List.of(primero,segundo).stream().map(id -> ejecutor.submit(() -> {
        inicio.await();
        var respuesta=mvc.perform(post("/api/erp/v1/matriculas/"+id+"/confirmar")
          .with(authentication(actor("operador-"+id))).with(csrf())).andReturn().getResponse();
        return Map.entry(respuesta.getStatus(),respuesta.getContentAsString());
      })).toList();
      inicio.countDown();
      var a=tareas.get(0).get(20,TimeUnit.SECONDS);
      var b=tareas.get(1).get(20,TimeUnit.SECONDS);
      assertEquals(1,List.of(a,b).stream().filter(r -> r.getKey()==200).count());
      assertTrue(List.of(a,b).stream().filter(r -> r.getKey()!=200)
        .allMatch(r -> (r.getKey()==400 || r.getKey()==409) && r.getValue().contains("matrícula")));
    }
    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mat_matriculas WHERE persona_id=? AND anio=2030 AND estado='VIGENTE'",Integer.class,personaId));
    assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM erp_solicitudes_externas WHERE entidad_tipo='MATRICULA' AND entidad_id IN (?,?)",Integer.class,primero,segundo));
    mvc.perform(get("/api/erp/v1/matriculas?buscar=Concurrente&anio=2030&estado=VIGENTE").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
      .andExpect(jsonPath("$.resultados[0].estudiante").value("Adulto Concurrente"));
    mvc.perform(get("/api/erp/v1/matriculas?buscar="+numero+"&estado=VIGENTE").with(authentication(actor())))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
      .andExpect(jsonPath("$.resultados[0].documento").value(numero));
  }
}
