package pe.boston.erp.plataforma;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cierra una sesión vencida incluso si hubo actividad reciente. */
final class SesionAbsolutaFiltro extends OncePerRequestFilter {
  private final long maxMillis;

  SesionAbsolutaFiltro(long seconds) {
    if (seconds <= 0) throw new IllegalArgumentException("Duración absoluta inválida");
    maxMillis = Math.multiplyExact(seconds, 1000L);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session != null && System.currentTimeMillis() - session.getCreationTime() >= maxMillis) {
      session.invalidate();
    }
    chain.doFilter(request, response);
  }
}
