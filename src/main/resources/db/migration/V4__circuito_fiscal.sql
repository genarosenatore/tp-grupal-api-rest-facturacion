-- Los IDs existentes siguen siendo técnicos. Nunca se inventa numeración/CAE.
ALTER TABLE factura_venta ADD COLUMN numero bigint CHECK (numero > 0);
ALTER TABLE factura_venta ADD COLUMN tipo_comprobante varchar(40);
ALTER TABLE factura_venta ADD COLUMN estado_arca varchar(20) NOT NULL DEFAULT 'PENDIENTE'
 CHECK (estado_arca IN ('PENDIENTE','EN_PROCESO','RECHAZADA','INCIERTA','AUTORIZADA'));
ALTER TABLE factura_venta ADD COLUMN tipo_autorizacion varchar(4);
ALTER TABLE factura_venta ADD COLUMN codigo_autorizacion varchar(30);
ALTER TABLE factura_venta ADD COLUMN vencimiento_autorizacion date;
ALTER TABLE factura_venta ADD COLUMN fecha_autorizacion timestamptz;
ALTER TABLE factura_venta ADD COLUMN motivo_anulacion varchar(1000);
ALTER TABLE factura_venta ADD CONSTRAINT autorizacion_completa CHECK
 (estado_arca <> 'AUTORIZADA' OR (numero IS NOT NULL AND tipo_comprobante IS NOT NULL
 AND tipo_autorizacion IS NOT NULL AND codigo_autorizacion IS NOT NULL
 AND vencimiento_autorizacion IS NOT NULL AND fecha_autorizacion IS NOT NULL));
CREATE UNIQUE INDEX idx_factura_numero_fiscal ON factura_venta(punto_venta_id, tipo_comprobante, numero)
 WHERE numero IS NOT NULL AND tipo_comprobante IS NOT NULL;
CREATE INDEX idx_factura_arca ON factura_venta(estado_arca, id DESC);
CREATE TABLE operacion_fiscal (
 id uuid PRIMARY KEY, factura_id bigint REFERENCES factura_venta(id),
 factura_original_id bigint NOT NULL,
 operacion varchar(20) NOT NULL, estado varchar(20) NOT NULL,
 creada timestamptz NOT NULL, finalizada timestamptz,
 motivo varchar(1000), mensaje varchar(2000), respuesta text,
 tipo_comprobante varchar(40), numero bigint, tipo_autorizacion varchar(4),
 codigo_autorizacion varchar(30), vencimiento_autorizacion date
);
CREATE INDEX idx_operacion_factura ON operacion_fiscal(factura_id, creada);
