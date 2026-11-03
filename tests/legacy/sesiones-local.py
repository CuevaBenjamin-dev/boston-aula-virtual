"""Comprueba que la sesión local sobrevive reinicios y termina al cerrar sesión."""
import runpy
import json
import subprocess
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
Session = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))["Session"]


def esperar(url):
    for _ in range(90):
        try:
            with urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (HTTPError, URLError, OSError, TimeoutError):
            pass
        time.sleep(1)
    raise AssertionError(f"No volvió a estar disponible: {url}")


def main():
    esperar("http://localhost:9000/actuator/health")
    esperar("http://localhost:8088/actuator/health")
    with urlopen(Request("http://localhost:9000/login", headers={"Cookie": "IDENTIDAD_SESSION=AA=="}), timeout=20) as response:
        assert response.status == 200
    try:
        urlopen(Request("http://localhost:8088/api/sesion", headers={"Cookie": "AULA_SESSION=AA=="}), timeout=20)
        raise AssertionError("Una cookie inválida no debe dar acceso al Aula")
    except HTTPError as error:
        assert error.code == 401, f"La cookie inválida causó HTTP {error.code}"
    sesion = Session("estudiante.demo")
    cookies = {cookie.name: cookie for cookie in sesion.cookies}
    assert cookies["AULA_SESSION"].expires is not None
    assert cookies["IDENTIDAD_SESSION"].expires is not None
    assert sesion.api("/perfil")["tipo"] == "ESTUDIANTE"
    assert isinstance(sesion.api("/mensajes/directorio"), list)

    subprocess.run(["docker", "compose", "restart", "identidad", "api-aula"], cwd=ROOT, check=True)
    esperar("http://localhost:9000/actuator/health")
    esperar("http://localhost:8088/actuator/health")
    assert sesion.api("/perfil")["tipo"] == "ESTUDIANTE"
    assert isinstance(sesion.api("/mensajes/directorio"), list)
    print("Sesión conservada después de reiniciar Identidad y Aula.")

    with sesion.opener.open("http://localhost:8088/api/sesion", timeout=20) as response:
        csrf = json.load(response)["csrfToken"]
    solicitud = Request("http://localhost:8088/logout", data=urlencode({"_csrf": csrf}).encode(), method="POST")
    with sesion.opener.open(solicitud, timeout=20) as response:
        response.read()
    sesion.api("/perfil", expected=401)
    print("Cierre de sesión invalidó el acceso.")


if __name__ == "__main__":
    main()
