# Documentos

## Objetivo

Gestionar documentos institucionales del ERP.

## Responsabilidades

Metadatos, custodia y acceso documental según requisitos por aprobar. Las operaciones concretas están pendientes de especificación institucional.

## Límites

No modifica reglas de matrícula, personas ni oferta.

## Código

- Backend: [documentos](../../../../apps/erp/api/src/main/java/pe/boston/erp/documentos/package-info.java), con carpetas api, application, domain e infrastructure.
- Frontend: [documentos](../../../../apps/erp/web/src/modules/documentos/.gitkeep).
- Pruebas: [documentos](../../../../apps/erp/api/src/test/java/pe/boston/erp/documentos/.gitkeep).

## Dependencias permitidas

Contratos públicos aprobados de los módulos propietarios. La implementación actual declara cero dependencias entre módulos. La infraestructura técnica compartida reside en plataforma y no contiene reglas de negocio.

## Referencias

- [Especificación](ESPECIFICACION.md)
- [Estado](ESTADO.md)
- [Políticas transversales](../../POLITICAS_TRANSVERSALES.md)
