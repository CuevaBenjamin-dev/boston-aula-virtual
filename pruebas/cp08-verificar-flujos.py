"""Comprobaciones locales focalizadas CP-08; elimina solo sus tareas temporales."""
import datetime as dt
import runpy
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT / "pruebas" / "cp07-verificar-flujos.py"))
Session, check, cleanup, erp_student = (helpers[name] for name in ("Session", "check", "cleanup", "erp_student"))


def main():
    docente = Session("cp06.carlos.demo")
    ajena = Session("cp06.patricia.demo")
    sofia = Session("cp06.sofia.demo")
    mateo = Session("cp06.mateo.demo")
    valeria = Session("cp06.valeria.demo")
    sofia_id, mateo_id = erp_student("CP06-SOFIA"), erp_student("CP06-MATEO")
    curso = next(c["id"] for c in docente.api("/cursos") if
                 (c["asignatura"], c["nivel"], c["grado"], c["seccion"]) ==
                 ("Matemática", "Primaria", "Quinto", "A"))
    tareas = []
    try:
        limite = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)).isoformat()
        tarea = docente.api(f"/cursos/{curso}/tareas", "POST", {
            "titulo": "CP08 temporal " + uuid.uuid4().hex[:8],
            "instrucciones": "Evaluación temporal de CP-08.", "fechaLimite": limite,
        })["id"]
        tareas.append(tarea)
        docente.api(f"/tareas/{tarea}/publicar", "POST", {})
        sofia.api(f"/tareas/{tarea}/entregas", "POST", {"respuesta": "Primer intento"})
        check(not [n for n in mateo.api(f"/cursos/{curso}/notas") if n["tarea_id"] == tarea],
              "Sin evaluación no se asigna una nota automática")
        docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {
            "estudianteId": mateo_id, "valor": 0, "comentario": "Sin entrega",
            "version": 0, "entregaVersion": 1,
        }, expected=400)
        mateo.api(f"/tareas/{tarea}/entregas", "POST", {"respuesta": "Entrega de Mateo"})
        base = {"estudianteId": sofia_id, "comentario": "Privado", "version": 0, "entregaVersion": 1}
        for valor in (-1, 21, 2.345):
            docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {**base, "valor": valor}, expected=400)
        borrador = docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {**base, "valor": 17.25})
        check(borrador["estado"] == "BORRADOR" and borrador["valor"] == 17.25,
              "Borrador numérico exacto guardado")
        check(any(n["id"] == borrador["id"] and n["estado"] == "BORRADOR" for n in docente.api(f"/cursos/{curso}/notas")),
              "Borrador persistido y visible al docente")
        check(not [n for n in sofia.api(f"/cursos/{curso}/notas") if n["id"] == borrador["id"]],
              "Borrador oculto en Mis notas")
        sofia.api(f"/notas/{borrador['id']}", expected=403)
        check(not [n for n in sofia.api("/notificaciones") if n.get("nota_id") == borrador["id"]],
              "Borrador no genera aviso")
        docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {**base, "valor": 18}, expected=409)
        ajena.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {**base, "valor": 18}, expected=403)
        sofia.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {**base, "valor": 18}, expected=403)
        publicacion = {"estudianteId": sofia_id, "entregaVersion": 1,
                       "version": borrador["version"], "operacionId": str(uuid.uuid4())}
        publicada = docente.api(f"/tareas/{tarea}/calificacion/publicar", "POST", publicacion)
        repetida = docente.api(f"/tareas/{tarea}/calificacion/publicar", "POST", publicacion)
        check(publicada["id"] == repetida["id"] and publicada["estado"] == "PUBLICADA", "Publicación idempotente")
        check(sofia.api(f"/notas/{publicada['id']}")["valor"] == 17.25, "Nota publicada visible")
        valeria.api(f"/notas/{publicada['id']}", expected=403)
        check(len(docente.api(f"/notas/{publicada['id']}/historial")) == 1, "Un evento de publicación")
        check(len([n for n in sofia.api("/notificaciones") if n.get("nota_id") == publicada["id"]]) == 1,
              "Un único aviso de publicación")
        correccion = docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {
            "estudianteId": sofia_id, "valor": 18.5, "comentario": "Revisado",
            "version": publicada["version"], "entregaVersion": 1,
        })
        check(sofia.api(f"/notas/{publicada['id']}")["valor"] == 17.25,
              "La corrección privada conserva visible la nota previa")
        docente.api(f"/tareas/{tarea}/calificacion/publicar", "POST", {
            "estudianteId": sofia_id, "entregaVersion": 1,
            "version": correccion["version"], "operacionId": str(uuid.uuid4()),
        })
        historia = docente.api(f"/notas/{publicada['id']}/historial")
        check(len(historia) == 2 and historia[-1]["valor_anterior"] == 17.25 and
              historia[-1]["valor_nuevo"] == 18.5, "Corrección conserva valores anteriores")
        check(len([n for n in sofia.api("/notificaciones") if n.get("nota_id") == publicada["id"]]) == 2,
              "La corrección publicada genera un segundo aviso")
        sofia.api(f"/tareas/{tarea}/entregas", "POST", {"respuesta": "Segundo intento"})
        notas = sofia.api(f"/cursos/{curso}/notas")
        check(any(n["id"] == publicada["id"] and n["entrega_version"] == 1 and
                  n["ultima_entrega_version"] == 2 for n in notas), "Intento previo permanece histórico")
        check(not any(n["tarea_id"] == tarea and n["entrega_version"] == 2 for n in notas),
              "Intento nuevo empieza pendiente")
        docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {
            "estudianteId": sofia_id, "valor": 19, "comentario": "Segundo intento",
            "version": 0, "entregaVersion": 1,
        }, expected=409)
        segundo = docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {
            "estudianteId": sofia_id, "valor": 19, "comentario": "Segundo intento",
            "version": 0, "entregaVersion": 2,
        })
        docente.api(f"/tareas/{tarea}/calificacion/publicar", "POST", {
            "estudianteId": sofia_id, "entregaVersion": 2,
            "version": segundo["version"], "operacionId": str(uuid.uuid4()),
        })
        check(len([n for n in sofia.api(f"/cursos/{curso}/notas") if n["tarea_id"] == tarea]) == 2,
              "Cada intento conserva su calificación")
        docente.api(f"/tareas/{tarea}/cerrar", "POST", {})
        cerrada = docente.api(f"/tareas/{tarea}/calificacion/borrador", "PUT", {
            "estudianteId": mateo_id, "valor": 16, "comentario": "Revisado tras cierre",
            "version": 0, "entregaVersion": 1,
        })
        docente.api(f"/tareas/{tarea}/calificacion/publicar", "POST", {
            "estudianteId": mateo_id, "entregaVersion": 1,
            "version": cerrada["version"], "operacionId": str(uuid.uuid4()),
        })
        check(any(n["tarea_id"] == tarea and n["valor"] == 16 for n in mateo.api(f"/cursos/{curso}/notas")),
              "Tarea cerrada manualmente permite calificar entrega recibida")
        tarde = docente.api(f"/cursos/{curso}/tareas", "POST", {
            "titulo": "CP08 tardía " + uuid.uuid4().hex[:8],
            "instrucciones": "Evaluación tardía temporal.",
            "fechaLimite": (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=1)).isoformat(),
        })["id"]
        tareas.append(tarde)
        docente.api(f"/tareas/{tarde}/publicar", "POST", {})
        recibo = mateo.api(f"/tareas/{tarde}/entregas", "POST", {"respuesta": "Entrega tardía"})
        check(any(e["id"] == recibo["id"] and e["estado"] == "TARDIA" for e in mateo.api(f"/tareas/{tarde}/entregas")),
              "La tardanza permanece marcada")
        borrador_tarde = docente.api(f"/tareas/{tarde}/calificacion/borrador", "PUT", {
            "estudianteId": mateo_id, "valor": 20, "comentario": "Sin descuento automático",
            "version": 0, "entregaVersion": 1,
        })
        docente.api(f"/tareas/{tarde}/calificacion/publicar", "POST", {
            "estudianteId": mateo_id, "entregaVersion": 1,
            "version": borrador_tarde["version"], "operacionId": str(uuid.uuid4()),
        })
        check(any(n["tarea_id"] == tarde and n["valor"] == 20 for n in mateo.api(f"/cursos/{curso}/notas")),
              "Entrega tardía se evalúa sin descuento")
        docente.api(f"/tareas/{tarde}/cancelar", "POST", {})
        docente.api(f"/tareas/{tarde}/calificacion/borrador", "PUT", {
            "estudianteId": mateo_id, "valor": 19, "comentario": "No permitido",
            "version": 2, "entregaVersion": 1,
        }, expected=409)
    finally:
        for temporal in reversed(tareas):
            cleanup(temporal)


if __name__ == "__main__":
    main()
