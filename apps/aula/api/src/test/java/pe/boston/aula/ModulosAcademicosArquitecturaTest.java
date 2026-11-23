package pe.boston.aula;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ModulosAcademicosArquitecturaTest {
  @Test void serviciosExtraidosNoDependanDeControladoresNiFachadas() {
    var clases=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
      .importPackages("pe.boston.aula");
    var servicios=noClasses().that().haveNameMatching(
      "pe\\.boston\\.aula\\.(PlanificacionClases|ValidacionArchivosAcademicos|ConsultaAcademica|CalificacionesTarea)");
    servicios.should().dependOnClassesThat().haveNameMatching(
      "pe\\.boston\\.aula\\.(CursosYTareas|ArchivosAcademicos|.*Controller)").check(clases);
    servicios.should().dependOnClassesThat().resideInAPackage("..integraciones..").check(clases);
  }
}
