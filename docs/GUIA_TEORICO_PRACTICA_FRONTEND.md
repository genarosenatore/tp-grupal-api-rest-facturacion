# 1. Introducción al desarrollo frontend

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


El frontend es la parte del sistema que ejecuta el navegador. En este TP convierte acciones
de una persona en solicitudes a la API de facturación, y transforma sus respuestas en
formularios, tablas y mensajes comprensibles. No se conecta directamente a PostgreSQL.

El objetivo es comprender el recorrido completo: formulario HTML, evento TypeScript,
Fetch API, controlador Spring Boot, servicio transaccional, repositorios, PostgreSQL,
reporte JSON y actualización del DOM. Cada capa tiene una responsabilidad concreta.

La entrega usa HTML5, CSS3 y TypeScript con Vite vanilla. No incorpora frameworks de
componentes. El backend existente se conserva: no se cambiaron entidades, migraciones,
reglas monetarias ni controladores para implementar esta interfaz.

## Qué se tomó del TP original

Se releyeron las tres páginas de TRABAJO PRÁCTICO CRUD FACTURA VENTA.pdf. Las páginas
2 y 3 piden interfaces espejo, una clase FacturaService, async/await, Fetch API, app.ts,
filtros, alta con detalles dinámicos y listado de comprobantes. El prompt nuevo amplía
validaciones, diseño, pruebas y documentación pedagógica.

La interfaz anterior era JavaScript servido por Spring Boot. La nueva vive en frontend/ y
se abre por el puerto 5173. La anterior y su JAR se preservan. Esta distinción evita
confundir una pantalla anterior con la nueva entrega TypeScript.

## Recorrido del sistema

[[FLUJO]]

Ejercicio inicial: identifique qué archivo actúa en cada caja y explique por qué el
navegador recibe DTOs en lugar de entidades JPA.

# 2. HTML5 y estructura del documento

HTML describe el significado y organización del contenido. index.html declara lang es-AR,
UTF-8, viewport adaptable, título y tres secciones principales con encabezados:
busqueda, alta y listado. Los diálogos complementan esas áreas sin sustituirlas.

Los formularios agrupan controles; label asocia una descripción con un control anidado;
select representa datos de catálogo; table organiza renglones. Los campos required
participan de la validación nativa. Un button sin type dentro de un formulario envía ese
formulario: los botones Agregar y Quitar usan type button para evitar envíos involuntarios.

Extracto real de index.html:

~~~html
<label>Fecha *<input name="fecha" type="date" required></label>
~~~

El atributo name sirve para identificar los datos. El id permite localizar un elemento
único. class agrupa estilos o selectores. No se escribe TypeScript dentro de onclick:
los eventos se conectan con addEventListener desde los módulos.

## Accesibilidad básica

Las secciones usan aria-labelledby. Los mensajes usan role status o alert; los controles
de renglones tienen nombres accesibles. Las tablas incluyen caption oculto visualmente.
Los diálogos modales administran foco y permiten Escape cuando no hay una escritura.
El texto de los estados acompaña al color para no depender exclusivamente de la visión.

Práctica: use Tab para recorrer filtros, alta y acciones. Explique por qué un botón Quitar
debe anunciar el número de artículo, especialmente al existir varios renglones.

# 3. CSS3, jerarquía visual y diseño responsive

CSS establece presentación: tipografía, colores, espaciado, bordes, tamaños y distribución.
style.css usa superficies blancas sobre gris, azul oscuro como base, azul para acciones,
verde para éxito y rojo para anulación o error. No necesita descargar tipografías externas.

El diseño combina Grid para cabecera y totales, Flexbox para botones y filtros, y media
queries para reducir columnas. A 600 px los formularios se apilan. Las tablas conservan
sus columnas y se desplazan dentro de table-wrap, no dentro de toda la página.

Fragmento real de CSS:

~~~css
.table-wrap{overflow-x:auto;width:100%}
~~~

## Qué significa responsive

No significa reducir toda la aplicación proporcionalmente. Significa reorganizar contenido
según el espacio disponible sin perder controles ni legibilidad. En móvil el listado puede
requerir desplazamiento horizontal interno, mientras el formulario sigue ocupando el ancho.

Los estados focus-visible hacen visible la navegación con teclado. hidden oculta zonas
sin destruir sus datos. Los botones disabled indican operaciones en curso e impiden
dobles envíos. Una animación no reemplaza información clara de carga.

Ejercicio: cambie la ventana a 390 px, agregue dos artículos y verifique que el documento
no desborda. Ubique las reglas media max-width 600px en style.css.

# 4. Fundamentos de TypeScript

TypeScript amplía JavaScript con comprobación estática de tipos. Permite descubrir errores
como pasar un string donde se espera un número antes de abrir el navegador. No agrega
precisión decimal a number y no valida automáticamente lo recibido por HTTP.

El proyecto activa strict, noUnusedLocals y noUnusedParameters. El comando build ejecuta
tsc --noEmit para verificar tipos y luego vite build para generar los archivos del navegador.
El servidor de desarrollo de Vite transforma TS rápidamente; por sí solo no reemplaza
la comprobación completa de tsc.

Extracto real de app.ts:

~~~typescript
const servicio = new FacturaService();
let invoices: FacturaReporteDTO[] = [], selected: FacturaReporteDTO | null = null;
~~~

El array contiene reportes. selected puede ser un reporte o null: TypeScript obliga a
controlar esa ausencia antes de acceder a su ID. const impide reasignar una variable,
pero no vuelve inmutable el contenido de un objeto o array.

## Unknown y errores

El JSON de la red se trata inicialmente como unknown. isRecord comprueba que sea un
objeto antes de leer campos. Un catch recibe un valor desconocido y mensaje(error)
verifica instanceof Error antes de acceder a message. Esto evita recurrir a any.

Práctica: cambie temporalmente un articuloId a texto en una variable tipada y ejecute
npm.cmd run build. Restaure el código y explique el mensaje del compilador.

# 5. Interfaces y correspondencia con Java

Una interfaz describe la forma de un objeto sin crear datos en ejecución. El archivo
models/factura.dto.ts contiene los cuatro tipos mínimos del TP y tipos auxiliares que
representan contratos reales: reportes de detalles y modo de cambio de estado.

~~~typescript
export interface FacturaDetalleRequestDTO {
  articuloId: number;
  cantidad: number;
}
~~~

FacturaRequestDTO completo incluye fecha, clienteId, condicionIvaId, puntoVentaId,
tipoMonedaId, observaciones opcionales y detalles. estado se impide en ese modo mediante
estado?: never. FacturaEstadoRequestDTO contiene estado y observaciones opcionales.
Son dos formas válidas de utilizar el mismo record Java, según la operación.

FacturaReporteDTO incluye id, version, fecha, relaciones, clienteNombre, moneda, estado,
fechaAnulacion, observaciones, neto, iva, total y detalles. No se inventó numeroFactura.
El identificador técnico se formatea con referencia() como #000001.

| Java / JSON | TypeScript | Motivo |
|---|---|---|
| Long serializado como número | number | IDs positivos; evitar valores fuera del rango seguro |
| BigDecimal serializado como número | number | Espejo del JSON; Java conserva precisión decimal |
| LocalDate | string | Fecha YYYY-MM-DD, sin hora |
| Instant anulable | string o null | Fecha/hora ISO con zona |
| EstadoFactura | EMITIDA o ANULADA | Unión de literales reales |
| Lista de detalles | FacturaDetalleReporteDTO[] | Colección tipada |

Los números JSON pueden perder precisión al superar la capacidad de number. El backend
sigue siendo autoridad monetaria. Los enteros BigInt de la vista previa no se envían por
JSON. Para montos de producción fuera de ese rango se necesitaría un contrato decimal
por string coordinado con Java; no se alteró el contrato de este TP.

Ejercicio: explique por qué las interfaces de request y response no son intercambiables.

# 6. Manipulación del DOM y renderizado seguro

El DOM es la representación del documento como árbol de objetos. querySelector localiza
nodos, textContent modifica texto y replaceChildren reemplaza hijos. La utilidad el<T>
devuelve un elemento tipado y falla explícitamente si el selector no existe.

Fragmento real de dom.ts:

~~~typescript
const node = root.querySelector<T>(selector);
if (!node) throw new Error(`No se encontró ${selector}`);
return node;
~~~

app.ts genera las filas a partir de invoices. InvoiceForm genera las filas de artículos.
No se recarga el documento al cambiar resultados. Los mensajes de error usan textContent.
Las opciones de catálogo se crean con new Option: el nombre del cliente se trata como
texto, aunque contenga caracteres que parezcan etiquetas HTML.

## Por qué escapar texto

Una descripción almacenada podría contener símbolos como <, > o comillas. Si se interpola
sin tratamiento en innerHTML, el navegador puede interpretarla como contenido ejecutable.
formatters.ts incluye escape, aplicada a textos externos usados en plantillas. Los IDs
interpolados provienen de reportes comprobados como números.

No use innerHTML para un mensaje directo del servidor: use textContent. Use plantillas
sólo cuando aporte estructura y asegure el escape de cada valor externo.

Práctica: escriba una observación con etiquetas HTML. Al consultar el detalle debe verse
como texto. Relacione ese comportamiento con escape en la plantilla de detail-content.

# 7. Eventos, formularios y confirmaciones

Los eventos avisan que ocurrió una interacción. input se dispara al editar un control;
change confirma una selección; submit inicia un envío. addEventListener registra el
comportamiento sin mezclarlo con atributos HTML.

~~~typescript
el<HTMLFormElement>('#invoice-form').addEventListener('submit', event => {
  void saveInvoice(event);
});
~~~

saveInvoice llama preventDefault para evitar navegación y usa el servicio. El prefijo void
expresa que el listener no devuelve esa Promise al navegador; el manejo de errores está
dentro de la función asíncrona. No debe ignorarse una Promise que pueda fallar sin catch.

Los botones dinámicos usan delegación: un listener del documento o tbody identifica
el botón con closest. Esto permite que los nuevos renglones funcionen sin instalar un
listener diferente en cada botón generado.

## Confirmación y datos sin guardar

confirmar() devuelve Promise<boolean> y usa un dialog. Confirmar resuelve true, Volver
o Escape resuelve false. Anular espera esa decisión antes del HTTP. Limpiar un formulario
con cambios también pide confirmación. beforeunload advierte cuando existe un borrador.

El evento reset sucede antes de restaurar controles. En app.ts la consulta se difiere
con setTimeout(..., 0) para leer los valores ya limpiados. La prueba de navegador detectó
esta condición y verificó su corrección.

Práctica: cancele una anulación y compruebe en Network que no se envía DELETE.

# 8. Fetch API y tratamiento de HTTP

fetch() inicia una solicitud y devuelve una Promise<Response>. Response informa status,
ok y encabezados. Su cuerpo se lee de forma asíncrona con json(). Que fetch se resuelva
no significa éxito funcional: HTTP 400 y 500 también producen una Response.

El único fetch del frontend está en services/http.ts. Recibe path, método y cuerpo
opcional; usa API_BASE centralizada, Accept JSON, Content-Type cuando hay cuerpo,
JSON.stringify y un timeout de veinte segundos.

Extracto real:

~~~typescript
if (response.status === 204 && response.ok) return undefined;
const json: unknown = await response.json().catch(() => null);
~~~

204 indica éxito sin contenido: intentar parsear JSON vacío sería un error. Para otros
éxitos se exige JSON válido y luego el servicio verifica su estructura. Una página HTML
de error del proxy no se muestra como si fuera un reporte.

| Situación | Comportamiento |
|---|---|
| 400 | Mostrar validación del backend |
| 404 | Informar recurso inexistente |
| 409 | Informar conflicto de estado, precio o concurrencia |
| 500 o superior | Mensaje general sin trazas internas |
| Red caída / timeout | Indicar revisión de API y base |

Las pruebas unitarias simulan estos escenarios para verificar el manejo sin forzar fallos
del servidor real. Las pruebas de navegador distinguen el CRUD real del caso interceptado.

# 9. Promises, async y await

Una Promise representa una operación que todavía puede estar pendiente. Puede resolverse
con un valor o rechazarse con un error. async hace que una función devuelva una Promise;
await obtiene el valor o propaga el rechazo dentro de una función asíncrona.

Ejemplo real de FacturaService:

~~~typescript
async obtenerFactura(id: number): Promise<FacturaReporteDTO> {
  return reporte(await http(`/facturas/${id}`));
}
~~~

La solicitud sale por http, se espera su JSON, se comprueba con reporte y se devuelve
una factura. Si http falla, el await propaga el error al catch de quien invocó al servicio.
El navegador puede seguir atendiendo otras tareas mientras la operación de red espera.

## try, catch y finally

app.ts utiliza try para guardar, catch para mostrar errores y finally para desbloquear
controles incluso si la operación falla. Sin finally un error podría dejar Crear factura
deshabilitado permanentemente. El indicador busy evita escrituras duplicadas.

Los filtros usan queryVersion para descartar respuestas de consultas anteriores. Esto
resuelve un problema común: una consulta lenta que llega después de otra reciente no
debe reemplazar los resultados más nuevos.

Ejercicio: explique la diferencia entre esperar crearFactura y llamar crearFactura sin
await. ¿En qué momento se mostraría el éxito y qué ocurriría si el POST falla?

# 10. APIs REST, catálogos y proxy

La API expone recursos mediante rutas HTTP. GET consulta, POST crea, PUT actualiza
y DELETE representa en este dominio una anulación lógica. El frontend no decide qué
SQL ejecutar: utiliza el contrato que publican los controladores Java.

| Método y ruta | Datos / respuesta |
|---|---|
| GET /api/catalogos | Catálogos de sólo lectura |
| GET /api/facturas | Array, filtros clienteId, estado, desde, hasta |
| GET /api/facturas/{id} | Reporte completo y detalles |
| POST /api/facturas | Cabecera y detalles; 201 con reporte |
| PUT /api/facturas/{id} | Completo o estado; 200 con reporte |
| DELETE /api/facturas/{id} | Anulación; 204 sin cuerpo |

No hay filtro remoto por número. No hay catálogo CRUD ni endpoint de reactivación.
No se agregó ningún GET nuevo porque CatalogoRestController ya ofrece los datos necesarios.

## Mismo origen mediante Vite

La pantalla abre por 127.0.0.1:5173 y llama a /api. Vite reenvía esas rutas hacia
127.0.0.1:8080. Para el navegador la llamada mantiene el origen del frontend; no hace
falta cambiar CORS ni deshabilitar seguridad en Spring Boot.

API_PROXY_TARGET configura el destino del proxy. VITE_API_BASE_URL configura la base
que ve el navegador. Las variables VITE_ son públicas: nunca deben contener credenciales.
Para publicar frontend/dist se necesita un servidor con /api reenviado a Spring Boot.

Práctica: ubique la única configuración de 8080 y explique por qué no aparece repetida
en cada botón. Reinicie Vite después de cambiar su entorno.

# 11. FacturaService.ts y contratos de actualización

La clase FacturaService centraliza las operaciones de facturas. app.ts no construye
fetch directamente. Esto separa detalles HTTP del comportamiento visual y permite
probar solicitudes sin depender del DOM.

obtenerFacturas construye URLSearchParams y comprueba un array. crearFactura envía
POST. obtenerFactura consulta un comprobante. actualizarFactura manda el request
completo. eliminarFactura espera DELETE sin devolver datos.

Implementación real del cambio de estado:

~~~typescript
async actualizarEstado(id: number, estado: string,
  observaciones?: string): Promise<FacturaReporteDTO> {
  if (estado !== 'EMITIDA' && estado !== 'ANULADA')
    throw new Error('Estado de factura inválido.');
  const body: FacturaEstadoRequestDTO = {
    estado, ...(observaciones === undefined ? {} : { observaciones })
  };
  return reporte(await http(`/facturas/${id}`, 'PUT', body));
}
~~~

## Dos modos válidos de PUT

El backend ya permite cambiar exclusivamente estado y observaciones. No se necesitó
un nuevo endpoint. Si se envían detalles o cabecera junto con estado, Java rechaza la
mezcla. PUT completo reemplaza todos los renglones y recalcula precios vigentes.

EMITIDA permite editar y actualizar observaciones. ANULADA no puede modificarse ni
reactivarse. El frontend oculta acciones inválidas, y Java mantiene esa protección aunque
alguien envíe solicitudes manuales.

DELETE no borra filas: guarda ANULADA y fechaAnulacion. El botón se llama Anular factura.
El formulario de estado permite registrar motivo con PUT. No se inventa un botón Eliminar
que prometa borrado físico. La conservación del historial se verificó con SQL.

# 12. app.ts y coordinación de la interfaz

app.ts inicia la aplicación, carga catálogos y facturas, conecta eventos y mantiene la
factura seleccionada. La lógica específica de alta se delega a InvoiceForm para evitar
un único archivo que mezcle todos los detalles.

bootstrap obtiene catálogos, llena filtros y crea o actualiza el editor sin perder un
borrador. loadInvoices valida fechas, invoca obtenerFacturas y renderiza resultados.
renderInvoices muestra importes confirmados y pagina ocho filas por pantalla; la API
continúa devolviendo el array completo porque no tiene paginación remota.

saveInvoice obtiene un payload validado, bloquea controles y usa POST o PUT completo
según editor.editing. Después de éxito limpia el editor, muestra ID y total confirmados
y recarga el listado. Si falla, conserva los campos y muestra el error.

showDetail consulta por ID en lugar de asumir que el listado sigue actualizado. editar
carga ese reporte en el formulario. anular espera la confirmación y llama eliminarFactura.
saveState usa actualizarEstado y confirma si el destino es ANULADA.

## Dónde viven los datos

invoices es el último resultado de la API; selected es el reporte consultado; page es
la página visual; busy impide dobles escrituras. Ninguna factura se simula en localStorage.
Los valores ingresados en el formulario son un borrador local hasta que el servidor
confirma la transacción.

Práctica: siga desde el listener submit hasta el método Java crear, anotando cada archivo
que participa. Distinga el FacturaService TypeScript del FacturaService Java.

# 13. Detalles dinámicos e importes preliminares

InvoiceForm mantiene un array de FacturaDetalleRequestDTO. Agregar incorpora un objeto
con articuloId pendiente y cantidad 1. Quitar hace splice y vuelve a generar filas. input
y change sincronizan valores. El payload copia los renglones para no enviar referencias
mutables que luego puedan modificarse.

La moneda seleccionada y el artículo identifican el precio de catálogo. Si falta precio,
el select recibe setCustomValidity y aparece una advertencia. La validación también exige
una fecha real, relaciones positivas, entre 1 y 100 detalles y cantidades dentro del rango.
No basta con que un campo tenga apariencia numérica: también se comprueba su precisión.

## Redondeo equivalente al backend

Fragmento real de utils/importes.ts:

~~~typescript
const neto = (decimal(cantidad, 3) * decimal(precio, 2) + 500n) / 1000n;
const iva = (neto * decimal(alicuota, 2) + 5000n) / 10000n;
return { neto, iva, total: neto + iva };
~~~

La cantidad se escala a milésimos y el precio a centavos. BigInt permite multiplicar
enteros sin los errores binarios típicos de 0.1 + 0.2. Se redondea HALF_UP por renglón:
primero neto y luego IVA sobre neto redondeado. Se suman importes ya redondeados.

Dos cuadernos de 1000 al 21% y tres lápices de 200 al 10,5% producen neto 2600,
IVA 483 y total 3083. El frontend envía solamente artículo y cantidad; Java vuelve a
resolver precios y devuelve los importes definitivos. Una vista previa puede quedar
desactualizada si el catálogo cambia entre carga y guardado.

Ejercicio: explique por qué redondear el total una sola vez puede diferir de sumar
renglones ya redondeados. Busque ese criterio en CalculadoraImportes.java.

# 14. Flujo completo y persistencia en PostgreSQL

Al pulsar Crear factura, el navegador construye FacturaRequestDTO y envía JSON. Spring
deserializa el cuerpo y valida sus restricciones. El servicio busca relaciones, calcula
importes y guarda cabecera y detalles en una transacción. La respuesta se construye
como DTO antes de salir de la capa transaccional.

[[SECUENCIA]]

## Verificar datos persistidos

Una fila visible no demuestra por sí sola persistencia. En esta entrega la prueba real
creó una factura desde Edge y luego se consultaron factura_venta y factura_detalle en
PostgreSQL. EVIDENCIA_SQL_TYPESCRIPT.txt conserva la salida para el comprobante 7.

Después de editarlo tenía neto 4600, IVA 903 y total 5503. Tras anular seguía la cabecera
con estado ANULADA y fecha_anulacion, junto con dos renglones: cuatro cuadernos y tres
lápices. La anulación no ejecutó una eliminación física de esas filas.

Desde la raíz del proyecto, para cualquier ID real:

~~~powershell
.\scripts\verificar-frontend-sql.ps1 -FacturaId 7
~~~

El script importa la conexión sin imprimir su contraseña, ejecuta únicamente SELECT y
restaura PGPASSWORD. No borra ni modifica información. Use el ID que acaba de crear si
los datos de su base son distintos de los usados en esta demostración.

# 15. Pruebas del CRUD desde la interfaz

Para iniciar: postgres-local.ps1 iniciar, ejecutar.ps1 -DesdeFuentes y, desde frontend,
npm.cmd ci seguido de npm.cmd run dev. Mantenga Vite y Java abiertos. Acceda al 5173.
DEMO_FRONTEND.md contiene los comandos completos y la secuencia para exponer el TP.

## Secuencia manual

1. Filtre por cliente, estado y fechas; pruebe un resultado vacío y limpie filtros.
2. Complete cabecera, agregue dos artículos y quite un tercer renglón.
3. Guarde y verifique 201 en Network, ID del reporte y tabla actualizada.
4. Consulte detalles, edite cantidad y verifique PUT completo 200.
5. Cambie observaciones manteniendo EMITIDA y verifique PUT con cuerpo de estado.
6. Anule con confirmación; verifique DELETE 204 y conservación SQL.
7. Sobre otra emitida, pruebe ANULADA con motivo mediante PUT.
8. Revise el diseño móvil y los errores sin perder un borrador.

## Capas de pruebas automatizadas

~~~powershell
# Desde frontend
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
# Desde la raíz
.\scripts\probar.ps1
~~~

Las 11 pruebas de contrato cubren JSON, estados HTTP, límites y cálculos. Las 19 pruebas
Java existentes cubren API y PostgreSQL, incluido rollback. Las 3 pruebas de Edge cubren
CRUD real, filtros, múltiples renglones, confirmaciones, errores y diseño móvil. Los
errores del caso específico de navegador se interceptan de manera explícita; el CRUD
utiliza el servidor real. No son datos simulados de la aplicación.

El navegador crea comprobantes de prueba y los deja anulados. Las pruebas Java limpian
sólo su esquema exclusivo en facturacion_test. No deben usarse contra producción.

# 16. Errores frecuentes y soluciones

| Síntoma | Causa probable / solución |
|---|---|
| Pantalla anterior al abrir 8080 | Abrir el frontend TypeScript por 5173 |
| No reconoce npm | Instalar Node, abrir nueva terminal y verificar PATH |
| npm.ps1 bloqueado | Usar npm.cmd sin cambiar política global |
| API sin conexión | Verificar Java, PostgreSQL y destino del proxy |
| Vite exige otro puerto | Puerto 5173 ocupado; el proyecto usa strictPort |
| Error 400 | Revisar nombres, tipos, fecha y cantidades del request |
| Error 404 | Recurso eliminado/inexistente o URL incorrecta |
| Error 409 al crear | Artículo sin precio en la moneda seleccionada |
| Error 409 al editar | Comprobante anulado o conflicto concurrente |
| JSON inválido al anular | No leer JSON después de 204 |
| Factura creada no aparece | Limpiar filtros que excluyen el nuevo comprobante |
| TS compila, pero falla JSON | Una interfaz no comprueba los datos de red |

## Problemas encontrados durante esta implementación

Se detectó un reset que consultaba antes de limpiar controles; se corrigió difiriendo
la lectura a la siguiente tarea del navegador. También faltaba favicon y producía un 404
de consola; se agregó un SVG local y se repitió la prueba hasta eliminar ese error.

El entorno de herramientas restringía conexiones y descargas. La instalación y las
verificaciones de red se ejecutaron con autorización fuera de ese entorno. No fue
necesario alterar CORS, contraseñas, migraciones ni la seguridad del backend.

Los formatos de controles date nativos pueden depender del idioma del navegador y del
sistema operativo. El valor enviado siempre es ISO y las fechas del listado se formatean
en es-AR. El manual no promete precisión decimal ilimitada del number de JavaScript.

# 17. Ejercicios prácticos de defensa

## Ejercicio A: reconstruir el JSON

Cree una factura de dos artículos desde la interfaz y copie el Request Payload de Network.
Marque campos de cabecera, relaciones por ID y detalles. Verifique que no incluye precio,
neto, IVA ni total. Compare cada nombre con FacturaRequestDTO.java.

## Ejercicio B: comparar modos PUT

Edite una cantidad y guarde. Luego cambie observaciones mediante el diálogo de estado.
Compare ambos JSON. Explique por qué el segundo no debe contener detalles y por qué
el primero reemplaza todos los ítems. Compruebe si cambiaron IDs de renglones.

## Ejercicio C: errores controlados

Seleccione un artículo sin precio. Observe la advertencia. Compare con una solicitud
manual válida en estructura pero que usa ese artículo: Java responde 409. Explique por
qué la validación del navegador mejora UX pero no protege por sí sola la base.

## Ejercicio D: concurrencia y carga

Demore una respuesta de listado desde DevTools y cambie filtros. Localice queryVersion
y explique cómo evita mostrar una consulta anterior. Distinga esto del bloqueo
transaccional que Java aplica al editar una factura.

## Ejercicio E: cálculos y formato

Pruebe 0.005 unidades a precio 1 y alícuota 50 en la prueba de importes. El resultado
debe ser neto un centavo, IVA un centavo, total dos centavos. Explique el orden del
redondeo y por qué formatear ARS 0,02 no cambia el valor que calcula el backend.

## Ejercicio F: persistencia y anulación

Cree una factura, registre su ID, anúlela y ejecute el script SQL de verificación. Prepare
una explicación con cabecera, detalles y fecha de anulación. No confunda DELETE HTTP
con DELETE SQL: en esta API representa una operación de negocio sobre el recurso.

# 18. Preguntas del profesor y respuestas razonadas

## ¿Por qué TypeScript si el navegador ejecuta JavaScript?

Porque agrega comprobación estática durante desarrollo. Ayuda a mantener el contrato
de DTOs y descubre usos incompatibles antes de ejecución. Vite genera JavaScript y
tsc verifica tipos; ninguno reemplaza validación de JSON ni del servidor.

## ¿Una interfaz valida la respuesta del servidor?

No. Se elimina al compilar. Por eso http devuelve unknown y reporte comprueba campos,
estados, detalles y tipos antes de devolver FacturaReporteDTO.

## ¿Por qué se envían IDs y no entidades?

Para desacoplar el contrato del modelo JPA y permitir que Java resuelva referencias
existentes. El navegador no debe crear una entidad Cliente anidada ni imponer precios.

## ¿Por qué fetch no lanza error automáticamente ante 400?

Porque recibió una respuesta HTTP. Hay que revisar response.ok y convertir el resultado
en un error de aplicación. Los fallos de red sí rechazan la Promise de fetch.

## ¿Por qué async/await no congela toda la página?

La función se suspende mientras espera la Promise, y el navegador puede continuar su
ciclo de eventos. Los botones se bloquean por una decisión explícita para evitar
operaciones duplicadas, no porque await congele la interfaz.

## ¿Por qué no se calcula todo sólo en TypeScript?

El navegador puede ser modificado y sus datos pueden quedar desactualizados. Java
resuelve catálogos y utiliza BigDecimal dentro de una transacción. La vista previa
orienta; la respuesta del backend establece los importes definitivos.

## ¿Qué diferencia hay entre PUT completo y cambio de estado?

El primero valida cabecera y detalles, reemplaza los ítems y recalcula precios. El segundo
admite estado y observaciones sin mezclar otros campos. Java ya implementaba ambas
variantes y el frontend las representa con tipos distintos.

## ¿Por qué el botón dice Anular si se usa DELETE?

Porque el contrato implementa anulación lógica: cambia estado y registra fecha, pero
conserva las filas. La interfaz comunica el efecto real y solicita confirmación.

## ¿Qué pasa si falla el segundo detalle al guardar?

La transacción Java revierte la cabecera y los detalles anteriores. Las pruebas existentes
lo verifican con un fallo real de PostgreSQL en su esquema de pruebas. El frontend
muestra el error y conserva el borrador para corregirlo.

## ¿Por qué no hizo falta agregar CORS?

Porque Vite sirve la página y reenvía /api al backend como proxy. El navegador usa su
mismo origen. Un despliegue con API externa necesitaría CORS limitado a su origen.

## ¿Cómo demuestra que esto está conectado a PostgreSQL?

Mostrando POST y reporte en Network, recargando por GET, y consultando cabecera y
detalles en SQL. El proyecto conserva evidencia SQL, capturas y resultados de pruebas
en docs; una fila dibujada sola no demostraría persistencia.

## ¿Qué quedó fuera del alcance?

Autorización fiscal, autenticación, conversión de monedas, catálogo CRUD y paginación
remota. No se inventaron para el TP. El frontend actual cumple las operaciones que la
API real ofrece y conserva el material anterior del proyecto.
