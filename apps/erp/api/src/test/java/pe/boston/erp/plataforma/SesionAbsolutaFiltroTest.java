package pe.boston.erp.plataforma;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.*;

class SesionAbsolutaFiltroTest {
  @Test void invalidaUnaSesionQueSuperoElLimiteAbsoluto() throws Exception {
    var request = new MockHttpServletRequest();
    HttpSession session = mock(HttpSession.class);
    when(session.getCreationTime()).thenReturn(System.currentTimeMillis() - 120_000);
    request.setSession(session);
    new SesionAbsolutaFiltro(60).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    verify(session).invalidate();
  }

  @Test void conservaUnaSesionDentroDelLimite() throws Exception {
    var request = new MockHttpServletRequest();
    HttpSession session = mock(HttpSession.class);
    when(session.getCreationTime()).thenReturn(System.currentTimeMillis());
    request.setSession(session);
    new SesionAbsolutaFiltro(60).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    verify(session, never()).invalidate();
  }
}
