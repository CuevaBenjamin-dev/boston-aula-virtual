package pe.boston.identidad;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClaveInicialController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  public ClaveInicialController(JdbcTemplate jdbc,PasswordEncoder encoder) { this.jdbc=jdbc; this.encoder=encoder; }

  @GetMapping(value="/clave-inicial",produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> formulario(Authentication actor,CsrfToken csrf) {
    String html="""
      <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Boston Identidad · Cambiar contraseña</title><style>body{font:16px Arial;background:#eef4f8;color:#102b4c;display:grid;place-items:center;min-height:100vh;margin:0}main{background:white;padding:36px;max-width:440px;width:90%;border:1px solid #c1d3e4}h1{font-size:28px}label{display:block;margin:18px 0}input,button{box-sizing:border-box;display:block;width:100%;padding:12px;margin-top:7px}button{background:#1e548c;color:white;border:0;cursor:pointer}</style></head>
      <body><main><h1>Cambia tu contraseña inicial</h1><p>Elige una contraseña personal de al menos 5 caracteres para continuar al Aula.</p>
      <form method="post" action="/clave-inicial"><input type="hidden" name="__NOMBRE__" value="__TOKEN__">
      <label>Contraseña inicial<input type="password" name="actual" autocomplete="current-password" required></label>
      <label>Nueva contraseña<input type="password" name="nueva" autocomplete="new-password" minlength="5" required></label>
      <button type="submit">Guardar y continuar</button></form>__AVISO__</main></body></html>
      """.replace("__NOMBRE__",csrf.getParameterName()).replace("__TOKEN__",csrf.getToken())
        .replace("__AVISO__","");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
  }

  @PostMapping(value="/clave-inicial",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Transactional
  public ResponseEntity<Void> cambiar(@RequestParam String actual,@RequestParam String nueva,
      Authentication actor,HttpServletRequest solicitud) {
    if (nueva==null || nueva.length()<5 || nueva.equals(actual))
      throw new IllegalArgumentException("La nueva contraseña debe tener al menos 5 caracteres y ser distinta");
    var filas=jdbc.query("SELECT id,clave_hash,cambio_clave_obligatorio FROM cuentas WHERE usuario=? FOR UPDATE",
      (rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)},actor.getName());
    if (filas.isEmpty() || !Boolean.TRUE.equals(filas.getFirst()[2]) || !encoder.matches(actual,(String)filas.getFirst()[1]))
      throw new IllegalArgumentException("Contraseña inicial incorrecta");
    UUID id=(UUID)filas.getFirst()[0];
    jdbc.update("UPDATE cuentas SET clave_hash=?,cambio_clave_obligatorio=FALSE,sesion_version=sesion_version+1 WHERE id=?",encoder.encode(nueva),id);
    jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name=?",actor.getName());
    jdbc.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) VALUES(?,?,'CLAVE_INICIAL_CAMBIADA')",UUID.randomUUID(),id);
    Object destino=solicitud.getSession().getAttribute("bbs.oauth.destino");
    solicitud.getSession().removeAttribute("bbs.oauth.destino");
    String ruta=destino instanceof String s && s.startsWith("http://localhost:9000/oauth2/authorize")?s:"http://localhost:8088/";
    return ResponseEntity.status(303).location(URI.create(ruta)).build();
  }
}
