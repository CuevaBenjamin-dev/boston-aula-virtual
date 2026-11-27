package pe.boston.aula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.boston.aula.integraciones.ErpCliente;

/** Fachada autenticada; no persiste ni calcula asistencia en Aula. */
@RestController @RequestMapping("/api/aula/asistencia")
public class AsistenciaController {
  private final UsuarioActual usuario;
  private final ErpCliente erp;
  private final ObjectMapper json=new ObjectMapper();
  public AsistenciaController(UsuarioActual usuario,ErpCliente erp) { this.usuario=usuario;this.erp=erp; }
  private void rol(Authentication a,String tipo) {
    if(!tipo.equals(usuario.tipo(a))) throw new org.springframework.security.access.AccessDeniedException("Perfil sin acceso a Asistencia");
  }
  private byte[] enviar(String metodo,String ruta,byte[] cuerpo,String tipo,Authentication a) throws Exception {
    return erp.asistencia(metodo,ruta,cuerpo,tipo,usuario.cuenta(a).toString(),usuario.tipo(a),usuario.referencia(a).toString());
  }
  private JsonNode llamada(String metodo,String ruta,Object cuerpo,Authentication a) throws Exception {
    byte[] datos=cuerpo==null?null:json.writeValueAsBytes(cuerpo);
    byte[] respuesta=enviar(metodo,ruta,datos,"application/json",a);
    return respuesta.length==0?json.createObjectNode():json.readTree(respuesta);
  }
  @GetMapping("/hoy/secciones") public JsonNode secciones(Authentication a) throws Exception {
    rol(a,"DOCENTE");return llamada("GET","/secciones",null,a);
  }
  @GetMapping("/hoy/secciones/{id}") public JsonNode jornada(@PathVariable UUID id,Authentication a) throws Exception {
    rol(a,"DOCENTE");return llamada("GET","/jornadas/"+id,null,a);
  }
  @PutMapping("/hoy/secciones/{id}/avance") public JsonNode avance(@PathVariable UUID id,@RequestBody JsonNode p,Authentication a) throws Exception {
    rol(a,"DOCENTE");return llamada("PUT","/jornadas/"+id+"/avance",p,a);
  }
  @PostMapping("/hoy/secciones/{id}/publicar") public JsonNode publicar(@PathVariable UUID id,@RequestBody JsonNode p,Authentication a) throws Exception {
    rol(a,"DOCENTE");return llamada("POST","/jornadas/"+id+"/publicar",p,a);
  }
  @GetMapping("/mi-asistencia") public JsonNode miAsistencia(@RequestParam int anio,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/mi-asistencia?anio="+anio,null,a);
  }
  @GetMapping("/mis-anios") public JsonNode misAnios(Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/mis-anios",null,a);
  }
  @GetMapping("/mis-estadisticas") public JsonNode estadisticas(@RequestParam int anio,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/mis-estadisticas?anio="+anio,null,a);
  }
  @GetMapping("/mis-justificaciones") public JsonNode justificaciones(Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/mis-justificaciones",null,a);
  }
  @PostMapping("/justificaciones/{registro}") public JsonNode solicitar(@PathVariable UUID registro,@RequestBody JsonNode p,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("POST","/justificaciones/"+registro,p,a);
  }
  @PostMapping("/justificaciones/{id}/complementar") public JsonNode complementar(@PathVariable UUID id,@RequestBody JsonNode p,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("POST","/justificaciones/"+id+"/complementar",p,a);
  }
  @PostMapping(value="/justificaciones/{id}/adjuntos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public JsonNode adjuntar(@PathVariable UUID id,@RequestPart("archivo") MultipartFile archivo,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");
    String borde="ast"+UUID.randomUUID().toString().replace("-","");
    String nombre=archivo.getOriginalFilename()==null?"adjunto":archivo.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]","_");
    byte[] encabezado=("--"+borde+"\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\""+nombre+"\"\r\n"
      +"Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] finaliza=("\r\n--"+borde+"--\r\n").getBytes(StandardCharsets.UTF_8);
    if(archivo.getSize()>2_000_000) throw new IllegalArgumentException("Archivo mayor a 2 MB");
    byte[] contenido=archivo.getBytes();
    byte[] envio=new byte[encabezado.length+contenido.length+finaliza.length];
    System.arraycopy(encabezado,0,envio,0,encabezado.length);
    System.arraycopy(contenido,0,envio,encabezado.length,contenido.length);
    System.arraycopy(finaliza,0,envio,encabezado.length+contenido.length,finaliza.length);
    return json.readTree(enviar("POST","/justificaciones/"+id+"/adjuntos",envio,
      "multipart/form-data; boundary="+borde,a));
  }
  @GetMapping("/justificaciones/{id}/adjuntos") public JsonNode adjuntos(@PathVariable UUID id,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/justificaciones/"+id+"/adjuntos",null,a);
  }
  @GetMapping("/adjuntos/{id}") public ResponseEntity<byte[]> descargar(@PathVariable UUID id,Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return ResponseEntity.ok().header("Cache-Control","no-store")
      .contentType(MediaType.APPLICATION_OCTET_STREAM).body(enviar("GET","/adjuntos/"+id,null,null,a));
  }
  @GetMapping("/mis-salidas") public JsonNode salidas(Authentication a) throws Exception {
    rol(a,"ESTUDIANTE");return llamada("GET","/mis-salidas",null,a);
  }
}
