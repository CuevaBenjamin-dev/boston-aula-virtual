"""Verifica cancelación pendiente y reservada sin conservar datos de prueba."""
import csv
import http.cookiejar
import json
import re
import subprocess
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import unquote, urlencode, urljoin
from urllib.request import HTTPCookieProcessor, Request, build_opener
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8089"


def sql(query):
    result = subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "boston",
                             "-d", "erp_boston", "-v", "ON_ERROR_STOP=1", "-Atc", query],
                            cwd=ROOT, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def main():
    with (ROOT / "cuentas-iniciales.local.txt").open(encoding="utf-8", newline="") as archivo:
        cuenta = next(f for f in csv.DictReader(archivo, delimiter="\t") if f["tipo"] == "COORDINACION")
    cookies = http.cookiejar.CookieJar()
    opener = build_opener(HTTPCookieProcessor(cookies))
    with opener.open(BASE + "/oauth2/authorization/boston-erp", timeout=20) as response:
        form, login = response.read().decode(), urljoin(response.geturl(), "/login")
    token = re.search(r'name="_csrf" value="([^"]+)"', form)
    assert token, "Formulario de acceso no disponible"
    body = urlencode({"username": cuenta["usuario"], "password": cuenta["clave"],
                      "_csrf": token.group(1)}).encode()
    with opener.open(Request(login, data=body, method="POST"), timeout=20) as response:
        response.read()

    def api(path, method="GET", payload=None, expected=200):
        xsrf = next((unquote(c.value) for c in cookies if c.name == "XSRF-TOKEN"), "")
        headers = {"Content-Type": "application/json", "X-XSRF-TOKEN": xsrf}
        request = Request(BASE + "/api/erp-web/v1" + path,
                          data=json.dumps(payload).encode() if payload is not None else None,
                          method=method, headers=headers)
        try:
            with opener.open(request, timeout=20) as response:
                status, content = response.status, response.read()
        except HTTPError as error:
            status, content = error.code, error.read()
        assert status == expected, f"{method} {path}: HTTP {status}, esperado {expected}"
        return json.loads(content) if content else None

    ids = []
    estudiante = None
    try:
        catalogos = api("/catalogos")
        periodo = next(p["id"] for p in catalogos["periodos"] if p["habilitado"])
        grado = next(g["id"] for g in catalogos["grados"] if g["habilitado"])
        primero = UUID(api("/matriculas/nueva", "POST", {
            "estudiante": {"nombres": "Temporal", "apellidos": "Verificacion Codex"},
            "periodoId": periodo, "gradoId": grado, "procedenciaDetalle": "Prueba transitoria"
        }, 201)["id"])
        ids.append(primero)
        estudiante = UUID(api(f"/matriculas/{primero}")["estudiante_id"])
        api(f"/matriculas/{primero}/cancelar", "POST", {})
        cancelada = api(f"/matriculas/{primero}")
        assert cancelada["estado"] == "CANCELADA" and cancelada["vigente"] is False
        assert any(h["accion"] == "CANCELADA" for h in cancelada["historial"])
        api(f"/matriculas/{primero}/confirmar", "POST", {"confirmacionExplicita": True}, 400)

        segundo = UUID(api("/matriculas", "POST", {
            "estudianteId": str(estudiante), "periodoId": periodo, "gradoId": grado,
            "procedencia": "ANTIGUO"
        }, 201)["id"])
        ids.append(segundo)
        api(f"/matriculas/{segundo}/reservar", "POST", {})
        api(f"/matriculas/{segundo}/cancelar", "POST", {})
        assert api(f"/matriculas/{segundo}")["estado"] == "CANCELADA"
        print("Cancelación pendiente y reservada, bloqueo de confirmación y nuevo intento: correctos.")
    finally:
        if estudiante is None and ids:
            estudiante = UUID(sql(f"SELECT estudiante_id FROM matriculas WHERE id='{ids[0]}'"))
        if estudiante:
            persona = UUID(sql(f"SELECT persona_id FROM estudiantes WHERE id='{estudiante}'"))
            matriculas = ",".join(f"'{item}'" for item in ids)
            objetivo = f"entidad_id='{estudiante}'" + (f" OR entidad_id IN ({matriculas})" if ids else "")
            sql("BEGIN; " +
                f"DELETE FROM auditoria_erp WHERE {objetivo}; " +
                (f"DELETE FROM historial_matriculas WHERE matricula_id IN ({matriculas}); " if ids else "") +
                (f"DELETE FROM matriculas WHERE id IN ({matriculas}); " if ids else "") +
                f"DELETE FROM estudiantes WHERE id='{estudiante}'; " +
                f"DELETE FROM personas WHERE id='{persona}'; COMMIT;")


if __name__ == "__main__":
    main()
