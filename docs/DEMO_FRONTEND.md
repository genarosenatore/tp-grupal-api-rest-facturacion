# Demostración del frontend TypeScript

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


## 1. Preparación en Windows

Requisitos: PostgreSQL instalado, Java 26 y Maven ya usados por el proyecto, Node.js
22.12 o posterior con npm y Microsoft Edge para las pruebas automatizadas.
No modifique las migraciones aplicadas ni elimine las bases existentes.

Abra PowerShell en `C:\Proyectos\backend-facturacion`:

```powershell
.\scripts\postgres-local.ps1 iniciar
.\scripts\postgres-local.ps1 estado
.\scripts\ejecutar.ps1 -DesdeFuentes
```

Deje Java en esa terminal. En otra:

```powershell
cd C:\Proyectos\backend-facturacion\frontend
npm.cmd ci
npm.cmd run dev
```

Abra http://127.0.0.1:5173. Debe decir API conectada y mostrar catálogos reales.
Los registros Demo vienen de la migración existente V2, nunca de arrays de facturas en TS.
Si necesita otro destino, copie .env.example a .env.local, cambie API_PROXY_TARGET
y reinicie Vite. No son necesarias modificaciones CORS con el proxy.

## 2. Consulta y búsqueda

1. En Búsqueda y filtros, seleccione cliente, estado, desde y hasta.
2. Pulse Buscar. En DevTools > Network revise el GET con los cuatro parámetros reales.
3. Pruebe fechas sin resultados y después Limpiar filtros.
4. El número mostrado es un ID técnico, no un comprobante fiscal.

## 3. Crear con varios artículos

1. Seleccione Ana Martínez, punto 0001, Consumidor final y ARS desde los catálogos.
2. Agregue Cuaderno, cantidad 2. Agregue otro renglón: Lápiz, cantidad 3.
3. Agregue un tercero y quítelo para mostrar la administración dinámica.
4. Escriba una observación reconocible, por ejemplo `Demostración profesor 23/10`.
5. La vista previa debe mostrar neto 2600,00, IVA 483,00 y total ARS 3083,00,
   siempre que conserve los precios y alícuotas de V2.
6. Pulse Crear factura. Network debe mostrar POST /api/facturas, 201 y JSON del reporte.
7. La tabla se recarga y el mensaje muestra el ID y total que confirmó Java.

Si hay filtros activos, una factura nueva puede no pertenecer al resultado: limpie filtros.
No envíe importes ni estado al crear. No hace falta ingresar IDs manualmente.

## 4. Demostrar persistencia en PostgreSQL

Anote el ID obtenido. En otra terminal desde la raíz:

```powershell
. .\scripts\herramientas.ps1
Import-Conexion
$pgBinDemo = Get-PostgresBin
$env:PGPASSWORD = $env:DB_PASSWORD
# Con la conexión local del proyecto (puerto guardado en PGPORT):
& "$pgBinDemo/psql.exe" -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT id, fecha, estado, neto, iva, total, observaciones FROM factura_venta ORDER BY id DESC LIMIT 5;"
& "$pgBinDemo/psql.exe" -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT factura_id, descripcion, cantidad, precio_unitario, neto, iva, total FROM factura_detalle ORDER BY id DESC LIMIT 10;"
Remove-Item Env:\PGPASSWORD
```

Si usa su servidor en 5432, adapte host/puerto/base a DB_URL. No imprima ni comparta la
contraseña. No consulte solamente el estado del navegador: cierre y vuelva a abrir la
página o consulte GET /api/facturas/{id} y compare los renglones SQL.

## 5. Edición, estado y anulación

1. Abra Ver detalle y muestre cabecera, importes y los dos renglones.
2. Pulse Editar factura. Cambie una cantidad y guarde. PUT completo devuelve 200
   y reemplaza todos los renglones con precios actuales.
3. Abra Cambiar estado / observaciones, deje Emitida y cambie el texto.
   En Network observe que PUT envía sólo estado y observaciones.
4. Pulse Anular factura, cancele para demostrar confirmación y vuelva a anular confirmando.
5. DELETE devuelve 204. La interfaz muestra ANULADA y fecha de anulación.
6. Consulte SQL otra vez: la cabecera y sus detalles siguen existiendo.
7. Filtre por ANULADA y compruebe que ya no se ofrecen edición ni reactivación.

Para mostrar anulación con motivo use Cambiar estado, elija Anulada y complete las
observaciones antes de confirmar. Hágalo sobre otra factura de prueba emitida.

## 6. Casos de error y móvil

- Pruebe un artículo sin precio: se muestra una advertencia y no permite guardar.
- Pruebe cantidad cero y más de tres decimales; el control muestra el error.
- Detenga Java, pulse Buscar y observe el mensaje de conexión; el borrador se conserva.
- Reinicie Java y pulse Actualizar datos.
- En DevTools pruebe 390 x 844: los formularios se apilan y las tablas tienen desplazamiento
  horizontal interno sin desbordar toda la página.

## 7. Verificaciones automatizadas

```powershell
# Desde frontend; Spring Boot y PostgreSQL deben estar activos para e2e:
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
# Desde la raíz; usa facturacion_test, nunca facturacion para limpiar pruebas:
.\scripts\probar.ps1
```

Las pruebas de contrato simulan fetch para cubrir errores difíciles de provocar.
Las del navegador usan API real para CRUD y rutas interceptadas sólo en el caso específico
de errores. Crean datos identificados con `Verificación navegador TS` y los dejan anulados.
Capturas: `docs/evidencias-frontend/`. Resultados: `docs/VERIFICACION_TYPESCRIPT.md`.

Detenga Vite y Java con Ctrl+C. Finalmente: `.\scripts\postgres-local.ps1 detener`.
