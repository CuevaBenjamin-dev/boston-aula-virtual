package pe.boston.erp.arquitectura;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import pe.boston.erp.ErpApplication;

class ArquitecturaModularTest {
  @Test
  void detectaSoloCincoModulosYVerificaSusLimites() {
    System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    var modulos = ApplicationModules.of(ErpApplication.class).verify();
    Set<String> nombres = modulos.stream()
      .map(modulo -> modulo.getIdentifier().toString())
      .collect(Collectors.toSet());
    assertEquals(Set.of("personas", "matriculas", "oferta", "asignaciones", "documentos"), nombres);
  }
}
