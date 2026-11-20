-- Copia por comprobante; no modifica retrospectivamente facturas autorizadas.
ALTER TABLE factura_venta ADD COLUMN cliente_domicilio VARCHAR(250);
ALTER TABLE factura_venta ADD COLUMN condicion_venta VARCHAR(80);
