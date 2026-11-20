-- Ejecutar con psql como administrador; pide la contraseña de la nueva cuenta.
-- No repetir si estas bases o este rol ya existen. No elimina datos existentes.
CREATE ROLE facturacion_app LOGIN;
\password facturacion_app
CREATE DATABASE facturacion OWNER facturacion_app ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE facturacion_test OWNER facturacion_app ENCODING 'UTF8' TEMPLATE template0;
