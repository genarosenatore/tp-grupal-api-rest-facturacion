# Verificación del frontend

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Fecha: 23 de octubre de 2026 (America/Buenos_Aires).

## Pruebas automáticas

- `scripts/probar.ps1`: BUILD SUCCESS, 19 pruebas Java, sin fallos, errores ni omitidas.
  Incluye 4 pruebas de cálculo y 15 de integración con PostgreSQL real.
- La integración nueva verifica HTTP 200 para `/`, `/styles.css`, `/app.js`,
  `/importes.mjs` y `/api/catalogos`, el contenido del HTML y los catálogos DTO.
- `node --test src/test/frontend/importes.test.mjs`: 4 pruebas aprobadas.
  Comprueba la factura de referencia, redondeo HALF_UP por renglón, cantidades
  fraccionarias, importes grandes y rechazo de decimales inválidos.
- `node --check src/main/resources/static/app.js`: sin errores de sintaxis.

Las pruebas Java de integración se ejecutaron únicamente en `facturacion_test`,
esquema `tp_pruebas`. El aislamiento inicial bloqueó HTTP local; la ejecución
completa se realizó con la conexión local autorizada y PostgreSQL activo.

## Comprobación en el navegador

Interfaz servida por Spring Boot en `http://127.0.0.1:8080`, conectada a los datos
de demostración existentes, sin crear, modificar ni anular sus facturas.

- Carga de las cuatro facturas existentes y resumen correcto: una emitida,
  tres anuladas y ARS 3.083,00 vigentes.
- Búsqueda por Ana: una factura y resumen correspondiente. Limpiar restaura las cuatro.
- Detalle de factura #000003 con artículos e importes históricos.
- Apertura de la confirmación de anulación y regreso sin anular.
- Catálogos de clientes y artículos con documentos, alícuotas, precios ARS y USD.
- Formulario dinámico con dos artículos: neto ARS 2.600,00, IVA ARS 483,00,
  total ARS 3.083,00. Cambio a USD: neto 5,50, IVA 1,00 y total 6,50.
- Artículo sin precio: aviso y bloqueo del envío mediante validación del selector.
- Confirmación propia para descartar cambios; regreso a la lista sin guardar.
- Sin errores de consola en la comprobación final.

Captura: `docs/frontend.png`. Se inspeccionaron las disposiciones de escritorio
y ancho intermedio. Las reglas CSS incluyen móvil; no se ejecutó una emulación móvil.
Las escrituras POST, PUT y DELETE se verificaron en la suite HTTP de integración;
la comprobación visual no envió escrituras sobre la base de demostración.

## Ejecución

El frontend está en `src/main/resources/static/` y se incluye en el JAR Spring Boot.
No necesita instalar npm ni descargar recursos externos. Para iniciar desde fuentes:

```powershell
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1 -DesdeFuentes
```

Abrir `http://127.0.0.1:8080`. La vista previa usa los catálogos vigentes y el
servidor sigue siendo la autoridad de validación y cálculo al guardar.
