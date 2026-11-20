ALTER TABLE factura_venta ADD COLUMN tipo_documento_receptor integer;
ALTER TABLE factura_venta ADD COLUMN condicion_iva_receptor_id integer;
ALTER TABLE factura_venta ADD COLUMN ambiente_arca varchar(30);
CREATE TABLE serie_fiscal (
 id varchar(100) PRIMARY KEY, operacion_activa uuid
);
CREATE TABLE reserva_fiscal (
 id uuid PRIMARY KEY, serie_id varchar(100) NOT NULL REFERENCES serie_fiscal(id),
 numero bigint NOT NULL, punto_venta integer NOT NULL, tipo integer NOT NULL,
 factura_id bigint NOT NULL, compensacion boolean NOT NULL,
 fecha date NOT NULL,
 estado varchar(20) NOT NULL, respuesta text
);
CREATE INDEX idx_reserva_serie ON reserva_fiscal(serie_id, numero);
