import { useCallback, useEffect, useState, type FormEvent } from "react";
import type { Sesion } from "../../shared/api/sesion";
import { erp } from "../../shared/api/erp";

type Seccion = {
  id: string;
  anio: number;
  nivel: string;
  grado: number;
  nombre: string;
};
type Asignatura = {
  id: string;
  anio: number;
  nivel: string;
  grado: number;
  codigo: string;
  nombre: string;
};
const GRADOS = [1, 2, 3, 4, 5, 6];
const orden = (n: number) => `${n}.º`;

export function OfertaModulo({ sesion }: { sesion: Sesion }) {
  const [anios, setAnios] = useState<number[]>([]);
  const [anio, setAnio] = useState<number>(new Date().getFullYear());
  const [nuevoAnio, setNuevoAnio] = useState(String(new Date().getFullYear()));
  const [nivel, setNivel] = useState("PRIMARIA");
  const [grado, setGrado] = useState(1);
  const [secciones, setSecciones] = useState<Seccion[]>([]);
  const [asignaturas, setAsignaturas] = useState<Asignatura[]>([]);
  const [nombreSeccion, setNombreSeccion] = useState("A");
  const [codigo, setCodigo] = useState("");
  const [nombreAsignatura, setNombreAsignatura] = useState("");
  const [error, setError] = useState("");
  const [aviso, setAviso] = useState("");
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);

  const recargar = useCallback(
    async (destino = anio) => {
      setCargando(true);
      setError("");
      try {
        const [y, s, a] = await Promise.all([
          erp<number[]>("/oferta/anios", sesion),
          erp<Seccion[]>(`/oferta/secciones?anio=${destino}`, sesion),
          erp<Asignatura[]>(`/oferta/asignaturas?anio=${destino}`, sesion),
        ]);
        setAnios(y);
        setSecciones(s);
        setAsignaturas(a);
        if (y.length && !y.includes(destino)) setAnio(y[0]);
      } catch (e) {
        setError(String(e instanceof Error ? e.message : e));
      } finally {
        setCargando(false);
      }
    },
    [anio, sesion],
  );
  useEffect(() => {
    void recargar();
  }, [recargar]);

  async function accion<T>(
    ruta: string,
    cuerpo: unknown,
    alGuardar: (valor: T) => void,
    mensaje: string,
  ) {
    setGuardando(true);
    setError("");
    setAviso("");
    try {
      const resultado = await erp<T>(ruta, sesion, "POST", cuerpo);
      alGuardar(resultado);
      setAviso(mensaje);
    } catch (e) {
      setError(String(e instanceof Error ? e.message : e));
    } finally {
      setGuardando(false);
    }
  }
  const seccionesGrado = secciones.filter(
    (s) => s.nivel === nivel && s.grado === grado,
  );
  const asignaturasGrado = asignaturas.filter(
    (a) => a.nivel === nivel && a.grado === grado,
  );
  const codigoCompleto = `${nivel === "PRIMARIA" ? "PR" : "SE"}-${String(grado).padStart(2, "0")}-${codigo || "SIGLA"}-${String(anio % 100).padStart(2, "0")}`;

  return (
    <section className="pagina">
      <div className="erp-pagina-titulo">
        <div>
          <p className="etiqueta">ORGANIZACIÓN ACADÉMICA</p>
          <p>
            Los cursos pertenecen a un grado y año; todas sus secciones
            comparten la misma malla.
          </p>
        </div>
      </div>
      {error && (
        <p className="per-aviso" role="alert">
          {error}
        </p>
      )}
      {aviso && (
        <p className="per-aviso exito" role="status">
          {aviso}
        </p>
      )}
      <div className="per-dos-columnas">
        <section className="per-panel">
          <div className="per-panel-cabecera">
            <h3>Año escolar</h3>
          </div>
          <label className="per-campo">
            Consultar año
            <select
              value={anio}
              disabled={!anios.length}
              onChange={(e) => setAnio(Number(e.target.value))}
            >
              {anios.length ? (
                anios.map((y) => <option key={y}>{y}</option>)
              ) : (
                <option value={anio}>Aún no hay años registrados</option>
              )}
            </select>
          </label>
          <form
            className="erp-inline"
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              void accion<{ anio: number }>(
                "/oferta/anios",
                { anio: Number(nuevoAnio) },
                (y) => {
                  setAnio(y.anio);
                  void recargar(y.anio);
                },
                "Año escolar creado. Configura sus secciones y asignaturas.",
              );
            }}
          >
            <label className="per-campo">
              Nuevo año
              <input
                type="number"
                min="2000"
                max="2100"
                value={nuevoAnio}
                onChange={(e) => setNuevoAnio(e.target.value)}
              />
            </label>
            <button className="boton-principal" disabled={guardando}>
              Crear año
            </button>
          </form>
        </section>
        <section className="per-panel">
          <div className="per-panel-cabecera">
            <h3>Grado</h3>
          </div>
          <div className="per-formulario">
            <label className="per-campo">
              Nivel
              <select
                value={nivel}
                onChange={(e) => {
                  setNivel(e.target.value);
                  setGrado(1);
                }}
              >
                <option value="PRIMARIA">Primaria</option>
                <option value="SECUNDARIA">Secundaria</option>
              </select>
            </label>
            <label className="per-campo">
              Grado
              <select
                value={grado}
                onChange={(e) => setGrado(Number(e.target.value))}
              >
                {GRADOS.filter((g) => nivel === "PRIMARIA" || g <= 5).map(
                  (g) => (
                    <option key={g} value={g}>
                      {orden(g)}
                    </option>
                  ),
                )}
              </select>
            </label>
          </div>
          <p className="per-texto">
            {nivel === "PRIMARIA"
              ? "Primaria: 1.º a 6.º"
              : "Secundaria: 1.º a 5.º"}{" "}
            · Periodos independientes.
          </p>
        </section>
      </div>
      {cargando ? (
        <p role="status">Cargando oferta…</p>
      ) : (
        <div className="per-dos-columnas">
          <section className="per-panel">
            <div className="per-panel-cabecera">
              <h3>Secciones · {orden(grado)}</h3>
              <span>{seccionesGrado.length}</span>
            </div>
            {seccionesGrado.length ? (
              seccionesGrado.map((s) => (
                <div className="per-fila" key={s.id}>
                  <strong>Sección {s.nombre}</strong>
                </div>
              ))
            ) : (
              <p className="per-vacio">Aún no hay secciones en este grado.</p>
            )}
            <form
              className="erp-inline"
              onSubmit={(e) => {
                e.preventDefault();
                void accion<Seccion>(
                  "/oferta/secciones",
                  { anio, nivel, grado, nombre: nombreSeccion },
                  (s) => {
                    setSecciones((v) => [...v, s]);
                    setNombreSeccion("");
                  },
                  "Sección creada.",
                );
              }}
            >
              <label className="per-campo">
                Nueva sección
                <input
                  required
                  maxLength={6}
                  value={nombreSeccion}
                  onChange={(e) =>
                    setNombreSeccion(e.target.value.toUpperCase())
                  }
                  placeholder="A"
                />
              </label>
              <button
                className="boton-principal"
                disabled={guardando || !anios.includes(anio)}
              >
                Agregar
              </button>
            </form>
          </section>
          <section className="per-panel">
            <div className="per-panel-cabecera">
              <h3>Asignaturas · {orden(grado)}</h3>
              <span>{asignaturasGrado.length}</span>
            </div>
            {asignaturasGrado.length ? (
              asignaturasGrado.map((a) => (
                <div className="per-fila" key={a.id}>
                  <strong>{a.nombre}</strong>
                  <small>{a.codigo}</small>
                </div>
              ))
            ) : (
              <p className="per-vacio">Aún no hay asignaturas en este grado.</p>
            )}
            <form
              className="erp-inline"
              onSubmit={(e) => {
                e.preventDefault();
                void accion<Asignatura>(
                  "/oferta/asignaturas",
                  { anio, nivel, grado, codigo, nombre: nombreAsignatura },
                  (a) => {
                    setAsignaturas((v) => [...v, a]);
                    setCodigo("");
                    setNombreAsignatura("");
                  },
                  "Asignatura creada para este año y grado.",
                );
              }}
            >
              <label className="per-campo">
                Sigla
                <input
                  required
                  maxLength={20}
                  pattern="[A-Z0-9_]+"
                  value={codigo}
                  onChange={(e) => setCodigo(e.target.value.toUpperCase())}
                  placeholder="MAT"
                  aria-describedby="oferta-codigo-previo"
                />
              </label>
              <label className="per-campo">
                Nombre
                <input
                  required
                  value={nombreAsignatura}
                  onChange={(e) => setNombreAsignatura(e.target.value)}
                  placeholder="Matemática"
                />
              </label>
              <button
                className="boton-principal"
                disabled={guardando || !anios.includes(anio)}
              >
                Agregar
              </button>
              <small
                id="oferta-codigo-previo"
                style={{ flex: "0 0 100%", fontSize: 12 }}
              >
                Código: {codigoCompleto}
              </small>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}
