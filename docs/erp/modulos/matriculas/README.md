# Matrículas

## Objetivo

Gestionar el proceso institucional de matrícula.

## Responsabilidades

Ciclo de vida de la matrícula, según requisitos por aprobar. Las operaciones concretas están pendientes de especificación institucional.

## Límites

No mantiene el catálogo de personas ni define la oferta académica.

## Código

- Backend: [matriculas](../../../../apps/erp/api/src/main/java/pe/boston/erp/matriculas/package-info.java), con carpetas api, application, domain e infrastructure.
- Frontend: [matriculas](../../../../apps/erp/web/src/modules/matriculas/.gitkeep).
- Pruebas: [matriculas](../../../../apps/erp/api/src/test/java/pe/boston/erp/matriculas/.gitkeep).

## Dependencias permitidas

Contratos públicos aprobados de Personas y Oferta académica. La implementación actual declara cero dependencias entre módulos. La infraestructura técnica compartida reside en plataforma y no contiene reglas de negocio.

## Referencias

- [Especificación](ESPECIFICACION.md)
- [Estado](ESTADO.md)
- [Políticas transversales](../../POLITICAS_TRANSVERSALES.md)
