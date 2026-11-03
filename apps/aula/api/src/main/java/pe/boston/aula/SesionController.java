package pe.boston.aula;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") public class SesionController {
  private final UsuarioActual usuario;
  public SesionController(UsuarioActual usuario) { this.usuario=usuario; }
  @GetMapping("/sesion") public Map<String,Object> sesion(Authentication auth, CsrfToken csrf) {
    var perfil=new java.util.HashMap<>(usuario.perfil(auth));
    perfil.put("csrfToken",csrf.getToken());
    return perfil;
  }
}
