# Expediente de descubrimiento para MODULA

**Corte:** 2026-11-11T10:00:11-05:00. **Repositorio:** `sistema-bbs`, commit `c6dece0b6b30fcc6cd9a3361e202e5388508238c`. Revisión documental y estática, sin cambios de código ni ejecución de servicios o pruebas.

## Inventario

| Elemento | Cantidad | Lectura |
|---|---:|---|
| Módulos/capacidades | 17 | Cinco módulos ERP; Aula e Identidad desglosados por responsabilidad técnica. |
| Actores | 6 | Dirección, Administración, Coordinación, Docente, Estudiante y Apoderado. |
| Requisitos/políticas | 72 | 52 aprobados de Personas, 9 políticas transversales y 11 comportamientos inferidos de código. |
| Casos de uso | 18 | 7 aprobados de Personas y 11 inferidos de código. |
| Relaciones | 161 | Referencias validadas contra claves estables. |
| Dependencias técnicas externas | 3 | PostgreSQL, S3 local y enlaces externos. |
| Hallazgos | 12 | Conflictos, brechas y puntos de revisión con referencias. |

## Estado observado

Personas tiene requisitos funcionales aprobados y una implementación parcial. Sus cinco verificaciones figuran en [ESTADO.md](docs/erp/modulos/personas/ESTADO.md); solo Requisitos está Completada. Matrículas, Oferta académica, Asignaciones docentes y Documentos tienen alcance documentado y estructura preparada, pero sus requisitos e implementación de negocio siguen pendientes. Aula e Identidad conservan código funcional; sus capacidades aquí se clasifican como `inferred_code` porque observar rutas o clases no acredita aprobación institucional vigente.

## Hallazgos prioritarios

1. La restricción de documentos históricos de [RI-PER-013](docs/erp/modulos/personas/ESPECIFICACION.md) difiere de la unicidad temporal en [V2](apps/erp/api/src/main/resources/db/migration/V2__personas.sql).
2. El [control de autorización de Personas](apps/erp/api/src/main/java/pe/boston/erp/personas/application/AutorizacionPersonas.java) permite alta por rol Coordinación sin ámbito comprobado y corrección de identidad por rol, mientras la política exige permiso expreso.
3. [Identidad, Matrículas y Asignaciones](docs/erp/modulos/personas/ESTADO.md) aún no aportan todos los contratos necesarios para permisos y acceso docente verificables. La integración ERP–Aula de negocio está temporalmente no disponible según [políticas](docs/erp/POLITICAS_TRANSVERSALES.md).
4. El [índice de Personas](docs/erp/modulos/personas/README.md) está desactualizado respecto de ESTADO; la [arquitectura general](docs/arquitectura/README.md) conserva referencias al ERP anterior. Consultar las fuentes vigentes por módulo antes de decidir.
5. Los [resultados de pruebas](docs/erp/modulos/personas/PRUEBAS.md) son históricos; este expediente no constituye validación de ejecución ni autorización para datos reales.

## Validación

El JSON [modula-discovery.v1.json](modula-discovery.v1.json) se validó en memoria contra el esquema adjunto: campos obligatorios, tipos, enumeraciones, formatos, tamaños, límites de arreglos, `additionalProperties`, claves únicas, referencias entre entidades y existencia de rutas de evidencia. Las referencias describen fuentes; no contienen comandos ni credenciales.
