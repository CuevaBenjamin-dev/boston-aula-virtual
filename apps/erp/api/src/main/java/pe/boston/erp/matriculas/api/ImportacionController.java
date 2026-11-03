package pe.boston.erp.matriculas.api;

import pe.boston.erp.matriculas.ImportacionMatriculas;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/erp/v1/importaciones")
public class ImportacionController {
  private final ImportacionMatriculas servicio;
  public ImportacionController(ImportacionMatriculas servicio) { this.servicio=servicio; }
  public record Confirmacion(boolean confirmacionExplicita) {}
  @GetMapping("/plantilla") public ResponseEntity<byte[]> plantilla(@AuthenticationPrincipal Jwt actor) throws IOException {
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=plantilla-matriculas-boston.xlsx")
      .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
      .body(servicio.plantilla(actor));
  }
  @PostMapping(value="/previsualizar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String,Object> previsualizar(@RequestPart("archivo") MultipartFile archivo,@AuthenticationPrincipal Jwt actor) throws IOException {
    return servicio.previsualizar(archivo,actor);
  }
  @PostMapping("/{id}/confirmar") public Map<String,Object> confirmar(@PathVariable UUID id,@RequestBody Confirmacion d,@AuthenticationPrincipal Jwt actor) {
    return servicio.confirmar(id,d.confirmacionExplicita(),actor);
  }
}
