-- Datos ficticios; los porcentajes no constituyen reglas fiscales.
INSERT INTO cliente(nombre,documento) VALUES ('Ana Demo','DEMO-001'),('Bruno Demo','DEMO-002');
INSERT INTO punto_venta(codigo,descripcion) VALUES ('0001','Punto de venta de prueba');
INSERT INTO condicion_iva(descripcion) VALUES ('Condición de prueba A'),('Condición de prueba B');
INSERT INTO tipo_moneda(codigo,descripcion) VALUES ('ARS','Pesos de prueba'),('USD','Dólares de prueba');
INSERT INTO articulo(codigo,descripcion,alicuota_iva) VALUES
 ('ART-001','Cuaderno de prueba',21.00),
 ('ART-002','Lápiz de prueba',10.50),
 ('ART-003','Artículo sin precio para probar conflictos',0.00);
INSERT INTO lista_precio_articulo(articulo_id,tipo_moneda_id,precio) VALUES
 (1,1,1000.00),(2,1,200.00),(1,2,2.00),(2,2,0.50);
