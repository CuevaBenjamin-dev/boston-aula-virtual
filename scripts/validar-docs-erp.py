"""Validación documental ligera del ERP; solo biblioteca estándar de Python."""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
ERP = ROOT / "docs" / "erp"
ENGINEERING = ERP / "ingenieria"
MODULES = ("personas", "matriculas", "oferta", "asignaciones", "documentos")
MODULE_FILES = (
    "README.md", "ESPECIFICACION.md", "ARQUITECTURA.md",
    "MODELO_DATOS.md", "PRUEBAS.md", "ESTADO.md",
)
GLOBAL_FILES = (
    "README.md", "MANUAL_DESARROLLO_MODULAR.md",
    "ESTANDAR_ARQUITECTURA_MODULAR.md", "ESTANDAR_POSTGRESQL.md",
    "ESTANDAR_API_INTEGRACIONES.md", "ESTANDAR_SEGURIDAD_PRIVACIDAD.md",
    "ESTANDAR_UX_UI.md", "GUIA_IMPLEMENTACION.md",
    "ESTANDAR_VERIFICACION.md", "LECCIONES_APRENDIDAS.md",
)
CHECKS = (
    "Requisitos", "Análisis funcional", "Arquitectura",
    "Implementación", "Verificación y pruebas",
)
LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
RULE = re.compile(r"^\|\s*(RI-PER-\d{3})\s*\|", re.MULTILINE)


def anchors(markdown: str) -> set[str]:
    result: set[str] = set()
    counts: dict[str, int] = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", markdown, re.MULTILINE):
        plain = re.sub(r"\[([^]]+)\]\([^)]+\)", r"\1", heading)
        plain = plain.replace("`", "").lower()
        slug = "".join(c for c in plain if c in " -_" or unicodedata.category(c)[0] in "LN").strip().replace(" ", "-")
        number = counts.get(slug, 0)
        counts[slug] = number + 1
        result.add(slug if number == 0 else f"{slug}-{number}")
    return result


def main() -> int:
    errors: list[str] = []
    required = [ERP / "README.md", ERP / "POLITICAS_TRANSVERSALES.md"]
    required += [ENGINEERING / name for name in GLOBAL_FILES]
    required += [ERP / "modulos" / module / name for module in MODULES for name in MODULE_FILES]
    required += [ROOT / "AGENTS.md", ROOT / "apps" / "erp" / "AGENTS.md"]
    for path in required:
        if not path.is_file() or not path.read_text(encoding="utf-8").strip():
            errors.append(f"Falta o está vacío: {path.relative_to(ROOT)}")

    for module in MODULES:
        state = ERP / "modulos" / module / "ESTADO.md"
        if not state.is_file():
            continue
        rows = re.findall(r"^\|\s*([^|]+?)\s*\|\s*(Pendiente|En progreso|Bloqueada|Completada)\s*\|", state.read_text(encoding="utf-8"), re.MULTILINE)
        names = [name.strip() for name, _ in rows]
        if names != list(CHECKS):
            errors.append(f"Cinco verificaciones incompletas o desordenadas: {state.relative_to(ROOT)} ({names})")

    spec = ERP / "modulos" / "personas" / "ESPECIFICACION.md"
    if spec.is_file():
        rules = RULE.findall(spec.read_text(encoding="utf-8"))
        expected = [f"RI-PER-{n:03d}" for n in range(1, 38)]
        if rules != expected:
            errors.append("RI-PER-001–037 faltan, están repetidos o fuera de orden en Personas")

    documents = [p for p in ERP.rglob("*.md") if "historico" not in p.parts]
    documents += [ROOT / "AGENTS.md", ROOT / "apps" / "erp" / "AGENTS.md"]
    for doc in documents:
        content = doc.read_text(encoding="utf-8")
        # Los bloques de código pueden mostrar ejemplos de Markdown, no enlaces reales.
        content = re.sub(r"```.*?```", "", content, flags=re.DOTALL)
        for raw in LINK.findall(content):
            target = unquote(raw.split()[0].strip("<>"))
            if target.startswith(("https://", "http://", "mailto:", "#")):
                continue
            file_part = target.split("#", 1)[0]
            if not file_part:
                continue
            path = (doc.parent / file_part).resolve()
            if not path.exists():
                errors.append(f"Enlace roto: {doc.relative_to(ROOT)} -> {raw}")
            elif "#" in target and path.suffix.lower() == ".md":
                fragment = target.split("#", 1)[1]
                if fragment and fragment not in anchors(path.read_text(encoding="utf-8")):
                    errors.append(f"Ancla rota: {doc.relative_to(ROOT)} -> {raw}")

    if errors:
        for error in errors:
            print("ERROR:", error)
        return 1
    print(f"OK: {len(required)} archivos obligatorios, {len(documents)} documentos, cinco verificaciones por módulo, RI-PER-001–037 y enlaces relativos existentes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
