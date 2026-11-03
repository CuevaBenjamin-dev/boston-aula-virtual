"""Comprueba que la sesión del ERP sobrevive a la recreación de su API local."""
import csv
import http.cookiejar
import json
import re
import subprocess
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin
from urllib.request import HTTPCookieProcessor, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8089"


def cuenta_coordinacion():
    with (ROOT / "cuentas-iniciales.local.txt").open(encoding="utf-8", newline="") as archivo:
        return next((fila for fila in csv.DictReader(archivo, delimiter="\t")
                     if fila["tipo"] == "COORDINACION"), None)


def sesion_erp(opener):
    with opener.open(BASE + "/api/erp-web/sesion", timeout=20) as respuesta:
        return json.load(respuesta)


def esperar_erp(opener):
    for _ in range(90):
        try:
            return sesion_erp(opener)
        except (HTTPError, URLError, OSError, TimeoutError):
            time.sleep(1)
    raise AssertionError("El ERP no volvió a estar disponible")


def main():
    cuenta = cuenta_coordinacion()
    assert cuenta, "Falta la cuenta local de coordinación"
    cookies = http.cookiejar.CookieJar()
    opener = build_opener(HTTPCookieProcessor(cookies))
    with opener.open(BASE + "/oauth2/authorization/boston-erp", timeout=20) as respuesta:
        formulario = respuesta.read().decode()
        destino_login = urljoin(respuesta.geturl(), "/login")
    csrf = re.search(r'name="_csrf" value="([^"]+)"', formulario)
    assert csrf, "No se encontró el formulario de Boston Identidad"
    datos = urlencode({"username": cuenta["usuario"], "password": cuenta["clave"],
                       "_csrf": csrf.group(1)}).encode()
    with opener.open(Request(destino_login, data=datos, method="POST"), timeout=20) as respuesta:
        respuesta.read()
    antes = sesion_erp(opener)
    assert antes.get("autenticado") and antes.get("autorizado"), "No inició la sesión de coordinación"
    assert any(c.name == "ERP_SESSION" and c.expires for c in cookies), "La cookie ERP no es persistente"

    subprocess.run(["docker", "compose", "up", "-d", "--force-recreate", "--no-deps", "api-erp"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)
    despues = esperar_erp(opener)
    assert despues.get("autenticado") and despues.get("autorizado"), "La sesión se perdió al recrear ERP"
    assert despues.get("usuario") == antes.get("usuario"), "Cambió la cuenta de la sesión"
    print("Sesión ERP conservada tras recrear api-erp.")

    solicitud = Request(BASE + "/logout", data=urlencode({"_csrf": despues["csrf"]}).encode(),
                        method="POST")
    with opener.open(solicitud, timeout=20) as respuesta:
        respuesta.read()
    assert not sesion_erp(opener).get("autenticado"), "El cierre de sesión no revocó el acceso"
    print("Cierre de sesión ERP invalidó el acceso.")


if __name__ == "__main__":
    main()
