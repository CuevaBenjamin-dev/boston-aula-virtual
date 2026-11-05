package pe.boston.erp.personas.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.boston.erp.personas.api.PersonaDto.*;
import pe.boston.erp.personas.PersonasParaMatriculas;
import pe.boston.erp.personas.domain.ReglasPersonas;
import pe.boston.erp.personas.infrastructure.PersonasRepositorio;

@Service
public class PersonasServicio implements PersonasParaMatriculas {
  private final PersonasRepositorio repositorio;
  private final AutorizacionPersonas permisos;
  public PersonasServicio(PersonasRepositorio repositorio, AutorizacionPersonas permisos) {
    this.repositorio=repositorio; this.permisos=permisos;
  }

  private static void existe(boolean valor, String mensaje) {
    if (!valor) throw new NoEncontrado(mensaje);
  }

  private void validarDocumento(DocumentoEntrada d) {
    if (d == null) throw new IllegalArgumentException("Documento requerido");
    ReglasPersonas.requerido(d.tipo(),"Tipo de documento");
    String pais=ReglasPersonas.requerido(d.pais(),"País emisor");
    if (!pais.matches("[A-Z]{2}")) throw new IllegalArgumentException("País emisor: código ISO de dos letras");
    var tipo=repositorio.tipoDocumento(d.tipo());
    existe(tipo!=null && tipo.activo(),"Tipo de documento no disponible");
    var numero=ReglasPersonas.documento(d.tipo(),pais,d.numero());
    if (tipo.patron()!=null && !Pattern.matches(tipo.patron(),numero))
      throw new IllegalArgumentException("El número no cumple el formato del tipo de documento");
  }

  private void validarPersona(CrearPersona p) {
    if (p == null) throw new IllegalArgumentException("Datos de persona requeridos");
    ReglasPersonas.requerido(p.nombres(),"Nombres");
    ReglasPersonas.requerido(p.apellidos(),"Apellidos");
    ReglasPersonas.nacimiento(p.fechaNacimiento());
    ReglasPersonas.requerido(p.sexo(),"Sexo");
    ReglasPersonas.requerido(p.nacionalidad(),"Nacionalidad");
    ReglasPersonas.requerido(p.direccion(),"Dirección");
    ReglasPersonas.condicion(p.condicion());
    ReglasPersonas.contactos(p.condicion(),p.telefono(),p.correo());
    if ((p.documento()==null) == (p.provisional()==null))
      throw new IllegalArgumentException("Indica un documento o identificación provisional");
    if (p.documento()!=null) validarDocumento(p.documento());
    if (p.provisional()!=null) {
      if (!"ESTUDIANTE".equals(p.condicion()))
        throw new IllegalArgumentException("Identificación provisional solo para estudiantes");
      ReglasPersonas.requerido(p.provisional().motivo(),"Motivo de identificación provisional");
      ReglasPersonas.requerido(p.provisional().sustentoTipo(),"Tipo de sustento");
      ReglasPersonas.requerido(p.provisional().sustentoReferencia(),"Referencia de sustento");
    }
    if ("APODERADO".equals(p.condicion()) && p.apoderamientoInicial()==null)
      throw new IllegalArgumentException("El apoderado inicial requiere vínculo con estudiante");
    if (!"APODERADO".equals(p.condicion()) && p.apoderamientoInicial()!=null)
      throw new IllegalArgumentException("Vínculo inicial solo para apoderado");
    if ("APODERADO".equals(p.condicion()) && !p.apoderamientoInicial().sinParentesco()
        && ReglasPersonas.opcional(p.parentescoInicialTipo())==null)
      throw new IllegalArgumentException("Indica el parentesco del apoderado");
  }

  @Transactional
  public UUID registrar(CrearPersona p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.registrar(actor); validarPersona(p);
    if ("APODERADO".equals(p.condicion())) permisos.gestionar(actor);
    if (p.documento()!=null && repositorio.documentoExiste(p.documento().tipo(),p.documento().pais(),
        ReglasPersonas.documento(p.documento().tipo(),p.documento().pais(),p.documento().numero())))
      throw new Conflicto("Documento vigente registrado en otra persona");
    var id=UUID.randomUUID(); var ahora=repositorio.momento();
    repositorio.crearPersona(id,p);
    if (p.documento()!=null) repositorio.documento(id,p.documento(),
      ReglasPersonas.documento(p.documento().tipo(),p.documento().pais(),p.documento().numero()),ahora);
    else repositorio.provisional(id,p.provisional());
    if ("APODERADO".equals(p.condicion())) {
      var a=p.apoderamientoInicial();
      if (a.apoderadoId()!=null && !id.equals(a.apoderadoId()))
        throw new IllegalArgumentException("El vínculo inicial debe corresponder a la persona creada");
      if (!a.sinParentesco()) repositorio.parentesco(UUID.randomUUID(),
        new ParentescoEntrada(a.estudianteId(),id,p.parentescoInicialTipo().trim()),ahora);
      vincularApoderado(new ApoderamientoEntrada(a.estudianteId(),id,a.sinParentesco(),a.autorizacion()),actor,ahora);
    } else repositorio.condicion(id,p.condicion(),ahora);
    repositorio.evento(id,id,"PERSONA_CREADA",actor.sub());
    return id;
  }

  @Transactional(readOnly=true)
  public Pagina buscar(String texto, String condicion, Boolean vigente, int pagina, int tamano, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (pagina<0 || tamano<1 || tamano>50) throw new IllegalArgumentException("Paginación inválida");
    if (condicion!=null && !condicion.isBlank()) ReglasPersonas.condicion(condicion);
    return repositorio.buscar(texto,condicion,vigente,pagina,tamano);
  }

  @Transactional(readOnly=true)
  public Persona ficha(UUID id, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.consultar(actor,id);
    var p=repositorio.ficha(id); if (p==null) throw new NoEncontrado("Persona no encontrada");
    return p;
  }

  @Transactional
  public void editar(UUID id, EditarPersona p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.identidad(actor);
    existe(repositorio.existe(id),"Persona no encontrada");
    if (p==null) throw new IllegalArgumentException("Datos de persona requeridos");
    ReglasPersonas.requerido(p.nombres(),"Nombres");
    ReglasPersonas.requerido(p.apellidos(),"Apellidos");
    ReglasPersonas.nacimiento(p.fechaNacimiento());
    ReglasPersonas.requerido(p.sexo(),"Sexo");
    ReglasPersonas.requerido(p.nacionalidad(),"Nacionalidad");
    ReglasPersonas.requerido(p.direccion(),"Dirección");
    repositorio.bloquear(id); repositorio.editar(id,p);
    repositorio.evento(id,id,"IDENTIDAD_CORREGIDA",actor.sub());
  }

  @Transactional
  public void contacto(UUID id, Contacto p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.contacto(actor,id);
    existe(repositorio.existe(id),"Persona no encontrada");
    if (p==null) throw new IllegalArgumentException("Datos de contacto requeridos");
    ReglasPersonas.requerido(p.direccion(),"Dirección");
    repositorio.bloquear(id); repositorio.contacto(id,p);
    repositorio.evento(id,id,"CONTACTO_ACTUALIZADO",actor.sub());
  }

  @Transactional
  public void documento(UUID id, DocumentoEntrada d, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.identidad(actor);
    existe(repositorio.existe(id),"Persona no encontrada"); validarDocumento(d);
    var numero=ReglasPersonas.documento(d.tipo(),d.pais(),d.numero());
    repositorio.bloquear(id);
    if (repositorio.documentoExiste(d.tipo(),d.pais(),numero))
      throw new Conflicto("Documento vigente ya registrado");
    var ahora=repositorio.momento();
    repositorio.cerrarDocumentoPrincipal(id,ahora);
    repositorio.regularizar(id,ahora);
    repositorio.documento(id,d,numero,ahora);
    repositorio.evento(id,id,"DOCUMENTO_CORREGIDO",actor.sub());
  }

  @Transactional
  public void condicion(UUID id, NuevaCondicion p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (p==null) throw new IllegalArgumentException("Condición requerida");
    ReglasPersonas.condicion(p.tipo());
    if ("APODERADO".equals(p.tipo())) throw new IllegalArgumentException("Apoderado se obtiene del vínculo vigente");
    existe(repositorio.existe(id),"Persona no encontrada"); repositorio.bloquear(id);
    if (repositorio.condicionActiva(id,p.tipo())) throw new Conflicto("La condición ya está activa");
    var ficha=repositorio.ficha(id);
    ReglasPersonas.contactos(p.tipo(),ficha.telefono(),ficha.correo());
    repositorio.condicion(id,p.tipo(),repositorio.momento());
    repositorio.evento(id,id,"CONDICION_ACTIVADA",actor.sub());
  }

  @Transactional
  public void desactivarCondicion(UUID persona, UUID condicion, Motivo motivo, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (motivo==null) throw new IllegalArgumentException("Motivo requerido");
    var c=repositorio.condicion(persona,condicion);
    if (c==null) throw new NoEncontrado("Condición no encontrada");
    if (c.vigenteHasta()!=null) throw new Conflicto("La condición ya está inactiva");
    repositorio.bloquear(persona);
    repositorio.cerrarCondicion(condicion,ReglasPersonas.requerido(motivo.motivo(),"Motivo"),repositorio.momento());
    repositorio.evento(persona,condicion,"CONDICION_DESACTIVADA",actor.sub());
  }

  @Transactional
  public UUID parentesco(ParentescoEntrada p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (p==null) throw new IllegalArgumentException("Parentesco requerido");
    validarVinculo(p.estudianteId(),p.familiarId());
    ReglasPersonas.requerido(p.tipo(),"Tipo de parentesco");
    if (!repositorio.condicionActiva(p.estudianteId(),"ESTUDIANTE"))
      throw new IllegalArgumentException("El estudiante requiere condición activa");
    if (repositorio.parentesco(p.estudianteId(),p.familiarId(),p.tipo())!=null)
      throw new Conflicto("Parentesco vigente duplicado");
    var id=UUID.randomUUID(); repositorio.parentesco(id,p,repositorio.momento());
    repositorio.evento(p.estudianteId(),id,"PARENTESCO_CREADO",actor.sub()); return id;
  }

  @Transactional
  public void desactivarParentesco(UUID persona, UUID id, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    var ficha=repositorio.ficha(persona); if (ficha==null) throw new NoEncontrado("Persona no encontrada");
    var relacion=ficha.parentescos().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(()->new NoEncontrado("Parentesco no encontrado"));
    if (relacion.vigenteHasta()!=null) throw new Conflicto("Parentesco ya inactivo");
    repositorio.cerrarParentesco(id,repositorio.momento());
    repositorio.evento(persona,id,"PARENTESCO_DESACTIVADO",actor.sub());
  }

  private void validarVinculo(UUID estudiante, UUID otra) {
    if (estudiante==null || otra==null || estudiante.equals(otra)) throw new IllegalArgumentException("Personas del vínculo inválidas");
    existe(repositorio.existe(estudiante) && repositorio.existe(otra),"Persona del vínculo no encontrada");
  }

  private UUID vincularApoderado(ApoderamientoEntrada p, AutorizacionPersonas.Actor actor, Instant ahora) {
    validarVinculo(p.estudianteId(),p.apoderadoId());
    if (!repositorio.condicionActiva(p.estudianteId(),"ESTUDIANTE"))
      throw new IllegalArgumentException("El estudiante requiere condición activa");
    var apoderado=repositorio.ficha(p.apoderadoId());
    ReglasPersonas.contactos("APODERADO",apoderado.telefono(),apoderado.correo());
    if (p.sinParentesco()) {
      if (p.autorizacion()==null) throw new IllegalArgumentException("Autorización verbal requerida");
      ReglasPersonas.requerido(p.autorizacion().autorizador(),"Autorizador");
      ReglasPersonas.requerido(p.autorizacion().medio(),"Medio de verificación");
      if (p.autorizacion().sustentoTipo()!=null)
        ReglasPersonas.requerido(p.autorizacion().sustentoTipo(),"Tipo de sustento");
      ReglasPersonas.requerido(p.autorizacion().sustentoReferencia(),"Referencia de sustento");
      if (p.autorizacion().fecha()==null) throw new IllegalArgumentException("Fecha de verificación requerida");
    } else if (apoderado.parentescos().stream().noneMatch(r->r.estudianteId().equals(p.estudianteId())
        && r.familiarId().equals(p.apoderadoId()) && r.vigenteHasta()==null)) {
      throw new IllegalArgumentException("Registra primero el parentesco vigente o una autorización sin parentesco");
    }
    if (repositorio.apoderamientoActivo(p.estudianteId(),p.apoderadoId()))
      throw new Conflicto("Apoderamiento vigente duplicado");
    var id=UUID.randomUUID(); repositorio.apoderamiento(id,p,actor.sub(),ahora);
    repositorio.evento(p.estudianteId(),id,"APODERAMIENTO_CREADO",actor.sub());
    return id;
  }

  @Transactional
  public UUID apoderamiento(ApoderamientoEntrada p, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (p==null) throw new IllegalArgumentException("Vínculo requerido");
    validarVinculo(p.estudianteId(),p.apoderadoId());
    // Orden estable de bloqueo para evitar interbloqueos cuando coinciden operaciones sobre la pareja.
    List<UUID> ids=List.of(p.estudianteId(),p.apoderadoId()).stream().sorted().toList();
    ids.forEach(repositorio::bloquear);
    return vincularApoderado(p,actor,repositorio.momento());
  }

  @Transactional
  public void desactivarApoderamiento(UUID persona, UUID id, Motivo motivo, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.gestionar(actor);
    if (motivo==null) throw new IllegalArgumentException("Motivo requerido");
    var ficha=repositorio.ficha(persona); if (ficha==null) throw new NoEncontrado("Persona no encontrada");
    var relacion=ficha.apoderamientos().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(()->new NoEncontrado("Vínculo no encontrado"));
    if (relacion.vigenteHasta()!=null) throw new Conflicto("Vínculo ya inactivo");
    repositorio.cerrarApoderamiento(id,persona,ReglasPersonas.requerido(motivo.motivo(),"Motivo"),repositorio.momento());
    repositorio.evento(persona,id,"APODERAMIENTO_DESACTIVADO",actor.sub());
  }

  @Transactional(readOnly=true)
  public List<TipoDocumento> tipos(Authentication autenticacion) {
    permisos.registrar(permisos.actor(autenticacion)); return repositorio.tiposDocumento();
  }

  @Transactional
  public void crearTipo(TipoDocumento tipo, Authentication autenticacion) {
    var actor=permisos.actor(autenticacion); permisos.identidad(actor);
    if (tipo==null) throw new IllegalArgumentException("Tipo de documento requerido");
    String codigo=ReglasPersonas.requerido(tipo.codigo(),"Código");
    if (!codigo.matches("[A-Z0-9_]{2,30}")) throw new IllegalArgumentException("Código de tipo inválido");
    ReglasPersonas.requerido(tipo.nombre(),"Nombre del tipo");
    if (tipo.patron()!=null) {
      if (tipo.patron().length()>128) throw new IllegalArgumentException("Patrón demasiado largo");
      try { Pattern.compile(tipo.patron()); }
      catch (java.util.regex.PatternSyntaxException e) { throw new IllegalArgumentException("Patrón documental inválido"); }
    }
    repositorio.crearTipo(tipo);
  }

  public Capacidades capacidades(Authentication autenticacion) {
    return permisos.capacidades(permisos.actor(autenticacion));
  }

  @Transactional(readOnly=true)
  public boolean duplicadoDocumental(DocumentoEntrada d, Authentication autenticacion) {
    permisos.registrar(permisos.actor(autenticacion)); validarDocumento(d);
    return repositorio.documentoExiste(d.tipo(),d.pais(),
      ReglasPersonas.documento(d.tipo(),d.pais(),d.numero()));
  }

  @Override @Transactional(readOnly=true)
  public UUID buscarPorDocumento(String tipo,String pais,String numero,Authentication actor) {
    permisos.gestionar(permisos.actor(actor));
    validarDocumento(new DocumentoEntrada(tipo,pais,numero));
    return repositorio.personaPorDocumento(tipo,pais,ReglasPersonas.documento(tipo,pais,numero));
  }

  @Override @Transactional
  public UUID registrarEstudiante(NuevaPersona p,Authentication actor) {
    var documento=p.tipoDocumento()==null?null:new DocumentoEntrada(p.tipoDocumento(),p.paisEmisor(),p.numeroDocumento());
    var provisional=p.tipoDocumento()!=null?null:new ProvisionalEntrada(p.motivoProvisional(),p.sustentoTipo(),p.sustentoReferencia());
    return registrar(new CrearPersona(p.nombres(),p.apellidos(),p.fechaNacimiento(),p.sexo(),p.nacionalidad(),
      p.telefono(),p.correo(),p.direccion(),"ESTUDIANTE",documento,provisional,null,null),actor);
  }

  @Override @Transactional(readOnly=true)
  public boolean esEstudianteActivo(UUID persona,Authentication actor) {
    permisos.gestionar(permisos.actor(actor));
    existe(repositorio.existe(persona),"Persona no encontrada");
    return repositorio.condicionActiva(persona,"ESTUDIANTE");
  }

  @Override @Transactional(readOnly=true)
  public List<UUID> apoderadosVigentes(UUID estudiante,Authentication actor) {
    permisos.gestionar(permisos.actor(actor));
    existe(repositorio.existe(estudiante),"Persona no encontrada");
    return repositorio.apoderadosVigentes(estudiante);
  }

  @Override @Transactional(readOnly=true)
  public boolean tieneIdentificacionValida(UUID persona,Authentication actor) {
    permisos.gestionar(permisos.actor(actor));
    existe(repositorio.existe(persona),"Persona no encontrada");
    return repositorio.identificacionValida(persona);
  }

  @Override @Transactional
  public UUID vincularApoderado(UUID estudiante,UUID apoderado,boolean sinParentesco,
      Autorizacion autorizacion,Authentication actor) {
    var a=autorizacion==null?null:new AutorizacionEntrada(autorizacion.autorizador(),
      autorizacion.medio(),autorizacion.fecha(),"VERBAL",autorizacion.referencia());
    return apoderamiento(new ApoderamientoEntrada(estudiante,apoderado,sinParentesco,a),actor);
  }

  public static final class NoEncontrado extends RuntimeException { public NoEncontrado(String m) { super(m); } }
  public static final class Conflicto extends RuntimeException { public Conflicto(String m) { super(m); } }
}
