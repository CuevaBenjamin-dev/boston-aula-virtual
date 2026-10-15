package pe.boston.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class YoutubeUrlTest {
  @Test void normalizaFormatosOficiales() {
    String esperado="https://www.youtube.com/watch?v=AbC_d-12345";
    for(String entrada:new String[]{
      "https://www.youtube.com/watch?v=AbC_d-12345&t=10",
      "https://youtu.be/AbC_d-12345",
      "https://www.youtube.com/shorts/AbC_d-12345",
      "https://www.youtube.com/live/AbC_d-12345",
      "https://www.youtube-nocookie.com/embed/AbC_d-12345"
    }) assertEquals(esperado,YoutubeUrl.normalizar(entrada).url());
  }

  @Test void rechazaDominiosYSintaxisFalsos() {
    for(String entrada:new String[]{
      "http://youtube.com/watch?v=AbC_d-12345",
      "https://youtube.com.ejemplo.test/watch?v=AbC_d-12345",
      "https://youtube.com@ejemplo.test/watch?v=AbC_d-12345",
      "https://youtu.be/no-es-id",
      "https://youtube.com/watch?v=AbC_d-12345#fragmento",
      "https://youtube.com:444/watch?v=AbC_d-12345"
    }) assertThrows(IllegalArgumentException.class,()->YoutubeUrl.normalizar(entrada));
  }
}
