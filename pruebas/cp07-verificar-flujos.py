"""Pruebas locales focalizadas CP-07 con una tarea temporal y cuentas ficticias."""
import datetime as dt
import hashlib
import hmac
import runpy
import socket
import subprocess
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
original_getaddrinfo = socket.getaddrinfo


def prefer_ipv4_localhost(host, port, *args, **kwargs):
    if host == "localhost":
        host = "127.0.0.1"
    return original_getaddrinfo(host, port, *args, **kwargs)


socket.getaddrinfo = prefer_ipv4_localhost
helpers = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))
Session, check, ENV = helpers["Session"], helpers["check"], helpers["ENV"]


def sql(statement):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1",
         "-U", "boston", "-d", "aula_boston", "-At"], cwd=ROOT,
        input=statement, text=True, capture_output=True, check=True,
    ).stdout.strip()


def erp_student(code):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "boston", "-d", "erp_boston", "-At",
         "-c", f"SELECT id FROM estudiantes WHERE codigo='{code}';"], cwd=ROOT,
        text=True, capture_output=True, check=True,
    ).stdout.strip()


def upload(session, draft, name, mime, content, replace=None, expected=200, completion_expected=200):
    start = session.api(f"/preparaciones/{draft}/archivos", "POST", {
        "nombre": name, "tipo": mime, "tamano": len(content), "sustituyeId": replace,
    }, expected=expected)
    if expected != 200:
        return None
    file_id = start["id"]
    part = session.api(f"/archivos/{file_id}/partes/1")
    with urlopen(Request(part["url"], data=content, method="PUT"), timeout=30) as response:
        etag = response.headers["ETag"]
    session.api(f"/archivos/{file_id}/completar", "POST", {"partes": [{"numero": 1, "etag": etag}]}, expected=completion_expected)
    return file_id


def delete_s3(key):
    access = ENV.get("S3_ACCESS_KEY", "local-aula")
    secret = ENV.get("S3_SECRET_KEY", "cambiar-local-s3")
    now = dt.datetime.now(dt.timezone.utc)
    stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
    path = "/aula-privada/" + quote(key, safe="/")
    payload = hashlib.sha256(b"").hexdigest()
    headers = f"host:localhost:8333\nx-amz-content-sha256:{payload}\nx-amz-date:{stamp}\n"
    signed = "host;x-amz-content-sha256;x-amz-date"
    canonical = "DELETE\n" + path + "\n\n" + headers + "\n" + signed + "\n" + payload
    scope = day + "/us-east-1/s3/aws4_request"
    string = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hashlib.sha256(canonical.encode()).hexdigest()
    signing = ("AWS4" + secret).encode()
    for value in (day, "us-east-1", "s3", "aws4_request"):
        signing = hmac.new(signing, value.encode(), hashlib.sha256).digest()
    signature = hmac.new(signing, string.encode(), hashlib.sha256).hexdigest()
    authorization = f"AWS4-HMAC-SHA256 Credential={access}/{scope}, SignedHeaders={signed}, Signature={signature}"
    request = Request("http://localhost:8333" + path, method="DELETE", headers={
        "x-amz-date": stamp, "x-amz-content-sha256": payload, "Authorization": authorization,
    })
    try:
        urlopen(request, timeout=10).read()
    except HTTPError as error:
        if error.code != 404:
            raise


def cleanup(task):
    if not task:
        return
    files = sql(f"SELECT clave_objeto FROM archivos_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid) OR preparacion_id IN (SELECT id FROM preparaciones_entrega WHERE tarea_id='{task}'::uuid);").splitlines()
    storage_errors = []
    for key in files:
        try:
            delete_s3(key)
        except Exception as error:
            storage_errors.append(f"{key}: {error}")
    script = f"""
    BEGIN;
    DELETE FROM registros_auditoria WHERE entidad_id='{task}'::uuid
      OR entidad_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid)
      OR entidad_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id='{task}'::uuid)
      OR entidad_id IN (SELECT id FROM archivos_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid)
          OR preparacion_id IN (SELECT id FROM preparaciones_entrega WHERE tarea_id='{task}'::uuid));
    DELETE FROM eventos_notificacion_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid);
    DELETE FROM notificaciones_tarea WHERE tarea_id='{task}'::uuid;
    DELETE FROM historial_calificaciones WHERE calificacion_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id='{task}'::uuid);
    DELETE FROM calificaciones_tarea WHERE tarea_id='{task}'::uuid;
    UPDATE archivos_entrega SET sustituye_id=NULL WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid)
      OR preparacion_id IN (SELECT id FROM preparaciones_entrega WHERE tarea_id='{task}'::uuid);
    DELETE FROM archivos_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id='{task}'::uuid)
      OR preparacion_id IN (SELECT id FROM preparaciones_entrega WHERE tarea_id='{task}'::uuid);
    DELETE FROM autorizaciones_reentrega WHERE tarea_id='{task}'::uuid;
    DELETE FROM preparaciones_entrega WHERE tarea_id='{task}'::uuid;
    DELETE FROM entregas WHERE tarea_id='{task}'::uuid;
    DELETE FROM tareas WHERE id='{task}'::uuid;
    COMMIT;
    """
    sql(script)
    if storage_errors:
        raise RuntimeError("No se pudo limpiar el almacenamiento temporal: " + "; ".join(storage_errors))


def main():
    teacher = Session("cp06.carlos.demo")
    sofia = Session("cp06.sofia.demo")
    mateo = Session("cp06.mateo.demo")
    patricia = Session("cp06.patricia.demo")
    sofia_id, mateo_id = erp_student("CP06-SOFIA"), erp_student("CP06-MATEO")
    course = next(c["id"] for c in teacher.api("/cursos") if
                  (c["asignatura"], c["nivel"], c["grado"], c["seccion"]) == ("Matemática", "Primaria", "Quinto", "A"))
    task = late_task = None
    try:
        due = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)).isoformat()
        task = teacher.api(f"/cursos/{course}/tareas", "POST", {
            "titulo": "CP07 prueba temporal " + uuid.uuid4().hex[:8],
            "instrucciones": "Entregar respuesta y archivos de prueba.", "fechaLimite": due,
        })["id"]
        teacher.api(f"/tareas/{task}/publicar", "POST", {})
        version = sofia.api(f"/tareas/{task}")["version"]
        prep = sofia.api(f"/tareas/{task}/preparacion", "POST", {"tareaVersion": version})["id"]
        sofia.api(f"/preparaciones/{prep}", "PUT", {"respuesta": "Trabajo guardado", "tareaVersion": version})
        check(not sofia.api(f"/tareas/{task}/entregas") and
              not [n for n in teacher.api("/notificaciones") if n.get("tarea_id") == task and n["tipo"] == "ENTREGAS"],
              "Preparación no cuenta como entrega ni avisa al docente")
        sofia.api(f"/preparaciones/{prep}/archivos", "POST", {
            "nombre": "exceso.txt", "tipo": "text/plain", "tamano": 25 * 1024 * 1024 + 1,
        }, expected=400)
        upload(sofia, prep, "no-permitido.exe", "application/octet-stream", b"falso", expected=400)
        upload(sofia, prep, "falso.pdf", "application/pdf", b"No es un PDF", completion_expected=400)
        check(not sofia.api(f"/preparaciones/{prep}/archivos"),
              "Extensión y contenido inválidos rechazados sin quedar adjuntos")
        payloads = [
            ("uno.txt", "text/plain", b"Uno\n"),
            ("dos.txt", "text/plain", b"Dos\n"),
            ("tres.pdf", "application/pdf", b"%PDF-1.4\nCP07\n"),
            ("cuatro.pdf", "application/pdf", b"%PDF-1.4\nCP07 segundo\n"),
        ]
        ids = [upload(sofia, prep, *item) for item in payloads]
        upload(sofia, prep, "quinto.txt", "text/plain", b"Cinco", expected=400)
        check(len(sofia.api(f"/preparaciones/{prep}/archivos")) == 4,
              "Cuatro archivos aceptados; quinto y tamaño mayor a 25 MB rechazados")
        replacement = upload(sofia, prep, "uno-reemplazo.txt", "text/plain", b"Reemplazo", replace=ids[0])
        current = sofia.api(f"/preparaciones/{prep}/archivos")
        check(len(current) == 4 and replacement in {f["id"] for f in current} and ids[1] in {f["id"] for f in current},
              "Reemplazo conserva los otros archivos")
        resumed = Session("cp06.sofia.demo")
        state = resumed.api(f"/tareas/{task}/preparacion")
        check(state["id"] == prep and state["respuesta"] == "Trabajo guardado" and
              len(resumed.api(f"/preparaciones/{prep}/archivos")) == 4,
              "Preparación recuperada tras iniciar una nueva sesión")
        operation = str(uuid.uuid4())
        receipt = resumed.api(f"/preparaciones/{prep}/confirmar", "POST", {"operacionId": operation})
        repeated = resumed.api(f"/preparaciones/{prep}/confirmar", "POST", {"operacionId": operation})
        check(receipt["id"] == repeated["id"] and receipt["version"] == 1 and
              len(resumed.api(f"/tareas/{task}/entregas")) == 1 and
              len(resumed.api(f"/entregas/{receipt['id']}/archivos")) == 4,
              "Confirmación idempotente conserva archivos y constancia")
        mateo.api(f"/archivos/{replacement}/url", expected=403)
        patricia.api(f"/tareas/{task}/entregas", expected=403)
        check(True, "Archivo ajeno y curso docente ajeno bloqueados")
        teacher_notice = [n for n in teacher.api("/notificaciones") if n.get("tarea_id") == task and n["tipo"] == "ENTREGAS"]
        check(len(teacher_notice) == 1 and teacher_notice[0]["nuevas_entregas"] == 1,
              "Docente recibe aviso agrupado solo tras la confirmación")
        grade = teacher.api(f"/tareas/{task}/calificacion", "PUT", {
            "estudianteId": sofia_id, "valor": 17, "comentario": "Primer intento", "version": 0, "entregaVersion": 1,
        })
        check(sofia.api(f"/tareas/{task}/preparacion")["puede_iniciar"],
              "Dentro del plazo se permite reentregar sin autorización docente")
        second = sofia.api(f"/tareas/{task}/preparacion", "POST", {"tareaVersion": version})
        sofia.api(f"/preparaciones/{second['id']}", "PUT", {"respuesta": "Segundo intento", "tareaVersion": version})
        second_receipt = sofia.api(f"/preparaciones/{second['id']}/confirmar", "POST", {"operacionId": str(uuid.uuid4())})
        old = sofia.api(f"/notas/{grade['id']}")
        followup = next(r for r in teacher.api(f"/tareas/{task}/seguimiento") if r["estudiante_erp_id"] == sofia_id)
        check(second_receipt["version"] == 2 and old["valor"] == 17 and old["comentario"] == "Primer intento" and
              old["entrega_version"] == 1 and followup["nota_id"] is None,
              "Segundo intento pendiente; nota y comentario del primero intactos")
        third = sofia.api(f"/tareas/{task}/preparacion", "POST", {"tareaVersion": version})
        sofia.api(f"/preparaciones/{third['id']}", "PUT", {"respuesta": "Tercer intento", "tareaVersion": version})
        third_receipt = sofia.api(f"/preparaciones/{third['id']}/confirmar", "POST", {"operacionId": str(uuid.uuid4())})
        check(third_receipt["version"] == 3 and len(sofia.api(f"/tareas/{task}/entregas")) == 3,
              "Dentro del plazo se admiten reentregas sucesivas")
        pending = mateo.api(f"/tareas/{task}/preparacion", "POST", {"tareaVersion": version})["id"]
        mateo.api(f"/preparaciones/{pending}", "PUT", {"respuesta": "Mateo prepara", "tareaVersion": version})
        started = mateo.api(f"/preparaciones/{pending}/archivos", "POST", {
            "nombre": "incompleto.txt", "tipo": "text/plain", "tamano": 5,
        })["id"]
        mateo.api(f"/preparaciones/{pending}/confirmar", "POST", {"operacionId": str(uuid.uuid4())}, expected=409)
        mateo.api(f"/archivos/{started}", "DELETE", expected=200)
        check(not mateo.api(f"/tareas/{task}/entregas"), "Carga interrumpida no genera entrega")
        teacher.api(f"/tareas/{task}/cerrar", "POST", {})
        mateo.api(f"/preparaciones/{pending}/confirmar", "POST", {"operacionId": str(uuid.uuid4())}, expected=400)
        check(not mateo.api(f"/tareas/{task}/entregas"), "Cierre durante la preparación bloquea la confirmación")
        late_due = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=1)).isoformat()
        late_task = teacher.api(f"/cursos/{course}/tareas", "POST", {
            "titulo": "CP07 prueba temporal tardía " + uuid.uuid4().hex[:8],
            "instrucciones": "Comprobar límite de reentrega tardía.", "fechaLimite": late_due,
        })["id"]
        teacher.api(f"/tareas/{late_task}/publicar", "POST", {})
        late_version = mateo.api(f"/tareas/{late_task}")["version"]
        for attempt in (1, 2):
            draft = mateo.api(f"/tareas/{late_task}/preparacion", "POST", {"tareaVersion": late_version})
            mateo.api(f"/preparaciones/{draft['id']}", "PUT", {
                "respuesta": f"Entrega tardía {attempt}", "tareaVersion": late_version,
            })
            received = mateo.api(f"/preparaciones/{draft['id']}/confirmar", "POST", {
                "operacionId": str(uuid.uuid4()),
            })
            check(received["version"] == attempt and received["estado"] == "TARDIA",
                  f"Intento tardío {attempt} registrado")
        check(not mateo.api(f"/tareas/{late_task}/preparacion")["puede_iniciar"],
              "Fuera de plazo solo se admite una reentrega")
        mateo.api(f"/tareas/{late_task}/preparacion", "POST", {"tareaVersion": late_version}, expected=400)
    finally:
        try:
            cleanup(task)
        finally:
            cleanup(late_task)


if __name__ == "__main__":
    main()
