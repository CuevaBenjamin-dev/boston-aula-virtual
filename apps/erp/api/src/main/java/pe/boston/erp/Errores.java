package pe.boston.erp;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice public class Errores {
  @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> prohibido(AccessDeniedException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error",e.getMessage())); }
  @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalido(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage())); }
  @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<?> duplicado(DuplicateKeyException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Registro duplicado")); }
  @ExceptionHandler(EmptyResultDataAccessException.class) public ResponseEntity<?> ausente(EmptyResultDataAccessException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Registro inexistente")); }
}
