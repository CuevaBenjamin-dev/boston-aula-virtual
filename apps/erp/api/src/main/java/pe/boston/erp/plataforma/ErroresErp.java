package pe.boston.erp.plataforma;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages={"pe.boston.erp.oferta", "pe.boston.erp.matriculas", "pe.boston.erp.asignaciones", "pe.boston.erp.asistencia", "pe.boston.erp.plataforma"})
public class ErroresErp {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String,String>> invalido(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("codigo","DATOS_INVALIDOS","mensaje",e.getMessage()));
  }
  @ExceptionHandler(SecurityException.class)
  ResponseEntity<Map<String,String>> denegado() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("codigo","SIN_PERMISO","mensaje","No tienes permiso para esta operación"));
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String,String>> conflicto() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo","CONFLICTO","mensaje","Ya existe un registro incompatible o hay dependencias vigentes"));
  }
  @ExceptionHandler(pe.boston.erp.asistencia.AsistenciaService.Conflicto.class)
  ResponseEntity<Map<String,String>> asistenciaConflicto(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo","CONFLICTO_ASISTENCIA","mensaje",e.getMessage()));
  }
}
