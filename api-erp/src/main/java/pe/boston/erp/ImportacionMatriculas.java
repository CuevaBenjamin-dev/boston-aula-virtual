package pe.boston.erp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service public class ImportacionMatriculas {
  private static final List<String> COLUMNAS=List.of("codigo_estudiante","tipo_documento_estudiante","documento_estudiante",
    "nombres_estudiante","apellidos_estudiante","fecha_nacimiento","periodo","nivel","grado","seccion","procedencia",
    "estado","tipo_documento_responsable","documento_responsable","nombres_responsable","apellidos_responsable",
    "vinculo","telefono_responsable","tipo_documento_financiero","documento_financiero","nombres_financiero","apellidos_financiero");
  private final JdbcTemplate db;
  private final ErpV1Service servicio;
  private final ObjectMapper json=new ObjectMapper();
  public ImportacionMatriculas(JdbcTemplate db,ErpV1Service servicio) { this.db=db;this.servicio=servicio; }
  private String serializar(Object valor) {
    try { return json.writeValueAsString(valor); }
    catch(Exception e) { throw new IllegalArgumentException("Reporte inválido"); }
  }
  private Map<String,Object> leer(String valor) {
    try { return json.readValue(valor,new TypeReference<>(){}); }
    catch(Exception e) { throw new IllegalArgumentException("Reporte inválido"); }
  }
  public byte[] plantilla(Jwt actor) throws IOException {
    ErpV1Service.lectura(actor);
    try(var libro=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
      var hoja=libro.createSheet("Matriculas");
      var cabecera=hoja.createRow(0);
      for(int i=0;i<COLUMNAS.size();i++) cabecera.createCell(i).setCellValue(COLUMNAS.get(i));
      var ejemplo=hoja.createRow(1);
      var datos=List.of("","DNI","00000000","Ana","Ejemplo","2015-04-12","2027","Primaria","Sexto","A","NUEVO",
        "PROCESO_PENDIENTE","DNI","11111111","Patricia","Ejemplo","Madre","999000000","","","","");
      for(int i=0;i<datos.size();i++) ejemplo.createCell(i).setCellValue(datos.get(i));
      libro.write(bytes);
      return bytes.toByteArray();
    }
  }
  private String normal(String s) { return s==null?"":s.trim(); }
  private String hash(byte[] bytes) {
    try {
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
      return java.util.HexFormat.of().formatHex(digest);
    } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private UUID periodo(String nombre) {
    var ids=db.query("SELECT id FROM periodos_academicos WHERE nombre=? AND habilitado",(rs,n)->(UUID)rs.getObject(1),nombre);
    return ids.size()==1?ids.getFirst():null;
  }
  private UUID grado(String nivel,String nombre) {
    var ids=db.query("""
      SELECT g.id FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id
      WHERE n.nombre=? AND g.nombre=? AND g.habilitado AND n.habilitado
      """,(rs,n)->(UUID)rs.getObject(1),nivel,nombre);
    return ids.size()==1?ids.getFirst():null;
  }
  private UUID seccion(UUID periodo,UUID grado,String nombre) {
    if(nombre.isBlank()||periodo==null||grado==null) return null;
    var ids=db.query("SELECT id FROM secciones WHERE periodo_id=? AND grado_id=? AND nombre=? AND habilitada",
      (rs,n)->(UUID)rs.getObject(1),periodo,grado,nombre);
    return ids.size()==1?ids.getFirst():null;
  }
  private UUID estudianteExistente(Map<String,String> fila) {
    String codigo=normal(fila.get("codigo_estudiante")),doc=normal(fila.get("documento_estudiante"));
    if(!codigo.isBlank()) {
      var ids=db.query("SELECT id FROM estudiantes WHERE codigo=?",(rs,n)->(UUID)rs.getObject(1),codigo);
      return ids.size()==1?ids.getFirst():null;
    }
    if(!doc.isBlank()) {
      var ids=db.query("""
        SELECT e.id FROM estudiantes e JOIN personas p ON p.id=e.persona_id
        WHERE p.tipo_documento=? AND p.documento=?
        """,(rs,n)->(UUID)rs.getObject(1),fila.get("tipo_documento_estudiante"),doc);
      return ids.size()==1?ids.getFirst():null;
    }
    return null;
  }
  private List<String> validar(Map<String,String> f,Set<String> documentos,Set<String> codigos) {
    var errores=new ArrayList<String>();
    String codigo=normal(f.get("codigo_estudiante")),doc=normal(f.get("documento_estudiante"));
    String tipo=normal(f.get("tipo_documento_estudiante"));
    if(codigo.isBlank()&&doc.isBlank()) errores.add("Se necesita documento o código institucional para identificar al estudiante");
    if(!codigo.isBlank()&&!codigos.add(codigo)) errores.add("Código duplicado en archivo");
    if(!doc.isBlank()&&!documentos.add(tipo+":"+doc)) errores.add("Documento de estudiante duplicado en archivo");
    if(!doc.isBlank()&&tipo.isBlank()) errores.add("Falta tipo de documento del estudiante");
    if(normal(f.get("nombres_estudiante")).isBlank()||normal(f.get("apellidos_estudiante")).isBlank()) errores.add("Faltan nombres o apellidos");
    if(!normal(f.get("fecha_nacimiento")).isBlank()) try { LocalDate.parse(f.get("fecha_nacimiento")); }
      catch(DateTimeParseException e) { errores.add("Fecha de nacimiento inválida; usa AAAA-MM-DD"); }
    UUID periodo=periodo(normal(f.get("periodo"))),grado=grado(normal(f.get("nivel")),normal(f.get("grado")));
    if(periodo==null) errores.add("Periodo no habilitado");
    if(grado==null) errores.add("Nivel o grado inexistente");
    if(!normal(f.get("seccion")).isBlank()&&seccion(periodo,grado,normal(f.get("seccion")))==null) errores.add("Sección no habilitada");
    if(!List.of("NUEVO","ANTIGUO","TRASLADO","REINCORPORACION").contains(f.get("procedencia"))) errores.add("Procedencia inválida");
    if(!List.of("PROCESO_PENDIENTE","VACANTE_RESERVADA","MATRICULADO").contains(f.get("estado"))) errores.add("Estado inválido");
    if("MATRICULADO".equals(f.get("estado"))&&
      (normal(f.get("tipo_documento_responsable")).isBlank()||normal(f.get("documento_responsable")).isBlank()||
       normal(f.get("nombres_responsable")).isBlank()||normal(f.get("apellidos_responsable")).isBlank()))
      errores.add("Matrícula confirmada requiere responsable identificado");
    if(!normal(f.get("documento_responsable")).isBlank()&&normal(f.get("tipo_documento_responsable")).isBlank())
      errores.add("Falta tipo de documento del responsable");
    if(!normal(f.get("documento_financiero")).isBlank()&&
      (normal(f.get("tipo_documento_financiero")).isBlank()||normal(f.get("nombres_financiero")).isBlank()||
       normal(f.get("apellidos_financiero")).isBlank()))
      errores.add("Datos incompletos del responsable financiero");
    UUID existente=estudianteExistente(f);
    if(!codigo.isBlank()&&existente==null) errores.add("Código institucional desconocido");
    if(existente!=null&&!doc.isBlank()) {
      var documentosRegistrados=db.query("""
        SELECT p.tipo_documento,p.documento FROM estudiantes e JOIN personas p ON p.id=e.persona_id
        WHERE e.id=?
        """,(rs,n)->Map.of("tipo",rs.getString(1)==null?"":rs.getString(1),
          "numero",rs.getString(2)==null?"":rs.getString(2)),existente);
      if(!documentosRegistrados.isEmpty()&&(!tipo.equals(documentosRegistrados.getFirst().get("tipo"))
        ||!doc.equals(documentosRegistrados.getFirst().get("numero"))))
        errores.add("El documento no coincide con el código institucional registrado");
    }
    if(existente!=null&&db.queryForObject("SELECT count(*) FROM matriculas WHERE estudiante_id=? AND periodo_id=? AND estado<>'CANCELADA'",Long.class,existente,periodo)>0)
      errores.add("El estudiante ya tiene matrícula en el periodo");
    return errores;
  }
  public Map<String,Object> previsualizar(MultipartFile archivo,Jwt actor) throws IOException {
    ErpV1Service.escritura(actor);
    if(archivo==null||archivo.isEmpty()||archivo.getSize()>2_000_000||
      archivo.getOriginalFilename()==null||!archivo.getOriginalFilename().toLowerCase().endsWith(".xlsx"))
      throw new IllegalArgumentException("Archivo .xlsx requerido (máximo 2 MB)");
    byte[] bytes=archivo.getBytes();
    String sha=hash(bytes);
    var anterior=db.query("SELECT id,estado,reporte::text AS reporte FROM importaciones_matriculas WHERE sha256=?",
      (rs,n)->Map.<String,Object>of("id",rs.getObject("id"),"estado",rs.getString("estado"),"reporte",leer(rs.getString("reporte"))),sha);
    if(!anterior.isEmpty() && "CONFIRMADA".equals(anterior.getFirst().get("estado"))) return anterior.getFirst();
    var filas=new ArrayList<Map<String,Object>>();
    var documentos=new HashSet<String>();var codigos=new HashSet<String>();
    try(var libro=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var hoja=libro.getSheetAt(0);
      if(hoja.getLastRowNum()>500) throw new IllegalArgumentException("Máximo 500 filas");
      var primera=hoja.getRow(0);
      if(primera==null) throw new IllegalArgumentException("Falta cabecera");
      var formato=new DataFormatter();
      for(int c=0;c<COLUMNAS.size();c++) {
        String valor=primera.getCell(c)==null?"":formato.formatCellValue(primera.getCell(c));
        if(!COLUMNAS.get(c).equals(valor)) throw new IllegalArgumentException("Cabecera incorrecta en columna "+(c+1)+": "+COLUMNAS.get(c));
      }
      for(int n=1;n<=hoja.getLastRowNum();n++) {
        Row row=hoja.getRow(n);
        if(row==null) continue;
        var datos=new LinkedHashMap<String,String>();
        boolean vacia=true,formula=false;
        for(int c=0;c<COLUMNAS.size();c++) {
          var celda=row.getCell(c);
          if(celda!=null&&celda.getCellType()==CellType.FORMULA) formula=true;
          String valor=celda==null?"":normal(formato.formatCellValue(celda));
          if(!valor.isBlank()) vacia=false;
          datos.put(COLUMNAS.get(c),valor);
        }
        if(vacia) continue;
        var errores=validar(datos,documentos,codigos);
        if(formula) errores.add("No se aceptan fórmulas");
        UUID existente=estudianteExistente(datos);
        filas.add(Map.of("fila",n+1,"datos",datos,"errores",errores,"estudianteExistente",existente!=null));
      }
    } catch(IllegalArgumentException e) { throw e; }
      catch(Exception e) { throw new IllegalArgumentException("No se pudo leer el archivo .xlsx"); }
    if(filas.isEmpty()) throw new IllegalArgumentException("El archivo no contiene registros");
    UUID id=anterior.isEmpty()?UUID.randomUUID():(UUID)anterior.getFirst().get("id");
    var reporte=Map.<String,Object>of("filas",filas,"total",filas.size(),
      "validas",filas.stream().filter(f->((List<?>)f.get("errores")).isEmpty()).count());
    if(anterior.isEmpty()) db.update("INSERT INTO importaciones_matriculas(id,sha256,archivo_nombre,actor,estado,reporte) VALUES (?,?,?,?,'PREVISUALIZADA',?::jsonb)",
      id,sha,archivo.getOriginalFilename(),actor.getSubject(),serializar(reporte));
    else db.update("UPDATE importaciones_matriculas SET reporte=?::jsonb,actor=? WHERE id=?",
      serializar(reporte),actor.getSubject(),id);
    return Map.of("id",id,"estado","PREVISUALIZADA","reporte",reporte);
  }
  private UUID persona(String tipo,String doc,String nombres,String apellidos,Jwt actor) {
    var encontrados=servicio.personas(tipo,doc,actor);
    if(!encontrados.isEmpty()) return (UUID)encontrados.getFirst().get("id");
    return servicio.crearPersona(new ErpV1Service.PersonaDato(nombres,apellidos,tipo,doc,null,null),actor);
  }
  @Transactional public Map<String,Object> confirmar(UUID id,boolean confirmado,Jwt actor) {
    ErpV1Service.escritura(actor);
    if(!confirmado) throw new IllegalArgumentException("Se requiere confirmación explícita de la importación");
    var registro=db.queryForMap("SELECT estado,reporte::text AS reporte FROM importaciones_matriculas WHERE id=? FOR UPDATE",id);
    Map<String,Object> reporte=leer((String)registro.get("reporte"));
    if("CONFIRMADA".equals(registro.get("estado"))) return reporte;
    @SuppressWarnings("unchecked") List<Map<String,Object>> filas=(List<Map<String,Object>>)reporte.get("filas");
    if(filas.stream().anyMatch(f->!((List<?>)f.get("errores")).isEmpty()))
      throw new IllegalArgumentException("Corrige las filas con errores antes de importar");
    var resultados=new ArrayList<Map<String,Object>>();
    for(var fila:filas) {
      @SuppressWarnings("unchecked") Map<String,String> f=(Map<String,String>)fila.get("datos");
      UUID periodo=periodo(f.get("periodo")),grado=grado(f.get("nivel"),f.get("grado"));
      UUID estudiante=estudianteExistente(f);
      if(estudiante==null) estudiante=servicio.crearEstudiante(new ErpV1Service.EstudianteDato(
        f.get("nombres_estudiante"),f.get("apellidos_estudiante"),
        f.get("fecha_nacimiento").isBlank()?null:LocalDate.parse(f.get("fecha_nacimiento")),
        f.get("tipo_documento_estudiante"),f.get("documento_estudiante"),null,"ACTIVO"),actor);
      if(db.queryForObject("SELECT count(*) FROM matriculas WHERE estudiante_id=? AND periodo_id=? AND estado<>'CANCELADA'",Long.class,estudiante,periodo)>0)
        throw new IllegalArgumentException("La matrícula ya existe; vuelve a previsualizar el archivo");
      if(!f.get("documento_responsable").isBlank()) {
        UUID persona=persona(f.get("tipo_documento_responsable"),f.get("documento_responsable"),
          f.get("nombres_responsable"),f.get("apellidos_responsable"),actor);
        servicio.vincular(estudiante,new ErpV1Service.VinculoDato(persona,"PRINCIPAL",
          f.get("vinculo").isBlank()?"Responsable":f.get("vinculo"),false,f.get("telefono_responsable"),null),actor);
      }
      UUID matricula=servicio.iniciarMatricula(new ErpV1Service.MatriculaDato(estudiante,periodo,grado,f.get("procedencia"),null),actor);
      UUID financiero=null;
      if(!f.get("documento_financiero").isBlank()) financiero=persona(f.get("tipo_documento_financiero"),
        f.get("documento_financiero"),f.get("nombres_financiero"),f.get("apellidos_financiero"),actor);
      servicio.editarMatricula(matricula,new ErpV1Service.CambioMatricula(grado,
        seccion(periodo,grado,f.get("seccion")),financiero,null),actor);
      if("VACANTE_RESERVADA".equals(f.get("estado"))) servicio.reservar(matricula,actor);
      if("MATRICULADO".equals(f.get("estado"))) servicio.confirmar(matricula,true,actor);
      resultados.add(Map.of("fila",fila.get("fila"),"estudianteId",estudiante,"matriculaId",matricula));
    }
    var finalReporte=new HashMap<>(reporte);
    finalReporte.put("resultados",resultados);
    db.update("UPDATE importaciones_matriculas SET estado='CONFIRMADA',reporte=?::jsonb,confirmado_en=now() WHERE id=?",
      serializar(finalReporte),id);
    return finalReporte;
  }
}
