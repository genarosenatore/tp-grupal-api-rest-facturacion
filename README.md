# Facturación de ventas

Sistema HTML/TypeScript y Spring Boot con PostgreSQL, filtros paginados, alta/edición
en modal, exploradores de clientes y precios, anulación auditada y descarga de PDF.

## Entrega académica pública

Este repositorio contiene fuentes, pruebas y documentación. Los datos iniciales
son ficticios. Las contraseñas, la identidad fiscal personal, los certificados,
las claves, los tickets de ARCA y las bases locales quedan fuera de la entrega.
La aplicación funciona en modo local con PostgreSQL sin credenciales de ARCA.
Para evaluarla: compilar el frontend, ejecutar `scripts/maven.ps1 package`,
configurar una base propia y seguir la sección **Ejecutar**.

La versión pública no incluye JAR, PDF fiscales, capturas ni bases anteriores;
el JAR se genera al compilar. Ver [guía de entrega](docs/ENTREGA_FINAL.md)
y [preparación para GitHub](docs/PUBLICACION_GITHUB.md).

La integración de **Facturas A, B y C en homologación** utiliza WSAA y WSFEv1:
autorización con CAE, conciliación y nota de crédito de la misma clase para anular.
La evidencia externa histórica corresponde a C; A/B se verifican con un doble SOAP.
Se activa de forma explícita; el inicio normal mantiene el envío fiscal deshabilitado.
No hay endpoints de producción en el cliente fiscal.
El estado actual, configuración y evidencias están en
[docs/HOMOLOGACION_OPERATIVA.md](docs/HOMOLOGACION_OPERATIVA.md).

La matriz de requisitos, los recorridos probados y los pendientes están en [AUDITORIA_TP.md](AUDITORIA_TP.md).
El recorrido para presentar y las evidencias anteriores están en [docs/ENTREGA_FINAL.md](docs/ENTREGA_FINAL.md).

## Ejecutar

Requiere JDK 26, PostgreSQL 18 y Maven; para desarrollar/compilar la interfaz,
Node.js 22.12 o posterior y npm. Los scripts detectan el JDK/Maven instalados en Windows.

Con el clúster local exclusivo del proyecto (no altera el servicio PostgreSQL existente):

```powershell
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1
```

Abrir http://127.0.0.1:8080. Compilar primero como indica la sección siguiente;
el JAR generado incluye el frontend compilado.
La conexión local se guarda en .local/conexion.ps1, que no debe compartirse.
Si Windows bloquea pg_ctl.exe, usar un servidor PostgreSQL autorizado y configurar
DB_URL, DB_USER y DB_PASSWORD; no modificar la política de seguridad del equipo.

Para usar un PostgreSQL existente, ver sql/crear-bases.sql y
scripts/configurar-conexion.ps1. Utilizar una base exclusiva terminada en _test
para las pruebas de integración destructivas.

Para probar Factura C con ARCA, configurar primero el laboratorio según [la guía de homologación](docs/HOMOLOGACION_OPERATIVA.md).
Después ejecutar `.\scripts\ejecutar-homologacion.ps1` y abrir http://127.0.0.1:8081.
El laboratorio usa H2 persistente en `.local/arca-homologacion/demo-h2` y el escenario de productos,
pesos, consumidor final y punto de venta 2. Este laboratorio requiere certificados
y configuración privada propios; la entrega pública no los contiene.

## Desarrollo y compilación

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Vite escucha en http://127.0.0.1:5173 y reenvía /api a Spring en 8080.
API_PROXY_TARGET permite cambiar ese destino. El ejemplo está en frontend/.env.example.
No poner secretos en variables VITE_.

Para publicar los cambios de UI en Spring y construir un JAR actualizado:

```powershell
cd frontend
npm.cmd run build
cd ..
.\scripts\maven.ps1 package
```

El build verifica TypeScript, genera frontend/dist y copia sus recursos a
src/main/resources/static. La fuente editable es frontend/src y frontend/index.html.
Maven genera target/facturacion-1.0.0.jar. Para ejecutarlo directamente:

```powershell
. .\.local\conexion.ps1
& 'C:\Program Files\Java\jdk-26\bin\java.exe' -jar .\target\facturacion-1.0.0.jar
```

Los lanzadores prefieren `target/facturacion-1.0.0.jar` una vez compilado.
`scripts/preparar-entrega.ps1` genera una entrega de fuentes para GitHub;
no incorpora ejecutables ni evidencia fiscal personal.
`scripts/ejecutar.ps1 -DesdeFuentes` inicia Spring mediante Maven. La configuración determina si se habilita ARCA.

## Uso

La pantalla inicial muestra filtros y grilla. Nueva factura y Editar abren el mismo
modal. Guardar correctamente lo cierra y actualiza la página conservando filtros;
un error conserva los datos. Los exploradores tienen búsquedas independientes.
El precio final y los importes siempre se resuelven en el backend.

Cada fila de facturas mantiene dos controles: **Consultar** y **Acciones**. El menú
ofrece Descargar PDF y las acciones permitidas por el estado del comprobante.
Anular y Eliminar borrador aparecen separados al final y conservan sus confirmaciones.
El menú admite teclado, se cierra con Escape o al hacer clic afuera y se posiciona
dentro de la pantalla sin quedar recortado por la tabla.

Los filtros de facturas se organizan en identificación y estado/fechas. Sus cambios
se preparan y se aplican únicamente con **Buscar** o **Enter**. Un aviso identifica
los cambios sin aplicar y los resultados mantienen la última consulta confirmada.
La banda inferior muestra los filtros activos; la × prepara quitar uno sin borrar
los demás y requiere Buscar para aplicarlo. **Limpiar filtros** restaura la consulta
general en un paso. Todos los botones **Buscar** incluyen una lupa y el de facturas indica
**Buscando…** mientras se actualizan los resultados.
El filtro **Cliente** abre el mismo explorador que el alta de facturas, con búsqueda
por nombre o documento y la condición fiscal visible en los resultados. Elegir un
cliente prepara el filtro sin cambiar el cliente ni los datos de un borrador;
se confirma con Buscar en la pantalla principal. Cancelar conserva la selección
anterior y **Quitar filtro de cliente** prepara quitar solo ese filtro. Los exploradores
de clientes, artículos y administración también requieren **Buscar** o **Enter**;
escribir o cambiar una opción no consulta el servidor ni borra otros campos. Los
resultados anteriores permanecen visibles hasta ejecutar la búsqueda. En los
exploradores se bloquea su selección mientras los filtros no coincidan con la
consulta mostrada; volver a los filtros anteriores habilita de nuevo esas filas.
El buscador de clientes tiene **Buscar** y **Limpiar** junto a los campos. Buscar
requiere nombre o documento; Limpiar solo vacía esos campos. **Ver todos los clientes**
aparece sobre el listado, limpia los campos y consulta sin filtros, sin seleccionar
un cliente automáticamente. En facturas y artículos, Limpiar actualiza la consulta;
al agregar artículos conserva la lista de precios obligatoria.
Cada uso conserva su propia búsqueda; las altas y ediciones de fichas se
ofrecen al seleccionar el cliente de una factura.

El combo de cliente abre directamente su explorador, sin un botón adicional de búsqueda.
**Agregar artículo** abre el explorador de precios; el combo de cada renglón permite
buscar un reemplazo en la misma ventana. Las
búsquedas se consultan al servidor y **Seleccionar** obtiene nuevamente el registro
vigente por ID. Los errores conservan la ventana y el borrador; las respuestas de
búsquedas anteriores no reemplazan resultados nuevos.

El alta empieza sin renglones vacíos y se organiza en cliente/comprobante, artículos
y descuento/total. El botón Guardar se habilita al agregar artículos. Cancelar una
búsqueda conserva el borrador. Después de agregar o reemplazar un artículo el foco
queda en su cantidad.

**Administrar catálogo**, en la pantalla principal, reúne las altas de productos y
listas y la carga/edición de precios. Esas acciones no aparecen mientras se eligen
artículos. Desde el explorador se puede entrar a la administración y volver a elegir
artículos sin perder los datos de la factura.

**Buscar cliente** permite crear o editar su ficha: nombre, tipo y número de documento,
condición fiscal y domicilio. Esos datos se completan automáticamente en la factura y
no se editan allí. El backend los toma del cliente y conserva una copia de nombre,
documento, tipo, condición y domicilio en cada comprobante. Cambiar la ficha no altera
los comprobantes guardados. Los datos fiscales desconocidos de clientes anteriores
deben completarse desde su ficha; no se infieren a partir del número de documento.

El documento (tipo y número juntos), la condición fiscal y el domicilio quedan
visibles como texto en un resumen compacto justo debajo del selector del cliente,
antes del tipo de comprobante. No se muestran como campos editables ni desplegables.
El selector de comprobantes filtra por receptor: A para responsable inscripto o
monotributista con CUIT, B para consumidor final o exento, y C para las cuatro
condiciones (CUIT para inscriptos, monotributistas y exentos). X sigue disponible
como comprobante sin validez fiscal. Si se cambia el cliente y el tipo anterior
deja de corresponder, se exige elegir otro y revisar el total; se conservan los
artículos y el descuento. El servidor también valida estas reglas. El laboratorio
permite probar escenarios de distintos emisores; este filtro por receptor no
reemplaza la habilitación fiscal del emisor.

El **descuento general** admite **Sin descuento**, **Porcentaje** o **Monto fijo** en la
moneda de la factura. Se usa una sola opción, con hasta dos decimales; el porcentaje
admite de 0 a 100 y el monto no puede superar el subtotal antes de IVA. El backend
redondea el descuento total y reparte los centavos entre los renglones de forma
proporcional por restos mayores, con desempate por orden. Después calcula el IVA
sobre cada base descontada. En C se descuenta el precio final sin discriminar IVA.
Se conservan el precio unitario original y el descuento por renglón. La vista previa,
la consulta, la edición y el PDF muestran los importes del descuento.

En **Administrar catálogo**, **Nuevo producto** abre el alta de código, descripción,
IVA, precio y moneda. Se guarda el artículo y su primer precio en una sola transacción.
El código se normaliza a mayúsculas y no se permiten repetidos. Tras guardarlo aparece
en el explorador y en el selector de artículos sin perder el borrador de factura.
Los errores mantienen el formulario y sus valores; los envíos simultáneos no duplican
el código. No modifica precios ni detalles históricos de facturas anteriores.
El ejemplo de API está en examples/crear-producto.json. La migración V7 incorpora las listas de precios. En la demo H2 se ajustan identidades a MAX(id)+1 al cargar
las semillas para conservar productos entre reinicios.

El número fiscal es distinto del ID técnico. Tener número no equivale a estar informada.
Cada factura guarda explícitamente `letraComprobante` (enum `LetraComprobante`: A, B, C o X)
en la columna `factura_venta.letra_comprobante`. El servidor asigna la letra según el tipo;
no se envía ni edita por separado en el request. El reporte de la API devuelve la letra
persistida, que se muestra en el listado, detalle y PDF. En el alta se elige el tipo
de comprobante y no se repite su letra en un campo adicional. **Comprobante X** es la opción local, identificada como
**DOCUMENTO NO VÁLIDO COMO FACTURA**, sin solicitud de autorización fiscal.
V9 completa las letras de los comprobantes existentes sin cambiar números, importes ni
autorizaciones, y valida la correspondencia con el tipo. La demo H2 con `ddl-auto=update`
realiza el ajuste equivalente al arrancar. Los tipos históricos de prueba mantienen X.
Se puede eliminar físicamente un borrador sin número ni autorización, con confirmación.
Anular exige motivo y conserva la historia. Una autorizada queda bloqueada y requiere
un ajuste fiscal asociado para anularse; si falta el adaptador, conserva su estado.
Consulta y PDF están disponibles incluso en facturas bloqueadas o anuladas.

Configurar EMISOR_RAZON_SOCIAL, EMISOR_CUIT, EMISOR_DOMICILIO, EMISOR_CONDICION_IVA,
EMISOR_INGRESOS_BRUTOS y EMISOR_INICIO_ACTIVIDADES (AAAA-MM-DD) para la impresión.
El laboratorio carga un comercio ficticio identificado desde un archivo privado
.local/arca-homologacion/emisor.properties, manteniendo el CUIT real de homologación.
El domicilio se obtiene de la ficha del cliente; la condición de venta se carga en
la factura. V10 añade la copia histórica completa del cliente y el descuento general.
Los datos históricos que el sistema no conservaba antes de V10 se copian del cliente
vigente al migrar; no es posible reconstruir modificaciones anteriores a esa versión.
El PDF usa cabecera A/001, B/006 o C/011, número completo, tabla paginada, importes argentinos,
QR de datos ARCA para el circuito compatible y referencia al ajuste de anulación.
Los comprobantes pendientes se identifican como SIN VALIDEZ FISCAL; el PDF solo muestra
código y vencimiento cuando existen, con la denominación real (CAE/CAEA/CAI).

## Listas de precios

La lista se elige en el explorador al agregar el primer artículo: **Minorista**,
**Mayorista** u otra lista creada por el usuario. No hay selector de lista en la cabecera.
Al abrir **Agregar artículo** sin una lista asignada, no se consultan artículos ni
precios: primero se pide seleccionar una lista y presionar **Buscar**. Después se consultan únicamente sus
precios, hasta 100 resultados según los filtros. Si la factura ya tiene una lista,
el explorador abre con esa lista seleccionada. Limpiar la lista vacía los resultados;
una lista sin precios muestra un aviso y no toma artículos de otras listas.
El inicio y la selección usan `/api/catalogos?incluirArticulos=false`, que devuelve
las opciones de cabecera sin consultar las tablas de artículos y precios. Al editar
una factura se consultan en bloque únicamente los precios de sus propios artículos.
Los precios anteriores quedan en Minorista (ID 1). V11 agrega ocho productos ficticios
con precios explícitos en pesos para Minorista y Mayorista, sin reemplazar precios ya
cargados. Las listas nuevas empiezan vacías y no toman precios de otras listas.

En **Administrar catálogo**:

- **Nueva lista** crea una lista con nombre único, por ejemplo Distribuidores.
- **Cargar precio** agrega un producto existente a una lista y moneda o actualiza su precio.
- **Editar precio** abre el precio de la fila seleccionada.
- Los filtros combinan lista, código/descripción, moneda e IVA; se pueden consultar todas las listas.
- **Nuevo producto** permite elegir en qué lista guardar su primer precio.

El primer artículo fija la lista de la factura. Si se intenta seleccionar un precio de
otra lista, aparece una alerta con el nombre de ambas listas; no se agrega el artículo
ni se cambian cantidades, descuento o precios cargados. **Cambiar lista** permite
consultar todos los precios de una nueva lista en una sola lectura, muestra los precios
unitarios y totales anteriores/nuevos y requiere confirmación. Si falta algún precio,
hay un error de conexión o el descuento fijo supera el nuevo subtotal, no se aplica
ningún cambio. Confirmar conserva artículos, cantidades, datos del cliente y descuento;
los cambios se persisten al guardar la factura. Cancelar conserva la lista anterior.
Al editar se conserva la lista guardada y se aplica el mismo recorrido.
La consulta de facturas también admite filtrar por lista y muestra la lista en el detalle.
Las facturas ya guardadas conservan sus precios e importes; una edición completa vuelve a
resolver los precios vigentes, como antes.

V7 migra PostgreSQL sin reemplazar los precios ni detalles existentes. La demo H2 persistente
asigna los datos previos a Minorista y retira exclusivamente la unicidad anterior de
artículo/moneda, manteniendo la nueva unicidad por lista/artículo/moneda. Este ajuste se
aplica solo con H2 y `ddl-auto=update`.

## API

| Método | Ruta | Función |
|---|---|---|
| GET | /api/catalogos | Catálogos de selección de solo lectura |
| GET | /api/clientes?q=&documento= | Buscar clientes en la base (hasta 100 coincidencias) |
| GET | /api/clientes/{id} | Obtener los datos actuales del cliente seleccionado |
| POST / PUT | /api/clientes / /api/clientes/{id} | Crear o editar la ficha del cliente |
| GET | /api/catalogos/precios | Buscar por q, listaPrecioId, tipoMonedaId e iva (hasta 100 coincidencias) |
| GET | /api/catalogos/precios?listaPrecioId=2&tipoMonedaId=1&articuloIds=1,2 | Consultar los precios de hasta 100 artículos en una sola lectura, sin modificar la factura |
| GET | /api/catalogos/precios/{articuloId}?tipoMonedaId=1&listaPrecioId=1 | Consultar el precio vigente al seleccionar |
| POST | /api/listas-precio | Crear una lista vacía con `{ "nombre": "Distribuidores" }`, 201 |
| PUT | /api/listas-precio/{id}/precios | Crear/actualizar un precio con articuloId, tipoMonedaId y precio |
| POST | /api/articulos | Alta transaccional de producto y primer precio, 201 |
| GET | /api/facturas | Página con content, page, size, totalElements y totalPages |
| GET | /api/facturas/{id} | Reporte completo |
| POST | /api/facturas | Alta, 201 con Location |
| PUT | /api/facturas/{id} | Edición permitida |
| DELETE | /api/facturas/{id} | Borrado físico de borrador permitido, 204 |
| POST | /api/facturas/{id}/anular | Anulación con motivo y trazabilidad |
| POST | /api/facturas/{id}/autorizar | Solicitud al adaptador ARCA configurado |
| POST | /api/facturas/{id}/conciliar | Consulta del resultado sin reenviar |
| GET | /api/facturas/{id}/pdf | Descarga application/pdf |

Filtros combinables: listaPrecioId, numero, clienteId, estado (EMITIDA/ANULADA), desde, hasta,
informadaArca (true/false; omitir para todas), page (desde 0) y size (1-100; defecto 8).
POST/PUT de factura y POST de producto aceptan listaPrecioId; si se omite se usa Minorista (ID 1).
POST/PUT de factura aceptan `tipoDescuento` (`SIN_DESCUENTO`, `PORCENTAJE`, `MONTO_FIJO`)
y `valorDescuento`; omitir ambos conserva el comportamiento sin descuento. Los campos
anteriores del receptor se aceptan por compatibilidad si coinciden con el cliente;
la interfaz envía únicamente `clienteId` para obtener estos datos.
Orden estable id DESC. Las fechas son inclusivas y desde no puede superar hasta.

Ejemplos de alta/edición en examples. Para anular, enviar examples/anular-factura.json
mediante POST a /api/facturas/{id}/anular. No se anula mediante DELETE ni PUT estado.
El GET paginado reemplaza el array del contrato anterior.

## Pruebas

```powershell
cd frontend
npm.cmd test
npm.cmd run build
cd ..
.\scripts\maven.ps1 test
```

La suite nueva ejecuta Spring/JPA y HTTP contra H2 en memoria con un doble fiscal
aislado. Para sumar los recorridos Playwright con Spring/H2 iniciados automáticamente:

```powershell
$env:RUN_BROWSER_TESTS = 'true'
$env:NODE_EXE = (Get-Command node.exe).Source
.\scripts\maven.ps1 test
```

Requiere Edge y puerto 5173 libre. No utiliza bases de trabajo ni conecta con ARCA.
Resultado Playwright en tmp/playwright.log. PDF y PNG de revisión en tmp/pdfs.
Para la suite PostgreSQL/Flyway, configurar conexión y ejecutar scripts/probar.ps1.
Se omite expresamente si TEST_DB_URL no está definido.

El bloqueo anterior de PostgreSQL se resolvió el 11/11/2026: el clúster del proyecto
funciona en 127.0.0.1:55432 y las migraciones V1-V6 fueron validadas en facturacion_test.
En la verificación anterior del 12/11/2026 pasaron los 51 casos Java de entonces. La funcionalidad se comprobó
con H2 y navegador, y se autorizó/anuló una Factura C contra ARCA de homologación.

## Arquitectura y datos

frontend -> controller -> service -> repository -> JPA -> PostgreSQL.
Los DTO se construyen dentro de transacciones; no se serializan entidades JPA.
Los importes usan BigDecimal y redondeo HALF_UP; el detalle conserva sus precios,
descripciones y alícuotas históricas. Se mantienen los catálogos y reglas previas:
un precio vigente por lista, artículo y moneda y hasta 100 detalles. El formulario carga cantidades
en unidades enteras y sus flechas avanzan de uno en uno (mínimo 1). La API conserva su
capacidad decimal para los datos históricos y las integraciones existentes.

Flyway administra V1-V11 y Hibernate valida el esquema. V4 añade autorización y auditoría
sin inventar números fiscales para facturas existentes. Los envíos tienen un intento
durable antes de llamar a un proveedor, bloqueo concurrente y conciliación ante dudas.
La instalación sigue siendo una demostración local sin autenticación.

Los manuales y verificaciones anteriores en docs son históricos. El README anterior
se conserva en docs/README_ETAPA_ANTERIOR.md. La guía de entrega vigente es
docs/ENTREGA_FINAL.md; el alcance del laboratorio fiscal está documentado en
docs/HOMOLOGACION_OPERATIVA.md.
