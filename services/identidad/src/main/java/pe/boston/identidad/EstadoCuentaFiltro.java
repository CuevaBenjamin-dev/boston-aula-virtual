package pe.boston.identidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component public class EstadoCuentaFiltro extends OncePerRequestFilter {
  private final JdbcTemplate db;
  public EstadoCuentaFiltro(JdbcTemplate db) { this.db=db; }
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    String ruta=request.getRequestURI();
    return !ruta.startsWith("/api/identidad/") && !ruta.equals("/oauth2/authorize") && !ruta.equals("/userinfo");
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth!=null && !(auth.getPrincipal() instanceof String s && "anonymousUser".equals(s))) {
      try {
        String sql; Object parametro; Integer version=null;
        if(auth instanceof JwtAuthenticationToken jwt) {
          String id=jwt.getToken().getClaimAsString("cuenta_id");
          if(id==null) { chain.doFilter(request,response); return; } // Token del servicio Aula.
          sql="SELECT activo,sesion_version FROM cuentas WHERE id=?";
          parametro=UUID.fromString(id);
          Object claim=jwt.getToken().getClaim("sesion_version");
          if(!(claim instanceof Number numero)) { denegar(request,response); return; }
          version=numero.intValue();
        } else {
          sql="SELECT activo,sesion_version FROM cuentas WHERE usuario=?";
          parametro=auth.getName();
        }
        Integer versionToken=version;
        var estado=db.query(sql,(rs,n)->new boolean[]{rs.getBoolean(1),versionToken==null || rs.getInt(2)==versionToken},parametro);
        if(estado.isEmpty()||!estado.getFirst()[0]||!estado.getFirst()[1]) { denegar(request,response); return; }
      } catch(Exception e) { response.sendError(503,"No se pudo verificar la cuenta"); return; }
    }
    chain.doFilter(request,response);
  }
  private void denegar(HttpServletRequest request,HttpServletResponse response) throws IOException {
    var sesion=request.getSession(false);
    if(sesion!=null) sesion.invalidate();
    SecurityContextHolder.clearContext();
    if(request.getRequestURI().equals("/oauth2/authorize")) response.sendRedirect("/login?error");
    else response.sendError(401,"Sesión revocada o cuenta suspendida");
  }
}
