package pe.boston.identidad;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DatosIniciales {
  @Bean CommandLineRunner cuentasIniciales(JdbcTemplate jdbc, PasswordEncoder encoder,
      @Value("${BOSTON_CUENTAS_INICIALES_FILE:}") String archivo) {
    return args -> {
      if(archivo.isBlank()) return;
      List<String> lineas=Files.readAllLines(Path.of(archivo),StandardCharsets.UTF_8);
      if(lineas.size()<2 || !lineas.get(0).equals("usuario\tclave\ttipo\treferencia_erp\tnivel\tgrado\tseccion"))
        throw new IllegalStateException("Archivo de cuentas iniciales incompleto");
      Set<String> tipos=Set.of("ESTUDIANTE","COORDINACION","DIRECCION","ADMINISTRADOR");
      for(String linea:lineas.subList(1,lineas.size())) {
        String[] c=linea.split("\t",-1);
        if(c.length!=7 || c[0].isBlank() || c[1].length()<16 || !tipos.contains(c[2]))
          throw new IllegalStateException("Cuenta inicial inválida");
        UUID referencia=c[3].isBlank()?null:UUID.fromString(c[3]);
        if((c[2].equals("ESTUDIANTE")) != (referencia!=null))
          throw new IllegalStateException("Referencia ERP de cuenta inicial inválida");
        if(jdbc.queryForObject("SELECT count(*) FROM cuentas WHERE usuario=?",Long.class,c[0])>0) continue;
        jdbc.update("INSERT INTO cuentas(id,usuario,clave_hash,tipo,referencia_erp,developer,tesoreria) VALUES (?,?,?,?,?,?,?)",
          UUID.randomUUID(),c[0],encoder.encode(c[1]),c[2],referencia,
          c[2].equals("ADMINISTRADOR"),c[2].equals("ADMINISTRADOR"));
      }
    };
  }
}
