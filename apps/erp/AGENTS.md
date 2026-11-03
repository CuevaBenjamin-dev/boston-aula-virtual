# ERP

- Módulos funcionales oficiales: `personas`, `matriculas`, `oferta`, `asignaciones` y `documentos`. `plataforma` contiene soporte transversal; `ErpV1Service`, `ErpV1Controller` e `Institucional` siguen como legado de compatibilidad y su extracción está pendiente.
- API: `api/src/main/java/pe/boston/erp`. Las clases del paquete raíz de cada módulo son contratos públicos. `api`, `application`, `domain` e `infrastructure` son internos; no importarlos desde otro módulo. Usar fachadas públicas y verificar con `ApplicationModules.verify()`.
- Web: `web/src/app`, `web/src/modules` y `web/src/shared`. El cliente HTTP está en `shared/api/client.ts`; conservar las rutas y errores visibles.
- Documentación vigente: `../../docs/erp`. Historia original: `../../docs/historico`.
- Cambio API o seguridad: `cd apps/erp/api && mvn test`. Cambio Web: `cd apps/erp/web && npm ci && npm run build`. Cambio de datos: restaurar y probar un dump aislado antes de aplicar Flyway a volúmenes existentes.
- No modificar la lógica de Aula o Identidad en tareas exclusivamente ERP sin autorización explícita. No eliminar migraciones, registros ni contratos vigentes.
