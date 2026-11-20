ALTER TABLE cliente ADD COLUMN tipo_documento integer;
ALTER TABLE cliente ADD COLUMN condicion_iva_receptor_id integer;
ALTER TABLE cliente ADD COLUMN domicilio varchar(250);
-- Los datos fiscales desconocidos se completan desde la ficha; no se adivinan por el documento.
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_tipo_documento CHECK (tipo_documento IN (80,96,99));
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_condicion CHECK (condicion_iva_receptor_id IN (1,4,5,6));

ALTER TABLE factura_venta ADD COLUMN cliente_nombre varchar(120);
ALTER TABLE factura_venta ADD COLUMN cliente_documento varchar(30);
UPDATE factura_venta SET cliente_nombre=(SELECT c.nombre FROM cliente c WHERE c.id=cliente_id),
 cliente_documento=(SELECT c.documento FROM cliente c WHERE c.id=cliente_id);
ALTER TABLE factura_venta ADD COLUMN tipo_descuento varchar(20) NOT NULL DEFAULT 'SIN_DESCUENTO';
ALTER TABLE factura_venta ADD COLUMN valor_descuento numeric(19,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_venta ADD COLUMN importe_descuento numeric(19,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_venta ADD CONSTRAINT ck_factura_descuento CHECK
 (valor_descuento>=0 AND importe_descuento>=0 AND (
 (tipo_descuento='SIN_DESCUENTO' AND valor_descuento=0 AND importe_descuento=0) OR
 (tipo_descuento='PORCENTAJE' AND valor_descuento<=100) OR tipo_descuento='MONTO_FIJO'));
ALTER TABLE factura_detalle ADD COLUMN descuento numeric(19,2) NOT NULL DEFAULT 0 CHECK (descuento>=0);
