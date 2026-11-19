# Arquitectura — Personas

## Componentes y límites

Personas será el registro central de personas físicas. Una persona podrá tener múltiples condiciones y relaciones institucionales sin duplicar sus datos personales.

Las entidades conceptuales aprobadas son Persona, Documento de identidad, Condición institucional y Vínculo entre personas. Su materialización PostgreSQL V2 es una propuesta técnica implementada, pendiente de aprobación arquitectónica definitiva.

La persona conserva su UUID al corregir su identidad. Las condiciones institucionales son independientes de los roles de seguridad, conforme a las [políticas transversales](../../POLITICAS_TRANSVERSALES.md). El vínculo con un estudiante es distinto de la condición Apoderado y del parentesco.

El módulo usa `api/`, `application/`, `domain/` e `infrastructure/` en Java y `modules/personas/` en React. Personas no es propietario de credenciales, matrículas, oferta académica ni asignaciones docentes. Spring Modulith verifica sus límites en la suite.

**Brecha vigente:** `application/` e `infrastructure/` importan `api.PersonaDto`; la separación de modelos internos y el posible paquete `contratos/` con `@NamedInterface` están pendientes de refactorización. El test actual de Spring Modulith verifica módulos, no esta regla entre capas. Ver [estándar modular](../../ingenieria/ESTANDAR_ARQUITECTURA_MODULAR.md).

## Contratos, seguridad e interfaz

Todos los endpoints nuevos están bajo `/api/erp/v1/personas`; reciben/devuelven JSON salvo las mutaciones que devuelven `Location` o 204. El contrato tipado se expone solo a Dirección en `/api/erp/v1/openapi` mediante springdoc. Los DTO están en `PersonaDto.java`; el esquema de error controlado es `{codigo,mensaje}`: 400 datos/formato, 401 sin sesión, 403 sin facultad, 404 recurso inexistente, 409 conflicto de unicidad o vigencia.

| Método y ruta relativa a `/api/erp/v1/personas` | Contrato principal | Facultad |
|---|---|---|
| `GET /capacidades` | `Capacidades` de la sesión | ERP autenticado permitido. |
| `GET /tipos-documento`, `POST /tipos-documento` | Lista y alta de `TipoDocumento` | Gestión ERP autorizada. |
| `GET /duplicado-documental?tipo=&pais=&numero=` | `{existe:boolean}`, sin identidad revelada | Registro. |
| `GET /?buscar=&condicion=&vigente=&pagina=&tamano=` | `Pagina<Resumen>`, tamaño 1–50 | Gestión con ámbito. |
| `POST /` | `CrearPersona`; 201 con `Location` | Dirección o Coordinación; apoderado inicial requiere autorización comprobada. |
| `GET /{id}` | `Persona` con documentos, condiciones, provisional y vínculos | Recurso autorizado. |
| `PUT /{id}`, `PUT /{id}/contacto` | `EditarPersona`, `Contacto` | Identidad reservada; contacto según ámbito o propio docente. |
| `POST /{id}/documentos` | `DocumentoEntrada` | Identidad reservada. |
| `POST /{id}/condiciones`, `POST /{id}/condiciones/{condicion}/desactivar` | `NuevaCondicion`, `Motivo` | Gestión con ámbito. |
| `POST /parentescos`, `POST /{id}/parentescos/{vinculo}/desactivar` | `ParentescoEntrada`, cierre | Gestión con ámbito. |
| `POST /apoderamientos`, `POST /{id}/apoderamientos/{vinculo}/desactivar` | `ApoderamientoEntrada` con autorización cuando aplique; cierre | Gestión con ámbito. |

La comprobación de autorización no familiar admite sustento verbal u otro tipo registrado sin imponer un catálogo institucional no aprobado. Si no se indica el tipo, el flujo actual usa `VERBAL` como valor predeterminado técnico. En todos los casos registra autorizador, verificador (sub de sesión), medio, instante y referencia. La facultad del autorizador se comprueba por el personal actuante; la forma exacta del sustento y sus pruebas institucionales permanecen pendientes.

La fachada `PersonasParaMatriculas` en la raíz del módulo ofrece búsqueda por documento, alta de estudiante, comprobación de condición e identificación, apoderados vigentes y vinculación autorizada; Matrículas la consume. `PersonasParaAsignaciones` permite comprobar condición Docente activa. Personas no accede a tablas de Matrículas o Asignaciones. Las operaciones participan en transacciones locales del ERP; no hacen HTTP a Identidad dentro de ellas.

```mermaid
flowchart LR
  W[ERP Web: tres vistas] --> A[API Personas v1]
  A --> S[Autorización por rol y recurso]
  A --> C[Casos de uso transaccionales]
  C --> P[(PostgreSQL V2)]
  M[Matrículas y Asignaciones] -. contratos públicos .-> C
  I[Identidad OIDC] --> S
```

Solo Dirección y Coordinación Académica ingresan al ERP y gestionan la ficha según la política operativa posterior a MODULA 532. Docentes, estudiantes, apoderados y otro personal no ingresan al ERP. La condición personal no concede rol. `SeguridadWeb` mantiene denegación por defecto y CSRF en escrituras. El diseño más fino de facultades por operación y la reconciliación con los requisitos formales previos permanecen pendientes.

El frontend contiene Directorio, Registro contextual y Ficha; usa búsqueda paginada, verificación de duplicado, campos por condición, identificación provisional, selector de personas para vínculos, estados de carga/vacío/error/éxito y acciones según capacidades. Está en `apps/erp/web/src/modules/personas/`. La cobertura y las verificaciones pendientes se documentan en [PRUEBAS.md](PRUEBAS.md).

## Decisiones técnicas pendientes

Revisar separación de DTO HTTP, contratos públicos y dependencias declaradas; definir facultades finas de Identidad compatibles con los dos roles ERP. La política funcional de [ESPECIFICACION.md](ESPECIFICACION.md) exige diferenciar contacto autorizado, apoderado con alcance limitado y representante legal, con historial de modificación/revocación; el contrato y el modelo actuales no bastan automáticamente. La corrección excepcional de asociación documental histórica, los estados de información pendiente y los catálogos requieren diseño propio. Las diferencias entre requisitos formales anteriores y las decisiones operativas del 19/11/2026 deben tramitarse en MODULA; esta descripción no equivale a aprobación formal.
