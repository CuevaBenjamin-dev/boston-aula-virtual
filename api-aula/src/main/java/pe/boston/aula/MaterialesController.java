package pe.boston.aula;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/aula") public class MaterialesController {
  private final JdbcTemplate db; private final UsuarioActual usuario;
  public MaterialesController(JdbcTemplate db,UsuarioActual usuario) { this.db=db;this.usuario=usuario; }
  public record Datos(String nombre,String descripcion,String enlace,List<UUID> cursoIds) {}

  @GetMapping("/cursos/{curso}/materiales") public List<Map<String,Object>> listar(@PathVariable UUID curso,Authentication a) {
    usuario.leerCurso(a,curso);
    String filtro="ESTUDIANTE".equals(usuario.tipo(a))?" AND m.estado='PUBLICADO'":"";
    return db.queryForList("""
      SELECT m.id,m.nombre,m.descripcion,m.enlace,m.estado,m.creado_en,m.actualizado_en,
             (SELECT count(*) FROM archivos_material f WHERE f.material_id=m.id AND f.estado='DISPONIBLE' AND f.activo) AS total_archivos
      FROM materiales_curso m JOIN destinos_material d ON d.material_id=m.id
      WHERE d.curso_id=?
      """+filtro+" ORDER BY m.actualizado_en DESC",curso);
  }

  @GetMapping("/materiales/{id}") public Map<String,Object> ver(@PathVariable UUID id,Authentication a) {
    var m=db.queryForMap("SELECT * FROM materiales_curso WHERE id=?",id);
    acceso(id,a,false);
    var r=new java.util.HashMap<>(m);
    if("ESTUDIANTE".equals(usuario.tipo(a))) r.put("curso_id",db.queryForObject("""
      SELECT d.curso_id FROM destinos_material d JOIN inscripciones_curso i ON i.curso_id=d.curso_id
      JOIN cursos c ON c.id=d.curso_id
      WHERE d.material_id=? AND i.estudiante_erp_id=? AND i.activo AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)
      ORDER BY d.curso_id LIMIT 1
      """,UUID.class,id,usuario.referencia(a)));
    else r.put("curso_ids",db.queryForList("SELECT curso_id FROM destinos_material WHERE material_id=? ORDER BY curso_id",UUID.class,id));
    return r;
  }

  @PostMapping("/materiales") @Transactional public Map<String,UUID> crear(@RequestBody Datos d,Authentication a) {
    var cursos=cursos(d.cursoIds(),a); validar(d);
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO materiales_curso(id,curso_id,nombre,descripcion,enlace,estado,creado_por) VALUES (?,?,?,?,?,'BORRADOR',?)",
      id,cursos.getFirst(),d.nombre().trim(),d.descripcion()==null?"":d.descripcion().trim(),url(d.enlace()),usuario.cuenta(a));
    for(UUID curso:cursos) db.update("INSERT INTO destinos_material(material_id,curso_id) VALUES (?,?)",id,curso);
    auditar(a,"MATERIAL_CREADO",id);
    return Map.of("id",id);
  }

  @PutMapping("/materiales/{id}") @Transactional public Map<String,Object> editar(@PathVariable UUID id,@RequestBody Datos d,Authentication a) {
    acceso(id,a,true); var cursos=cursos(d.cursoIds(),a); validar(d);
    if("PUBLICADO".equals(db.queryForObject("SELECT estado FROM materiales_curso WHERE id=?",String.class,id)) && url(d.enlace())==null &&
       db.queryForObject("SELECT count(*) FROM archivos_material WHERE material_id=? AND estado='DISPONIBLE' AND activo",Long.class,id)==0)
      throw new IllegalArgumentException("Un material publicado requiere archivo o enlace");
    db.update("UPDATE materiales_curso SET nombre=?,descripcion=?,enlace=?,actualizado_en=now() WHERE id=?",
      d.nombre().trim(),d.descripcion()==null?"":d.descripcion().trim(),url(d.enlace()),id);
    db.update("DELETE FROM destinos_material WHERE material_id=?",id);
    for(UUID curso:cursos) db.update("INSERT INTO destinos_material(material_id,curso_id,publicado_en) VALUES (?,?,CASE WHEN (SELECT estado FROM materiales_curso WHERE id=?)='PUBLICADO' THEN now() ELSE NULL END)",id,curso,id);
    auditar(a,"MATERIAL_EDITADO",id);
    return ver(id,a);
  }

  @PostMapping("/materiales/{id}/publicar") @Transactional public void publicar(@PathVariable UUID id,Authentication a) {
    acceso(id,a,true); var m=db.queryForMap("SELECT enlace,estado FROM materiales_curso WHERE id=? FOR UPDATE",id);
    if("PUBLICADO".equals(m.get("estado"))) return;
    if(m.get("enlace")==null && db.queryForObject("SELECT count(*) FROM archivos_material WHERE material_id=? AND estado='DISPONIBLE' AND activo",Long.class,id)==0)
      throw new IllegalArgumentException("Agrega al menos un archivo o enlace antes de publicar");
    db.update("UPDATE materiales_curso SET estado='PUBLICADO',actualizado_en=now() WHERE id=?",id);
    db.update("UPDATE destinos_material SET publicado_en=now() WHERE material_id=?",id);
    auditar(a,"MATERIAL_PUBLICADO",id);
  }

  @PostMapping("/materiales/{id}/ocultar") @Transactional public void ocultar(@PathVariable UUID id,Authentication a) {
    acceso(id,a,true);
    if(db.update("UPDATE materiales_curso SET estado='OCULTO',actualizado_en=now() WHERE id=? AND estado='PUBLICADO'",id)!=1)
      throw new IllegalArgumentException("Solo un material publicado puede ocultarse");
    auditar(a,"MATERIAL_OCULTO",id);
  }

  void acceso(UUID material,Authentication a,boolean escritura) {
    var destinos=db.queryForList("SELECT curso_id FROM destinos_material WHERE material_id=?",UUID.class,material);
    if(destinos.isEmpty()) throw new AccessDeniedException("Material sin destino");
    if(escritura) { for(UUID curso:destinos) usuario.docente(a,curso); return; }
    if("ESTUDIANTE".equals(usuario.tipo(a))) {
      if(!"PUBLICADO".equals(db.queryForObject("SELECT estado FROM materiales_curso WHERE id=?",String.class,material))) throw new AccessDeniedException("Material oculto");
      for(UUID curso:destinos) if(db.queryForObject("""
          SELECT count(*) FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id
          WHERE i.curso_id=? AND i.estudiante_erp_id=? AND i.activo
            AND (c.periodo_fin IS NULL OR c.periodo_fin>=CURRENT_DATE)
          """,Long.class,curso,usuario.referencia(a))>0) return;
      throw new AccessDeniedException("Material ajeno");
    }
    for(UUID curso:destinos) {
      try { usuario.leerCurso(a,curso); return; } catch(AccessDeniedException ignored) { /* Buscar otra sección autorizada. */ }
    }
    throw new AccessDeniedException("Material ajeno");
  }

  private List<UUID> cursos(List<UUID> ids,Authentication a) {
    if(ids==null||ids.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un curso");
    var únicos=new LinkedHashSet<>(ids);
    if(únicos.contains(null)) throw new IllegalArgumentException("Curso inválido");
    for(UUID curso:únicos) usuario.docente(a,curso);
    return List.copyOf(únicos);
  }
  private void validar(Datos d) {
    if(d.nombre()==null||d.nombre().isBlank()||d.nombre().length()>200) throw new IllegalArgumentException("Nombre inválido");
    if(d.descripcion()!=null&&d.descripcion().length()>10000) throw new IllegalArgumentException("Descripción extensa");
    url(d.enlace());
  }
  private String url(String valor) {
    if(valor==null||valor.isBlank()) return null;
    if(valor.length()>2000) throw new IllegalArgumentException("Enlace extenso");
    URI u=URI.create(valor.trim());
    if(!List.of("http","https").contains(u.getScheme())||u.getHost()==null) throw new IllegalArgumentException("Enlace inválido");
    return u.toString();
  }
  private void auditar(Authentication a,String accion,UUID id) {
    db.update("INSERT INTO registros_auditoria(id,actor,accion,entidad_id) VALUES (?,?,?,?)",UUID.randomUUID(),usuario.oidc(a).getSubject(),accion,id);
  }
}
