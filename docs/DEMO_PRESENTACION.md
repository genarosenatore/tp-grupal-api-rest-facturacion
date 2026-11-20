# Demostración de 5 a 10 minutos

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Abra IntelliJ con pom.xml y SDK Java 26, dos terminales PowerShell y pgAdmin o psql.
Los comandos siguientes se ejecutan desde la raíz del proyecto.
Use la opción local preparada o la conexión al servidor existente descripta en README;
no necesita instalar otro PostgreSQL.

## Terminal 1: iniciar

~~~
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1
~~~

Espere "Started FacturacionApplication". Mantenga la terminal abierta.
Si utiliza el servidor existente y configuró su conexión, omita postgres-local.ps1.

## Terminal 2: verificar PostgreSQL e importar conexión

~~~
. .\.local\conexion.ps1
$env:PGPASSWORD = $env:DB_PASSWORD
pg_isready -h 127.0.0.1 -p $env:PGPORT
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c '\dt'
~~~

Se esperan las ocho tablas de dominio y la tabla de historial de Flyway.
En pgAdmin registre otro servidor apuntando al puerto que indica PGPORT,
usuario facturacion_app y contraseña de la configuración local; no muestre esa contraseña.

## 1. Mostrar los catálogos

~~~
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c 'SELECT * FROM articulo;'
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c 'SELECT * FROM lista_precio_articulo;'
~~~

Explique que los IDs 1 y 2 ya están cargados y que los precios los obtiene el servidor.

## 2. POST: crear

~~~
$api = 'http://127.0.0.1:8080/api/facturas'
$json = Get-Content .\examples\crear-factura.json -Raw -Encoding UTF8
$respuesta = Invoke-WebRequest -Uri $api -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -UseBasicParsing
$respuesta.StatusCode
$factura = $respuesta.Content | ConvertFrom-Json
$idFactura = $factura.id
$factura | ConvertTo-Json -Depth 8
~~~

Se espera 201, neto 2600.00, IVA 483.00, total 3083.00 y dos detalles.
El ID se toma de la respuesta; no se supone que la factura tenga ID 1.
En la exposición muestre FacturaRestController.crear, FacturaService.crear y reemplazarDetalles.

## 3. Verificar filas y explicar cascada

~~~
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT id,estado,neto,iva,total FROM factura_venta WHERE id=$idFactura;"
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT factura_id,descripcion,cantidad,precio_unitario,neto,iva,total FROM factura_detalle WHERE factura_id=$idFactura;"
~~~

El servicio guardó un agregado: cabecera y dos detalles. CascadeType.ALL propagó persistencia.
No se crearon nuevos clientes ni artículos.

## 4. GET: consultar y filtrar

~~~
Invoke-RestMethod -Uri "$api/$idFactura" | ConvertTo-Json -Depth 8
Invoke-RestMethod -Uri ($api + '?clienteId=1&estado=EMITIDA&desde=2026-10-23&hasta=2026-10-23') | ConvertTo-Json -Depth 8
~~~

## 5. PUT: actualizar cabecera y reemplazar detalles

~~~
$cambio = Get-Content .\examples\actualizar-factura.json -Raw -Encoding UTF8
Invoke-RestMethod -Uri "$api/$idFactura" -Method Put -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cambio)) | ConvertTo-Json -Depth 8
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT * FROM factura_detalle WHERE factura_id=$idFactura;"
~~~

Se espera clienteId 2, un renglón del artículo 2 con cantidad 4:
neto 800.00, IVA 84.00, total 884.00.
Muestre orphanRemoval=true: los ítems anteriores desaparecen y el nuevo tiene otro ID.

## 6. DELETE: anular conservando historia

~~~
$baja = Invoke-WebRequest -Uri "$api/$idFactura" -Method Delete -UseBasicParsing
$baja.StatusCode
Invoke-RestMethod -Uri "$api/$idFactura" | ConvertTo-Json -Depth 8
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT id,estado,fecha_anulacion,total FROM factura_venta WHERE id=$idFactura;"
~~~

Se espera 204 sin cuerpo; GET sigue devolviendo la factura con ANULADA y fecha UTC.
Repetir DELETE vuelve a devolver 204 sin alterar la fecha. No confundir DELETE HTTP
con borrado físico SQL: aquí se decidió conservar información.

## 7. Error controlado de validación

~~~
$invalido = $json | ConvertFrom-Json
$invalido.detalles[0].cantidad = 0
$bodyInvalido = $invalido | ConvertTo-Json -Depth 8
try {
    Invoke-RestMethod -Uri $api -Method Post -ContentType 'application/json' -Body $bodyInvalido
} catch {
    [int]$_.Exception.Response.StatusCode
    $_.ErrorDetails.Message
}
~~~

Se espera 400 y detalle de la validación. Para mostrar 404:

~~~
try { Invoke-RestMethod -Uri "$api/999999999" }
catch { [int]$_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
~~~

Para mostrar 409 intente el PUT completo sobre la factura anulada.
Para probar PUT de estado, cree otra factura y envíe examples/anular-factura.json.

## 8. Pruebas y rollback real

~~~
.\scripts\probar.ps1
~~~

Las pruebas utilizan otro puerto HTTP (18080) y otra base (facturacion_test).
Muestre rollbackRealSiPostgresRechazaSegundoDetalle y su trigger: la BD rechaza cantidad 13
en el segundo ítem. El test exige que no quede ni cabecera ni detalles.
La regla "cantidad 13 falla" existe SOLO en la prueba; no en la aplicación de demostración.

## Guion

0:00-1:00 problema y alcance. 1:00-2:00 arquitectura y relaciones.
2:00-4:00 POST, cálculos y tablas. 4:00-5:30 GET y PUT.
5:30-6:30 DELETE lógico. 6:30-8:00 validación y prueba de rollback.
8:00-9:00 explicar DTO, JPA/Hibernate y @Transactional. 9:00-10:00 preguntas.

Frase de cierre: "El controlador administra HTTP; el servicio define reglas y la transacción;
los repositorios acceden a entidades; Hibernate traduce cambios a SQL; PostgreSQL garantiza
persistencia e integridad. La respuesta es un DTO y no una entidad JPA".

Al terminar: Ctrl+C en la terminal de Java, luego postgres-local.ps1 detener si usó ese clúster.
Retire PGPASSWORD de la terminal:

~~~
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
~~~
