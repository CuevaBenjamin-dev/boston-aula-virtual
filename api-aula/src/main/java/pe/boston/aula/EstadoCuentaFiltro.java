package pe.boston.aula;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component public class EstadoCuentaFiltro extends OncePerRequestFilter {
  private final ErpCliente credenciales;
  private final String identidad;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper json=new ObjectMapper();
  public EstadoCuentaFiltro(ErpCliente credenciales,@Value("${BOSTON_IDENTITY_URL:http://identidad:9000}") String identidad) { this.credenciales=credenciales;this.identidad=identidad; }
  @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/api/"); }
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth!=null && auth.getPrincipal() instanceof OidcUser user) {
      try {
        HttpRequest consulta=HttpRequest.newBuilder(URI.create(identidad+"/api/identidad/estado/"+user.getClaimAsString("cuenta_id")))
          .timeout(Duration.ofSeconds(4)).header("Authorization","Bearer "+credenciales.token()).GET().build();
        var respuesta=http.send(consulta,HttpResponse.BodyHandlers.ofString());
        if(respuesta.statusCode()!=200) throw new IllegalStateException("Identidad: HTTP "+respuesta.statusCode());
        var estado=json.readTree(respuesta.body());
        Object versionToken=user.getClaim("sesion_version");
        if(!estado.path("activo").asBoolean() || !(versionToken instanceof Number version) || version.intValue()!=estado.path("sesionVersion").asInt(-1)) {
          var sesion=req.getSession(false); if(sesion!=null) sesion.invalidate();
          SecurityContextHolder.clearContext(); res.sendError(401,"Sesión revocada o cuenta suspendida"); return;
        }
      } catch(Exception e) { res.sendError(503,"No se pudo verificar la cuenta"); return; }
    }
    chain.doFilter(req,res);
  }
}
