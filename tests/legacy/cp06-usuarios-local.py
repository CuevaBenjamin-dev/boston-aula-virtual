"""Prepara siete usuarios CP-06 mediante las APIs locales; no crea tareas.

Única excepción de catálogo: ERP no ofrece alta de grados. Se agrega Tercero de
Secundaria de modo idempotente en la base ERP; todo lo demás usa sus APIs.
"""
import json
import runpy
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT / "pruebas" / "cp05-verificar-flujos.py"))
Session, PASSWORD = helpers["Session"], helpers["PASSWORD"]

STUDENTS = (
    ("cp06.sofia.demo", "Sofía", "Ramírez", "CP06-SOFIA", "Primaria", "Quinto", "A", "Matemática"),
    ("cp06.mateo.demo", "Mateo", "Fernández", "CP06-MATEO", "Primaria", "Quinto", "A", "Matemática"),
    ("cp06.valeria.demo", "Valeria", "Torres", "CP06-VALERIA", "Secundaria", "Tercero", "A", "Comunicación"),
    ("cp06.diego.demo", "Diego", "Mendoza", "CP06-DIEGO", "Secundaria", "Quinto", "B", "Inglés"),
)
TEACHERS = (
    ("cp06.carlos.demo", "Carlos", "Herrera", (("Primaria", "Quinto", "A", "Matemática"), ("Primaria", "Quinto", "B", "Matemática"))),
    ("cp06.patricia.demo", "Patricia", "Salazar", (("Secundaria", "Tercero", "A", "Comunicación"),)),
    ("cp06.roberto.demo", "Roberto", "Vargas", (("Secundaria", "Quinto", "B", "Inglés"),)),
)


def sql(base, statement):
    completed = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", "boston", "-d", base, "-At"],
        cwd=ROOT, input=statement, capture_output=True, text=True, encoding="utf-8", check=True,
    )
    return completed.stdout.strip()


def one(base, statement):
    rows = sql(base, statement).splitlines()
    if len(rows) > 1:
        raise AssertionError("Se encontraron varios registros para una clave CP-06")
    return json.loads(rows[0]) if rows else None


def local_only():
    container = subprocess.run(["docker", "compose", "ps", "-q", "postgres"], cwd=ROOT,
                               capture_output=True, text=True, check=True).stdout.strip()
    if not container:
        raise RuntimeError("PostgreSQL local no está funcionando")
    labels = json.loads(subprocess.run(["docker", "inspect", container, "--format", "{{json .Config.Labels}}"],
                                       capture_output=True, text=True, check=True).stdout)
    if labels.get("com.docker.compose.project") != "boston-aula" or Path(labels.get("com.docker.compose.project.working_dir", "")).resolve() != ROOT:
        raise RuntimeError("El contenedor no pertenece a este proyecto local")


def course(nivel, grado, seccion, asignatura):
    # Los nombres son constantes del script, nunca entradas del usuario.
    return one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT o.id,o.seccion_id,ad.docente_id
        FROM ofertas_academicas o JOIN secciones s ON s.id=o.seccion_id
        JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id
        JOIN periodos_academicos p ON p.id=s.periodo_id
        JOIN asignaturas a ON a.id=o.asignatura_id
        LEFT JOIN asignaciones_docentes ad ON ad.oferta_id=o.id AND ad.estado='ACTIVA'
        WHERE n.nombre='{nivel}' AND g.nombre='{grado}' AND s.nombre='{seccion}'
          AND a.nombre='{asignatura}' AND p.nombre='2026' AND o.estado='ACTIVA'
      ) x;
    """)


def section(nivel, grado, seccion):
    return one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT s.id,s.grado_id,s.periodo_id FROM secciones s
        JOIN grados g ON g.id=s.grado_id JOIN niveles_educativos n ON n.id=g.nivel_id
        JOIN periodos_academicos p ON p.id=s.periodo_id
        WHERE n.nombre='{nivel}' AND g.nombre='{grado}' AND s.nombre='{seccion}' AND p.nombre='2026'
      ) x;
    """)


def ensure_grade():
    # El catálogo y la API ERP no exponen POST /grados; esta es la única alta SQL.
    sql("erp_boston", """
      INSERT INTO grados(id,nivel_id,nombre)
      SELECT md5('cp06:grado:Secundaria:Tercero')::uuid,n.id,'Tercero'
      FROM niveles_educativos n WHERE n.nombre='Secundaria'
      ON CONFLICT(nivel_id,nombre) DO NOTHING;
    """)
    result = one("erp_boston", """
      SELECT row_to_json(x) FROM (
        SELECT g.id FROM grados g JOIN niveles_educativos n ON n.id=g.nivel_id
        WHERE n.nombre='Secundaria' AND g.nombre='Tercero'
      ) x;
    """)
    if not result:
        raise AssertionError("No se pudo preparar el grado Tercero de Secundaria")


def ensure_section(admin, nivel, grado, letra):
    found = section(nivel, grado, letra)
    if found:
        return found["id"]
    meta = one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT g.id AS grado_id,p.id AS periodo_id FROM grados g
        JOIN niveles_educativos n ON n.id=g.nivel_id CROSS JOIN periodos_academicos p
        WHERE n.nombre='{nivel}' AND g.nombre='{grado}' AND p.nombre='2026'
      ) x;
    """)
    if not meta:
        raise AssertionError(f"Falta el grado {nivel} {grado} del periodo 2026")
    admin.api("/administracion-erp/secciones", "POST", {"gradoId": meta["grado_id"], "periodoId": meta["periodo_id"], "nombre": letra}, expected=201)
    return section(nivel, grado, letra)["id"]


def ensure_course(admin, nivel, grado, letra, asignatura):
    found = course(nivel, grado, letra, asignatura)
    if found:
        return found["id"]
    meta = section(nivel, grado, letra)
    subject = one("erp_boston", f"SELECT row_to_json(x) FROM (SELECT id FROM asignaturas WHERE nombre='{asignatura}') x;")
    if not meta or not subject:
        raise AssertionError(f"Falta sección o asignatura para {asignatura} {nivel} {grado} {letra}")
    admin.api("/administracion-erp/ofertas", "POST", {"seccionId": meta["id"], "asignaturaId": subject["id"]}, expected=201)
    return course(nivel, grado, letra, asignatura)["id"]


def ensure_student(admin, given, surname, code, sec):
    found = one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT e.id,p.nombres,p.apellidos,e.estado FROM estudiantes e
        JOIN personas p ON p.id=e.persona_id WHERE e.codigo='{code}'
      ) x;
    """)
    if found:
        if (found["nombres"], found["apellidos"], found["estado"]) != (given, surname, "ACTIVO"):
            raise AssertionError(f"Código académico {code} ocupado por otra persona o inactivo")
        student_id = found["id"]
    else:
        student_id = admin.api("/administracion-erp/estudiantes", "POST", {"nombres": given, "apellidos": surname, "codigo": code}, expected=201)["id"]
    current = one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT id,seccion_id FROM matriculas WHERE estudiante_id='{student_id}'::uuid AND estado='ACTIVA'
      ) x;
    """)
    if current and current["seccion_id"] != sec:
        raise AssertionError(f"{code} ya tiene matrícula activa en otra sección")
    if not current:
        admin.api("/administracion-erp/matriculas", "POST", {"estudianteId": student_id, "seccionId": sec}, expected=201)
    return student_id


def ensure_teacher(admin, given, surname):
    found = one("erp_boston", f"""
      SELECT row_to_json(x) FROM (
        SELECT d.id,d.estado FROM docentes d JOIN personas p ON p.id=d.persona_id
        WHERE p.nombres='{given}' AND p.apellidos='{surname}'
      ) x;
    """)
    if found:
        if found["estado"] != "ACTIVO":
            raise AssertionError(f"Docente {given} {surname} inactivo")
        return found["id"]
    return admin.api("/administracion-erp/docentes", "POST", {"nombres": given, "apellidos": surname}, expected=201)["id"]


def ensure_account(admin, existing, username, kind, reference):
    found = existing.get(username)
    if found:
        if found["tipo"] != kind or found["referencia_erp"] != reference:
            raise AssertionError(f"La cuenta {username} tiene otro rol o vínculo ERP")
        if not found["activo"]:
            admin.api(f"/cuentas/{found['id']}/estado", "PUT", {"activo": True})
        return found["id"]
    result = admin.api("/cuentas", "POST", {"usuario": username, "claveInicial": PASSWORD,
                                            "tipo": kind, "referenciaErp": reference, "developer": False})
    existing[username] = {"id": result["id"], "tipo": kind, "referencia_erp": reference, "activo": True}
    return result["id"]


def counts():
    return {
        "erp_estudiantes": int(sql("erp_boston", "SELECT count(*) FROM estudiantes;")),
        "erp_docentes": int(sql("erp_boston", "SELECT count(*) FROM docentes;")),
        "erp_secciones": int(sql("erp_boston", "SELECT count(*) FROM secciones;")),
        "erp_ofertas": int(sql("erp_boston", "SELECT count(*) FROM ofertas_academicas;")),
        "erp_matriculas": int(sql("erp_boston", "SELECT count(*) FROM matriculas;")),
        "erp_asignaciones": int(sql("erp_boston", "SELECT count(*) FROM asignaciones_docentes;")),
        "identidad_cuentas": int(sql("identidad_boston", "SELECT count(*) FROM cuentas;")),
        "aula_cursos": int(sql("aula_boston", "SELECT count(*) FROM cursos;")),
        "aula_tareas": int(sql("aula_boston", "SELECT count(*) FROM tareas;")),
    }


def verify_projection(admin, assigned, students, teachers):
    admin.api("/integraciones/reintentar", "POST", {})
    for _ in range(30):
        projected = True
        for username, student_id in students.items():
            wanted = section(*assigned[username][:3])["id"]
            actual = one("aula_boston", f"""
              SELECT row_to_json(x) FROM (
                SELECT count(DISTINCT c.id)::int AS cursos,count(DISTINCT c.seccion_erp_id)::int AS secciones
                FROM inscripciones_curso i JOIN cursos c ON c.id=i.curso_id
                WHERE i.estudiante_erp_id='{student_id}'::uuid AND i.activo AND c.seccion_erp_id='{wanted}'::uuid
              ) x;
            """)
            if actual["cursos"] < 1 or actual["secciones"] != 1:
                projected = False
        for username, teacher_id in teachers.items():
            wanted = assigned[username]
            actual = sql("aula_boston", f"SELECT count(*) FROM docentes_curso WHERE docente_erp_id='{teacher_id}'::uuid AND activo;")
            if int(actual) != len(wanted):
                projected = False
        if projected:
            return
        time.sleep(2)
        admin.api("/integraciones/reintentar", "POST", {})
    raise AssertionError("No se completó la sincronización ERP → Aula")


def verify_access(assigned, student_ids, teacher_ids):
    sessions = {}
    for username, _given, _surname, _code, nivel, grado, letra, subject in STUDENTS:
        session = Session(username)
        courses = session.api("/cursos")
        expected = {c["id"] for c in (course(nivel, grado, letra, a) for a in ({subject} if nivel == "Secundaria" else {"Matemática", "Comunicación", "Inglés"}))}
        actual = {c["id"] for c in courses}
        if actual != expected or any((c["nivel"], c["grado"], c["seccion"]) != (nivel, grado, letra) for c in courses):
            raise AssertionError(f"Cursos inesperados para {username}")
        sessions[username] = session
        print(f"OK {username}: {len(courses)} curso(s), {nivel} {grado} {letra}")
    sofia = {c["id"] for c in sessions["cp06.sofia.demo"].api("/cursos")}
    mateo = {c["id"] for c in sessions["cp06.mateo.demo"].api("/cursos")}
    if sofia != mateo or student_ids["cp06.sofia.demo"] == student_ids["cp06.mateo.demo"]:
        raise AssertionError("Sofía y Mateo no comparten exactamente los cursos de su sección")

    for username, _given, _surname, wanted in TEACHERS:
        session = Session(username)
        courses = session.api("/cursos")
        expected = {course(*item)["id"] for item in wanted}
        if {c["id"] for c in courses} != expected:
            raise AssertionError(f"Asignaciones inesperadas para {username}")
        for item in wanted:
            session.api(f"/cursos/{course(*item)['id']}/tareas")
        sessions[username] = session
        print(f"OK {username}: {len(courses)} curso(s) autorizado(s)")
    forbidden = one("aula_boston", """
      SELECT row_to_json(x) FROM (
        SELECT t.id,t.titulo,t.instrucciones,t.fecha_limite,t.version,t.curso_id
        FROM tareas t JOIN cursos c ON c.id=t.curso_id
        WHERE c.nivel='Primaria' AND c.grado='Quinto' AND c.seccion='A' AND c.asignatura='Matemática'
        ORDER BY t.creado_en LIMIT 1
      ) x;
    """)
    if not forbidden:
        raise AssertionError("Falta tarea de referencia para comprobar permisos")
    sessions["cp06.patricia.demo"].api(f"/tareas/{forbidden['id']}", "PUT", {
        "titulo": forbidden["titulo"], "instrucciones": forbidden["instrucciones"],
        "fechaLimite": forbidden["fecha_limite"], "version": forbidden["version"],
        "cursoId": forbidden["curso_id"], "tipoCambio": "MENOR"
    }, expected=403)
    print("OK: edición de curso ajeno rechazada sin cambios")


def main():
    local_only()
    baseline = counts()
    if baseline["erp_estudiantes"] < 66 or baseline["aula_cursos"] < 9 or baseline["aula_tareas"] < 7:
        raise AssertionError("No está presente el conjunto CP-05/CP-06 esperado")
    task_ids = sql("aula_boston", "SELECT id FROM tareas ORDER BY id;")
    admin = Session("admin.demo")
    ensure_grade()
    ensure_section(admin, "Secundaria", "Tercero", "A")
    ensure_section(admin, "Secundaria", "Quinto", "B")
    ensure_course(admin, "Secundaria", "Tercero", "A", "Comunicación")
    ensure_course(admin, "Secundaria", "Quinto", "B", "Inglés")
    students = {}
    assigned = {}
    for username, given, surname, code, nivel, grado, letra, subject in STUDENTS:
        students[username] = ensure_student(admin, given, surname, code, section(nivel, grado, letra)["id"])
        assigned[username] = (nivel, grado, letra, subject)
    teachers = {}
    for username, given, surname, wanted in TEACHERS:
        teacher_id = ensure_teacher(admin, given, surname)
        teachers[username] = teacher_id
        assigned[username] = wanted
        for item in wanted:
            offer = course(*item)
            if offer["docente_id"] != teacher_id:
                admin.api(f"/administracion-erp/ofertas/{offer['id']}/docente", "PUT", {"docenteId": teacher_id})
    accounts = {c["usuario"]: c for c in admin.api("/cuentas")}
    for username, reference in students.items():
        ensure_account(admin, accounts, username, "ESTUDIANTE", reference)
    for username, reference in teachers.items():
        ensure_account(admin, accounts, username, "DOCENTE", reference)
    verify_projection(admin, assigned, students, teachers)
    verify_access(assigned, students, teachers)
    after = counts()
    if sql("aula_boston", "SELECT id FROM tareas ORDER BY id;") != task_ids:
        raise AssertionError("El conjunto de tareas de referencia cambió")
    if after["erp_estudiantes"] < 70 or after["aula_cursos"] < 11:
        raise AssertionError("Faltan estudiantes o cursos CP-06")
    print("OK: proyección ERP → Aula; tareas de referencia intactas")
    print("Conteos locales:", json.dumps(after, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
