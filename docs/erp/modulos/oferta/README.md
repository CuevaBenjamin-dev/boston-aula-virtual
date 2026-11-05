# Oferta académica

## Objetivo

Definir la estructura académica ofertada.

## Responsabilidades

Periodos, grados, secciones y asignaturas solo cuando se apruebe su modelo. Las operaciones concretas están pendientes de especificación institucional.

## Límites

No matricula personas ni asigna docentes.

## Código

- Backend: [oferta](../../../../apps/erp/api/src/main/java/pe/boston/erp/oferta/package-info.java), con carpetas api, application, domain e infrastructure.
- Frontend: [oferta](../../../../apps/erp/web/src/modules/oferta/.gitkeep).
- Pruebas: [oferta](../../../../apps/erp/api/src/test/java/pe/boston/erp/oferta/.gitkeep).

## Dependencias permitidas

Contratos públicos aprobados de otros módulos, si se demuestran necesarios. La implementación actual declara cero dependencias entre módulos. La infraestructura técnica compartida reside en plataforma y no contiene reglas de negocio.

## Referencias

- [Especificación](ESPECIFICACION.md)
- [Estado](ESTADO.md)
- [Políticas transversales](../../POLITICAS_TRANSVERSALES.md)
