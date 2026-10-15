"""Genera un inventario OpenAPI local a partir de los controladores Spring.

Mantiene las rutas alineadas con @RequestMapping/@*Mapping. Las reglas de
autorización y los cuerpos de dominio se explican en contratos-api.md.
"""
from pathlib import Path
import re

RAIZ = Path(__file__).resolve().parents[1]
CONTROLADORES = [
    RAIZ / "identidad/src/main/java",
    RAIZ / "api-erp/src/main/java",
    RAIZ / "api-aula/src/main/java",
]
RUTA = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?')
PARAMETRO = re.compile(r"\{([^}]+)\}")


def esc(valor: str) -> str:
    return "'" + valor.replace("'", "''") + "'"


operaciones = {}
for carpeta in CONTROLADORES:
    for archivo in carpeta.rglob("*Controller.java"):
        fuente = archivo.read_text(encoding="utf-8")
        base = re.search(r'@RequestMapping\("([^"]+)"\)', fuente)
        if not base:
            continue
        for indice, match in enumerate(RUTA.finditer(fuente), 1):
            metodo = match.group(1).lower()
            ruta = base.group(1) + (match.group(2) or "")
            resto = fuente[match.end():]
            firma = resto.split("{", 1)[0]
            cuerpo = "@RequestBody" in firma
            nombre = re.search(r"\bpublic\s+[\w<>?, ]+\s+(\w+)\s*\(", firma)
            resumen = nombre.group(1) if nombre else f"operacion_{indice}"
            operaciones.setdefault(ruta, []).append((metodo, f"{archivo.stem}_{indice}", resumen, cuerpo))

lineas = [
    "openapi: 3.1.0",
    "info:",
    "  title: Boston Aula Virtual V1 - API local",
    "  version: 1.0.0",
    "  description: Rutas reales de Identidad, ERP y Aula en Docker Compose. Consulte contratos-api.md para reglas de negocio.",
    "servers:",
    "  - url: http://localhost:8088",
    "    description: Proxy local y BFF del Aula",
    "paths:",
]
for ruta in sorted(operaciones):
    lineas.append(f"  {esc(ruta)}:")
    for metodo, identificador, resumen, cuerpo in sorted(operaciones[ruta]):
        lineas += [f"    {metodo}:", f"      operationId: {identificador}", f"      summary: {esc(resumen.replace('_', ' ') + ' - ' + identificador.split('_')[0])}"]
        if ruta.startswith("/api/erp/"):
            lineas += ["      servers:", "        - url: http://api-erp:8080"]
        elif ruta.startswith("/api/identidad/"):
            lineas += ["      servers:", "        - url: http://localhost:9000"]
        if ruta != "/api/identidad/restablecer":
            esquema = "portador" if ruta.startswith("/api/erp/") or ruta.startswith("/api/identidad/") else "sesionBff"
            lineas += ["      security:", f"        - {esquema}: []"]
        parametros = PARAMETRO.findall(ruta)
        if parametros:
            lineas.append("      parameters:")
            for parametro in parametros:
                lineas += [f"        - name: {parametro}", "          in: path", "          required: true", "          schema:", "            type: string"]
        if cuerpo:
            lineas += ["      requestBody:", "        required: true", "        content:", "          application/json:", "            schema:", "              type: object", "              additionalProperties: true"]
        lineas += ["      responses:", "        '200':", "          description: Operación completada", "        '400':", "          description: Datos o transición inválidos", "        '401':", "          description: Sesión o token ausente", "        '403':", "          description: Recurso fuera del ámbito autorizado", "        '409':", "          description: Conflicto de versión o estado"]

lineas += [
    "components:",
    "  securitySchemes:",
    "    sesionBff:",
    "      type: apiKey",
    "      in: cookie",
    "      name: JSESSIONID",
    "      description: Los cambios requieren además X-XSRF-TOKEN obtenido de la cookie CSRF.",
    "    portador:",
    "      type: http",
    "      scheme: bearer",
    "      bearerFormat: JWT",
]
(RAIZ / "documentacion/openapi.yaml").write_text("\n".join(lineas) + "\n", encoding="utf-8")
print(f"{len(operaciones)} rutas, {sum(map(len, operaciones.values()))} operaciones")
