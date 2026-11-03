"""Verificación focalizada CP-10/11/13 en Docker local; elimina solo datos propios."""
import datetime as dt
import runpy
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
Session = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))["Session"]


def sql(statement):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1",
         "-U", "boston", "-d", "aula_boston", "-At"],
        cwd=ROOT, input=statement, text=True, capture_output=True, check=True,
    ).stdout.strip()


def check(value, label):
    if not value:
        raise AssertionError(label)
    print("OK", label, flush=True)


def cleanup(classes, messages):
    if messages:
        ids = ",".join(f"'{uuid.UUID(x)}'::uuid" for x in messages)
        sql(f"BEGIN; DELETE FROM mensajes_destinatarios WHERE mensaje_id IN ({ids}); DELETE FROM registros_auditoria WHERE entidad_id IN ({ids}); DELETE FROM mensajes WHERE id IN ({ids}); COMMIT;")
    if classes:
        ids = ",".join(f"'{uuid.UUID(x)}'::uuid" for x in classes)
        sql(f"""
          BEGIN;
          DELETE FROM notificaciones_grabacion WHERE grabacion_id IN (SELECT id FROM grabaciones_youtube WHERE clase_id IN ({ids}));
          DELETE FROM historial_grabaciones WHERE grabacion_id IN (SELECT id FROM grabaciones_youtube WHERE clase_id IN ({ids}));
          DELETE FROM registros_auditoria WHERE entidad_id IN (SELECT id FROM grabaciones_youtube WHERE clase_id IN ({ids}));
          DELETE FROM grabaciones_youtube WHERE clase_id IN ({ids});
          DELETE FROM notificaciones_vistas WHERE historial_id IN (SELECT id FROM historial_clases WHERE clase_id IN ({ids}));
          DELETE FROM notificaciones_clase WHERE clase_id IN ({ids});
          DELETE FROM historial_clases WHERE clase_id IN ({ids});
          DELETE FROM bloques_zoom WHERE clase_id IN ({ids});
          DELETE FROM registros_auditoria WHERE entidad_id IN ({ids});
          DELETE FROM clases_academicas WHERE id IN ({ids});
          COMMIT;
        """)


def main():
    teachers = [(Session("cp06.carlos.demo"), Session("cp06.sofia.demo"), "Matemática", "A"),
                (Session("cp06.patricia.demo"), Session("cp06.valeria.demo"), "Comunicación", "A"),
                (Session("cp06.roberto.demo"), Session("cp06.diego.demo"), "Inglés", "B")]
    mateo = Session("cp06.mateo.demo")
    coord = Session("coordinacion.demo")
    admin = Session("admin.demo")
    classes, messages = [], []
    original = admin.api("/modulos")["clasesYGrabaciones"]
    names = ("Carlos", "Patricia", "Roberto")
    try:
        if not original:
            admin.api("/modulos/clases-y-grabaciones", "PUT", {"habilitado": True})
        per_teacher = []
        for index, (teacher, student, subject, section) in enumerate(teachers):
            course = next(c for c in teacher.api("/cursos") if c["asignatura"] == subject and c["seccion"] == section)
            start = dt.datetime.now(dt.timezone(dt.timedelta(hours=-5))).replace(hour=10, minute=0, second=0, microsecond=0) + dt.timedelta(days=13+index)
            result = teacher.api(f"/cursos/{course['id']}/clases", "POST", {
                "tema": f"CP10 temporal {names[index]} {uuid.uuid4().hex[:6]}",
                "inicio": start.isoformat(), "fin": (start+dt.timedelta(hours=1)).isoformat(),
                "enlace": "https://zoom.us/j/12345678901", "operacionId": str(uuid.uuid4()),
                "confirmarConflicto": True,
            })
            cid = result["id"]
            classes.append(cid)
            payload = {"claseId": cid, "orden": 0, "titulo": "Primera parte",
                       "url": f"https://videos.escuela.example/{names[index].lower()}/parte-1",
                       "publicar": False, "operacionId": str(uuid.uuid4())}
            teacher.api("/grabaciones", "POST", payload, expected=409)
            teacher.api(f"/clases/{cid}/finalizar", "POST", {"operacionId": str(uuid.uuid4())})
            check(teacher.api(f"/clases/{cid}")["estado"] == "FINALIZADA", f"{names[index]} finaliza clase propia")
            created = teacher.api("/grabaciones", "POST", payload)
            rid = created["id"]
            check(teacher.api("/grabaciones", "POST", payload)["id"] == rid, f"{names[index]} reintenta registro sin duplicar")
            check(not any(n.get("grabacion_id") == rid for n in student.api("/notificaciones")), f"{names[index]} no avisa borrador")
            teacher.api(f"/grabaciones/{rid}/estado", "PUT", {"publicar": True, "operacionId": str(uuid.uuid4())})
            found = [g for g in student.api("/grabaciones") if g["id"] == rid]
            check(len(found) == 1 and found[0]["url"] == payload["url"] and found[0]["estado"] == "PUBLICADA",
                  f"{names[index]} publica y estudiante abre URL elegida")
            check(len([n for n in student.api("/notificaciones") if n.get("grabacion_id") == rid]) == 1,
                  f"{names[index]} avisa solo al publicar")
            other = teachers[(index+1) % 3][0]
            other.api(f"/grabaciones/{rid}", "PUT", {"titulo": "Ajena", "orden": 1,
                      "url": payload["url"], "operacionId": str(uuid.uuid4())}, expected=403)
            check(True, f"{names[index]} protege referencia frente a docente ajeno")
            per_teacher.append((teacher, student, course, cid, rid, payload["url"]))

        teacher, student, course, cid, first, first_url = per_teacher[0]
        second = teacher.api("/grabaciones", "POST", {"claseId": cid, "orden": 0,
            "titulo": "Segunda parte", "url": "https://otro-proveedor.example/parte-2",
            "publicar": False, "operacionId": str(uuid.uuid4())})["id"]
        check(next(g for g in teacher.api(f"/grabaciones/clase/{cid}") if g["id"] == second)["orden"] == 2,
              "Varias partes con numeración automática")
        teacher.api("/grabaciones", "POST", {"claseId": cid, "orden": 3, "titulo": "Duplicada",
            "url": first_url, "publicar": False, "operacionId": str(uuid.uuid4())}, expected=400)
        teacher.api("/grabaciones", "POST", {"claseId": cid, "orden": 3, "titulo": "Inválida",
            "url": "javascript:alert(1)", "publicar": False, "operacionId": str(uuid.uuid4())}, expected=400)
        teacher.api(f"/grabaciones/{second}", "PUT", {"titulo": "Parte complementaria", "orden": 3,
            "url": "https://otro-proveedor.example/parte-2", "operacionId": str(uuid.uuid4())})
        check(any(g["id"] == second and g["orden"] == 3 and g["titulo"] == "Parte complementaria"
                  for g in teacher.api(f"/grabaciones/clase/{cid}")), "Edición de título y orden")
        teacher.api(f"/grabaciones/{first}", "PUT", {"titulo": "Primera parte", "orden": 1,
            "url": "https://otro-proveedor.example/enlace-corregido", "operacionId": str(uuid.uuid4())})
        check(len([n for n in student.api("/notificaciones") if n.get("grabacion_id") == first and n["tipo"] == "GRABACION_CORREGIDA"]) == 1,
              "Corrección publicada avisa una vez")
        teacher.api(f"/grabaciones/{first}/estado", "PUT", {"publicar": False, "operacionId": str(uuid.uuid4())})
        check(not any(g["id"] == first for g in student.api("/grabaciones")), "Referencia oculta invisible para estudiante")
        teacher.api(f"/grabaciones/{first}/estado", "PUT", {"publicar": True, "operacionId": str(uuid.uuid4())})
        teacher.api(f"/grabaciones/{first}/retirar", "POST", {"operacionId": str(uuid.uuid4())})
        check(not any(g["id"] == first for g in student.api("/grabaciones")) and
              any(h["accion"] == "RETIRADA" for h in teacher.api(f"/grabaciones/{first}/historial")),
              "Retirada conserva historial y oculta referencia")
        student.api(f"/grabaciones/{first}/historial", expected=403)
        check(True, "El estudiante no puede consultar enlaces históricos ocultos")

        # CP-11: directorio y envío se validan con matrículas y asignaciones actuales.
        sofia, patricia, valeria = teachers[0][1], teachers[1][0], teachers[1][1]
        carlos = teachers[0][0]
        directory_sofia = {d["usuario"]: d["id"] for d in sofia.api("/mensajes/directorio")}
        directory_carlos = {d["usuario"]: d["id"] for d in carlos.api("/mensajes/directorio")}
        directory_coord = {d["usuario"]: d["id"] for d in coord.api("/mensajes/directorio")}
        check("cp06.mateo.demo" in directory_sofia and "cp06.valeria.demo" not in directory_sofia and
              "cp06.carlos.demo" not in directory_sofia, "Directorio estudiante: misma sección y rol")
        check("cp06.sofia.demo" in directory_carlos and "cp06.valeria.demo" not in directory_carlos,
              "Directorio docente: estudiantes de cursos asignados")
        mateo_id = directory_sofia["cp06.mateo.demo"]
        valeria_id = directory_coord["cp06.valeria.demo"]
        msg = sofia.api("/mensajes", "POST", {"asunto": "CP11 temporal", "cuerpo": "Prueba interna",
                        "destinatarios": [mateo_id], "cursoId": None, "cursoCompleto": False})["id"]
        messages.append(msg)
        check(any(m["id"] == msg and m["leida_en"] is None for m in mateo.api("/mensajes?carpeta=recibidos")),
              "Mensaje recibido permanece no leído")
        mateo.api(f"/mensajes/{msg}")
        mateo.api(f"/mensajes/{msg}/leer", "POST", {})
        check(next(m for m in mateo.api("/mensajes?carpeta=recibidos") if m["id"] == msg)["leida_en"] is not None,
              "Lectura individual conserva historial")
        valeria.api(f"/mensajes/{msg}", expected=403)
        sofia.api("/mensajes", "POST", {"asunto": "No", "cuerpo": "No", "destinatarios": [valeria_id], "cursoCompleto": False}, expected=403)
        sofia.api("/mensajes", "POST", {"asunto": "No", "cuerpo": "No", "destinatarios": [],
                   "cursoId": course["id"], "cursoCompleto": True}, expected=403)
        carlos.api("/mensajes", "POST", {"asunto": "No", "cuerpo": "No", "destinatarios": [valeria_id], "cursoCompleto": False}, expected=403)
        check(True, "Peticiones directas y conversaciones privadas ajenas rechazadas")
        direct = carlos.api("/mensajes", "POST", {"asunto": "CP11 docente", "cuerpo": "A mi curso",
                             "destinatarios": [directory_carlos["cp06.sofia.demo"]], "cursoCompleto": False})["id"]
        messages.append(direct)
        check(any(m["id"] == direct for m in sofia.api("/mensajes?carpeta=recibidos")), "Docente escribe a estudiante propio")
        institutional = coord.api("/mensajes", "POST", {"asunto": "CP11 coordinación", "cuerpo": "Aviso",
                                  "destinatarios": [directory_coord["cp06.carlos.demo"]], "cursoCompleto": False})["id"]
        messages.append(institutional)
        grouped = admin.api("/mensajes", "POST", {"asunto": "CP11 grupo", "cuerpo": "Aviso",
                           "destinatarios": [], "grupoTipo": "DOCENTE", "cursoCompleto": False})["id"]
        messages.append(grouped)
        check(any(m["id"] == institutional for m in carlos.api("/mensajes?carpeta=recibidos")) and
              any(m["id"] == grouped for m in carlos.api("/mensajes?carpeta=recibidos")),
              "Coordinación individual y Administración grupal envían mensajes")
        coord.api(f"/mensajes/{msg}", expected=403)

        # CP-13: solo lectura del ámbito asignado, sin borradores de nota.
        summary = coord.api("/supervision/coordinacion/resumen")
        scope = {r["id"] for r in summary}
        assignments = coord.api("/supervision/coordinacion/docentes")
        tasks = coord.api("/supervision/coordinacion/tareas")
        pupils = coord.api("/supervision/coordinacion/estudiantes")
        classes_visible = coord.api("/supervision/coordinacion/clases")
        check(bool(scope) and bool(assignments) and bool(tasks) and bool(pupils) and
              all(t["curso_id"] in scope for t in tasks) and all(p["curso_id"] in scope for p in pupils) and
              all(c["curso_id"] in scope for c in classes_visible), "Supervisión limitada a ámbitos ERP")
        check(all("valor" not in x and "comentario" not in x for x in tasks+pupils),
              "Supervisión no expone borradores ni comentarios privados de notas")
        check(isinstance(coord.api("/historial/cursos"), list), "Historial de traslados y retiros disponible para Coordinación")
        student.api("/supervision/coordinacion/resumen", expected=403)
        admin.api("/supervision/coordinacion/resumen", expected=403)
        check(True, "Supervisión de Coordinación no permite otros roles")
        admin.api("/modulos/clases-y-grabaciones", "PUT", {"habilitado": False})
        carlos.api("/clases", expected=409)
        carlos.api("/grabaciones", expected=409)
        check(coord.api("/supervision/coordinacion/clases") == [], "Interruptor CP-09 desactiva clases y grabaciones")
    finally:
        if admin.api("/modulos")["clasesYGrabaciones"] != original:
            admin.api("/modulos/clases-y-grabaciones", "PUT", {"habilitado": original})
        cleanup(classes, messages)


if __name__ == "__main__":
    main()
