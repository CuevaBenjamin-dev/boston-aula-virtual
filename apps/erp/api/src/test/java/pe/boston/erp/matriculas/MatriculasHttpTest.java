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

  private OAuth2AuthenticationToken actor() {
    var claims=Map.<String,Object>of("sub","prueba-coordinacion","tipo","COORDINACION");
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

    var datos=Map.of("personaId",personaId.toString(),"anio",2027,"seccionId",seccionId.toString());
    var cuerpo=json.writeValueAsBytes(Map.of("datos",datos));
    mvc.perform(post("/api/erp/v1/matriculas").with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("El estudiante ya tiene una matrícula en 2027. Consulta su expediente existente."));
    mvc.perform(put("/api/erp/v1/matriculas/"+borradorId).with(authentication(actor())).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
      .andExpect(status().isBadRequest());
    assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM mat_matriculas",Integer.class));
    assertEquals("{}",jdbc.queryForObject("SELECT borrador::text FROM mat_matriculas WHERE id=?",String.class,borradorId));
  }
}
