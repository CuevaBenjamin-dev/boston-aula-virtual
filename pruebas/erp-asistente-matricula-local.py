"""Comprueba borradores, alta definitiva y pago externo sin conservar registros de prueba."""
import csv
import http.cookiejar
import json
import re
import subprocess
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import unquote, urlencode, urljoin
from urllib.request import HTTPCookieProcessor, Request, build_opener
from uuid import uuid4

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
    body = urlencode({"username": cuenta["usuario"], "password": cuenta["clave"], "_csrf": token.group(1)}).encode()
    with opener.open(Request(login, data=body, method="POST"), timeout=20) as response:
        response.read()

    def api(path, method="GET", payload=None, expected=200):
        xsrf = next((unquote(c.value) for c in cookies if c.name == "XSRF-TOKEN"), "")
        request = Request(BASE + "/api/erp-web/v1" + path,
                          data=json.dumps(payload).encode() if payload is not None else None,
                          method=method, headers={"Content-Type": "application/json", "X-XSRF-TOKEN": xsrf})
        try:
            with opener.open(request, timeout=20) as response:
                status, content = response.status, response.read()
        except HTTPError as error:
            status, content = error.code, error.read()
        assert status == expected, f"{method} {path}: HTTP {status}, esperado {expected}: {content[:300]!r}"
        return json.loads(content) if content else None

    ids = []
    try:
        catalogos = api("/catalogos")
        periodo = next(p["id"] for p in catalogos["periodos"] if p["habilitado"])
        grado = next(g["id"] for g in catalogos["grados"] if g["habilitado"])
        assert api("/estudiantes/verificar?" + urlencode({"tipoDocumento": "PROVISIONAL", "documento": "SIN-" + uuid4().hex}))["encontrado"] is False
        inicial = int(sql("SELECT count(*) FROM estudiantes"))
        for pagado in (False, True):
            sufijo = uuid4().hex[:10]
            datos = {
                "procedencia": "NUEVO", "usarExistente": False, "estudianteId": "",
                "estudiante": {"nombres": "Prueba", "apellidos": "Asistente " + sufijo,
                               "tipoDocumento": "PROVISIONAL", "documento": "A" + sufijo,
                               "fechaNacimiento": "2015-02-03"},
                "periodoId": periodo, "gradoId": grado, "seccionId": "", "detalle": "",
                "apoderado": {"nombres": "Responsable", "apellidos": "Prueba " + sufijo,
                              "tipoDocumento": "PROVISIONAL", "documento": "R" + sufijo,
                              "telefono": "", "correo": ""},
                "vinculo": "Tutor", "emergencia": True, "responsableFinanciero": "MISMO_APODERADO" if pagado else "SIN_DESIGNAR",
                "financiero": {}, "pagoEstado": "REGISTRADO" if pagado else "PENDIENTE",
                "pagoMedio": "TRANSFERENCIA" if pagado else "",
                "pagoMonto": "123.45" if pagado else ""
            }
            borrador = api("/matriculas/borradores", "POST", {"paso": 2, "datos": datos}, 201)["id"]
            ids.append({"borrador": borrador, "documento": "A" + sufijo})
            api("/matriculas/borradores/" + borrador, "PUT", {"paso": 3, "datos": datos})
            assert api("/matriculas/borradores/" + borrador)["datos"]["estudiante"]["apellidos"] == "Asistente " + sufijo
            assert int(sql("SELECT count(*) FROM estudiantes")) == inicial + sum("matricula" in x for x in ids)
            matricula = api("/matriculas/borradores/" + borrador + "/finalizar", "POST")["id"]
            ids[-1]["matricula"] = matricula
            ficha = api("/matriculas/" + matricula)
            ids[-1]["estudiante"] = ficha["estudiante_id"]
            ids[-1]["apoderado"] = ficha["apoderados"][0]["persona_id"]
            assert ficha["estado"] == "MATRICULADO" and ficha["codigo"].startswith("BOS-")
            assert ficha["pago_estado"] == ("REGISTRADO" if pagado else "PENDIENTE")
            verificada = api("/estudiantes/verificar?" + urlencode({"tipoDocumento": "PROVISIONAL", "documento": "A" + sufijo}))
            assert verificada["encontrado"] and verificada["matriculado"]
            assert verificada["estudiante"]["id"] == ids[-1]["estudiante"]
            assert verificada["estudiante"]["edad"] is not None
            assert verificada["historial"] and verificada["apoderado"]["documento"] == "R" + sufijo
            assert any(c["id"] == ids[-1]["estudiante"] for c in api("/estudiantes/candidatos?buscar=A" + sufijo))
            if pagado:
                assert ficha["responsable_financiero_id"] == ids[-1]["apoderado"]
            assert api("/matriculas/borradores/" + borrador + "/finalizar", "POST")["id"] == matricula
            if not pagado:
                api("/matriculas/" + matricula + "/pago-externo", "POST", {"medio": "EFECTIVO", "monto": "99.50"})
                assert api("/matriculas/" + matricula)["pago_estado"] == "REGISTRADO"
        # Una matrícula cancelada conserva al solicitante sin código y permite reutilizarlo.
        sufijo = uuid4().hex[:10]
        documento = "A" + sufijo
        cancelada = api("/matriculas/nueva", "POST", {
            "estudiante": {"nombres": "Prueba", "apellidos": "Reingreso " + sufijo,
                            "tipoDocumento": "PROVISIONAL", "documento": documento},
            "periodoId": periodo, "gradoId": grado
        }, 201)["id"]
        reingreso = {"cancelada": cancelada, "documento": documento}
        ids.append(reingreso)
        ficha_cancelada = api("/matriculas/" + cancelada)
        reingreso["estudiante"] = ficha_cancelada["estudiante_id"]
        assert ficha_cancelada["codigo"] is None
        preliminar = api("/estudiantes/verificar?" + urlencode({"tipoDocumento": "PROVISIONAL", "documento": documento}))
        assert preliminar["encontrado"] and not preliminar["matriculado"]
        assert preliminar["procesoActivo"]["id"] == cancelada
        assert any(c["id"] == reingreso["estudiante"] for c in api("/estudiantes/candidatos?buscar=" + documento))
        api("/matriculas/" + cancelada + "/cancelar", "POST", {})
        assert api("/estudiantes/" + reingreso["estudiante"] + "/verificar")["procesoActivo"] is None
        datos = {
            "procedencia": "NUEVO", "usarExistente": False, "estudianteId": "",
            "estudiante": {"nombres": "Prueba", "apellidos": "Reingreso " + sufijo,
                           "tipoDocumento": "PROVISIONAL", "documento": documento,
                           "fechaNacimiento": "2015-02-03"},
            "periodoId": periodo, "gradoId": grado, "seccionId": "", "detalle": "",
            "apoderado": {"nombres": "Responsable", "apellidos": "Reingreso " + sufijo,
                          "tipoDocumento": "PROVISIONAL", "documento": "R" + sufijo,
                          "telefono": "", "correo": ""},
            "vinculo": "Tutor", "emergencia": True, "responsableFinanciero": "SIN_DESIGNAR",
            "financiero": {}, "pagoEstado": "PENDIENTE", "pagoMedio": "", "pagoMonto": ""
        }
        reingreso["borrador"] = api("/matriculas/borradores", "POST", {"paso": 3, "datos": datos}, 201)["id"]
        reingreso["matricula"] = api("/matriculas/borradores/" + reingreso["borrador"] + "/finalizar", "POST")["id"]
        ficha = api("/matriculas/" + reingreso["matricula"])
        reingreso["apoderado"] = ficha["apoderados"][0]["persona_id"]
        assert ficha["estudiante_id"] == reingreso["estudiante"] and ficha["codigo"].startswith("BOS-")
        assert api("/matriculas/" + cancelada)["estado"] == "CANCELADA"
        print("Borrador, código al confirmar, ambos caminos de pago y reingreso tras cancelación: correctos.")
    finally:
        for item in reversed(ids):
            borrador = item.get("borrador")
            matricula = item.get("matricula")
            cancelada = item.get("cancelada")
            estudiante = item.get("estudiante")
            if matricula and not estudiante:
                estudiante = sql(f"SELECT estudiante_id FROM matriculas WHERE id='{matricula}'")
            if estudiante:
                apoderado = item.get("apoderado")
                if not apoderado:
                    apoderado = sql(f"SELECT p.id FROM personas p JOIN apoderados a ON a.persona_id=p.id JOIN vinculos_estudiante_apoderado v ON v.apoderado_id=a.id WHERE v.estudiante_id='{estudiante}' LIMIT 1")
                persona = sql(f"SELECT persona_id FROM estudiantes WHERE id='{estudiante}'")
                entidades = ",".join(f"'{x}'" for x in (estudiante, matricula, cancelada, apoderado, persona) if x)
                matriculas = ",".join(f"'{x}'" for x in (matricula, cancelada) if x)
                sql("BEGIN; " +
                    (f"DELETE FROM borradores_matricula WHERE id='{borrador}'; " if borrador else "") +
                    f"DELETE FROM historial_matriculas WHERE matricula_id IN ({matriculas}); " +
                    f"DELETE FROM auditoria_erp WHERE entidad_id IN ({entidades}); " +
                    f"DELETE FROM vinculos_estudiante_apoderado WHERE estudiante_id='{estudiante}'; " +
                    f"DELETE FROM matriculas WHERE id IN ({matriculas}); " +
                    f"DELETE FROM estudiantes WHERE id='{estudiante}'; " +
                    (f"DELETE FROM apoderados WHERE persona_id='{apoderado}'; " if apoderado else "") +
                    f"DELETE FROM personas WHERE id='{persona}'; " +
                    (f"DELETE FROM personas WHERE id='{apoderado}'; " if apoderado else "") + "COMMIT;")
            elif borrador:
                sql(f"DELETE FROM borradores_matricula WHERE id='{borrador}'")


if __name__ == "__main__":
    main()
