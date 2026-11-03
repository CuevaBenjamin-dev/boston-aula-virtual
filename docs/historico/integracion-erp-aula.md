# Integración ERP → Aula

Cada mutación académica del ERP escribe datos y un snapshot `OFERTA_ACTUALIZADA` versión 1 en una misma transacción PostgreSQL. El snapshot contiene curso, sección, asignatura, periodo, estudiantes activos y docentes activos. Aula sondea el outbox con credencial OAuth `client_credentials`, aplica eventos en orden y registra la secuencia. Un evento repetido se omite por `eventos_procesados.secuencia_erp` único.

Si ERP no responde, Aula conserva su proyección académica y guarda `ultimo_error`; el próximo sondeo reintenta. La tesorería se consulta en vivo, por lo que muestra error si ERP falla. La reconciliación manual actual consiste en publicar de nuevo los snapshots de todas las ofertas desde el servicio ERP; falta exponer una operación controlada para ello.

