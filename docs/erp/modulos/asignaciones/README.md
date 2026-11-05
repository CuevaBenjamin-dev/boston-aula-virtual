# Asignaciones docentes

## Objetivo

Gestionar la relación entre docentes y oferta académica.

## Responsabilidades

Asignaciones docentes según reglas institucionales por aprobar. Las operaciones concretas están pendientes de especificación institucional.

## Límites

No mantiene identidad de docentes ni estructura de la oferta.

## Código

- Backend: [asignaciones](../../../../apps/erp/api/src/main/java/pe/boston/erp/asignaciones/package-info.java), con carpetas api, application, domain e infrastructure.
- Frontend: [asignaciones](../../../../apps/erp/web/src/modules/asignaciones/.gitkeep).
- Pruebas: [asignaciones](../../../../apps/erp/api/src/test/java/pe/boston/erp/asignaciones/.gitkeep).

## Dependencias permitidas

Contratos públicos aprobados de Personas y Oferta académica. La implementación actual declara cero dependencias entre módulos. La infraestructura técnica compartida reside en plataforma y no contiene reglas de negocio.

## Referencias

- [Especificación](ESPECIFICACION.md)
- [Estado](ESTADO.md)
- [Políticas transversales](../../POLITICAS_TRANSVERSALES.md)
