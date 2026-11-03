package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class YoutubeUrlTest {
  @Test void aceptaEnlacesWebDeCualquierProveedor() {
    for(String entrada:new String[]{
      "https://www.youtube.com/watch?v=AbC_d-12345&t=10",
      "https://video.escuela.test/clase/parte-1",
      "http://otro-servidor.test/recurso?id=2"
    }) assertEquals(entrada,YoutubeUrl.normalizar(" "+entrada+" "));
  }

  @Test void rechazaEnlacesNoNavegables() {
    for(String entrada:new String[]{"", "no-es-url", "javascript:alert(1)",
      "file:///videos/clase.mp4", "https://usuario:clave@video.test/clase", "https://video.test/\ntexto"
    }) assertThrows(IllegalArgumentException.class,()->YoutubeUrl.normalizar(entrada));
  }
}
