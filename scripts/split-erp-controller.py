"""Migración única de los endpoints V1 hacia los límites HTTP de cada módulo."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / "apps/erp/api/src/main/java/pe/boston/erp"
source = root / "ErpV1Controller.java"
content = source.read_text(encoding="utf-8")
lines = content.splitlines()
records = {re.search(r"public record (\w+)", line).group(1): line for line in lines if "public record " in line}
endpoints = [line for line in lines if re.match(r"\s+@(Get|Post|Put|Delete)Mapping", line)]

groups = {
    "personas": ("PersonasController", ("estudiantes", "personas"), ("Id",), False),
    "matriculas": ("MatriculasController", ("matriculas",), ("Id", "Confirmacion", "PagoExterno"), False),
    "oferta": ("OfertaController", ("periodos", "niveles", "grados", "secciones", "ofertas", "asignaturas"),
               ("Id", "EstadoSeccion", "AsignaturaDato", "RelacionDato", "NombreDato", "Habilitacion", "GradoDato", "OfertaDato", "OfertaGradoDato"), False),
    "asignaciones": ("AsignacionesController", ("docentes",),
                     ("Id", "AsignacionDato", "AsignacionesLoteDato", "CuentaDocenteDato", "DocenteEdicion"), True),
}

imports = """import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.boston.erp.ErpV1Service;
"""

for module, (name, prefixes, record_names, uses_identity) in groups.items():
    selected = []
    for line in endpoints:
        path = re.search(r'@(?:Get|Post|Put|Delete)Mapping\("([^"]+)"\)', line)
        if path and path.group(1).lstrip("/").split("/")[0] in prefixes:
            selected.append(line)
    destination = root / module / "api" / (name + ".java")
    destination.parent.mkdir(parents=True, exist_ok=True)
    header = "package pe.boston.erp." + module + ".api;\n\n" + imports
    if uses_identity:
        header += "import pe.boston.erp.DirectorioIdentidad;\n"
    header += "\n@RestController @RequestMapping(\"/api/erp/v1\")\npublic class " + name + " {\n"
    header += "  private final ErpV1Service servicio;\n"
    if uses_identity:
        header += "  private final DirectorioIdentidad identidad;\n"
        header += "  public " + name + "(ErpV1Service servicio, DirectorioIdentidad identidad) { this.servicio=servicio; this.identidad=identidad; }\n"
    else:
        header += "  public " + name + "(ErpV1Service servicio) { this.servicio=servicio; }\n"
    header += "\n".join(records[item] for item in record_names) + "\n\n"
    destination.write_text(header + "\n".join(selected) + "\n}\n", encoding="utf-8")

legacy = """package pe.boston.erp;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Consultas de tablero y catálogos legadas, pendientes de separar por caso de uso. */
@RestController @RequestMapping("/api/erp/v1")
public class ErpV1Controller {
  private final ErpV1Service servicio;
  public ErpV1Controller(ErpV1Service servicio) { this.servicio=servicio; }
"""
remaining = [line for line in endpoints if 'Mapping("/resumen")' in line or 'Mapping("/catalogos")' in line]
source.write_text(legacy + "\n".join(remaining) + "\n}\n", encoding="utf-8")
