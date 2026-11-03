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
  private static final String ESTILOS="""
    :root{font-family:Arial,Helvetica,sans-serif;color:#0c2542;background:#f9f8f6}*{box-sizing:border-box}
    body{margin:0}.auth-page{min-height:100vh;display:grid;grid-template-columns:minmax(0,1.15fr) minmax(460px,.85fr)}
    .auth-story{position:relative;display:flex;flex-direction:column;justify-content:space-between;min-width:0;min-height:100vh;overflow:hidden;padding:clamp(34px,4vw,72px);background:#0e2745;color:#fff}
    .auth-story:after{content:"";position:absolute;right:-16%;bottom:-26%;width:min(70vw,850px);aspect-ratio:1;border:1px solid rgba(255,255,255,.12);border-radius:50%;box-shadow:0 0 0 90px rgba(255,255,255,.025),0 0 0 180px rgba(255,255,255,.02);pointer-events:none}
    .auth-logo,.auth-story-copy,.auth-story-footer{position:relative;z-index:1}.auth-logo{display:flex;align-items:center;gap:19px}.auth-logo-letters{display:flex;gap:5px;flex:none}
    .auth-logo-letters span{display:grid;place-items:center;width:42px;height:42px;border-radius:3px;background:#24508e;color:#fff;font:800 33px/1 Arial,Helvetica,sans-serif}.auth-logo-letters span:nth-child(2){background:#c90023}.auth-logo-letters span:nth-child(3){background:#f35c0b}
    .auth-logo strong{display:block;font-size:14px;line-height:1.15}.auth-logo small{display:block;margin-top:6px;font-size:9px;font-weight:700;letter-spacing:3.8px}
    .auth-story-copy{max-width:760px;padding:55px 0 70px}.auth-kicker,.auth-step,.auth-story-footer,.auth-entry-footer{font-size:11px;font-weight:700;letter-spacing:2.4px}
    .auth-kicker{display:block;margin-bottom:27px;color:#a6bfd8}.auth-story h1{margin:0;color:#fff;font:900 clamp(76px,7.5vw,150px)/.91 Impact,'Arial Narrow',sans-serif;letter-spacing:-1.5px}.auth-story h1 span,.auth-entry h2 span{color:#f35c0b}
    .auth-story-copy p{max-width:480px;margin:34px 0 0;color:#d2e0ef;font-size:16px;line-height:1.55}.auth-story-footer{display:flex;justify-content:space-between;gap:20px;padding-top:18px;border-top:1px solid rgba(255,255,255,.26);color:#aac1d9;font-size:10px}
    .auth-entry{display:flex;flex-direction:column;justify-content:center;min-width:0;min-height:100vh;padding:clamp(36px,5vw,86px);border-top:8px solid #f9f8f6}.auth-entry-inner{width:min(100%,440px);margin:auto}
    .auth-step{display:block;margin-bottom:28px;color:#b10c2a}.auth-entry h2{margin:0 0 19px;color:#0c2542;font:900 clamp(43px,3.8vw,72px)/.98 Impact,'Arial Narrow',sans-serif;letter-spacing:.2px;text-transform:uppercase}
    .auth-entry-inner>p:not(.aviso):not(.salida){max-width:370px;margin:0 0 27px;color:#55708c;font-size:16px;line-height:1.55}.auth-form{display:grid;gap:18px}
    .auth-form label{display:grid;gap:8px;color:#0c2542;font-size:12px;font-weight:700}.auth-form input{width:100%;min-height:49px;padding:12px 14px;border:1px solid #aebfd0;border-radius:3px;background:#fff;color:#0c2542;font:15px Arial,Helvetica,sans-serif}
    .auth-form input:focus{outline:3px solid #b8d9e8;outline-offset:1px;border-color:#174f8b}.auth-form button{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:59px;margin-top:7px;padding:16px 20px;border:1px solid #174f8b;border-radius:3px;background:#174f8b;color:#fff;font:700 14px Arial,Helvetica,sans-serif;cursor:pointer}
    .auth-form button:hover{background:#103d70;border-color:#103d70}.auth-form button:focus-visible,.auth-help a:focus-visible{outline:3px solid #f35c0b;outline-offset:3px}.auth-form button span:last-child{font-size:24px;line-height:1}
    .auth-help{margin-top:23px;padding-top:19px;border-top:1px solid #d6e0e8;color:#55708c;font-size:12px;line-height:1.5}.auth-help a{color:#174f8b;font-weight:700;text-decoration:underline;text-underline-offset:3px}
    .auth-entry-footer{align-self:center;margin-top:45px;color:#7890a8;font-size:9px}.aviso,.salida{padding:13px 15px;margin:0 0 22px;border:1px solid;border-radius:3px;font-size:13px;line-height:1.5}.aviso{border-color:#f0c6be;background:#fff1ed;color:#8e3227}.salida{border-color:#b7ddc6;background:#edf8f1;color:#215f3b}
    @media(max-width:900px){.auth-page{grid-template-columns:1fr}.auth-story{min-height:350px;padding:30px 36px}.auth-story-copy{padding:48px 0 30px}.auth-story h1{font-size:clamp(58px,11vw,96px)}.auth-story-copy p{margin-top:16px}.auth-entry{min-height:auto;padding:50px 36px 42px}.auth-entry-inner{margin:0 auto}.auth-entry-footer{margin-top:55px}}
    @media(max-width:520px){.auth-story{min-height:0;padding:24px}.auth-logo{gap:12px}.auth-logo-letters span{width:31px;height:31px;font-size:24px}.auth-logo strong{font-size:11px}.auth-logo small{font-size:7px;letter-spacing:2.7px}.auth-story-copy{padding:26px 0 0}.auth-kicker{margin-bottom:12px;font-size:8px;letter-spacing:1.5px}.auth-story h1{font-size:clamp(47px,11vw,60px)}.auth-story-copy p,.auth-story-footer{display:none}.auth-entry{padding:38px 24px 30px}.auth-entry h2{font-size:52px}.auth-entry-inner>p:not(.aviso):not(.salida){font-size:14px}}
    """;
  private static final String PORTADA="""
    <section class="auth-story" aria-label="Boston Bilingual School">
      <div class="auth-logo" aria-label="Boston Bilingual School, Aula Virtual"><div class="auth-logo-letters" aria-hidden="true"><span>B</span><span>B</span><span>S</span></div><div><strong>Boston Bilingual School</strong><small>AULA VIRTUAL</small></div></div>
      <div class="auth-story-copy"><span class="auth-kicker">BIENVENIDO A TU ESPACIO DE APRENDIZAJE</span><h1>APRENDER<br>ES CRECER<span>.</span></h1><p>Tu comunidad escolar, tus clases y tus logros en un solo lugar.</p></div>
      <div class="auth-story-footer"><span>BOSTON BILINGUAL SCHOOL</span><span>TRUJILLO · PERÚ</span></div>
    </section>
    """;
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
      <title>Boston Identidad · Iniciar sesión</title><style>__ESTILOS__</style></head><body><main class="auth-page">__PORTADA__
      <section class="auth-entry" aria-labelledby="auth-title"><div class="auth-entry-inner"><span class="auth-step">02 / IDENTIDAD INSTITUCIONAL</span>
      <h2 id="auth-title">Entra a tu aula<span>.</span></h2><p>Usa tu cuenta institucional para acceder a tus clases y actividades.</p>__AVISO____SALIDA__
      <form class="auth-form" action="/login" method="post"><input type="hidden" name="__CSRF_NAME__" value="__CSRF_TOKEN__">
      <label for="username">Usuario<input id="username" name="username" autocomplete="username" required autofocus></label>
      <label for="password">Contraseña<input id="password" name="password" type="password" autocomplete="current-password" required></label>
      <button type="submit"><span>Iniciar sesión</span><span aria-hidden="true">→</span></button></form>
      <div class="auth-help"><a href="/restablecer">Ya tengo un código de recuperación</a></div></div><div class="auth-entry-footer">AULA VIRTUAL BOSTON</div></section></main></body></html>
      """.replace("__ESTILOS__",ESTILOS).replace("__PORTADA__",PORTADA).replace("__AVISO__",aviso).replace("__SALIDA__",salida)
      .replace("__CSRF_NAME__",csrf.getParameterName()).replace("__CSRF_TOKEN__",csrf.getToken());
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
  }
  @GetMapping(value="/restablecer",produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> formulario(HttpServletRequest request,CsrfToken csrf) {
    String aviso=request.getParameterMap().containsKey("error")?"<p role=\"alert\" class=\"aviso\">Código inválido o vencido. Comprueba también que la nueva contraseña tenga al menos 12 caracteres.</p>":"";
    String html="""
      <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Boston Identidad · Restablecer acceso</title><style>__ESTILOS__</style></head><body><main class="auth-page">__PORTADA__
      <section class="auth-entry" aria-labelledby="auth-title"><div class="auth-entry-inner"><span class="auth-step">03 / RECUPERAR ACCESO</span>
      <h2 id="auth-title">Vuelve a entrar<span>.</span></h2><p>Escribe el código temporal recibido por el canal institucional y establece una nueva contraseña.</p>__AVISO__
      <form class="auth-form" action="/restablecer" method="post"><input type="hidden" name="__CSRF_NAME__" value="__CSRF_TOKEN__">
      <label for="token">Código de recuperación<input id="token" name="token" autocomplete="off" required></label>
      <label for="nuevaClave">Nueva contraseña<input id="nuevaClave" name="nuevaClave" type="password" autocomplete="new-password" minlength="12" required></label>
      <button type="submit"><span>Cambiar contraseña</span><span aria-hidden="true">→</span></button></form>
      <div class="auth-help"><a href="/login">Volver al inicio de sesión</a></div></div><div class="auth-entry-footer">AULA VIRTUAL BOSTON</div></section></main></body></html>
      """.replace("__ESTILOS__",ESTILOS).replace("__PORTADA__",PORTADA).replace("__AVISO__",aviso).replace("__CSRF_NAME__",csrf.getParameterName()).replace("__CSRF_TOKEN__",csrf.getToken());
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
  }
  @PostMapping(value="/restablecer",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> cambiar(@RequestParam String token,@RequestParam String nuevaClave) {
    try { restablecimiento.cambiar(token,nuevaClave);return ResponseEntity.status(303).location(URI.create("/login?restablecida")).build(); }
    catch(IllegalArgumentException error) { return ResponseEntity.status(303).location(URI.create("/restablecer?error")).build(); }
  }
}
