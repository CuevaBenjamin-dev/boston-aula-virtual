package pe.boston.erp.asignaciones;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class AsignacionesHttpTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
  static { postgres.start(); }
  @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",postgres::getJdbcUrl);
    r.add("spring.datasource.username",postgres::getUsername);
    r.add("spring.datasource.password",postgres::getPassword);
    r.add("spring.security.oauth2.client.registration.boston-erp.client-secret",()->"prueba-local");
  }
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  ObjectMapper json=new ObjectMapper();
  RequestPostProcessor auth(String tipo) {
    var token=new OidcIdToken("token-test",Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),Map.of("sub","prueba-"+tipo,"tipo",tipo));
    var usuario=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_"+tipo)),token);
    return authentication(new OAuth2AuthenticationToken(usuario,usuario.getAuthorities(),"boston-erp"));
  }
  String alta(String ruta,Map<String,?> datos) throws Exception {
    return mvc.perform(post("/api/erp/v1"+ruta).with(auth("DIRECCION")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(datos)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }
  @Test void loteAtomicoYConsultaCompleta() throws Exception {
    int anio=2090;
    alta("/oferta/anios",Map.of("anio",anio));
    String a=json.readTree(alta("/oferta/secciones",Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"nombre","A"))).path("id").asText();
    String b=json.readTree(alta("/oferta/secciones",Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"nombre","B"))).path("id").asText();
    String c=json.readTree(alta("/oferta/secciones",Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"nombre","C"))).path("id").asText();
    String materia=json.readTree(alta("/oferta/asignaturas",Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"codigo","MAT","nombre","Matemática"))).path("id").asText();
    var persona=Map.of("nombres","Docente","apellidos","Ensayo","telefono","987654321","fechaNacimiento","1980-02-10","sexo","F","nacionalidad","PE","direccion","Calle de prueba","condicion","DOCENTE","documento",Map.of("tipo","DNI","pais","PE","numero","00008190"));
    String loc=mvc.perform(post("/api/erp/v1/personas").with(auth("DIRECCION")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(persona)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertNotNull(loc);String docente=loc.substring(loc.lastIndexOf('/')+1);
    var lote=new java.util.HashMap<String,Object>(Map.of("anio",anio,"docenteId",docente,"asignaturaId",materia,"seccionIds",List.of(a,b),"tipo","TITULAR","desde","2090-03-01"));
    mvc.perform(post("/api/erp/v1/asignaciones/lote").with(auth("DIRECCION")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(lote)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].seccionId").value(a));
    long antes=jdbc.queryForObject("SELECT count(*) FROM erp_solicitudes_externas WHERE entidad_tipo='ASIGNACION'",Long.class);
    lote.put("seccionIds",List.of(c,a)); // C sería nueva, A duplicada: ninguna alta debe quedar persistida.
    mvc.perform(post("/api/erp/v1/asignaciones/lote").with(auth("DIRECCION")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(lote))).andExpect(status().isBadRequest());
    assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM asi_asignaciones WHERE seccion_id=?",Long.class,UUID.fromString(c)));
    assertEquals(antes,jdbc.queryForObject("SELECT count(*) FROM erp_solicitudes_externas WHERE entidad_tipo='ASIGNACION'",Long.class));
    lote.put("seccionIds",List.of(c));lote.put("desde","2089-03-01");
    mvc.perform(post("/api/erp/v1/asignaciones/lote").with(auth("DIRECCION")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(lote))).andExpect(status().isBadRequest());
    // Historial sintético exclusivamente en PostgreSQL efímero de pruebas.
    jdbc.update("""
      INSERT INTO asi_asignaciones(id,docente_id,seccion_id,asignatura_id,tipo,desde,estado,actor_sub)
      SELECT gen_random_uuid(),?,?,?,'TITULAR',DATE '2080-01-01'+i,'CERRADA','prueba'
      FROM generate_series(1,501) i
      """,UUID.fromString(docente),UUID.fromString(c),UUID.fromString(materia));
    mvc.perform(get("/api/erp/v1/asignaciones?anio=2090").with(auth("DIRECCION")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(503));
    mvc.perform(get("/api/erp/v1/asignaciones?anio=2090").with(auth("DOCENTE"))).andExpect(status().isForbidden());
  }
}
