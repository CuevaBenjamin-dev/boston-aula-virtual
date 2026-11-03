package pe.boston.aula;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component public class UsuarioActual {
  private final JdbcTemplate db;
  public UsuarioActual(JdbcTemplate db) { this.db=db; }
  public OidcUser oidc(Authentication auth) {
    if (auth==null || !(auth.getPrincipal() instanceof OidcUser u)) throw new AccessDeniedException("Inicie sesión");
    return u;
  }
  public String tipo(Authentication a) { return oidc(a).getClaimAsString("tipo"); }
  public UUID referencia(Authentication a) {
    String id=oidc(a).getClaimAsString("referencia_erp");
    if (id==null || id.isBlank()) throw new AccessDeniedException("Cuenta sin vínculo ERP");
    return UUID.fromString(id);
  }
  public UUID cuenta(Authentication a) { return UUID.fromString(oidc(a).getClaimAsString("cuenta_id")); }
  public boolean developer(Authentication a) { return Boolean.TRUE.equals(oidc(a).getClaim("developer")); }
  public void estudiante(Authentication a) { if (!"ESTUDIANTE".equals(tipo(a))) throw new AccessDeniedException("Solo cuenta académica"); }
  public void docente(Authentication a,UUID curso) {
    if (!"DOCENTE".equals(tipo(a))) throw new AccessDeniedException("Solo docente");
    if (db.queryForObject("SELECT count(*) FROM docentes_curso d JOIN cursos c ON c.id=d.curso_id WHERE d.curso_id=? AND d.docente_erp_id=? AND d.activo=TRUE AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)",Long.class,curso,referencia(a))==0) throw new AccessDeniedException("Curso ajeno");
  }
  public void estudianteCurso(Authentication a,UUID curso) {
    estudiante(a);
    if (db.queryForObject("SELECT count(*) FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id WHERE i.curso_id=? AND i.estudiante_erp_id=? AND i.activo=TRUE AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)",Long.class,curso,referencia(a))==0) throw new AccessDeniedException("Curso ajeno");
  }
  public void leerCurso(Authentication a,UUID curso) {
    if ("ESTUDIANTE".equals(tipo(a))) estudianteCurso(a,curso);
    else if ("DOCENTE".equals(tipo(a))) docente(a,curso);
    else if ("COORDINACION".equals(tipo(a))) {
      if(db.queryForObject("SELECT count(*) FROM cursos c JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id WHERE c.id=? AND x.cuenta_id=? AND x.confirmado_erp AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)",Long.class,curso,cuenta(a))==0) throw new AccessDeniedException("Curso fuera del ámbito de coordinación");
    } else throw new AccessDeniedException("Sin acceso académico");
  }
  public void administrador(Authentication a) { if (!"ADMINISTRADOR".equals(tipo(a))) throw new AccessDeniedException("Solo administrador"); }
  public void developerRequerido(Authentication a) { administrador(a); if (!developer(a)) throw new AccessDeniedException("Permiso Developer requerido"); }
  public Map<String,Object> perfil(Authentication a) { OidcUser u=oidc(a); return Map.of("usuario",u.getSubject(),"tipo",tipo(a),"developer",developer(a)); }
}
