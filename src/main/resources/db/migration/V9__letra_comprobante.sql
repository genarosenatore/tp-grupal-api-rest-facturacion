-- La letra es un dato explícito de cada factura. No cambia su tipo ni autorización.
ALTER TABLE factura_venta ADD COLUMN letra_comprobante varchar(1);
UPDATE factura_venta SET letra_comprobante = CASE
 WHEN tipo_comprobante IN ('1','3') THEN 'A'
 WHEN tipo_comprobante IN ('6','8') THEN 'B'
 WHEN tipo_comprobante IN ('11','13') THEN 'C'
 ELSE 'X' END;
ALTER TABLE factura_venta ALTER COLUMN letra_comprobante SET DEFAULT 'X';
ALTER TABLE factura_venta ALTER COLUMN letra_comprobante SET NOT NULL;
ALTER TABLE factura_venta ADD CONSTRAINT ck_factura_letra
 CHECK (letra_comprobante IN ('A','B','C','X'));
ALTER TABLE factura_venta ADD CONSTRAINT ck_factura_letra_tipo CHECK
 (letra_comprobante = CASE
   WHEN tipo_comprobante IN ('1','3') THEN 'A'
   WHEN tipo_comprobante IN ('6','8') THEN 'B'
   WHEN tipo_comprobante IN ('11','13') THEN 'C'
   ELSE 'X' END);
