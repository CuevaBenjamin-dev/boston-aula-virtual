package pe.boston.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Compara la forma HTTP de los adaptadores heredado y V1 que usa Aula. */
class IntegracionV1ContratoTest {
  @Test void lecturasDeAulaConservanStatusYJson() throws Exception {
    var servicio = mock(Institucional.class);
    UUID cuenta = UUID.randomUUID();
    UUID curso = UUID.randomUUID();
    UUID perfil = UUID.randomUUID();
    when(servicio.eventos(7, 20)).thenReturn(List.of(Map.of("secuencia", 8, "tipo", "OFERTA_ACTUALIZADA")));
    when(servicio.docentesResumen(null)).thenReturn(List.of(Map.of("id", cuenta, "nombres", "Docente")));
    when(servicio.estudiantesResumenCurso(curso, null)).thenReturn(List.of(Map.of("id", perfil)));
    when(servicio.perfil("estudiante", perfil, null)).thenReturn(Map.of("id", perfil, "estado", "ACTIVO"));
    when(servicio.ambitos(cuenta, null)).thenReturn(List.of(Map.of("seccion_id", UUID.randomUUID())));
    var principalNulo = new HandlerMethodArgumentResolver() {
      @Override public boolean supportsParameter(MethodParameter parametro) {
        return parametro.hasParameterAnnotation(AuthenticationPrincipal.class);
      }
      @Override public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
          NativeWebRequest solicitud, org.springframework.web.bind.support.WebDataBinderFactory binder) {
        return null;
      }
    };
    var mvc = MockMvcBuilders.standaloneSetup(new InstitucionalController(servicio), new IntegracionV1Controller(servicio))
      .setCustomArgumentResolvers(principalNulo).build();
    for (String recurso : List.of("/eventos?despues=7&limite=20", "/docentes/resumen",
        "/cursos/" + curso + "/estudiantes/resumen", "/perfiles/estudiante/" + perfil,
        "/coordinaciones/" + cuenta + "/ambitos")) {
      var anterior = mvc.perform(get("/api/erp" + recurso)).andExpect(status().isOk()).andReturn().getResponse();
      var v1 = mvc.perform(get("/api/erp/v1" + recurso)).andExpect(status().isOk()).andReturn().getResponse();
      assertEquals(anterior.getContentType(), v1.getContentType(), recurso);
      assertEquals(anterior.getContentAsString(), v1.getContentAsString(), recurso);
    }
    verify(servicio, times(2)).eventos(7, 20);
  }
}
