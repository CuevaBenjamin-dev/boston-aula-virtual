package pe.boston.erp.personas;

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
class PersonasHttpTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
  static { postgres.start(); }
  @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.security.oauth2.client.registration.boston-erp.client-secret", ()->"prueba-local");
  }
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  ObjectMapper json = new ObjectMapper();

  @Test void buscaNombresSinDistinguirTildesYCajaMientrasSeEscribe() throws Exception {
    UUID maria=crearPersona("ESTUDIANTE",false,null,null);
    UUID martha=crearPersona("ESTUDIANTE",false,null,null);
    jdbc.update("UPDATE per_personas SET nombres='ZyxMaría' WHERE id=?",maria);
    jdbc.update("UPDATE per_personas SET nombres='ZyxMartha' WHERE id=?",martha);
    mvc.perform(get("/api/erp/v1/personas?buscar=zyxmar").with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.resultados.length()").value(2));
    mvc.perform(get("/api/erp/v1/personas?buscar=ZYXMARI").with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.resultados.length()").value(1))
      .andExpect(jsonPath("$.resultados[0].id").value(maria.toString()));
  }

  private UUID crearPersona(String condicion, boolean provisional, String telefono, String correo) throws Exception {
    var cuerpo = new java.util.HashMap<String,Object>();
    cuerpo.put("nombres","Persona"); cuerpo.put("apellidos","Prueba");
    cuerpo.put("fechaNacimiento","2012-02-10"); cuerpo.put("sexo","F");
    cuerpo.put("nacionalidad","PE"); cuerpo.put("direccion","Calle de prueba");
    cuerpo.put("telefono",telefono); cuerpo.put("correo",correo); cuerpo.put("condicion",condicion);
    if (provisional) cuerpo.put("provisional",Map.of("motivo","Sin documento oficial",
      "sustentoTipo","Declaración jurada","sustentoReferencia","Prueba de integración"));
    else cuerpo.put("documento",Map.of("tipo","DNI","pais","PE","numero",
      String.format("%08d",Math.abs(UUID.randomUUID().hashCode()) % 100000000)));
    String ruta=mvc.perform(post("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertNotNull(ruta);
    return UUID.fromString(ruta.substring(ruta.lastIndexOf('/')+1));
  }

  static OAuth2AuthenticationToken actor(String tipo, UUID persona) {
    var claims = new java.util.HashMap<String,Object>();
    claims.put("sub", "prueba-"+tipo); claims.put("tipo",tipo);
    if (persona!=null) claims.put("referencia_erp",persona.toString());
    var token = new OidcIdToken("token-test",Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),claims);
    var usuario = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_"+tipo)),token);
    return new OAuth2AuthenticationToken(usuario,usuario.getAuthorities(),"boston-erp");
  }

  @Test void eliminaSoloRegistroIncorrectoSinUsoYLiberarDocumento() throws Exception {
    UUID persona=crearPersona("ESTUDIANTE",false,null,null);
    String ruta="/api/erp/v1/personas/"+persona;
    String numero=jdbc.queryForObject("SELECT numero FROM per_documentos WHERE persona_id=?",String.class,persona);
    mvc.perform(delete(ruta).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isForbidden());
    mvc.perform(delete(ruta).with(authentication(actor("DOCENTE",null))).with(csrf()))
      .andExpect(status().isForbidden());
    mvc.perform(delete(ruta).with(authentication(actor("COORDINACION",null))).with(csrf()))
      .andExpect(status().isNoContent());
    mvc.perform(get(ruta).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isNotFound());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM per_eliminaciones WHERE persona_id=?",Integer.class,persona));
    var cuerpo=Map.of("nombres","Persona","apellidos","Correcta","fechaNacimiento","2012-02-10",
      "condicion","ESTUDIANTE","documento",Map.of("tipo","DNI","pais","PE","numero",numero));
    mvc.perform(post("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)))
      .andExpect(status().isCreated());

    UUID vinculada=crearPersona("ESTUDIANTE",true,null,null);
    UUID familiar=crearPersona("PERSONAL_INSTITUCIONAL",false,"987654321","familiar@prueba.local");
    mvc.perform(post("/api/erp/v1/personas/parentescos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
        Map.of("estudianteId",vinculada.toString(),"familiarId",familiar.toString(),"tipo","MADRE"))))
      .andExpect(status().isCreated());
    mvc.perform(delete("/api/erp/v1/personas/"+vinculada)
      .with(authentication(actor("DIRECCION",null))).with(csrf()))
      .andExpect(status().isConflict());
    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM per_personas WHERE id=?",Integer.class,vinculada));
  }

  @Test void autenticacionYPermisosPorOperacionYRecurso() throws Exception {
    mvc.perform(get("/api/erp/v1/personas")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/erp/v1/personas").with(authentication(actor("DOCENTE",null))))
      .andExpect(status().isForbidden());
    UUID fichaDocente=UUID.randomUUID();
    mvc.perform(get("/api/erp/v1/personas/capacidades")
        .with(authentication(actor("DOCENTE",fichaDocente))))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/erp/v1/personas").with(authentication(actor("ESTUDIANTE",null))))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/erp/v1/personas").with(authentication(actor("COORDINACION",null))))
      .andExpect(status().isOk());
    mvc.perform(get("/api/erp/v1/personas/tipos-documento").with(authentication(actor("COORDINACION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").exists());
    mvc.perform(get("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk());
    mvc.perform(get("/api/erp/v1/openapi")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/erp/v1/openapi").with(authentication(actor("DOCENTE",null))))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/erp/v1/openapi").with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/erp/v1/personas']").exists());
  }

  @Test void altaConsultaCorreccionYCsrf() throws Exception {
    var numero = String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000);
    var cuerpo = Map.of("nombres","Lucía","apellidos","Integración","fechaNacimiento","2012-02-10",
      "sexo","F","nacionalidad","PE","direccion","Calle de prueba", "condicion","ESTUDIANTE",
      "documento",Map.of("tipo","DNI","pais","PE","numero",numero));
    mvc.perform(post("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null)))
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo))).andExpect(status().isForbidden());
    String ubicacion = mvc.perform(post("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertNotNull(ubicacion);
    UUID id = UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/')+1));
    mvc.perform(get(ubicacion).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Lucía"));
    mvc.perform(get(ubicacion).with(authentication(actor("DOCENTE",UUID.randomUUID()))))
      .andExpect(status().isForbidden());
    mvc.perform(get(ubicacion).with(authentication(actor("DOCENTE",id))))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/erp/v1/personas").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)))
      .andExpect(status().isConflict());
    mvc.perform(post(ubicacion+"/documentos").with(authentication(actor("COORDINACION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("tipo","CE","pais","PE","numero","ABC123"))))
      .andExpect(status().isNoContent());
    mvc.perform(get(ubicacion).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.documentos.length()").value(2));
  }

  @Test void condicionesParentescosApoderamientosEIdentificacionProvisional() throws Exception {
    UUID estudiante=crearPersona("ESTUDIANTE",true,null,null);
    UUID familiar=crearPersona("PERSONAL_INSTITUCIONAL",false,"987654321","persona@prueba.local");
    UUID otro=crearPersona("PERSONAL_INSTITUCIONAL",false,"987654322","otro@prueba.local");
    String base="/api/erp/v1/personas/";
    mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.provisional.motivo").value("Sin documento oficial"));

    var parentesco=Map.of("estudianteId",estudiante.toString(),"familiarId",familiar.toString(),"tipo","MADRE");
    mvc.perform(post(base+"parentescos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(parentesco)))
      .andExpect(status().isCreated());
    var vinculo=Map.of("estudianteId",estudiante.toString(),"apoderadoId",familiar.toString(),"sinParentesco",false);
    String ruta=mvc.perform(post(base+"apoderamientos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(vinculo)))
      .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    assertNotNull(ruta);
    UUID vinculoFamiliar=UUID.fromString(ruta.substring(ruta.lastIndexOf('/')+1));
    mvc.perform(post(base+"apoderamientos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(vinculo)))
      .andExpect(status().isConflict());

    var sinAutorizacion=Map.of("estudianteId",estudiante.toString(),"apoderadoId",otro.toString(),"sinParentesco",true);
    mvc.perform(post(base+"apoderamientos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(sinAutorizacion)))
      .andExpect(status().isBadRequest());
    var verbal=new java.util.HashMap<String,Object>(sinAutorizacion);
    verbal.put("autorizacion",Map.of("autorizador","Madre facultada","medio","Teléfono",
      "fecha",Instant.now().toString(),"sustentoReferencia","Llamada verificada"));
    mvc.perform(post(base+"apoderamientos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(verbal)))
      .andExpect(status().isCreated());
    mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.apoderamientos.length()").value(2));
    var respuestaVinculos=mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var referencia=json.readTree(respuestaVinculos).path("referencias").path(familiar.toString());
    assertEquals("Persona",referencia.path("nombres").asText());
    assertEquals("DNI",referencia.path("tipoDocumento").asText());
    assertFalse(referencia.path("numeroDocumento").asText().isBlank());
    mvc.perform(get(base+familiar).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.referencias['"+estudiante+"'].sexo").value("F"));

    mvc.perform(post(base+estudiante+"/apoderamientos/"+vinculoFamiliar+"/desactivar")
      .with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("motivo","Cambio de contacto"))))
      .andExpect(status().isNoContent());
    mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.apoderamientos[1].vigenteHasta").isNotEmpty());

    mvc.perform(post(base+estudiante+"/documentos").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("tipo","CE","pais","PE","numero","CE-"+UUID.randomUUID()))))
      .andExpect(status().isNoContent());
    mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.provisional.regularizadaEn").isNotEmpty());

    mvc.perform(post(base+estudiante+"/condiciones").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("tipo","DOCENTE"))))
      .andExpect(status().isBadRequest());
    mvc.perform(put(base+estudiante+"/contacto").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("telefono","987654323"))))
      .andExpect(status().isNoContent());
    mvc.perform(post(base+estudiante+"/condiciones").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("tipo","DOCENTE"))))
      .andExpect(status().isNoContent());
    var ficha=mvc.perform(get(base+estudiante).with(authentication(actor("DIRECCION",null))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var condiciones=json.readTree(ficha).get("condiciones");
    String condicionId=null;
    for (var c: condiciones) if ("DOCENTE".equals(c.get("tipo").asText())) condicionId=c.get("id").asText();
    assertNotNull(condicionId);
    mvc.perform(post(base+estudiante+"/condiciones/"+condicionId+"/desactivar")
      .with(authentication(actor("DIRECCION",null))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsBytes(Map.of("motivo","Fin de contrato"))))
      .andExpect(status().isNoContent());
    mvc.perform(post(base+estudiante+"/condiciones").with(authentication(actor("DIRECCION",null))).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("tipo","DOCENTE"))))
      .andExpect(status().isNoContent());
  }

  @Test void ofertaYMatriculaNuevaConBorradorYApoderado() throws Exception {
    var auth=authentication(actor("COORDINACION",null));
    int anio=2026;
    mvc.perform(post("/api/erp/v1/oferta/anios").with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("anio",anio))))
      .andExpect(status().isOk());
    String seccionJson=mvc.perform(post("/api/erp/v1/oferta/secciones").with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
        Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"nombre","A"))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String seccion=json.readTree(seccionJson).path("id").asText();
    String asignaturaJson=mvc.perform(post("/api/erp/v1/oferta/asignaturas").with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
        Map.of("anio",anio,"nivel","PRIMARIA","grado",1,"codigo","PRI-01-MAT","nombre","Matemática"))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String asignatura=json.readTree(asignaturaJson).path("id").asText();
    String numero=String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000);
    long personasAntes=jdbc.queryForObject("SELECT count(*) FROM per_personas",Long.class);
    var datos=Map.of("nombres","Alumno","apellidos","Integración","fechaNacimiento","2016-03-04",
      "tipoDocumento","DNI","paisEmisor","PE","numeroDocumento",numero,"seccionId",seccion,
      "apoderado",Map.of("nombres","Madre","apellidos","Integración","telefono","987654321",
        "tipoDocumento","DNI","paisEmisor","PE","numeroDocumento",
        String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000),"parentesco","MADRE"));
    String borradorJson=mvc.perform(post("/api/erp/v1/matriculas").with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("datos",datos))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"))
      .andReturn().getResponse().getContentAsString();
    String id=json.readTree(borradorJson).path("id").asText();
    assertEquals(personasAntes,jdbc.queryForObject("SELECT count(*) FROM per_personas",Long.class));
    var incompleto=new java.util.HashMap<String,Object>(datos);
    incompleto.remove("apoderado");
    mvc.perform(put("/api/erp/v1/matriculas/"+id).with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("datos",incompleto))))
      .andExpect(status().isOk());
    mvc.perform(post("/api/erp/v1/matriculas/"+id+"/confirmar").with(auth).with(csrf()))
      .andExpect(status().isBadRequest());
    assertEquals(personasAntes,jdbc.queryForObject("SELECT count(*) FROM per_personas",Long.class));
    mvc.perform(put("/api/erp/v1/matriculas/"+id).with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("datos",datos))))
      .andExpect(status().isOk());
    mvc.perform(post("/api/erp/v1/matriculas/"+id+"/confirmar").with(auth).with(csrf()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VIGENTE"))
      .andExpect(jsonPath("$.personaId").isNotEmpty());
    assertEquals(personasAntes+2,jdbc.queryForObject("SELECT count(*) FROM per_personas",Long.class));
    assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM erp_solicitudes_externas WHERE entidad_id=?",Long.class,UUID.fromString(id)));
    mvc.perform(get("/api/erp/v1/matriculas/"+id).with(auth))
      .andExpect(status().isOk()).andExpect(jsonPath("$.borrador.nombres").value("Alumno"));
    mvc.perform(post("/api/erp/v1/matriculas/"+id+"/retirar").with(auth).with(csrf()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADA"));
    mvc.perform(post("/api/erp/v1/matriculas/"+id+"/reincorporar").with(auth).with(csrf()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VIGENTE"));

    UUID docente=crearPersona("DOCENTE",false,"987654323",null);
    String asignacionJson=mvc.perform(post("/api/erp/v1/asignaciones").with(auth).with(csrf())
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
        "docenteId",docente,"seccionId",seccion,"asignaturaId",asignatura,
        "tipo","TITULAR","desde","2026-03-01"))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVA"))
      .andReturn().getResponse().getContentAsString();
    String asignacionId=json.readTree(asignacionJson).path("id").asText();
    mvc.perform(get("/api/erp/v1/asignaciones?anio=2026").with(auth))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].asignatura").value("Matemática"));
    mvc.perform(post("/api/erp/v1/asignaciones/"+asignacionId+"/cerrar").with(auth).with(csrf()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));
  }
}
