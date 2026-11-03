"""Migración única de las pantallas ERP a carpetas por funcionalidad.

Se conserva este script como registro reproducible del cambio de rutas.
"""
from pathlib import Path
import re
import shutil

root = Path(__file__).resolve().parents[1] / "apps" / "erp" / "web" / "src"

destinations = {
    "App": "app/router/App.tsx",
    "api": "shared/api/client.ts",
    "ui": "shared/ui/index.tsx",
    "PageHeading": "shared/ui/PageHeading.tsx",
    "style": "shared/ui/style.css",
    "AsistenteMatricula": "modules/matriculas/pages/AsistenteMatricula.tsx",
    "Oferta": "modules/oferta/pages/Oferta.tsx",
    "CatalogoAsignaturas": "modules/oferta/components/CatalogoAsignaturas.tsx",
}
for name, target in destinations.items():
    source = root / (name + (".css" if name == "style" else ".ts" if name == "api" else ".tsx"))
    destination = root / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(source, destination)

pages = {
    "Inicio": "app/pages/Inicio.tsx",
    "Estudiantes": "modules/personas/pages/Estudiantes.tsx",
    "FichaEstudiante": "modules/personas/pages/FichaEstudiante.tsx",
    "Matriculas": "modules/matriculas/pages/Matriculas.tsx",
    "FichaMatricula": "modules/matriculas/pages/FichaMatricula.tsx",
    "Apoderados": "modules/personas/pages/Apoderados.tsx",
    "Importaciones": "modules/matriculas/pages/Importaciones.tsx",
    "Asignaturas": "modules/oferta/pages/Asignaturas.tsx",
    "Docentes": "modules/asignaciones/pages/Docentes.tsx",
}


def relative(source: Path, target: Path) -> str:
    import os
    value = os.path.relpath(target, source.parent).replace("\\", "/")
    if not value.startswith("."):
        value = "./" + value
    return re.sub(r"\.(tsx?|css)$", "", value)


def rewrite_imports(content: str, destination: Path) -> str:
    for old, new in destinations.items():
        content = content.replace("from './" + old + "'", "from '" + relative(destination, root / new) + "'")
    content = content.replace("import './style.css'", "import '" + relative(destination, root / destinations["style"]) + ".css'")
    return content


def selected_imports(imports: list[str], body: str, destination: Path) -> str:
    result = []
    for line in imports:
        if "{" not in line:
            continue
        prefix, rest = line.split("{", 1)
        values, suffix = rest.split("}", 1)
        kept = []
        for value in values.split(","):
            value = value.strip()
            identifier = value.replace("type ", "").split(" as ")[-1]
            if re.search(r"\b" + re.escape(identifier) + r"\b", body):
                kept.append(value)
        if kept:
            result.append(prefix + "{ " + ", ".join(kept) + " }" + suffix)
    return rewrite_imports("\n".join(result), destination)


for filename in ("academico.tsx", "gestion.tsx"):
    source = root / filename
    content = source.read_text(encoding="utf-8")
    lines = content.splitlines()
    imports = [line for line in lines if line.startswith("import ")]
    starts = [(i, re.match(r"export function (\w+)", line).group(1)) for i, line in enumerate(lines)
              if re.match(r"export function (\w+)", line)]
    for index, (start, name) in enumerate(starts):
        end = starts[index + 1][0] if index + 1 < len(starts) else len(lines)
        body = "\n".join(lines[start:end]).strip() + "\n"
        body = re.sub(r"^export \{ AsistenteMatricula as NuevaMatricula \}.*\n", "", body, flags=re.MULTILINE)
        destination = root / pages[name]
        destination.parent.mkdir(parents=True, exist_ok=True)
        header = selected_imports(imports, body, destination)
        destination.write_text(header + "\n\n" + body, encoding="utf-8")
    source.unlink()

for target in destinations.values():
    destination = root / target
    if destination.suffix in (".ts", ".tsx"):
        content = rewrite_imports(destination.read_text(encoding="utf-8"), destination)
        destination.write_text(content, encoding="utf-8")

app = root / destinations["App"]
content = app.read_text(encoding="utf-8")
content = content.replace("from './academico'", "from '" + relative(app, root / pages["Matriculas"]) + "'")
content = content.replace("from './gestion'", "from '" + relative(app, root / pages["Asignaturas"]) + "'")
content = content.replace("import { Inicio, Estudiantes, FichaEstudiante, Matriculas, FichaMatricula, NuevaMatricula }", "import { Matriculas }")
content = content.replace("import { Apoderados, Importaciones, Asignaturas, Docentes }", "import { Asignaturas }")
for name in ("Inicio", "Estudiantes", "FichaEstudiante", "FichaMatricula", "Apoderados", "Importaciones", "Docentes"):
    content = "import { " + name + " } from '" + relative(app, root / pages[name]) + "'\n" + content
content = "import { AsistenteMatricula as NuevaMatricula } from '" + relative(app, root / destinations["AsistenteMatricula"]) + "'\n" + content
app.write_text(content, encoding="utf-8")

main = root / "main.tsx"
main.write_text(rewrite_imports(main.read_text(encoding="utf-8"), main), encoding="utf-8")
