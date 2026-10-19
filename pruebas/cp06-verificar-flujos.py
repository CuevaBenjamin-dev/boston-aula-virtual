"""CP-06: comprobaciones locales con tareas transitorias; conserva el conjunto CP-05."""
import datetime as dt
import runpy
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))
Session, ident, check = helpers["Session"], helpers["ident"], helpers["check"]
PREFIX = "CP06 prueba temporal"


def sql(statement):
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1",
         "-U", "boston", "-d", "aula_boston", "-At"],
        cwd=ROOT, input=statement, text=True, capture_output=True, check=True,
    )
    return result.stdout.strip()


def cleanup(lote):
    tareas = f"SELECT id FROM tareas WHERE titulo LIKE '{PREFIX}%'"
    script = ["BEGIN;",
      f"DELETE FROM registros_auditoria WHERE entidad_id IN ({tareas}) OR entidad_id IN (SELECT id FROM entregas WHERE tarea_id IN ({tareas})) OR entidad_id IN (SELECT id FROM archivos_tarea WHERE tarea_id IN ({tareas})) OR entidad_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id IN ({tareas}));",
      f"DELETE FROM eventos_notificacion_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id IN ({tareas}));",
      f"DELETE FROM notificaciones_tarea WHERE tarea_id IN ({tareas});",
      f"DELETE FROM archivos_entrega WHERE entrega_id IN (SELECT id FROM entregas WHERE tarea_id IN ({tareas}));",
      f"DELETE FROM historial_calificaciones WHERE calificacion_id IN (SELECT id FROM calificaciones_tarea WHERE tarea_id IN ({tareas}));",
      f"DELETE FROM calificaciones_tarea WHERE tarea_id IN ({tareas});",
      f"DELETE FROM archivos_tarea WHERE tarea_id IN ({tareas});",
      f"DELETE FROM entregas WHERE tarea_id IN ({tareas});",
      f"DELETE FROM tareas WHERE tarea_anterior_id IS NOT NULL AND titulo LIKE '{PREFIX}%';",
      f"DELETE FROM tareas WHERE titulo LIKE '{PREFIX}%';",
      f"DELETE FROM lotes_tarea WHERE id='{lote}'::uuid;",
      "COMMIT;",
    ]
    sql("\n".join(script))


def main():
    teacher = Session("docente.demo")
    a = Session("estudiante.demo")
    b = Session("otro.demo")
    c = Session("estudiante.p5a3.demo")
    p5b_student = Session("estudiante.p5b.demo")
    p5a = ident("cp05:oferta:P5A:Matemática")
    p5b = ident("cp05:oferta:P5B:Matemática")
    communication = ident("cp05:oferta:P5A:Comunicación")
    lote = str(uuid.uuid4())
    try:
        now = dt.datetime.now(dt.timezone.utc)
        future = (now + dt.timedelta(days=2)).isoformat()
        extended = (now + dt.timedelta(days=3)).isoformat()
        past = (now - dt.timedelta(hours=1)).isoformat()

        task = teacher.api(f"/cursos/{p5a}/tareas", "POST", {
            "titulo": f"{PREFIX} edición", "instrucciones": "Resolver ejercicios.", "fechaLimite": future
        })["id"]
        teacher.api(f"/tareas/{task}/publicar", "POST", {})
        check(teacher.api(f"/tareas/{task}")["estado"] == "PUBLICADA", "Publicación y plazo explícito")
        delivery = a.api(f"/tareas/{task}/entregas", "POST", {"respuesta": "Respuesta dentro del plazo"})["id"]
        delivered = a.api(f"/tareas/{task}/entregas")[0]
        check(delivered["id"] == delivery and delivered["estado"] == "ENVIADA" and delivered["fecha_limite_al_entregar"],
              "Entrega dentro del plazo con fecha y límite conservados")

        before = teacher.api(f"/tareas/{task}")
        op_minor = str(uuid.uuid4())
        minor = {"titulo": before["titulo"], "instrucciones": before["instrucciones"], "fechaLimite": extended,
                 "version": before["version"], "cursoId": p5a, "tipoCambio": "MENOR", "operacionId": op_minor}
        edited = teacher.api(f"/tareas/{task}", "PUT", minor)
        retry = teacher.api(f"/tareas/{task}", "PUT", minor)
        check(edited["id"] == task and retry["id"] == task and len(teacher.api(f"/tareas/{task}/entregas")) == 1,
              "Ampliación menor conserva la tarea y la entrega; reintento idempotente")
        notices = [n for n in a.api("/notificaciones") if n.get("tarea_id") == task and n["tipo"] == "AMPLIADA"]
        check(len(notices) == 1 and a.api(f"/tareas/{task}/entregas")[0]["fecha_limite_al_entregar"] == delivered["fecha_limite_al_entregar"],
              "Ampliación notifica una vez y mantiene el plazo aplicable a la entrega")

        old = teacher.api(f"/tareas/{task}")
        successor = teacher.api(f"/tareas/{task}", "PUT", {
            "titulo": f"{PREFIX} nueva versión", "instrucciones": "Resolver ejercicios distintos.",
            "fechaLimite": extended, "version": old["version"], "cursoId": p5a,
            "tipoCambio": "SUSTANCIAL", "confirmarReinicio": True, "operacionId": str(uuid.uuid4())
        })
        check(successor["id"] != task and len(teacher.api(f"/tareas/{task}/entregas")) == 1 and
              len(teacher.api(f"/tareas/{successor['id']}/entregas")) == 0,
              "Cambio sustancial crea versión sin trasladar entregas")
        teacher.api(f"/tareas/{task}/publicar", "POST", {}, expected=400)
        check(True, "Versión histórica no puede reabrirse")
        teacher.api(f"/tareas/{successor['id']}/cancelar", "POST", {})
        a.api(f"/tareas/{successor['id']}/entregas", "POST", {"respuesta": "No admitida"}, expected=400)
        check(teacher.api(f"/tareas/{successor['id']}")["estado"] == "CANCELADA", "Cancelación bloquea entregas sin borrar historial")

        late_task = teacher.api(f"/cursos/{p5a}/tareas", "POST", {
            "titulo": f"{PREFIX} tardía", "instrucciones": "Entrega tardía de prueba.", "fechaLimite": past
        })["id"]
        teacher.api(f"/tareas/{late_task}/publicar", "POST", {})
        late_delivery = b.api(f"/tareas/{late_task}/entregas", "POST", {"respuesta": "Entrega después del vencimiento"})["id"]
        late = b.api(f"/tareas/{late_task}/entregas")[0]
        check(late["id"] == late_delivery and late["estado"] == "TARDIA" and
              teacher.api(f"/tareas/{late_task}")["estado"] == "PUBLICADA",
              "Vencimiento no cierra la tarea y la entrega queda tardía")
        teacher.api(f"/tareas/{late_task}/cerrar", "POST", {})
        c.api(f"/tareas/{late_task}/entregas", "POST", {"respuesta": "Bloqueada"}, expected=400)
        teacher.api(f"/tareas/{late_task}/publicar", "POST", {})
        reopened = c.api(f"/tareas/{late_task}/entregas", "POST", {"respuesta": "Entrega tras reapertura"})
        check(teacher.api(f"/tareas/{late_task}")["estado"] == "PUBLICADA" and
              c.api(f"/tareas/{late_task}/entregas")[0]["id"] == reopened["id"] and
              c.api(f"/tareas/{late_task}/entregas")[0]["estado"] == "TARDIA",
              "Cierre bloquea; reapertura recibe de nuevo como tardía")
        row = next(t for t in teacher.api(f"/cursos/{p5a}/tareas") if t["id"] == late_task)
        groups = {g: teacher.api(f"/tareas/{late_task}/seguimiento?grupo={g}") for g in
                  ("ENTREGARON", "TARDIAS", "PENDIENTES", "REVISION")}
        check(row["total_estudiantes"] == 22 and row["entregados"] == 2 and row["tardias"] == 2 and
              row["pendientes"] == 20 and row["pendientes_revision"] == 2 and
              [len(groups[g]) for g in groups] == [2, 2, 20, 2],
              "Seguimiento cuenta estudiantes únicos y permite filtrar cada grupo")

        multiple = {"cursoIds": [p5a, p5b], "titulo": f"{PREFIX} secciones",
                    "instrucciones": "Mismo enunciado, tareas independientes.", "fechaLimite": future,
                    "operacionId": lote}
        created = teacher.api("/tareas/lotes", "POST", multiple)
        repeated = teacher.api("/tareas/lotes", "POST", multiple)
        tasks = created["tareas"]
        check(len(tasks) == 2 and {t["curso_id"] for t in tasks} == {p5a, p5b} and
              {t["id"] for t in repeated["tareas"]} == {t["id"] for t in tasks} and
              all(t["estado"] == "FALTA_COMPLETAR" for t in tasks),
              "Creación múltiple transaccional e idempotente, con borradores independientes")
        source = tasks[0]["id"]
        sql(f"""
          INSERT INTO archivos_tarea(id,tarea_id,nombre,clave_objeto,tamano,tipo,estado,activo)
          SELECT gen_random_uuid(),'{source}'::uuid,'Referencia temporal.pdf',clave_objeto,tamano,tipo,'DISPONIBLE',TRUE
          FROM archivos_tarea WHERE tarea_id=md5('cp05:tarea:entregas')::uuid AND estado='DISPONIBLE' AND activo LIMIT 1;
        """)
        teacher.api(f"/tareas/lotes/{lote}/compartir-archivos", "POST", {})
        check(all(len(teacher.api(f"/tareas/{t['id']}/archivos")) == 1 for t in tasks) and
              sql(f"SELECT count(DISTINCT clave_objeto) FROM archivos_tarea WHERE tarea_id IN ('{tasks[0]['id']}'::uuid,'{tasks[1]['id']}'::uuid);") == "1",
              "Un archivo físico compartido entre tareas independientes")
        teacher.api(f"/tareas/lotes/{lote}/publicar", "POST", {})
        teacher.api(f"/tareas/lotes/{lote}/publicar", "POST", {})
        by_course = {t["curso_id"]: t["id"] for t in tasks}
        a.api(f"/tareas/{by_course[p5b]}", expected=403)
        p5b_student.api(f"/tareas/{by_course[p5a]}", expected=403)
        check(any(n.get("tarea_id") == by_course[p5a] for n in a.api("/notificaciones")) and
              any(n.get("tarea_id") == by_course[p5b] for n in p5b_student.api("/notificaciones")),
              "Publicación y permisos aislados por sección")
        teacher.api(f"/tareas/{by_course[p5a]}/cancelar", "POST", {})
        check(teacher.api(f"/tareas/{by_course[p5b]}")["estado"] == "PUBLICADA",
              "Cancelar una sección no modifica la otra")

        foreign = str(uuid.uuid4())
        sql(f"""
          INSERT INTO tareas(id,curso_id,titulo,instrucciones,fecha_limite,estado,creado_por)
          VALUES ('{foreign}'::uuid,'{communication}'::uuid,'{PREFIX} ajena','Sin acceso docente',now()+interval '1 day','FALTA_COMPLETAR',gen_random_uuid());
        """)
        teacher.api(f"/tareas/{foreign}", "PUT", {"titulo": f"{PREFIX} ajena", "instrucciones": "Cambio indebido",
                    "fechaLimite": future, "version": 0, "cursoId": communication,
                    "tipoCambio": "MENOR", "operacionId": str(uuid.uuid4())}, expected=403)
        check(True, "Docente no puede editar tarea de otro curso")
    finally:
        cleanup(lote)


if __name__ == "__main__":
    main()
