"""Verifica programación conjunta/separada CP-09 y limpia únicamente sus clases temporales."""
import datetime as dt
import runpy
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT / "pruebas" / "cp09-verificar-flujos.py"))
Session, sql, limpiar, comprobar = (helpers[k] for k in ("Session", "sql", "limpiar", "comprobar"))


def main():
    docente = Session("cp06.carlos.demo")
    cursos = [c for c in docente.api("/cursos") if
              (c["asignatura"], c["nivel"], c["grado"]) == ("Matemática", "Primaria", "Quinto")]
    destinos = {c["seccion"]: c["id"] for c in cursos}
    a, b = destinos["A"], destinos["B"]
    base = (dt.datetime.now(dt.timezone(dt.timedelta(hours=-5))) + dt.timedelta(days=12)).replace(
        hour=9, minute=0, second=0, microsecond=0)
    prefijo = "CP09 temporal secciones " + uuid.uuid4().hex[:8]
    zoom_a, zoom_b = "https://zoom.us/j/12345678901", "enlace-libre-de-prueba"
    creadas = []

    def plan(curso, tema, comienzo, enlace):
        return {"cursoId": curso, "tema": tema, "inicio": comienzo.isoformat(),
                "fin": (comienzo + dt.timedelta(hours=1)).isoformat(), "enlace": enlace}

    try:
        vacia = {"programaciones": [plan(a, prefijo + " vacía", base, "   ")],
                 "claseGrupal": False, "operacionId": str(uuid.uuid4())}
        docente.api("/clases/programaciones", "POST", vacia, expected=400)
        conjunta = {"programaciones": [plan(a, prefijo + " conjunta", base, zoom_a),
                                     plan(b, prefijo + " conjunta", base, zoom_a)],
                    "claseGrupal": True, "operacionId": str(uuid.uuid4())}
        grupo = docente.api("/clases/programaciones", "POST", conjunta)
        creadas.extend(c["id"] for c in grupo)
        repetido = docente.api("/clases/programaciones", "POST", conjunta)
        comprobar(len(grupo) == 2 and grupo == repetido and grupo[0]["id"] != grupo[1]["id"],
                  "Clase compartida: dos registros independientes, sin falsa alerta y sin duplicados")
        vistas = [docente.api(f"/clases/{c['id']}") for c in grupo]
        comprobar(all(c["inicio"] == vistas[0]["inicio"] and c["enlace_actual"] == zoom_a for c in vistas),
                  "Clase compartida: mismo horario y enlace Zoom en ambas secciones")

        separadas = {"programaciones": [plan(a, prefijo + " A", base + dt.timedelta(hours=2), zoom_a),
                                        plan(b, prefijo + " B", base + dt.timedelta(hours=3), zoom_b)],
                     "claseGrupal": False, "operacionId": str(uuid.uuid4())}
        independientes = docente.api("/clases/programaciones", "POST", separadas)
        creadas.extend(c["id"] for c in independientes)
        detalles = {c["curso_id"]: docente.api(f"/clases/{c['id']}") for c in independientes}
        comprobar(detalles[a]["tema"] != detalles[b]["tema"] and detalles[a]["inicio"] != detalles[b]["inicio"]
                  and detalles[a]["enlace_actual"] != detalles[b]["enlace_actual"],
                  "Clases separadas: tema, horario y enlace propios por sección")

        conflicto = {"programaciones": [plan(a, prefijo + " libre", base + dt.timedelta(hours=5), zoom_a),
                                        plan(b, prefijo + " en conflicto", base, zoom_b)],
                     "claseGrupal": False, "operacionId": str(uuid.uuid4())}
        aviso = docente.api("/clases/programaciones", "POST", conflicto, expected=409)["error"]
        comprobar(prefijo + " conjunta" in aviso and "Matemática" in aviso and "Quinto" in aviso and
                  "B" in aviso and base.strftime("%d/%m/%Y") in aviso,
                  "Conflicto previo muestra tema, curso, sección, fecha y horario")
        comprobar(sql(f"SELECT count(*) FROM clases_academicas WHERE tema='{prefijo} libre';") == "0",
                  "Un conflicto no crea parcialmente el lote")
        creadas.extend(c["id"] for c in docente.api("/clases/programaciones", "POST",
                                                   {**conflicto, "confirmarConflicto": True}))

        internos = {"programaciones": [plan(a, prefijo + " interna A", base + dt.timedelta(hours=7), zoom_a),
                                       plan(b, prefijo + " interna B", base + dt.timedelta(hours=7), zoom_b)],
                    "claseGrupal": False, "operacionId": str(uuid.uuid4())}
        aviso_interno = docente.api("/clases/programaciones", "POST", internos, expected=400)["error"]
        comprobar("sección A" in aviso_interno and "sección B" in aviso_interno,
                  "Horarios separados superpuestos se rechazan con sus secciones")
    finally:
        limpiar(creadas)
        print("Clases temporales retiradas.", flush=True)


if __name__ == "__main__":
    main()
