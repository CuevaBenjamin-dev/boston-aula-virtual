"""Verificación funcional local de CP-05. Limpia únicamente sus registros transitorios."""
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import re
import subprocess
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://localhost:8088"
ENV = {}
if (ROOT / ".env").exists():
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            ENV[k.strip()] = v.strip().strip('"').strip("'")
PASSWORD = ENV.get("SEED_PASSWORD", "cambiar-local-cuentas")


def ident(text):
    return str(uuid.UUID(hex=hashlib.md5(text.encode()).hexdigest()))


class Session:
    def __init__(self, username):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cookies))
        response = self.opener.open(BASE + "/oauth2/authorization/boston", timeout=15)
        form = response.read().decode()
        token = re.search(r'name="_csrf" value="([^"]+)"', form)
        if not token:
            raise AssertionError(f"Formulario de acceso no disponible para {username}")
        body = urlencode({"username": username, "password": PASSWORD, "_csrf": token.group(1)}).encode()
        self.opener.open(Request("http://localhost:9000/login", data=body, method="POST"), timeout=15).read()
        perfil = self.api("/perfil")
        if not isinstance(perfil, dict):
            raise AssertionError(f"Sesión no iniciada para {username}")

    def api(self, path, method="GET", body=None, expected=200):
        payload = json.dumps(body).encode() if body is not None else None
        headers = {"Content-Type": "application/json"} if payload is not None else {}
        if method not in ("GET", "HEAD"):
            csrf = next((cookie.value for cookie in self.cookies if cookie.name == "XSRF-TOKEN"), None)
            if csrf:
                headers["X-XSRF-TOKEN"] = csrf
        request = Request(BASE + "/api/aula" + path, data=payload, method=method, headers=headers)
        try:
            response = self.opener.open(request, timeout=20)
            status = response.status
            content = response.read()
        except HTTPError as error:
            status = error.code
            content = error.read()
        if status != expected:
            raise AssertionError(f"{method} {path}: HTTP {status}, esperado {expected}; {content[:300]!r}")
        return json.loads(content) if content else None


def upload(session, parent, name, content, replace=None):
    start = session.api(f"/tareas/{parent}/archivos", "POST", {
        "nombre": name, "tipo": "application/pdf", "tamano": len(content), "sustituyeId": replace
    })
    file_id = str(uuid.UUID(start["id"]))
    part = session.api(f"/archivos/{file_id}/partes/1")
    with urlopen(Request(part["url"], data=content, method="PUT"), timeout=20) as response:
        etag = response.headers.get("ETag")
    if not etag:
        raise AssertionError("S3 no devolvió ETag")
    session.api(f"/archivos/{file_id}/completar", "POST", {"partes": [{"numero": 1, "etag": etag}]})
    return file_id


def sql_cleanup(task_id, material_id):
    statements = ["BEGIN;"]
    if task_id:
        tid = str(uuid.UUID(task_id))
        ts = f"SELECT id FROM tareas WHERE id='{tid}'::uuid OR tarea_anterior_id='{tid}'::uuid"
        statements += [
            f"DELETE FROM registros_auditoria WHERE entidad_id IN ({ts}) OR entidad_id IN (SELECT id FROM archivos_tarea WHERE tarea_id IN ({ts})) OR entidad_id IN (SELECT id FROM entregas WHERE tarea_id IN ({ts})) OR entidad_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id IN ({ts}));",
            f"DELETE FROM eventos_notificacion_entrega WHERE notificacion_id IN (SELECT id FROM notificaciones_tarea WHERE tarea_id IN ({ts}));",
            f"DELETE FROM notificaciones_tarea WHERE tarea_id IN ({ts});",
            f"DELETE FROM archivos_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id IN ({ts}));",
            f"DELETE FROM historial_calificaciones WHERE calificacion_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id IN ({ts}));",
            f"DELETE FROM calificaciones_tarea WHERE tarea_id IN ({ts});",
            f"DELETE FROM archivos_tarea WHERE tarea_id IN ({ts});",
            f"DELETE FROM entregas WHERE tarea_id IN ({ts});",
            f"DELETE FROM tareas WHERE tarea_anterior_id='{tid}'::uuid;",
            f"DELETE FROM tareas WHERE id='{tid}'::uuid;",
        ]
    if material_id:
        mid = str(uuid.UUID(material_id))
        statements += [
            f"DELETE FROM registros_auditoria WHERE entidad_id IN (SELECT id FROM archivos_material WHERE material_id='{mid}'::uuid);",
            f"DELETE FROM archivos_material WHERE material_id='{mid}'::uuid;",
            f"DELETE FROM destinos_material WHERE material_id='{mid}'::uuid;",
            f"DELETE FROM registros_auditoria WHERE entidad_id='{mid}'::uuid;",
            f"DELETE FROM materiales_curso WHERE id='{mid}'::uuid;",
        ]
    statements.append("COMMIT;")
    subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", "boston", "-d", "aula_boston"], cwd=ROOT,
                   input="\n".join(statements), text=True, check=True, capture_output=True)


def check(condition, message):
    if not condition:
        raise AssertionError(message)
    print("OK:", message)


def main():
    teacher = Session("docente.demo")
    first = Session("estudiante.demo")
    second = Session("otro.demo")
    third = Session("estudiante.p5a3.demo")
    other_section = Session("estudiante.p5b.demo")
    outside = Session("estudiante.s5a.demo")
    p5a = ident("cp05:oferta:P5A:Matemática")
    p5b = ident("cp05:oferta:P5B:Matemática")
    task_id = material_id = None
    try:
        due = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=5)).isoformat()
        task_id = str(uuid.UUID(teacher.api(f"/cursos/{p5a}/tareas", "POST", {
            "titulo": "CP05 prueba temporal de versiones", "instrucciones": "Respuesta ficticia de prueba.",
            "fechaLimite": due
        })["id"]))
        check(all(x["id"] != task_id for x in first.api(f"/cursos/{p5a}/tareas")), "Borrador invisible para estudiante")

        pdf = b"%PDF-1.4\nArchivo ficticio transitorio para comprobar adjuntos CP05.\n%%EOF"
        old_file = upload(teacher, task_id, "temporal-uno.pdf", pdf)
        second_file = upload(teacher, task_id, "temporal-dos.pdf", pdf)
        check(len(teacher.api(f"/tareas/{task_id}/archivos")) == 2, "Dos adjuntos coexisten")
        upload(teacher, task_id, "temporal-sustituto.pdf", pdf, old_file)
        files = teacher.api(f"/tareas/{task_id}/archivos")
        check(len(files) == 2 and any(x["id"] == second_file for x in files), "Sustitución conserva el otro archivo")
        teacher.api(f"/archivos/{second_file}", "DELETE", expected=200)
        check(len(teacher.api(f"/tareas/{task_id}/archivos")) == 1, "Retiro individual del adjunto")

        teacher.api(f"/tareas/{task_id}/publicar", "POST", {})
        notices = [n for n in first.api("/notificaciones") if n.get("tarea_id") == task_id]
        check(len(notices) == 1 and notices[0]["tipo"] == "PUBLICADA", "Publicación notifica una vez al estudiante")
        check(not any(n.get("tarea_id") == task_id for n in other_section.api("/notificaciones")), "Publicación aislada de otra sección")

        for client in (first, second):
            client.api(f"/tareas/{task_id}/entregas", "POST", {"respuesta": "Entrega ficticia CP05"})
        grouped = [n for n in teacher.api("/notificaciones") if n.get("tarea_id") == task_id and n["tipo"] == "ENTREGAS"]
        check(len(grouped) == 1 and grouped[0]["nuevas_entregas"] == 2 and grouped[0]["leida_en"] is None,
              "Dos entregas generan un aviso activo agrupado")
        teacher.api(f"/notificaciones/{grouped[0]['id']}/leer", "POST", {})
        third.api(f"/tareas/{task_id}/entregas", "POST", {"respuesta": "Entrega posterior ficticia CP05"})
        grouped = [n for n in teacher.api("/notificaciones") if n.get("tarea_id") == task_id and n["tipo"] == "ENTREGAS"]
        check(len(grouped) == 2 and sum(n["leida_en"] is None for n in grouped) == 1 and
              next(n for n in grouped if n["leida_en"] is None)["nuevas_entregas"] == 1,
              "Una entrega posterior crea un aviso nuevo y conserva el leído")

        teacher.api(f"/tareas/{task_id}/calificacion", "PUT", {
            "estudianteId": ident("cp05:estudiante:1:1"), "valor": 18, "comentario": "Nota de prueba anterior", "version": 0
        })
        old = teacher.api(f"/tareas/{task_id}")
        operation = str(uuid.uuid4())
        edited = {"titulo": "CP05 prueba temporal de versiones (nueva)", "instrucciones": "Nueva instrucción ficticia.",
                  "fechaLimite": due, "version": old["version"], "cursoId": p5b,
                  "confirmarReinicio": True, "operacionId": operation}
        successor = teacher.api(f"/tareas/{task_id}", "PUT", edited)
        successor_id = str(uuid.UUID(successor["id"]))
        check(successor_id != task_id and successor["tarea_anterior_id"] == task_id,
              "Edición con entregas crea una sucesora vinculada")
        check(len(teacher.api(f"/tareas/{task_id}/entregas")) == 3 and
              len(teacher.api(f"/tareas/{successor_id}/entregas")) == 0,
              "Entregas anteriores permanecen separadas")
        check(any(n["tarea_id"] == task_id for n in teacher.api(f"/cursos/{p5a}/notas")) and
              not any(n["tarea_id"] == successor_id for n in teacher.api(f"/cursos/{p5b}/notas")),
              "La nota anterior no se copia a la nueva sección")
        check(teacher.api(f"/tareas/{task_id}", "PUT", edited)["id"] == successor_id,
              "Reintento conserva una sola versión sucesora")
        first.api(f"/tareas/{successor_id}", expected=403)
        other_section.api(f"/tareas/{task_id}", expected=403)
        check(any(n.get("tarea_id") == successor_id for n in other_section.api("/notificaciones")),
              "Nueva sección recibe aviso y acceso a la versión vigente")

        material_id = str(uuid.UUID(teacher.api("/materiales", "POST", {
            "nombre": "CP05 prueba temporal compartida", "descripcion": "Material ficticio.",
            "enlace": "http://localhost:8088/", "cursoIds": [p5a, p5b]
        })["id"]))
        check(not any(m["id"] == material_id for m in first.api(f"/cursos/{p5a}/materiales")),
              "Borrador de material invisible")
        teacher.api(f"/materiales/{material_id}/publicar", "POST", {})
        check(any(m["id"] == material_id for m in first.api(f"/cursos/{p5a}/materiales")) and
              any(m["id"] == material_id for m in other_section.api(f"/cursos/{p5b}/materiales")),
              "Material compartido visible en ambas secciones autorizadas")
        outside.api(f"/materiales/{material_id}", expected=403)
        teacher.api(f"/materiales/{material_id}/ocultar", "POST", {})
        first.api(f"/materiales/{material_id}", expected=403)
        check(not any(m["id"] == material_id for m in other_section.api(f"/cursos/{p5b}/materiales")),
              "Material oculto inaccesible en ambas secciones")
    finally:
        sql_cleanup(task_id, material_id)


if __name__ == "__main__":
    main()
