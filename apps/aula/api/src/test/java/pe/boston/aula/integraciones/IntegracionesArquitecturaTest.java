package pe.boston.aula.integraciones;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import pe.boston.aula.UsuarioActual;

class IntegracionesArquitecturaTest {
  private final ArchRule limite=noClasses().that().resideInAPackage("..integraciones..")
    .should().dependOnClassesThat().resideInAPackage("pe.boston.aula");

  @Test void integracionesNoDependeDeClasesInternasDeOtrosModulos() {
    var produccion=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
      .importPackages("pe.boston.aula");
    limite.check(produccion);
  }

  @Test void elLimiteDetectaUnaDependenciaProhibidaReal() {
    var infraccion=new ClassFileImporter().importClasses(DependenciaProhibida.class,UsuarioActual.class);
    assertThrows(AssertionError.class,()->limite.check(infraccion));
  }

  private static final class DependenciaProhibida {
    private final UsuarioActual usuario=null;
    UsuarioActual usuario() { return usuario; }
  }
}
