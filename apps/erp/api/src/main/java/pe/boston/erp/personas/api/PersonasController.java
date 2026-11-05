package pe.boston.erp.personas.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.personas.api.PersonaDto.*;
import pe.boston.erp.personas.application.PersonasServicio;

@RestController
@RequestMapping("/api/erp/v1/personas")
public class PersonasController {
  private final PersonasServicio servicio;
  public PersonasController(PersonasServicio servicio) { this.servicio=servicio; }

  @GetMapping("/capacidades") public Capacidades capacidades(Authentication actor) { return servicio.capacidades(actor); }
  @GetMapping("/tipos-documento") public List<TipoDocumento> tipos(Authentication actor) { return servicio.tipos(actor); }
  @GetMapping("/duplicado-documental") public java.util.Map<String,Boolean> duplicado(
      @RequestParam String tipo,@RequestParam String pais,@RequestParam String numero,Authentication actor) {
    return java.util.Map.of("existe",servicio.duplicadoDocumental(new DocumentoEntrada(tipo,pais,numero),actor));
  }
  @PostMapping("/tipos-documento") public ResponseEntity<Void> crearTipo(@RequestBody TipoDocumento p, Authentication actor) {
    servicio.crearTipo(p,actor); return ResponseEntity.noContent().build();
  }
  @GetMapping public Pagina buscar(@RequestParam(required=false) String buscar,
      @RequestParam(required=false) String condicion, @RequestParam(required=false) Boolean vigente,
      @RequestParam(defaultValue="0") int pagina, @RequestParam(defaultValue="15") int tamano,
      Authentication actor) {
    return servicio.buscar(buscar,condicion,vigente,pagina,tamano,actor);
  }
  @PostMapping public ResponseEntity<Void> registrar(@RequestBody CrearPersona p, Authentication actor) {
    UUID id=servicio.registrar(p,actor);
    return ResponseEntity.created(URI.create("/api/erp/v1/personas/"+id)).build();
  }
  @GetMapping("/{id}") public Persona ficha(@PathVariable UUID id, Authentication actor) {
    return servicio.ficha(id,actor);
  }
  @PutMapping("/{id}") public ResponseEntity<Void> editar(@PathVariable UUID id,
      @RequestBody EditarPersona p, Authentication actor) {
    servicio.editar(id,p,actor); return ResponseEntity.noContent().build();
  }
  @PutMapping("/{id}/contacto") public ResponseEntity<Void> contacto(@PathVariable UUID id,
      @RequestBody Contacto p, Authentication actor) {
    servicio.contacto(id,p,actor); return ResponseEntity.noContent().build();
  }
  @PostMapping("/{id}/documentos") public ResponseEntity<Void> documento(@PathVariable UUID id,
      @RequestBody DocumentoEntrada p, Authentication actor) {
    servicio.documento(id,p,actor); return ResponseEntity.noContent().build();
  }
  @PostMapping("/{id}/condiciones") public ResponseEntity<Void> condicion(@PathVariable UUID id,
      @RequestBody NuevaCondicion p, Authentication actor) {
    servicio.condicion(id,p,actor); return ResponseEntity.noContent().build();
  }
  @PostMapping("/{id}/condiciones/{condicion}/desactivar") public ResponseEntity<Void> desactivarCondicion(
      @PathVariable UUID id,@PathVariable UUID condicion,@RequestBody Motivo p,Authentication actor) {
    servicio.desactivarCondicion(id,condicion,p,actor); return ResponseEntity.noContent().build();
  }
  @PostMapping("/parentescos") public ResponseEntity<Void> parentesco(@RequestBody ParentescoEntrada p,Authentication actor) {
    UUID id=servicio.parentesco(p,actor); return ResponseEntity.created(URI.create("/api/erp/v1/personas/parentescos/"+id)).build();
  }
  @PostMapping("/{id}/parentescos/{vinculo}/desactivar") public ResponseEntity<Void> desactivarParentesco(
      @PathVariable UUID id,@PathVariable UUID vinculo,Authentication actor) {
    servicio.desactivarParentesco(id,vinculo,actor); return ResponseEntity.noContent().build();
  }
  @PostMapping("/apoderamientos") public ResponseEntity<Void> apoderamiento(@RequestBody ApoderamientoEntrada p,Authentication actor) {
    UUID id=servicio.apoderamiento(p,actor); return ResponseEntity.created(URI.create("/api/erp/v1/personas/apoderamientos/"+id)).build();
  }
  @PostMapping("/{id}/apoderamientos/{vinculo}/desactivar") public ResponseEntity<Void> desactivarApoderamiento(
      @PathVariable UUID id,@PathVariable UUID vinculo,@RequestBody Motivo p,Authentication actor) {
    servicio.desactivarApoderamiento(id,vinculo,p,actor); return ResponseEntity.noContent().build();
  }
}
