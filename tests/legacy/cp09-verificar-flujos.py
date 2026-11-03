"""Seis flujos focalizados CP-09 sobre Docker local; limpia solo sus clases temporales."""
import datetime as dt
import runpy
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
Session = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))["Session"]


def sql(sentencia):
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1",
         "-U", "boston", "-d", "aula_boston", "-At"], cwd=ROOT,
        input=sentencia, text=True, capture_output=True, check=True,
    ).stdout.strip()


def comprobar(condicion, descripcion):
    if not condicion:
        raise AssertionError(descripcion)
    print("OK", descripcion, flush=True)


def limpiar(ids):
    if not ids:
        return
    lista = ",".join(f"'{uuid.UUID(x)}'::uuid" for x in ids)
    sql(f"""
      BEGIN;
      DELETE FROM notificaciones_vistas WHERE historial_id IN
        (SELECT id FROM historial_clases WHERE clase_id IN ({lista}));
      DELETE FROM notificaciones_clase WHERE clase_id IN ({lista});
      DELETE FROM historial_clases WHERE clase_id IN ({lista});
      DELETE FROM bloques_zoom WHERE clase_id IN ({lista});
      DELETE FROM registros_auditoria WHERE entidad_id IN ({lista});
      DELETE FROM clases_academicas WHERE id IN ({lista});
      COMMIT;
    """)


def main():
    docente, sofia, mateo = (Session(x) for x in
                             ("cp06.carlos.demo", "cp06.sofia.demo", "cp06.mateo.demo"))
    ajena, admin = Session("cp06.patricia.demo"), Session("admin.demo")
    cursos = [c for c in docente.api("/cursos") if
              (c["asignatura"], c["nivel"], c["grado"]) == ("Matemática", "Primaria", "Quinto")]
    por_seccion = {c["seccion"]: c["id"] for c in cursos}
    aula_a, aula_b = por_seccion["A"], por_seccion["B"]
    original = admin.api("/modulos")["clasesYGrabaciones"]
    creadas = []
    prefijo = "CP09 temporal " + uuid.uuid4().hex[:8]
    base = dt.datetime.now(dt.timezone(dt.timedelta(hours=-5))).replace(hour=10, minute=0, second=0, microsecond=0) + dt.timedelta(days=9)
    inicio, fin = base.isoformat(), (base + dt.timedelta(hours=1)).isoformat()
    zoom1, zoom2 = "https://zoom.us/j/12345678901?pwd=CP09LOCAL", "https://us02web.zoom.us/j/12345678902?pwd=CP09LOCAL"
    try:
        # 1. Dos secciones, operación atómica e idempotente.
        operacion = str(uuid.uuid4())
        lote = {"cursoIds": [aula_a, aula_b], "tema": prefijo, "inicio": inicio, "fin": fin,
                "enlace": zoom1, "operacionId": operacion}
        aviso = docente.api("/clases/lotes", "POST", lote, expected=409)
        comprobar(aviso["error"].startswith("CONFLICTO_HORARIO:"), "Conflicto simultáneo del docente requiere confirmación")
        clases = docente.api("/clases/lotes", "POST", {**lote, "confirmarConflicto": True})
        creadas.extend(c["id"] for c in clases)
        por_curso = {c["curso_id"]: c["id"] for c in clases}
        clase_a, clase_b = por_curso[aula_a], por_curso[aula_b]
        comprobación = docente.api("/clases/lotes", "POST", lote)
        comprobar(len(clases) == 2 and clase_a != clase_b and comprobación == clases,
                  "Dos secciones producen clases independientes y el reintento no duplica")

        # 2. Consulta, destinatarios y acceso académico.
        ambas = (sofia, mateo)
        for estudiante in ambas:
            visibles = {c["id"] for c in estudiante.api("/clases")}
            avisos = [n for n in estudiante.api("/notificaciones") if n.get("clase_id") == clase_a and n["tipo"] == "PROGRAMADA"]
            comprobar(clase_a in visibles and clase_b not in visibles and len(avisos) == 1 and
                      prefijo in avisos[0]["mensaje"] and "Matemática" in avisos[0]["mensaje"],
                      "Estudiante de A ve su clase y su aviso con curso y tema")
        sofia.api(f"/clases/{clase_b}", expected=403)
        inscritos = sql(f"SELECT count(*) FROM inscripciones_curso WHERE curso_id='{aula_a}' AND activo;")
        avisados = sql(f"SELECT count(*) FROM notificaciones_clase WHERE clase_id='{clase_a}' AND tipo='PROGRAMADA';")
        comprobar(inscritos == avisados, "Avisos solo para matriculados vigentes de la sección")

        # 3. Continuación: una clase y un solo enlace vigente.
        linkop = str(uuid.uuid4())
        docente.api(f"/clases/{clase_a}/enlaces", "POST", {"enlace": zoom2, "operacionId": linkop})
        docente.api(f"/clases/{clase_a}/enlaces", "POST", {"enlace": zoom2, "operacionId": linkop})
        enlaces = docente.api(f"/clases/{clase_a}/enlaces")
        vigente = sofia.api(f"/clases/{clase_a}")
        comprobar(len(enlaces) == 2 and enlaces[0]["enlace"] == zoom1 and enlaces[1]["enlace"] == zoom2 and
                  vigente["enlace_actual"] == zoom2 and len([n for n in sofia.api("/notificaciones") if n.get("clase_id") == clase_a and n["tipo"] == "CONTINUACION"]) == 1,
                  "Continuación conserva historial, cambia enlace vigente y avisa una sola vez")
        for falso in ("", "   "):
            docente.api(f"/clases/{clase_a}/enlaces", "POST", {"enlace": falso, "operacionId": str(uuid.uuid4())}, expected=400)

        # 4 y 5. Horario adyacente, conflicto al editar, cancelación y finalización manual.
        adyacente = {"tema": prefijo + " editada", "inicio": fin,
                     "fin": (base + dt.timedelta(hours=2)).isoformat(), "enlace": zoom2,
                     "operacionId": str(uuid.uuid4())}
        docente.api(f"/clases/{clase_a}", "PUT", adyacente)
        comprobar(docente.api(f"/clases/{clase_a}")["tema"] == adyacente["tema"],
                  "La hora exacta de fin/inicio no es conflicto")
        solapada = {**adyacente, "inicio": (base + dt.timedelta(minutes=30)).isoformat(),
                    "operacionId": str(uuid.uuid4())}
        advertencia = docente.api(f"/clases/{clase_a}", "PUT", solapada, expected=409)
        comprobar(advertencia["error"].startswith("CONFLICTO_HORARIO:"), "Edición superpuesta advierte sin guardar")
        docente.api(f"/clases/{clase_a}", "PUT", {**solapada, "confirmarConflicto": True})
        docente.api(f"/clases/{clase_a}", "PUT", {**solapada, "confirmarConflicto": True})
        comprobar(len([h for h in docente.api(f"/clases/{clase_a}/historial") if h["accion"] == "EDITADA"]) == 2,
                  "Edición confirmada conserva anterior/nuevo y el reintento no repite")
        docente.api(f"/clases/{clase_a}", "PUT", {**adyacente, "operacionId": str(uuid.uuid4())})
        cancelop, finop = str(uuid.uuid4()), str(uuid.uuid4())
        docente.api(f"/clases/{clase_b}/cancelar", "POST", {"operacionId": cancelop})
        docente.api(f"/clases/{clase_b}/cancelar", "POST", {"operacionId": cancelop})
        docente.api(f"/clases/{clase_a}/finalizar", "POST", {"operacionId": finop})
        docente.api(f"/clases/{clase_a}/finalizar", "POST", {"operacionId": finop})
        fin_a, can_b = docente.api(f"/clases/{clase_a}"), docente.api(f"/clases/{clase_b}")
        comprobar(fin_a["estado"] == "FINALIZADA" and can_b["estado"] == "CANCELADA" and
                  fin_a["enlace_actual"] is None and can_b["enlace_actual"] is None,
                  "Finalizar y cancelar requieren acción manual y retiran el acceso Zoom")
        comprobar(len([n for n in sofia.api("/notificaciones") if n.get("clase_id") == clase_a and n["tipo"] == "FINALIZADA"]) == 1,
                  "La finalización avisa una sola vez")

        # 6. Permisos, fechas pasadas, duplicados intencionales e interruptor conjunto.
        ajena.api(f"/clases/{clase_a}/finalizar", "POST", {"operacionId": str(uuid.uuid4())}, expected=403)
        docente.api(f"/clases/{clase_a}/enlaces", "POST", {"enlace": zoom1, "operacionId": str(uuid.uuid4())}, expected=409)
        pasado = base - dt.timedelta(days=20)
        vieja = docente.api(f"/cursos/{aula_a}/clases", "POST", {"tema": prefijo + " pasada",
            "inicio": pasado.isoformat(), "fin": (pasado + dt.timedelta(hours=1)).isoformat(),
            "enlace": zoom1, "operacionId": str(uuid.uuid4()), "confirmarConflicto": True})["id"]
        creadas.append(vieja)
        vieja_data = docente.api(f"/clases/{vieja}")
        comprobar(vieja_data["estado"] == "PROGRAMADA" and vieja_data["estado_visible"] == "PASADA",
                  "Horario transcurrido no confirma realización")
        nueva = docente.api(f"/cursos/{aula_b}/clases", "POST", {"tema": prefijo + " segunda",
            "inicio": inicio, "fin": fin, "enlace": zoom1, "operacionId": str(uuid.uuid4())})["id"]
        creadas.append(nueva)
        comprobar(nueva != clase_b, "Cancelar libera su horario y otra operación crea clase distinta")
        admin.api("/modulos/clases-y-grabaciones", "PUT", {"habilitado": False})
        docente.api("/clases", expected=409)
        comprobar(bool(sofia.api("/cursos")) and not admin.api("/modulos")["clasesYGrabaciones"],
                  "Interruptor deniega clases sin afectar cursos")
    finally:
        if admin.api("/modulos")["clasesYGrabaciones"] != original:
            admin.api("/modulos/clases-y-grabaciones", "PUT", {"habilitado": original})
        limpiar(creadas)
        print("Datos temporales CP-09 retirados; interruptor restaurado.", flush=True)


if __name__ == "__main__":
    main()
