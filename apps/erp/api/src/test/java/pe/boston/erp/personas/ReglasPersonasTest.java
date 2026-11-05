package pe.boston.erp.personas;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.boston.erp.personas.domain.ReglasPersonas;

class ReglasPersonasTest {
  @Test void documentoConservaCaracteresInternosYValidaDni() {
    assertEquals("AB- 123",ReglasPersonas.documento("PASAPORTE","PE"," AB- 123 "));
    assertEquals("01234567",ReglasPersonas.documento("DNI","PE"," 01234567 "));
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.documento("DNI","PE","1234567"));
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.documento("DNI","US","01234567"));
  }

  @Test void condicionesYContactosSegunRol() {
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.condicion("DESCONOCIDA"));
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.contactos("DOCENTE",null,"a@b.test"));
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.contactos("APODERADO",null,null));
    assertDoesNotThrow(()->ReglasPersonas.contactos("ESTUDIANTE",null,null));
    assertThrows(IllegalArgumentException.class,()->ReglasPersonas.nacimiento(LocalDate.now().plusDays(1)));
  }
}
