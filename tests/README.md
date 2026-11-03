# Pruebas transversales

`tests/legacy` conserva los scripts originales de integración y carga. Algunos preparan, restablecen o alteran datos de prueba: revisarlos y ejecutarlos únicamente sobre una copia aislada. Las pruebas unitarias y de arquitectura de cada backend están junto al proyecto correspondiente en `src/test`.

Evidencia verificada de esta reestructuración: restauración de tres dumps PostgreSQL en contenedor temporal, `mvn test` en los tres backends, migraciones ERP V1–V11 en PostgreSQL 18.6 mediante Testcontainers, build de ERP Web, `docker compose config --quiet`, arranque local y comprobaciones HTTP/health. La batería de scripts legados y un flujo completo autenticado ERP–Aula no se ejecutaron; véase [informe](../docs/erp/ENTREGA_PARA_CHATGPT.md).
