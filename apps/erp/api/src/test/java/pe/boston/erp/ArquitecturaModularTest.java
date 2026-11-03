package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArquitecturaModularTest {
  @Test
  void verificaLimitesYModulosDelErp() {
    var modulos = ApplicationModules.of(ErpApplication.class).verify();
    Set<String> nombres = modulos.stream().map(m -> m.getIdentifier().toString()).collect(Collectors.toSet());
    assertTrue(nombres.containsAll(Set.of("personas", "matriculas", "oferta", "asignaciones", "documentos")), nombres.toString());
  }
}
