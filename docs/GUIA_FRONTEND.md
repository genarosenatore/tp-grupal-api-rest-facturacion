# Guía del frontend de facturación

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Esta guía acompaña al frontend de `frontend/`. El servidor Java, sus reglas, PostgreSQL,
las migraciones y el manual anterior permanecen intactos. El material ampliado se encuentra
en `GUIA_TEORICO_PRACTICA_FRONTEND.md` y su PDF.

## Cómo se conectan las piezas

```text
index.html (formulario)
    -> app.ts (evento submit y lectura)
    -> invoice-form.ts (detalles y FacturaRequestDTO)
    -> FacturaService.ts -> http.ts -> fetch
    -> FacturaRestController -> FacturaService Java
    -> repositorios / Hibernate -> PostgreSQL
    <- FacturaReporteDTO JSON <- respuesta HTTP
    -> app.ts -> tabla y mensajes del DOM
```

HTML describe la estructura; CSS define presentación y adaptación a móviles; TypeScript
controla los eventos y las llamadas HTTP. Vite transforma TypeScript a JavaScript para
el navegador. JavaScript es lo que se ejecuta; los tipos de TypeScript se comprueban al
compilar y desaparecen del código final. Por eso también verificamos el JSON al recibirlo.

## Archivos y responsabilidades

| Archivo en frontend/ | Responsabilidad |
|---|---|
| index.html | Tres secciones, formularios, tablas y diálogos accesibles |
| src/main.ts | Importa CSS y llama a iniciar() |
| src/app.ts | Conecta eventos, filtros, listado, CRUD y confirmaciones |
| src/invoice-form.ts | Catálogos, array de renglones, edición y totales preliminares |
| src/models/factura.dto.ts | Interfaces de facturas, detalles, filtros y estados |
| src/models/catalogos.dto.ts | Contrato de GET /api/catalogos |
| src/services/FacturaService.ts | Métodos HTTP de facturas y validación del reporte |
| src/services/CatalogoService.ts | Carga y comprobación de catálogos |
| src/services/http.ts | Único fetch, JSON, timeout y ApiError |
| src/utils/dom.ts | Selectores tipados y opciones con texto seguro |
| src/utils/validators.ts | Fechas, IDs, cantidades y límites Java |
| src/utils/importes.ts | Vista previa decimal con BigInt y HALF_UP |
| src/utils/formatters.ts | Fechas, moneda y escape de texto |
| src/styles/style.css | Diseño azul y gris adaptable |
| vite.config.ts | Proxy /api y puerto 5173 |
| tests/contrato.test.ts | Contrato HTTP simulado, errores y validaciones |
| tests/browser/crud.spec.ts | Navegador real con API y persistencia reales |

## TypeScript e interfaces

Una interfaz describe la forma que debe tener un objeto. No es una clase Java ni crea
una instancia en ejecución. Este fragmento real de `factura.dto.ts` exige ID y cantidad:

```typescript
export interface FacturaDetalleRequestDTO {
  articuloId: number;
  cantidad: number;
}
```

El detalle de creación no contiene precio, IVA, total ni objetos de entidades. Java los
resuelve con los IDs de artículo y moneda. El DTO de reporte sí contiene importes y datos
históricos. `fecha` es YYYY-MM-DD y `fechaAnulacion` es un Instant ISO o null.
BigDecimal mantiene precisión en Java; en JSON llega como número y TypeScript usa number.
La precisión de number tiene límites: la vista previa no reemplaza cálculos de Java.

## DOM y eventos

El DOM es el árbol de elementos HTML accesible desde JavaScript. `el('#invoice-form')`
obtiene el formulario; `.textContent` cambia texto; `.replaceChildren()` reemplaza nodos.
Un evento comunica una interacción: click, input, change o submit. En `app.ts`:

```typescript
el<HTMLFormElement>('#invoice-form').addEventListener('submit', event => {
  void saveInvoice(event);
});
```

`saveInvoice` usa `preventDefault()` para evitar el envío HTML que recargaría la página.
Lee los valores, valida y espera al servicio. No hay atributos onclick. Los textos de API
se insertan con textContent, Option o con la función escape al construir plantillas HTML.

## Fetch, Promise, async y await

`fetch()` inicia una solicitud HTTP y devuelve una Promise: un resultado futuro que puede
resolverse o rechazarse. Una función async también devuelve una Promise. await espera
su resultado dentro de esa función sin bloquear la interacción del navegador.

Fragmento real de `FacturaService.ts`:

```typescript
async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
  return reporte(await http('/facturas', 'POST', factura));
}
```

`http.ts` construye la URL centralizada, serializa con JSON.stringify, agrega
Content-Type: application/json y llama fetch. Spring deserializa el cuerpo al record Java.
Luego el controlador llama al servicio transaccional. La respuesta se decodifica con
response.json() y se verifica antes de tiparla como reporte. Un HTTP 400 no rechaza
automáticamente la Promise de fetch: debemos consultar response.ok.
Un DELETE exitoso responde 204 y no se llama a response.json() porque no tiene cuerpo.

## Detalles dinámicos

`InvoiceForm` mantiene `private lines: FacturaDetalleRequestDTO[]`. Agregar hace push;
quitar usa splice; input y change sincronizan el array con los controles. `renderLines()`
crea selects e inputs y `preview()` consulta precios de catálogos según artículo y moneda.
No hay IDs fijos: las opciones provienen de `/api/catalogos`.

`payload()` genera una copia de los renglones, valida cabecera, fecha y entre 1 y 100 ítems.
Las cantidades van de 0.001 a 999999.999 con tres decimales como máximo. Los precios,
las alícuotas y los importes no se envían. El backend confirma importes al responder.
La vista previa usa enteros BigInt escalados y redondeo HALF_UP, como el cálculo Java.

## Contratos y estados

| Operación | Solicitud |
|---|---|
| Buscar | GET /api/facturas?clienteId=...&estado=...&desde=...&hasta=... |
| Consultar detalles | GET /api/facturas/{id} |
| Crear | POST /api/facturas + cabecera y detalles |
| Editar | PUT /api/facturas/{id} + cabecera y TODOS los detalles |
| Estado/observaciones | PUT /api/facturas/{id} + estado y observaciones |
| Anular | DELETE /api/facturas/{id}, respuesta 204 |

No existe filtro por número en el servidor; no se inventó un parámetro. El ID mostrado
no es una numeración fiscal. EMITIDA y ANULADA son los únicos estados. Cambiar a EMITIDA
permite cambiar observaciones de una emitida; no reactiva anuladas. Cambiar a ANULADA
exige confirmación, igual que Anular. El primer caso usa PUT y puede conservar un motivo;
Anular usa DELETE. Se encuentran en flujos distintos y no se presentan como dos botones
de borrado equivalentes. Una anulada sólo se consulta.

PUT completo reemplaza todos los renglones y recalcula usando precios vigentes: los IDs
de los detalles pueden cambiar. PUT de estado no los reemplaza. No se modificó Java.

## Carga, errores y concurrencia

Las escrituras bloquean los botones, y los formularios conservan sus datos si falla la API.
Las consultas numeran las solicitudes para no aplicar una respuesta vieja por encima
de una nueva. No se mezclan totales de distintas monedas en un resumen agregado.
400/404/409 muestran ErrorDTO.mensaje y errores; 500 muestra un mensaje general.
Conexión caída o timeout muestra una indicación para revisar los servidores.
La validación visual no reemplaza la del backend.

## Ejecutar, verificar y defender

Ver `DEMO_FRONTEND.md` para las tres terminales Windows y el flujo completo.
Desde `frontend/`: `npm.cmd ci`, `npm.cmd test`, `npm.cmd run build` y
`npm.cmd run test:e2e`. Esta última requiere Edge y servidores, y escribe datos de prueba.
Vea resultados y limitaciones efectivamente observadas en `VERIFICACION_TYPESCRIPT.md`.

El frontend previo sigue en `src/main/resources/static` y abre por el puerto 8080.
La entrega TypeScript se abre por el 5173. El JAR previo no incorpora automáticamente
el nuevo build. Para servirlo en un despliegue, publique frontend/dist y configure /api.
