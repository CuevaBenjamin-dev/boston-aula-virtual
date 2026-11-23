package pe.boston.erp.oferta.api;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CodigoAsignaturaTest {
  @Test void generaCodigoAnualYConservaCompatibilidad() {
    assertEquals("PR-01-AC-27",CodigoAsignatura.normalizar(2027,"PRIMARIA",1,"ac"));
    assertEquals("PR-01-ING-26",CodigoAsignatura.normalizar(2026,"PRIMARIA",1,"PR-01-ING"));
    assertEquals("PR-03-MAT-27",CodigoAsignatura.normalizar(2027,"PRIMARIA",3,"PR-03-MAT-27"));
    assertEquals("SE-05-DPCC-27",CodigoAsignatura.normalizar(2027,"SECUNDARIA",5,"DPCC"));
    assertEquals("PR-01-MAT-00",CodigoAsignatura.normalizar(2000,"PRIMARIA",1,"MAT"));
  }
  @Test void rechazaInconsistencias() {
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"PRIMARIA",1,"PR-01-MAT-26"));
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"PRIMARIA",1,"SE-01-MAT"));
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"PRIMARIA",1,"PR-02-MAT"));
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"PRIMARIA",1,""));
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"SECUNDARIA",6,"MAT"));
    assertThrows(IllegalArgumentException.class,()->CodigoAsignatura.normalizar(2027,"PRIMARIA",1,"A".repeat(21)));
  }
}
