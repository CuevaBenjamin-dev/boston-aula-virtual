# Personas

## Objetivo

Identificar y mantener personas institucionales.

## Responsabilidades

Registro central de personas físicas, condiciones institucionales y vínculos entre personas. Los objetivos de nueve requisitos funcionales y siete casos de uso están aprobados; sus flujos, reglas detalladas y criterios de aceptación siguen en análisis.

## Límites

No decide matrícula, oferta, asignación docente ni documentos.

## Código

- Backend: [personas](../../../../apps/erp/api/src/main/java/pe/boston/erp/personas/package-info.java), con carpetas api, application, domain e infrastructure.
- Frontend: [personas](../../../../apps/erp/web/src/modules/personas/.gitkeep).
- Pruebas: [personas](../../../../apps/erp/api/src/test/java/pe/boston/erp/personas/.gitkeep).

## Dependencias permitidas

Identidad solo mediante contratos técnicos aprobados. La implementación actual declara cero dependencias entre módulos. La infraestructura técnica compartida reside en plataforma y no contiene reglas de negocio.

## Referencias

- [Especificación](ESPECIFICACION.md)
- [Estado](ESTADO.md)
- [Políticas transversales](../../POLITICAS_TRANSVERSALES.md)
