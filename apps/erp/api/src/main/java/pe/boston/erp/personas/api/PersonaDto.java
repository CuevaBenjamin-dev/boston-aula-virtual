package pe.boston.erp.personas.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PersonaDto {
  private PersonaDto() {}

  public record DocumentoEntrada(String tipo, String pais, String numero) {}
  public record ProvisionalEntrada(String motivo, String sustentoTipo, String sustentoReferencia) {}
  public record AutorizacionEntrada(String autorizador, String medio, Instant fecha, String sustentoTipo,
    String sustentoReferencia) {}
  public record ApoderamientoEntrada(UUID estudianteId, UUID apoderadoId, boolean sinParentesco,
    AutorizacionEntrada autorizacion) {}
  public record CrearPersona(String nombres, String apellidos, LocalDate fechaNacimiento,
    String sexo, String nacionalidad, String telefono, String correo, String direccion,
    String condicion, DocumentoEntrada documento, ProvisionalEntrada provisional,
    ApoderamientoEntrada apoderamientoInicial, String parentescoInicialTipo) {}
  public record EditarPersona(String nombres, String apellidos, LocalDate fechaNacimiento,
    String sexo, String nacionalidad, String telefono, String correo, String direccion) {}
  public record Contacto(String telefono, String correo, String direccion) {}
  public record NuevaCondicion(String tipo) {}
  public record Motivo(String motivo) {}
  public record ParentescoEntrada(UUID estudianteId, UUID familiarId, String tipo) {}
  public record TipoDocumento(String codigo, String nombre, String patron, boolean activo) {}
  public record Documento(UUID id, String tipo, String pais, String numero, boolean principal,
    Instant vigenteDesde, Instant vigenteHasta) {}
  public record Condicion(UUID id, String tipo, Instant vigenteDesde, Instant vigenteHasta, String motivoDesactivacion) {}
  public record Parentesco(UUID id, UUID estudianteId, UUID familiarId, String tipo,
    Instant vigenteDesde, Instant vigenteHasta) {}
  public record Apoderamiento(UUID id, UUID estudianteId, UUID apoderadoId, boolean sinParentesco,
    String autorizador, String medio, Instant verificadoEn, Instant vigenteDesde, Instant vigenteHasta) {}
  public record Provisional(String motivo, String sustentoTipo, String sustentoReferencia,
    Instant registradaEn, Instant regularizadaEn) {}
  public record Persona(UUID id, String nombres, String apellidos, LocalDate fechaNacimiento,
    String sexo, String nacionalidad, String telefono, String correo, String direccion,
    List<Documento> documentos, List<Condicion> condiciones, Provisional provisional,
    List<Parentesco> parentescos, List<Apoderamiento> apoderamientos) {}
  public record Resumen(UUID id, String nombres, String apellidos, String documento,
    List<String> condiciones) {}
  public record Pagina(List<Resumen> resultados, long total, int pagina, int tamano) {}
  public record Capacidades(boolean registrar, boolean consultar, boolean editarContacto,
    boolean corregirIdentidad, boolean gestionarVinculos, boolean gestionarCondiciones,
    boolean accesoDocentePendiente, UUID personaPropiaId) {}
}
