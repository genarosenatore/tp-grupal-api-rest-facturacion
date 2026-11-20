-- Conservar IDs y referencias históricas; la instalación es de demostración.
UPDATE punto_venta SET descripcion = 'Pruebas locales - sin envio fiscal'
WHERE codigo = '0001' AND descripcion IN ('Central', 'Casa central', 'Punto de venta de prueba');
