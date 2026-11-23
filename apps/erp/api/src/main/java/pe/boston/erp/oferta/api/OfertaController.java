package pe.boston.erp.oferta.api;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.boston.erp.oferta.OfertaConsulta;
import pe.boston.erp.plataforma.AccesoErp;

@RestController
@RequestMapping("/api/erp/v1/oferta")
public class OfertaController {
  public record Anio(int anio) {}
  public record SeccionEntrada(int anio, String nivel, int grado, String nombre) {}
  public record AsignaturaEntrada(int anio, String nivel, int grado, String codigo, String nombre) {}
  public record Asignatura(UUID id, int anio, String nivel, int grado, String codigo, String nombre) {}
  private final JdbcTemplate jdbc;
  public OfertaController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  @GetMapping("/anios") public List<Integer> anios(Authentication auth) {
    AccesoErp.actor(auth);
    return jdbc.query("SELECT anio FROM ofe_anios ORDER BY anio DESC",(r,n)->r.getInt(1));
  }
  @PostMapping("/anios") @Transactional public Anio crearAnio(@RequestBody Anio entrada,Authentication auth) {
    String actor=AccesoErp.actor(auth);
    if (entrada.anio()<2000 || entrada.anio()>2100) throw new IllegalArgumentException("Año escolar inválido");
    jdbc.update("INSERT INTO ofe_anios(anio,actor_sub) VALUES(?,?)",entrada.anio(),actor);
    return entrada;
  }
  @GetMapping("/secciones") public List<OfertaConsulta.Seccion> secciones(@RequestParam int anio,Authentication auth) {
    AccesoErp.actor(auth);
    return jdbc.query("SELECT id,anio,nivel,grado,nombre FROM ofe_secciones WHERE anio=? ORDER BY nivel,grado,nombre",
      (r,n)->new OfertaConsulta.Seccion(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getInt(4),r.getString(5)),anio);
  }
  @PostMapping("/secciones") @Transactional public OfertaConsulta.Seccion crearSeccion(@RequestBody SeccionEntrada p,Authentication auth) {
    AccesoErp.actor(auth); validarGrado(p.nivel(),p.grado());
    String nombre=p.nombre()==null?"":p.nombre().trim().toUpperCase();
    if (!nombre.matches("[A-Z0-9]{1,6}")) throw new IllegalArgumentException("La sección debe tener de 1 a 6 letras o números");
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_secciones(id,anio,nivel,grado,nombre) VALUES(?,?,?,?,?)",id,p.anio(),p.nivel(),p.grado(),nombre);
    return new OfertaConsulta.Seccion(id,p.anio(),p.nivel(),p.grado(),nombre);
  }
  @GetMapping("/asignaturas") public List<Asignatura> asignaturas(@RequestParam int anio,Authentication auth) {
    AccesoErp.actor(auth);
    return jdbc.query("SELECT id,anio,nivel,grado,codigo,nombre FROM ofe_asignaturas WHERE anio=? ORDER BY nivel,grado,nombre",
      (r,n)->new Asignatura(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getInt(4),r.getString(5),r.getString(6)),anio);
  }
  @PostMapping("/asignaturas") @Transactional public Asignatura crearAsignatura(@RequestBody AsignaturaEntrada p,Authentication auth) {
    AccesoErp.actor(auth); validarGrado(p.nivel(),p.grado());
    String codigo=CodigoAsignatura.normalizar(p.anio(),p.nivel(),p.grado(),p.codigo());
    String nombre=p.nombre()==null?"":p.nombre().trim();
    if (nombre.isEmpty() || nombre.length()>120)
      throw new IllegalArgumentException("Código o nombre de asignatura inválido");
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO ofe_asignaturas(id,anio,nivel,grado,codigo,nombre) VALUES(?,?,?,?,?,?)",
      id,p.anio(),p.nivel(),p.grado(),codigo,nombre);
    return new Asignatura(id,p.anio(),p.nivel(),p.grado(),codigo,nombre);
  }
  private static void validarGrado(String nivel,int grado) {
    if (!("PRIMARIA".equals(nivel) && grado>=1 && grado<=6)
        && !("SECUNDARIA".equals(nivel) && grado>=1 && grado<=5))
      throw new IllegalArgumentException("Nivel o grado inválido");
  }
}

@Component
class OfertaConsultaImpl implements OfertaConsulta {
  private final JdbcTemplate jdbc;
  OfertaConsultaImpl(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @Override public Seccion seccion(UUID id) {
    if (id==null) return null;
    var filas=jdbc.query("SELECT id,anio,nivel,grado,nombre FROM ofe_secciones WHERE id=?",
      (r,n)->new Seccion(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getInt(4),r.getString(5)),id);
    return filas.isEmpty()?null:filas.getFirst();
  }
}
