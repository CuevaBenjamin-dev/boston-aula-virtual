package pe.boston.identidad;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccesoController {
  private final Restablecimiento restablecimiento;
  public AccesoController(Restablecimiento restablecimiento) { this.restablecimiento=restablecimiento; }
  @GetMapping(value="/login",produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> login(HttpServletRequest request,CsrfToken csrf) {
    String aviso=request.getParameterMap().containsKey("error")
      ? "<p class=\"aviso\" role=\"alert\">Usuario o contraseña incorrectos, o cuenta temporalmente bloqueada.</p>" : "";
    String salida=request.getParameterMap().containsKey("logout")
      ? "<p class=\"salida\" role=\"status\">Sesión cerrada correctamente.</p>" : "";
    if(request.getParameterMap().containsKey("restablecida")) salida="<p class=\"salida\" role=\"status\">Contraseña actualizada. Ya puedes iniciar sesión.</p>";
    String html="""
      <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Boston Identidad · Iniciar sesión</title><style>
      :root{font-family:system-ui,-apple-system,Segoe UI,sans-serif;color:#18283d;background:#edf4f8}
      *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px}
      main{width:min(100%,420px);padding:36px;background:#fff;border:1px solid #dce8ef;border-radius:18px;box-shadow:0 20px 55px #12355618}
      .logo{display:grid;place-items:center;width:42px;height:42px;border-radius:11px;background:#10a19a;color:#fff;font-size:26px;font-weight:800}
      small{display:block;margin-top:18px;color:#098b87;letter-spacing:.13em;font-weight:800}h1{margin:7px 0 10px;font-size:26px;letter-spacing:-.03em}
      p{color:#617187;line-height:1.5}form{display:grid;gap:16px;margin-top:26px}label{display:grid;gap:7px;font-size:14px;font-weight:700}
      input{width:100%;border:1px solid #cbd9e4;border-radius:8px;padding:11px 12px;font:inherit}input:focus{outline:3px solid #a9e5e0;border-color:#0b918c}
      button{border:0;border-radius:8px;background:#174f8b;color:white;padding:12px 16px;font:inherit;font-weight:700;cursor:pointer}
      button:hover{background:#0f3c6c}.aviso,.salida{padding:12px;border-radius:8px;margin:17px 0 0}.aviso{background:#fff0ed;color:#973c30}.salida{background:#e9f8ee;color:#236e43}
      </style></head><body><main><div class="logo">B</div><small>BOSTON BILINGUAL SCHOOL</small><h1>Identidad institucional</h1>
      <p>Ingresa con tu cuenta para continuar al Aula Virtual.</p>__AVISO____SALIDA__
      <form action="/login" method="post"><input type="hidden" name="__CSRF_NAME__" value="__CSRF_TOKEN__">
      <label>Usuario<input name="username" autocomplete="username" required autofocus></label>
      <label>Contraseña<input name="password" type="password" autocomplete="current-password" required></label>
      <button type="submit">Iniciar sesión</button></form><p><a href="/restablecer">Ya tengo un código de recuperación</a></p></main></body></html>
      """.replace("__AVISO__",aviso).replace("__SALIDA__",salida)
      .replace("__CSRF_NAME__",csrf.getParameterName()).replace("__CSRF_TOKEN__",csrf.getToken());
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
  }
  @GetMapping(value="/restablecer",produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> formulario(HttpServletRequest request,CsrfToken csrf) {
    String aviso=request.getParameterMap().containsKey("error")?"<p role=\"alert\" class=\"aviso\">Código inválido o vencido. Comprueba también que la nueva contraseña tenga al menos 12 caracteres.</p>":"";
    String html="""
      <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Boston Identidad · Restablecer acceso</title><style>
      :root{font-family:system-ui,-apple-system,Segoe UI,sans-serif;color:#18283d;background:#edf4f8}*{box-sizing:border-box}
      body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px}main{width:min(100%,420px);padding:36px;background:white;border:1px solid #dce8ef;border-radius:18px;box-shadow:0 20px 55px #12355618}
      h1{margin:0 0 12px;font-size:25px}p{color:#617187;line-height:1.5}form{display:grid;gap:16px;margin:25px 0}label{display:grid;gap:7px;font-size:14px;font-weight:700}
      input{width:100%;border:1px solid #cbd9e4;border-radius:8px;padding:11px 12px;font:inherit}input:focus{outline:3px solid #a9e5e0;border-color:#0b918c}
      button{border:0;border-radius:8px;background:#174f8b;color:#fff;padding:12px 16px;font:inherit;font-weight:700;cursor:pointer}a{color:#174f8b}
      .aviso{background:#fff0ed;color:#973c30;padding:12px;border-radius:8px}
      </style></head><body><main><h1>Restablecer acceso</h1><p>Escribe el código temporal recibido por el canal institucional y establece una nueva contraseña.</p>__AVISO__
      <form action="/restablecer" method="post"><input type="hidden" name="__CSRF_NAME__" value="__CSRF_TOKEN__">
      <label>Código de recuperación<input name="token" autocomplete="off" required></label>
      <label>Nueva contraseña<input name="nuevaClave" type="password" autocomplete="new-password" minlength="12" required></label>
      <button type="submit">Cambiar contraseña</button></form><a href="/login">Volver al inicio de sesión</a></main></body></html>
      """.replace("__AVISO__",aviso).replace("__CSRF_NAME__",csrf.getParameterName()).replace("__CSRF_TOKEN__",csrf.getToken());
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
  }
  @PostMapping(value="/restablecer",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> cambiar(@RequestParam String token,@RequestParam String nuevaClave) {
    try { restablecimiento.cambiar(token,nuevaClave);return ResponseEntity.status(303).location(URI.create("/login?restablecida")).build(); }
    catch(IllegalArgumentException error) { return ResponseEntity.status(303).location(URI.create("/restablecer?error")).build(); }
  }
}
