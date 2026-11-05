package pe.boston.erp.personas.api;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.boston.erp.personas.application.AutorizacionPersonas;
import pe.boston.erp.personas.application.PersonasServicio;

@RestControllerAdvice(assignableTypes=PersonasController.class)
public class PersonasErrores {
  private ResponseEntity<Map<String,String>> error(HttpStatus estado,String codigo,String mensaje) {
    return ResponseEntity.status(estado).body(Map.of("codigo",codigo,"mensaje",mensaje));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String,String>> invalido(IllegalArgumentException e) {
    return error(HttpStatus.BAD_REQUEST,"DATOS_INVALIDOS",e.getMessage());
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String,String>> formato() {
    return error(HttpStatus.BAD_REQUEST,"FORMATO_INVALIDO","Revisa los datos enviados");
  }
  @ExceptionHandler(PersonasServicio.NoEncontrado.class)
  public ResponseEntity<Map<String,String>> noEncontrado(PersonasServicio.NoEncontrado e) {
    return error(HttpStatus.NOT_FOUND,"NO_ENCONTRADO",e.getMessage());
  }
  @ExceptionHandler(AutorizacionPersonas.AccesoDenegado.class)
  public ResponseEntity<Map<String,String>> denegado() {
    return error(HttpStatus.FORBIDDEN,"SIN_PERMISO","No tienes permiso para esta operación");
  }
  @ExceptionHandler({PersonasServicio.Conflicto.class,DataIntegrityViolationException.class})
  public ResponseEntity<Map<String,String>> conflicto() {
    return error(HttpStatus.CONFLICT,"CONFLICTO","La operación contradice un registro o vigencia existente");
  }
}
