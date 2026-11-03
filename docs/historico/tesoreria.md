# Tesorería

ERP conserva conceptos, obligaciones, pagos, aplicaciones y auditoría. Un pago administrativo exige cuenta con rol `ADMINISTRADOR` y permiso `tesoreria=true`, referencia única, importe positivo con dos decimales y saldo suficiente bajo transacción. Las cuentas administrativas nuevas carecen del permiso; `admin.demo` lo recibe expresamente para la prueba local. La anulación requiere motivo y recalcula el estado de cada obligación. Aula consulta ERP en cada visita a Mis pagos y responde 503 si está inaccesible. La UI comunica que pago en línea no está habilitado. No se simula autorización bancaria.

