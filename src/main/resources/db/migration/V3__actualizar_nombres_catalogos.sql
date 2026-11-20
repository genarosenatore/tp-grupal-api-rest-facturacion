-- Actualiza las etiquetas iniciales conservando IDs, relaciones e importes.
-- V2 se conserva para mantener el historial y los checksums de Flyway.
UPDATE cliente SET nombre = 'Ana Martínez', documento = '30123456'
WHERE nombre = 'Ana Demo' AND documento = 'DEMO-001';
UPDATE cliente SET nombre = 'Bruno Fernández', documento = '32987654'
WHERE nombre = 'Bruno Demo' AND documento = 'DEMO-002';

UPDATE punto_venta SET descripcion = 'Casa central'
WHERE codigo = '0001' AND descripcion = 'Punto de venta de prueba';

UPDATE condicion_iva SET descripcion = 'Consumidor final'
WHERE descripcion = 'Condición de prueba A';
UPDATE condicion_iva SET descripcion = 'IVA responsable inscripto'
WHERE descripcion = 'Condición de prueba B';

UPDATE tipo_moneda SET descripcion = 'Peso argentino'
WHERE codigo = 'ARS' AND descripcion = 'Pesos de prueba';
UPDATE tipo_moneda SET descripcion = 'Dólar estadounidense'
WHERE codigo = 'USD' AND descripcion = 'Dólares de prueba';

UPDATE articulo SET descripcion = 'Cuaderno A4 rayado de 80 hojas'
WHERE codigo = 'ART-001' AND descripcion = 'Cuaderno de prueba';
UPDATE articulo SET descripcion = 'Lápiz negro HB'
WHERE codigo = 'ART-002' AND descripcion = 'Lápiz de prueba';
UPDATE articulo SET descripcion = 'Carpeta A4 con elástico'
WHERE codigo = 'ART-003' AND descripcion = 'Artículo sin precio para probar conflictos';

-- Los detalles guardan una copia de la descripción del artículo.
UPDATE factura_detalle SET descripcion = 'Cuaderno A4 rayado de 80 hojas'
WHERE descripcion = 'Cuaderno de prueba';
UPDATE factura_detalle SET descripcion = 'Lápiz negro HB'
WHERE descripcion = 'Lápiz de prueba';
UPDATE factura_detalle SET descripcion = 'Carpeta A4 con elástico'
WHERE descripcion = 'Artículo sin precio para probar conflictos';

UPDATE factura_venta SET observaciones = 'Venta de artículos de librería'
WHERE observaciones = 'Factura para la demostración académica';
UPDATE factura_venta SET observaciones = 'Anulada por un error en la carga'
WHERE observaciones = 'Anulación de prueba';
