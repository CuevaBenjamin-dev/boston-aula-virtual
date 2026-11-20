package pe.boston.erp.matriculas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import pe.boston.erp.matriculas.api.MatriculasController;
import pe.boston.erp.oferta.OfertaConsulta;
import pe.boston.erp.personas.PersonasParaMatriculas;

class FinalizacionAnioTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final MatriculasController servicio=new MatriculasController(db,mock(PersonasParaMatriculas.class),mock(OfertaConsulta.class));
  private OAuth2AuthenticationToken actor() {
    var token=new OidcIdToken("prueba",Instant.now().minusSeconds(1),Instant.now().plusSeconds(60),
      Map.of("sub","coordinacion-prueba","tipo","COORDINACION"));
    var user=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_COORDINACION")),token);
    return new OAuth2AuthenticationToken(user,user.getAuthorities(),"boston-erp");
  }
  @Test void soloFinalizaVigentesDelAnioYRepetirNoDuplicaEfectos() {
    UUID primera=UUID.randomUUID(),segunda=UUID.randomUUID();
    when(db.query(contains("WHERE anio=? AND estado='VIGENTE' FOR UPDATE"),any(org.springframework.jdbc.core.RowMapper.class),eq(2025)))
      .thenReturn(List.of(primera,segunda),List.of());
    assertEquals(2,servicio.finalizar(2025,actor()).finalizadas());
    assertEquals(0,servicio.finalizar(2025,actor()).finalizadas());
    verify(db,times(2)).update(startsWith("UPDATE mat_matriculas SET estado='FINALIZADA'"),any(UUID.class));
  }
  @Test void impideCerrarAnioTodaviaEnCurso() {
    assertThrows(IllegalArgumentException.class,()->servicio.finalizar(java.time.LocalDate.now().getYear(),actor()));
    verify(db,never()).query(contains("FOR UPDATE"),any(org.springframework.jdbc.core.RowMapper.class),anyInt());
  }
}
