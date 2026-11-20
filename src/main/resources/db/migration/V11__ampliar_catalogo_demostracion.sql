-- Productos e importes ficticios para practicar. No reemplaza artículos ni precios existentes.
INSERT INTO articulo(codigo,descripcion,alicuota_iva)
SELECT d.codigo,d.descripcion,d.iva FROM (VALUES
 ('DEMO-004','Resma A4 80 g',21.00),('DEMO-005','Bolígrafo azul',21.00),
 ('DEMO-006','Carpeta A4 con elástico',21.00),('DEMO-007','Marcador permanente',21.00),
 ('DEMO-008','Regla de 30 cm',21.00),('DEMO-009','Goma de borrar',21.00),
 ('DEMO-010','Lápices de colores x 12',21.00),('DEMO-011','Adhesivo en barra',21.00)
) AS d(codigo,descripcion,iva) WHERE NOT EXISTS (SELECT 1 FROM articulo a WHERE a.codigo=d.codigo);

INSERT INTO lista_precio_articulo(articulo_id,tipo_moneda_id,precio,lista_precio_id)
SELECT a.id,m.id,CASE WHEN l.id=1 THEN d.minorista ELSE d.mayorista END,l.id
FROM (VALUES
 ('DEMO-004',6500.00,5900.00),('DEMO-005',800.00,650.00),('DEMO-006',2200.00,1900.00),
 ('DEMO-007',1500.00,1250.00),('DEMO-008',950.00,800.00),('DEMO-009',500.00,400.00),
 ('DEMO-010',4200.00,3600.00),('DEMO-011',1800.00,1500.00)
) AS d(codigo,minorista,mayorista)
JOIN articulo a ON a.codigo=d.codigo CROSS JOIN tipo_moneda m CROSS JOIN lista_precio l
WHERE m.codigo='ARS' AND l.id IN (1,2)
AND NOT EXISTS (SELECT 1 FROM lista_precio_articulo p WHERE p.articulo_id=a.id AND p.tipo_moneda_id=m.id AND p.lista_precio_id=l.id);
