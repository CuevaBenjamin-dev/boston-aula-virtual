package pe.boston.aula;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/aula/perfil") public class PerfilController {
  private final JdbcTemplate db; private final UsuarioActual usuario; private final ErpCliente erp;
  public PerfilController(JdbcTemplate db,UsuarioActual usuario,ErpCliente erp) { this.db=db;this.usuario=usuario;this.erp=erp; }
  @GetMapping public Map<String,Object> ver(Authentication a) {
    var datos=new LinkedHashMap<String,Object>(usuario.perfil(a));
    UUID cuenta=usuario.cuenta(a);
    var fotos=db.queryForList("SELECT actualizada_en FROM perfiles_aula WHERE cuenta_id=? AND foto IS NOT NULL",cuenta);
    datos.put("fotoActualizada",fotos.isEmpty()?null:fotos.getFirst().get("actualizada_en"));
    String tipo=usuario.tipo(a);
    if("ESTUDIANTE".equals(tipo)||"DOCENTE".equals(tipo)) {
      try {
        var respuesta=erp.get("/api/erp/perfiles/"+tipo+"/"+usuario.referencia(a));
        var institucional=new LinkedHashMap<String,Object>();
        respuesta.fields().forEachRemaining(campo->{var valor=campo.getValue();institucional.put(campo.getKey(),valor.isNull()?null:valor.isNumber()?valor.numberValue():valor.asText());});
        datos.put("datosInstitucionales",institucional);
      }
      catch(Exception e) { datos.put("datosInstitucionalesDisponibles",false); }
    }
    if("COORDINACION".equals(tipo)) datos.put("secciones",db.queryForList("""
      SELECT DISTINCT c.grado,c.seccion,c.periodo FROM cursos c
      JOIN coordinaciones_seccion x ON x.seccion_erp_id=c.seccion_erp_id
      WHERE x.cuenta_id=? AND x.confirmado_erp ORDER BY c.grado,c.seccion,c.periodo
      """,cuenta));
    return datos;
  }
  @GetMapping("/foto") public ResponseEntity<byte[]> foto(Authentication a) {
    var filas=db.queryForList("SELECT foto,foto_tipo FROM perfiles_aula WHERE cuenta_id=? AND foto IS NOT NULL",usuario.cuenta(a));
    if(filas.isEmpty()) return ResponseEntity.notFound().build();
    var fila=filas.getFirst();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType((String)fila.get("foto_tipo"))).body((byte[])fila.get("foto"));
  }
  @PostMapping(value="/foto",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public void subirFoto(@RequestParam("foto") MultipartFile foto,Authentication a) throws Exception {
    if(foto.isEmpty()||foto.getSize()>2L*1024*1024) throw new IllegalArgumentException("La foto debe pesar entre 1 byte y 2 MB");
    String tipo=foto.getContentType();
    if(!"image/jpeg".equals(tipo)&&!"image/png".equals(tipo)) throw new IllegalArgumentException("Usa una foto JPG o PNG");
    byte[] bytes=foto.getBytes();
    boolean firma="image/png".equals(tipo)?bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71:bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
    if(!firma) throw new IllegalArgumentException("El archivo no corresponde a la imagen indicada");
    var imagen=ImageIO.read(new ByteArrayInputStream(bytes));
    if(imagen==null||imagen.getWidth()>4096||imagen.getHeight()>4096) throw new IllegalArgumentException("Imagen inválida o demasiado grande");
    db.update("""
      INSERT INTO perfiles_aula(cuenta_id,foto,foto_tipo) VALUES (?,?,?)
      ON CONFLICT(cuenta_id) DO UPDATE SET foto=EXCLUDED.foto,foto_tipo=EXCLUDED.foto_tipo,actualizada_en=now()
      """,usuario.cuenta(a),bytes,tipo);
  }
}
