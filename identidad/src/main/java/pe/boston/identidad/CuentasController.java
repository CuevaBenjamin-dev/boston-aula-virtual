package pe.boston.identidad;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/identidad") public class CuentasController {
  private final JdbcTemplate db; private final PasswordEncoder encoder; private final Restablecimiento restablecimiento; private final SecureRandom aleatorio=new SecureRandom();
  public CuentasController(JdbcTemplate db,PasswordEncoder encoder,Restablecimiento restablecimiento) { this.db=db;this.encoder=encoder;this.restablecimiento=restablecimiento; }
  public record Nueva(String usuario,String claveInicial,String tipo,UUID referenciaErp,boolean developer) {}
  public record Suspension(boolean activo) {}
  public record CambioClave(String token,String nuevaClave) {}
  public record CambioPropio(String claveActual,String nuevaClave) {}
  @GetMapping("/directorio") public List<Map<String,Object>> directorio() {
    return db.queryForList("SELECT id,usuario,tipo,referencia_erp FROM cuentas WHERE activo=TRUE ORDER BY usuario");
  }
  @PostMapping("/mi-clave") @Transactional public void cambiarMiClave(@RequestBody CambioPropio d,@AuthenticationPrincipal Jwt actor) {
    if(actor==null||actor.getClaimAsString("cuenta_id")==null) throw new AccessDeniedException("Cuenta requerida");
    if(d.claveActual()==null||d.nuevaClave()==null||d.nuevaClave().length()<12) throw new IllegalArgumentException("Contraseña inválida; usa al menos 12 caracteres");
    UUID id=UUID.fromString(actor.getClaimAsString("cuenta_id"));
    var actual=db.queryForMap("SELECT clave_hash,activo FROM cuentas WHERE id=? FOR UPDATE",id);
    if(!Boolean.TRUE.equals(actual.get("activo"))) throw new AccessDeniedException("Cuenta suspendida");
    if(!encoder.matches(d.claveActual(),(String)actual.get("clave_hash"))) throw new IllegalArgumentException("Contraseña actual incorrecta");
    db.update("UPDATE cuentas SET clave_hash=?,intentos_fallidos=0,bloqueado_hasta=NULL,sesion_version=sesion_version+1 WHERE id=?",encoder.encode(d.nuevaClave()),id);
    db.update("DELETE FROM oauth2_authorization WHERE principal_name=(SELECT usuario FROM cuentas WHERE id=?)",id);
    auditar(id,"CLAVE_CAMBIADA");
  }
  @GetMapping("/cuentas") public List<Map<String,Object>> listar() { return db.queryForList("SELECT id,usuario,tipo,referencia_erp,activo,developer,tesoreria,creado_en FROM cuentas ORDER BY creado_en DESC LIMIT 200"); }
  @GetMapping("/estado/{id}") public Map<String,Object> activo(@PathVariable UUID id) {
    var encontrados=db.query("SELECT activo,sesion_version FROM cuentas WHERE id=?",(rs,n)->Map.<String,Object>of("activo",rs.getBoolean("activo"),"sesionVersion",rs.getInt("sesion_version")),id);
    return encontrados.isEmpty()?Map.of("activo",false,"sesionVersion",-1):encontrados.getFirst();
  }
  @PostMapping("/cuentas") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,UUID> crear(@RequestBody Nueva d) {
    if(d.usuario()==null||!d.usuario().matches("[a-z0-9._-]{4,120}")||d.claveInicial()==null||d.claveInicial().length()<12||!List.of("ESTUDIANTE","DOCENTE","COORDINACION","ADMINISTRADOR").contains(d.tipo())) throw new IllegalArgumentException("Cuenta inválida");
    if(("ESTUDIANTE".equals(d.tipo())||"DOCENTE".equals(d.tipo()))&&d.referenciaErp()==null) throw new IllegalArgumentException("Vínculo ERP requerido");
    if(d.developer()&&!"ADMINISTRADOR".equals(d.tipo())) throw new IllegalArgumentException("Developer requiere administración");
    UUID id=UUID.randomUUID(); db.update("INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,developer) VALUES (?,?,?,?,?,?)",id,d.usuario(),encoder.encode(d.claveInicial()),d.tipo(),d.referenciaErp(),d.developer());
    auditar(id,"CUENTA_CREADA"); return Map.of("id",id);
  }
  @PutMapping("/cuentas/{id}/estado") public void estado(@PathVariable UUID id,@RequestBody Suspension d) {
    db.update("UPDATE cuentas SET activo=?,sesion_version=sesion_version+1 WHERE id=?",d.activo(),id); auditar(id,d.activo()?"CUENTA_ACTIVADA":"CUENTA_SUSPENDIDA");
    if(!d.activo()) db.update("DELETE FROM oauth2_authorization WHERE principal_name=(SELECT usuario FROM cuentas WHERE id=?)",id);
  }
  @PostMapping("/cuentas/{id}/recuperacion") public Map<String,String> recuperacion(@PathVariable UUID id) {
    byte[] bytes=new byte[32]; aleatorio.nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("INSERT INTO recuperaciones(id,cuenta_id,token_hash,vence_en) VALUES (?,?,?,now()+interval '30 minutes')",UUID.randomUUID(),id,Restablecimiento.hash(token));
    auditar(id,"RECUPERACION_CREADA"); return Map.of("token",token,"aviso","Mostrar una sola vez al titular; vence en 30 minutos");
  }
  @PostMapping("/restablecer") public void restablecer(@RequestBody CambioClave d) { restablecimiento.cambiar(d.token(),d.nuevaClave()); }
  private void auditar(UUID id,String accion) { db.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) VALUES (?,?,?)",UUID.randomUUID(),id,accion); }
}
