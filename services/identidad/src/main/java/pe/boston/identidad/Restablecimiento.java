package pe.boston.identidad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Restablecimiento {
  private final JdbcTemplate db; private final PasswordEncoder encoder;
  public Restablecimiento(JdbcTemplate db,PasswordEncoder encoder) { this.db=db;this.encoder=encoder; }
  @Transactional public void cambiar(String token,String nuevaClave) {
    if(token==null||token.isBlank()||nuevaClave==null||nuevaClave.length()<12) throw new IllegalArgumentException("Código o contraseña inválidos");
    String huella=hash(token);
    UUID id=db.query("SELECT cuenta_id FROM recuperaciones WHERE token_hash=? AND usado_en IS NULL AND vence_en>now() FOR UPDATE",(rs,n)->(UUID)rs.getObject(1),huella).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Código inválido o vencido"));
    db.update("UPDATE cuentas SET clave_hash=?,intentos_fallidos=0,bloqueado_hasta=NULL,sesion_version=sesion_version+1 WHERE id=?",encoder.encode(nuevaClave),id);
    db.update("UPDATE recuperaciones SET usado_en=now() WHERE token_hash=?",huella);
    db.update("DELETE FROM oauth2_authorization WHERE principal_name=(SELECT usuario FROM cuentas WHERE id=?)",id);
    db.update("INSERT INTO auditoria_seguridad(id,cuenta_id,accion) VALUES (?,?,?)",UUID.randomUUID(),id,"CLAVE_RESTABLECIDA");
  }
  public static String hash(String token) {
    try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch(Exception e) { throw new IllegalStateException(e); }
  }
}
