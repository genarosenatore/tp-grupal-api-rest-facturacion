INSERT INTO lista_precio(id,nombre) VALUES (1,'Minorista'),(2,'Mayorista');
ALTER TABLE lista_precio ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM lista_precio);
INSERT INTO cliente(id,nombre,documento,tipo_documento,condicion_iva_receptor_id,domicilio) VALUES
 (1,'Ana Martínez','12345678',96,5,'Calle de Prueba 456'),(2,'Bruno Fernández','23456789',96,5,'Av. del Ejemplo 789');
ALTER TABLE cliente ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM cliente);
INSERT INTO condicion_iva(id,descripcion) VALUES (1,'Consumidor final');
INSERT INTO punto_venta(id,codigo,descripcion) VALUES (1,'0001','Pruebas locales - sin envio fiscal');
INSERT INTO tipo_moneda(id,codigo,descripcion) VALUES (1,'ARS','Pesos'),(2,'USD','Dólares');
INSERT INTO articulo(id,codigo,descripcion,alicuota_iva) VALUES (1,'A1','Cuaderno A4',21),(2,'A2','Lápiz',10.5),(3,'A3','Sin precio',0);
INSERT INTO lista_precio_articulo(id,articulo_id,tipo_moneda_id,precio) VALUES (1,1,1,1000),(2,2,1,200),(3,1,2,2),(4,2,2,0.5);
-- H2 no adelanta automáticamente las identidades al cargar IDs explícitos.
-- En el laboratorio persistente se toma MAX para conservar productos ya creados.
ALTER TABLE articulo ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM articulo);
ALTER TABLE lista_precio_articulo ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM lista_precio_articulo);
