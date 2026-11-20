SELECT * FROM cliente;
SELECT * FROM condicion_iva;
SELECT * FROM tipo_moneda;
SELECT * FROM punto_venta;
SELECT a.id, a.descripcion, a.alicuota_iva, m.codigo AS moneda, p.precio
FROM articulo a LEFT JOIN lista_precio_articulo p ON p.articulo_id=a.id
LEFT JOIN tipo_moneda m ON m.id=p.tipo_moneda_id ORDER BY a.id,m.id;
SELECT id,fecha,cliente_id,estado,neto,iva,total,fecha_anulacion FROM factura_venta ORDER BY id;
SELECT f.id, count(d.id) AS renglones, f.total, sum(d.total) AS suma_detalles
FROM factura_venta f JOIN factura_detalle d ON d.factura_id=f.id
GROUP BY f.id ORDER BY f.id;
SELECT * FROM factura_detalle ORDER BY factura_id,id;
