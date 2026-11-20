# Sistema de facturación de ventas

Guía para comprender el código y presentar el trabajo práctico

Versión del proyecto revisada el 13 de noviembre de 2026

Este sistema permite registrar ventas con varios artículos, calcular sus importes, consultar facturas, modificarlas cuando corresponde, imprimirlas y conservar su historia al anularlas. Incluye una interfaz web, una API en Java y persistencia en una base de datos. Como ampliación, incorpora la autorización de Factura C y su nota de crédito asociada en el entorno de homologación de ARCA.

La idea central para entenderlo es seguir un dato de principio a fin. La persona carga una cantidad en el navegador; TypeScript arma una solicitud; Java valida las reglas, busca el precio y calcula el total; la base guarda la factura y sus renglones; finalmente, el navegador muestra el resultado confirmado. La autorización fiscal es una operación posterior y separada.

Esta guía se puede leer sin conocer todas las tecnologías. Primero explica el funcionamiento visible, después recorre el código y termina con un guion de exposición y preguntas de defensa. Los ejemplos numéricos son didácticos y no dependen de que el catálogo de la computadora tenga esos mismos precios.

## Cómo estudiar esta guía

- Primera lectura: capítulos 1 a 4 para entender qué hace el programa y cómo se conectan sus partes.
- Segunda lectura: capítulos 5 a 12 con el proyecto abierto para relacionar explicaciones y archivos.
- Preparación de la defensa: capítulos 13 a 17 para ejecutar, demostrar, justificar decisiones y practicar respuestas.

## Recorrido del documento

1. Qué resuelve el programa
2. Tecnologías y responsabilidades
3. Arquitectura y organización de archivos
4. Recorrido completo al crear una factura
5. Cómo funciona la interfaz
6. Cómo funciona la API
7. Modelo de datos y relaciones
8. Cálculos y conservación de precios
9. Transacciones y concurrencia
10. Estados y operaciones permitidas
11. Integración con ARCA en homologación
12. Generación del PDF
13. Mejoras implementadas y su motivo
14. Ejecución y compilación
15. Pruebas y evidencias
16. Guion para presentar al profesor
17. Preguntas de defensa y mapa de lectura

<!-- page -->
# 1 Qué resuelve el programa

Una venta tiene información general y una lista de productos. La información general se llama cabecera: fecha, cliente, condición de IVA, punto de venta, moneda, domicilio del cliente, condición de venta y observaciones. Cada producto agregado ocupa un detalle o renglón, con cantidad, descripción, precio e importes.

El programa reúne esas partes en una factura. Por ejemplo, una venta puede contener dos cuadernos y tres lápices. Sigue siendo una sola factura, pero tiene dos detalles. Por eso el modelo necesita una relación de una factura a muchos detalles.

## Qué puede hacer la persona usuaria

- Buscar facturas por cliente, número, estado, fechas y condición de informada a ARCA.
- Recorrer los resultados mediante páginas.
- Crear una factura y agregar o quitar renglones.
- Buscar clientes y precios mediante exploradores dentro del formulario.
- Crear un producto con su primer precio sin perder el borrador de factura.
- Consultar el detalle completo, editar los comprobantes permitidos y descargar su PDF.
- Eliminar un borrador que cumpla las condiciones o anular una factura conservando su historia.
- En el laboratorio configurado, solicitar autorización, conciliar una respuesta incierta y anular mediante un ajuste fiscal asociado.

## Dos recorridos dentro del mismo sistema

En el recorrido local se registra y administra la venta. El servidor guarda la factura como EMITIDA y con estado fiscal PENDIENTE. Esa palabra EMITIDA es un estado interno del programa: por sí sola no indica que ARCA la haya autorizado.

En el recorrido de homologación se selecciona Factura C antes de guardar y luego se solicita su autorización. El adaptador implementado trabaja con productos, pesos argentinos, consumidor final y el punto de venta configurado. El laboratorio usa normalmente el punto 2.

La integración externa utiliza exclusivamente homologación. Los comprobantes de ese entorno se identifican como pruebas sin validez fiscal. El proyecto es una demostración académica local y no tiene autenticación de usuarios.

## Cómo explicarlo oralmente

“El sistema administra facturas con cabecera y múltiples detalles. Separa la carga comercial de la autorización fiscal. La interfaz ayuda a completar los datos, pero las reglas y los importes definitivos se resuelven en el backend”.

<!-- page -->
# 2 Tecnologías y responsabilidades

Cada tecnología cumple una función concreta. No hace falta memorizar sus definiciones de manera aislada: conviene asociarlas con una parte del recorrido de la factura.

| Tecnología | Dónde se usa | Para qué sirve en el proyecto |
|---|---|---|
| HTML y CSS | Interfaz | Definir formularios, tablas, diálogos y su presentación en distintas pantallas. |
| TypeScript | Navegador | Manejar eventos, validar, calcular una vista previa y consumir la API. |
| Vite | Desarrollo y build | Servir la interfaz durante el desarrollo y generar sus archivos publicables. |
| Java 26 y Spring Boot 4.1.2 | Backend | Iniciar el servidor, exponer rutas HTTP y organizar sus componentes. |
| Spring Data JPA e Hibernate | Persistencia | Trabajar con entidades Java y traducir sus cambios a operaciones de base de datos. |
| PostgreSQL | Ejecución habitual | Guardar los datos y aplicar relaciones y restricciones SQL. |
| Flyway | Esquema PostgreSQL | Aplicar las migraciones V1 a V6 de manera ordenada. |
| H2 | Pruebas y laboratorio | Proveer bases aisladas; el laboratorio fiscal usa una base H2 persistente. |
| PDFBox y ZXing | Impresión | Crear el PDF y generar el código QR cuando hay datos compatibles. |
| Bouncy Castle | Cliente WSAA | Firmar la solicitud de autenticación con el certificado y la clave. |
| Maven | Proyecto Java | Resolver dependencias, ejecutar pruebas y construir el JAR. |
| JUnit y Playwright | Verificación | Probar cálculos, flujos HTTP, persistencia e interacción en navegador. |

## Tres conceptos que conviene distinguir

Una biblioteca aporta funciones que el código invoca. Por ejemplo, PDFBox permite dibujar texto y tablas sobre un documento PDF.

Un framework organiza parte de la ejecución. Spring encuentra componentes como controladores y servicios, construye sus instancias y atiende solicitudes HTTP con ellos.

Una herramienta de construcción prepara el programa. Maven construye el backend y Vite compila la interfaz. TypeScript no se ejecuta directamente como fuente en el navegador: el build produce JavaScript y CSS.

Las versiones declaradas se encuentran en pom.xml y frontend/package.json. El frontend usa TypeScript sin React ni Angular. Spring Boot también sirve la interfaz compilada, por lo que la entrega puede ejecutarse desde un único JAR más su base de datos y configuración.

<!-- page -->
# 3 Arquitectura y organización de archivos

El sistema está organizado por responsabilidades. La interfaz presenta y recoge datos; el controlador atiende HTTP; el servicio decide si una operación es válida; el repositorio accede a los datos; Hibernate y la base concretan la persistencia.

```text
Navegador y TypeScript
        | HTTP con JSON
        v
Controller -> Service -> Repository
                            |
                            v
                      JPA e Hibernate
                            |
                            v
                        PostgreSQL
```

La respuesta vuelve en sentido inverso como un DTO. Un DTO es un objeto preparado para transportar datos. Permite elegir qué recibe el navegador, sin exponer directamente todas las relaciones de las entidades de la base.

## Dónde encontrar cada parte

Las rutas Java siguientes parten de src/main/java/ar/edu/utn/facturacion.

| Carpeta o archivo | Responsabilidad |
|---|---|
| frontend/index.html y frontend/src/styles | Estructura y estilo de la pantalla. |
| frontend/src/app.ts | Coordinación del listado, diálogos y operaciones. |
| frontend/src/invoice-form.ts | Estado del formulario y construcción de la solicitud. |
| controller | Rutas de facturas, productos y catálogos. |
| dto | Contratos de entrada y salida. |
| service | Reglas, cálculos, mapeo, impresión y circuito fiscal. |
| entity y repository | Modelo persistente y acceso a datos. |
| fiscal | Contrato del proveedor y cliente WSAA y WSFE. |
| exception | Errores de negocio y su traducción a HTTP. |
| src/main/resources/db/migration | Evolución del esquema mediante SQL. |

## Cómo arranca Java

FacturacionApplication contiene main y llama a SpringApplication.run. La anotación @SpringBootApplication habilita el arranque y la búsqueda de componentes. Cuando FacturaRestController necesita un FacturaService, Spring lo entrega a través del constructor. Esta técnica se llama inyección de dependencias.

La ventaja de separar capas aparece al modificar una regla. Por ejemplo, impedir editar una factura autorizada corresponde al servicio y a la entidad; cambiar el texto de un botón corresponde a la interfaz. El controlador puede conservar la misma ruta HTTP en ambos casos.

<!-- page -->
# 4 Recorrido completo al crear una factura

Supongamos que la persona selecciona un cliente, pesos argentinos y dos productos. Al presionar Crear factura ocurre lo siguiente.

1. InvoiceForm.payload lee los campos y los renglones. Valida el formulario y construye un FacturaRequestDTO de TypeScript.
2. app.ts llama a crearFactura del servicio TypeScript. http.ts convierte el objeto a JSON y ejecuta fetch hacia POST /api/facturas.
3. FacturaRestController recibe el JSON como FacturaRequestDTO de Java. La validación comprueba los campos requeridos.
4. FacturaService.crear vuelve a validar el contenido, impide que el cliente asigne el estado inicial y crea una FacturaVenta.
5. aplicarContenido busca cliente, condición, punto de venta y moneda. Por cada renglón busca el artículo y su precio para la moneda elegida.
6. CalculadoraImportes obtiene neto, IVA y total. FacturaDetalle conserva la descripción, el precio y la alícuota aplicados.
7. reemplazarDetalles vincula cada detalle con su factura y suma los importes. saveAndFlush guarda los cambios y adelanta la detección de errores SQL.
8. FacturaMapper transforma la entidad en un FacturaReporteDTO dentro de la transacción. Al finalizar correctamente el método transaccional se confirma la operación.
9. El controlador responde 201 Created y agrega Location con la ruta de la nueva factura. La interfaz cierra el modal y actualiza el listado conservando los filtros.

## Qué viaja por la red

Este ejemplo reducido representa un comprobante local. Los identificadores deben existir en los catálogos de la base utilizada.

```json
{
  "fecha": "2026-11-13",
  "clienteId": 1, "condicionIvaId": 1,
  "puntoVentaId": 1, "tipoMonedaId": 1,
  "detalles": [{ "articuloId": 1, "cantidad": 2 }]
}
```

No se envían precioUnitario, neto, IVA ni total como valores a aceptar. El navegador puede mostrarlos como ayuda, pero Java vuelve a obtenerlos y calcularlos. Tampoco se autoriza fiscalmente una factura por el hecho de crearla.

Si algo falla, la operación transaccional se revierte. La interfaz conserva el formulario y muestra el error para que la persona pueda corregirlo. El archivo examples/crear-factura.json contiene una solicitud más completa.

<!-- page -->
# 5 Cómo funciona la interfaz

frontend/src/main.ts importa el CSS y ejecuta iniciar de app.ts. Esa función conecta los eventos de botones y formularios y llama a bootstrap. bootstrap pide los catálogos, prepara los selectores y carga la primera página de facturas.

## Qué guarda cada parte

app.ts mantiene filtros activos, página, resultados y factura seleccionada. InvoiceForm conserva los renglones que la persona está cargando, la factura original cuando edita y el indicador dirty, que señala cambios pendientes. Esa separación permite actualizar el listado sin reconstruir todo el editor.

Al guardar, app.ts elige POST si no hay un ID en edición y PUT si lo hay. Mientras espera, busy bloquea acciones repetidas y el formulario se deshabilita. En caso de error se vuelven a habilitar los controles y los datos siguen disponibles. Al intentar salir con cambios pendientes, beforeunload solicita la confirmación propia del navegador.

Los exploradores de explorers.ts filtran los catálogos ya cargados. La búsqueda de clientes combina texto y documento. La de precios combina artículo, moneda e IVA. Sus filtros son independientes de los filtros del listado de facturas. Seleccionar un precio exige que su moneda coincida con la de la factura.

## Vista previa de importes

preview busca el precio de cada artículo en el catálogo y usa utils/importes.ts. Este módulo trabaja con bigint e importes escalados, como centavos, para reproducir el redondeo. La vista previa puede quedar desactualizada si el catálogo cambia en el servidor; por eso el mensaje de guardado utiliza el total confirmado por Java.

Los renglones nuevos usan cantidades enteras con mínimo 1 y paso 1. Si se abre una factura histórica con una cantidad fraccionaria, ese renglón conserva la posibilidad de trabajar con tres decimales. La API también admite cantidades decimales dentro de sus límites.

## Respuestas que llegan fuera de orden

Si la persona cambia rápidamente un filtro, dos búsquedas pueden estar en curso. queryVersion identifica cuál es la consulta más reciente. Cuando llega una respuesta anterior, app.ts la descarta y evita que reemplace el resultado nuevo. detailVersion aplica el mismo criterio al diálogo de consulta.

## Validación y presentación

Los atributos HTML como required, min y step ayudan a corregir la carga; validators.ts agrega reglas comunes. Los datos mostrados se escapan o se insertan mediante APIs del DOM para evitar que se interpreten como HTML. TypeScript ayuda durante el desarrollo, pero los servicios también verifican en ejecución la forma del JSON recibido. La distribución visual se adapta mediante CSS a escritorio y móvil.

<!-- page -->
# 6 Cómo funciona la API

Una API es el conjunto de operaciones que el backend ofrece al frontend. En este proyecto usa HTTP y, salvo la descarga de PDF y las respuestas sin contenido, intercambia JSON. El método y la ruta determinan qué controlador se ejecuta.

| Método y ruta | Qué hace | Respuesta habitual |
|---|---|---|
| GET /api/catalogos | Consulta los datos de los selectores. | 200 con JSON |
| POST /api/articulos | Crea producto y primer precio. | 201 con JSON |
| GET /api/facturas | Lista con filtros y paginación. | 200 con página |
| GET /api/facturas/{id} | Consulta una factura. | 200 con reporte |
| POST /api/facturas | Crea cabecera y detalles. | 201 y Location |
| PUT /api/facturas/{id} | Actualiza contenido permitido. | 200 con reporte |
| DELETE /api/facturas/{id} | Elimina un borrador permitido. | 204 sin cuerpo |
| POST /api/facturas/{id}/anular | Anula con motivo y auditoría. | 200 con reporte |
| POST /api/facturas/{id}/autorizar | Solicita autorización fiscal. | 200 si confirma |
| POST /api/facturas/{id}/conciliar | Consulta un envío pendiente. | 200 si confirma |
| GET /api/facturas/{id}/pdf | Descarga la impresión. | 200 application/pdf |

## Cómo leer el controlador

```java
@PostMapping
public ResponseEntity<FacturaReporteDTO> crear(
    @Validated(ValidacionCompleta.class)
    @RequestBody FacturaRequestDTO request) {
    FacturaReporteDTO resultado = servicio.crear(request);
    return ResponseEntity.created(
        URI.create("/api/facturas/" + resultado.id()))
        .body(resultado);
}
```

@PostMapping asocia el método con POST. @RequestBody convierte el JSON en el DTO. @Validated aplica el grupo de restricciones de una carga completa. El controlador delega en el servicio y construye la respuesta HTTP; no realiza el cálculo de los importes.

ManejadorExcepciones transforma solicitudes inválidas en 400, recursos inexistentes en 404 y conflictos de negocio o integridad en 409. http.ts interpreta esas respuestas y muestra mensajes de la API. Para fallos 500 utiliza un mensaje general y evita mostrar detalles internos al usuario.

<!-- page -->
# 7 Modelo de datos y relaciones

Una entidad es una clase Java asociada a una tabla. FacturaVenta representa factura_venta y FacturaDetalle representa factura_detalle. El identificador id es la clave primaria técnica; las claves foráneas conectan unas tablas con otras.

| Tabla | Qué representa | Relación principal |
|---|---|---|
| cliente | Nombre y documento del comprador. | Un cliente puede tener muchas facturas. |
| condicion_iva | Condiciones del catálogo. | Una condición puede usarse en muchas facturas. |
| punto_venta | Código y descripción del punto. | Un punto puede tener muchas facturas. |
| tipo_moneda | Código y descripción de moneda. | Se usa en facturas y precios. |
| articulo | Código, descripción y alícuota. | Un artículo puede tener precios en varias monedas. |
| lista_precio_articulo | Precio de un artículo en una moneda. | Un precio por combinación artículo y moneda. |
| factura_venta | Cabecera, importes y estados. | Una factura contiene varios detalles. |
| factura_detalle | Renglón y valores históricos. | Pertenece a una factura y referencia un precio. |
| operacion_fiscal | Intento, motivo y resultado fiscal. | Conserva la auditoría de la operación. |
| serie_fiscal y reserva_fiscal | Control de numeración y envío. | Coordinan operaciones de la misma serie. |

## Cómo se expresa en Java

@ManyToOne en FacturaVenta indica que muchas facturas pueden referenciar al mismo cliente, moneda o punto de venta. @OneToMany en detalles expresa la relación inversa entre factura y renglones. factura_id se almacena en el detalle.

cascade = CascadeType.ALL propaga las operaciones de persistencia de la factura a sus detalles. orphanRemoval = true permite retirar de la base los detalles que se quitan de la colección durante una edición. No significa que se borre el artículo del catálogo.

FetchType.LAZY permite cargar relaciones cuando se necesitan. Como open-in-view está deshabilitado, FacturaMapper construye el reporte dentro de una transacción, cuando esas asociaciones todavía pueden resolverse.

## Reglas también en la base

Las claves foráneas impiden referencias inexistentes. UNIQUE evita repetir documentos, códigos y la combinación artículo y moneda. Los CHECK exigen cantidades positivas, importes válidos y total igual a neto más IVA. Estas restricciones complementan las validaciones Java y protegen también frente a operaciones concurrentes.

El detalle guarda copias de precio, descripción y alícuota. El reporte del nombre y documento del cliente, en cambio, se obtiene de su entidad relacionada: no todos los datos del catálogo están congelados como una copia histórica.

<!-- page -->
# 8 Cálculos y conservación de precios

CalculadoraImportes concentra el cálculo monetario. Usa BigDecimal para representar decimales y redondea por renglón a dos decimales con HALF_UP. Después la factura suma los renglones ya redondeados.

```java
BigDecimal neto = cantidad.multiply(precio)
    .setScale(2, RoundingMode.HALF_UP);
BigDecimal iva = neto.multiply(alicuota)
    .divide(new BigDecimal("100"), 2,
            RoundingMode.HALF_UP);
return new Importes(neto, iva, neto.add(iva));
```

multiply multiplica cantidad por precio; setScale fija dos decimales; divide calcula el porcentaje con el redondeo indicado. HALF_UP hace que un valor positivo de 1,005 se redondee a 1,01. Es importante acordar dónde se redondea: sumar valores sin redondear y redondear una sola vez puede producir otro resultado.

## Ejemplo de comprobante local

| Renglón | Cantidad | Precio | Alícuota | Neto | IVA | Total |
|---|---|---|---|---|---|---|
| Cuaderno | 2 | 1.000,00 | 21 % | 2.000,00 | 420,00 | 2.420,00 |
| Lápiz | 3 | 100,00 | 21 % | 300,00 | 63,00 | 363,00 |
| Suma | | | | 2.300,00 | 483,00 | 2.783,00 |

El servidor busca el precio por artículo y moneda en ListaPrecioArticuloRepository. Si no existe esa combinación, rechaza la operación con un conflicto. La moneda no provoca una conversión automática: un precio en USD es otro registro del catálogo.

## Qué cambia en Factura C

Cuando tipoComprobante es "11", el precio de lista se toma como precio final. FacturaService aplica alícuota cero para el cálculo y guarda IVA cero, de manera que neto y total coinciden. Con los mismos precios y cantidades del ejemplo, el total sería 2.300,00. Es la representación del escenario implementado de Factura C sin IVA discriminado, no una regla que deba trasladarse a cualquier régimen fiscal.

## Qué significa conservar el precio histórico

Si un cuaderno se guardó a 1.000,00 y su precio de catálogo cambia después a 1.200,00, consultar o imprimir aquella factura conserva 1.000,00 porque FacturaDetalle almacena precioUnitario. También conserva su descripción y alícuota aplicadas.

La edición completa de una factura permitida vuelve a resolver los precios actuales y reconstruye sus detalles. Por lo tanto, consultar conserva la historia, mientras que guardar una edición completa puede recalcularla. Una factura autorizada no admite esa edición.

<!-- page -->
# 9 Transacciones y concurrencia

Una transacción reúne cambios que deben confirmarse juntos. La creación de una factura necesita guardar la cabecera y todos sus detalles; no sería correcto conservar la cabecera si falla el segundo renglón.

FacturaService usa @Transactional en los métodos que escriben. Si se produce un error que provoca rollback, se revierten los cambios de esa operación. ProductoService aplica el mismo criterio: crear un artículo y su primer precio es una sola unidad de trabajo. Si falla el precio, tampoco queda el artículo creado.

## Guardar no siempre significa confirmar

save registra la entidad en la unidad de persistencia. flush fuerza a sincronizar los cambios pendientes con la base y permite detectar restricciones SQL antes de construir la respuesta. El commit confirma la transacción al finalizar correctamente. Un flush por sí solo no impide que un fallo posterior revierta todo.

Al editar, Hibernate detecta cambios sobre la entidad administrada. Por eso actualizar puede modificarla y llamar a flush sin un segundo save explícito. El mecanismo se conoce como detección de cambios o dirty checking.

## Qué ocurre si llegan dos solicitudes a la vez

FacturaRepository.buscarParaActualizar utiliza un bloqueo PESSIMISTIC_WRITE. Mientras una transacción modifica esa factura, otra escritura sobre la misma entidad debe esperar o resolver el conflicto. Así se serializan decisiones como comprobar su estado y luego editarla o eliminarla.

FacturaVenta también tiene @Version. Hibernate mantiene un contador para detectar determinadas escrituras con versiones antiguas. Esto complementa los bloqueos, pero el PUT actual no exige que el navegador envíe la versión que vio al abrir el formulario. No conviene afirmar que el sistema siempre avisa si otra persona editó antes de guardar: esa comprobación explícita del cliente no forma parte del contrato actual.

En productos, la consulta previa de código repetido mejora el mensaje, pero dos solicitudes podrían superar esa consulta al mismo tiempo. La restricción UNIQUE de la base es la defensa final. El manejador traduce el conflicto y la transacción evita un alta parcial.

## Límites de una transacción local

Una llamada a ARCA ocurre en otro sistema. Deshacer una transacción SQL no deshace una autorización que el proveedor ya procesó. Por eso CircuitoFiscalService confirma primero un intento durable, realiza la llamada externa y después consolida el resultado en otra transacción. Si el resultado es dudoso, conserva el bloqueo y solicita conciliación.

Frase para la defensa: “La transacción garantiza la unidad de la operación local; la incertidumbre externa se maneja con registro durable, reserva y consulta del resultado”.

<!-- page -->
# 10 Estados y operaciones permitidas

El programa tiene dos dimensiones de estado. estado describe la situación administrativa: EMITIDA o ANULADA. estadoArca describe el circuito de autorización. Mantenerlas separadas permite representar, por ejemplo, una factura anulada que conserva su autorización original.

| Estado fiscal | Significado en el programa | Consecuencia principal |
|---|---|---|
| PENDIENTE | No tiene autorización confirmada. | Puede editarse si sigue emitida y no hay evidencia fiscal que la bloquee. |
| EN_PROCESO | Hay una operación externa registrada en curso. | Bloquea edición, eliminación y otro envío. |
| INCIERTA | No se pudo confirmar un resultado definitivo. | Requiere conciliar; no permite reenviar libremente. |
| RECHAZADA | La operación terminó con rechazo definitivo. | Si no existe autorización previa y sigue emitida, puede corregirse. |
| AUTORIZADA | Se confirmó la autorización. | Conserva los datos fiscales y bloquea la edición ordinaria. |

## ID técnico y número fiscal

id identifica la fila en la base. numero identifica el comprobante fiscal dentro de su punto de venta y tipo. Pueden tener valores distintos y el número puede ser null. Tener un número aislado tampoco prueba autorización: isInformadaArca comprueba estado autorizado o evidencias como código o fecha de autorización.

isFiscalBloqueada considera autorización previa, EN_PROCESO e INCIERTA. isEditable exige EMITIDA y ausencia de ese bloqueo. FacturaMapper informa editable y eliminable a la interfaz, y Java vuelve a comprobar las condiciones cuando recibe la operación.

## Eliminar y anular

Eliminar ejecuta DELETE físico y solo se permite si la factura es editable y no tiene número. Sus detalles se eliminan con ella. Si tuvo operaciones rechazadas, se desvincula la referencia de auditoría, pero se conserva el identificador original registrado en ella.

Anular exige un motivo. Si la factura no está informada y no tiene un envío incierto, se marca ANULADA y se registra una operación ANULAR_LOCAL. La cabecera y los renglones permanecen. No se permite reactivarla ni modificarla después.

Si estaba autorizada, la anulación requiere confirmar un ajuste fiscal asociado. En el adaptador implementado es una Nota de Crédito C. Si el ajuste falla o queda incierto, no se cambia arbitrariamente el estado a ANULADA.

La ruta para anular es POST /api/facturas/{id}/anular. Un PUT con estado ANULADA se rechaza para mantener un único circuito auditado. Repetir una anulación ya confirmada devuelve su situación actual y no crea otra anulación.

<!-- page -->
# 11 Integración con ARCA en homologación

La interfaz ArcaGateway define el contrato que usa CircuitoFiscalService. El servicio conoce operaciones como ejecutar y consultar; el adaptador WsfeHomologacionGateway implementa los mensajes concretos. Así se separan las reglas del sistema de los detalles del proveedor.

Cuando arca.homologacion.enabled está deshabilitado, ArcaConfig proporciona un adaptador que rechaza el envío antes de registrar una operación fiscal. Al habilitarlo, Spring utiliza el cliente de homologación. La configuración normal parte del envío deshabilitado.

## Autenticación con WSAA

WsaaHomologacion lee el certificado y la clave privada locales, comprueba el certificado de pruebas y obtiene su CUIT. Construye una solicitud de acceso al servicio wsfe, la firma en formato CMS mediante Bouncy Castle y solicita un ticket a WSAA.

El ticket contiene Token y Sign, que sirven para autenticar las llamadas posteriores. Se reutiliza mientras siga vigente y corresponda al mismo certificado, identificado mediante su huella SHA-256. Las claves y el ticket se guardan fuera del código distribuido y no deben mostrarse durante la exposición.

## Autorización con WSFE

1. CircuitoFiscalService bloquea y revisa la factura. Verifica configuración, tipo de comprobante y reglas del escenario.
2. Guarda una OperacionFiscal con un UUID y marca EN_PROCESO. Confirma esa transacción antes del envío.
3. ReservaFiscalService coordina la serie por ambiente, CUIT, punto y tipo. Consulta el último autorizado y reserva el siguiente número de forma durable.
4. El adaptador envía FECAESolicitar con documento, fecha, moneda e importes. El escenario usa tipo 11, concepto productos y pesos con cotización 1.
5. Se verifica que la respuesta corresponda a la reserva y contenga una autorización completa. Después se guardan número, CAE, vencimiento, ambiente y fecha de autorización.

CAE significa Código de Autorización Electrónico. El programa no lo inventa ni lo deduce del ID; lo toma de la respuesta confirmada del proveedor.

## Cómo se anula una autorizada

El adaptador solicita el tipo 13, Nota de Crédito C, y agrega la referencia al comprobante original mediante CbtesAsoc. Solo al confirmar el ajuste asociado se marca la factura como ANULADA. La factura conserva su número y CAE originales; los datos del ajuste quedan registrados en la operación y la reserva.

SoapArca realiza el transporte HTTPS y el procesamiento XML, con destinos de homologación, tiempos de espera y protección frente a entidades XML externas.

<!-- page -->
# 11 Continuación Qué pasa si la respuesta es incierta

El caso más importante para comprender esta integración es una caída de conexión. Supongamos que Java envía la solicitud y se agota el tiempo de espera. ARCA podría haber autorizado el comprobante aunque el programa no haya recibido la respuesta.

Si el sistema tratara ese caso como un rechazo seguro y volviera a emitir, podría producir un duplicado. Por eso diferencia rechazo definitivo de incertidumbre.

## Conciliar significa consultar

Ante un fallo de comunicación o una respuesta incompleta, CircuitoFiscalService marca INCIERTA y conserva el intento. La factura queda bloqueada. La acción Conciliar usa la misma operación y la reserva existente para llamar a FECompConsultar. No vuelve a ejecutar FECAESolicitar.

La consulta compara número, punto, tipo, fecha, documento, moneda e importes con lo esperado. En un ajuste verifica también la asociación al original. Esta comprobación evita aceptar por error la autorización de otro comprobante.

Si el proveedor aún no devuelve un resultado, esa ausencia no demuestra que la solicitud anterior no vaya a procesarse. Por eso el sistema mantiene la incertidumbre. Una respuesta completa y definitiva permite consolidar la autorización o el rechazo.

## Concurrencia y numeración

Una misma factura no puede iniciar libremente otro envío si tiene uno EN_PROCESO o INCIERTA. Además, la tabla serie_fiscal permite una sola operación activa por serie. La reserva se identifica por UUID y permanece disponible tras reiniciar el proceso.

Un rechazo definitivo libera la serie. Una incertidumbre conserva la reserva. Si falla la consulta del último número antes de solicitar el CAE y el emisor confirma que no hubo envío, el sistema admite un reintento de la operación.

## Un límite real que conviene conocer

Si el proceso termina entre guardar el intento fiscal y confirmar su reserva, puede quedar una operación pendiente cuya reserva no aparezca. La conciliación conserva el bloqueo y ese caso puede necesitar revisión por UUID. El laboratorio no lo desbloquea automáticamente.

Esto muestra por qué no alcanza con tener un botón Reintentar. En una operación externa que puede generar un comprobante, hace falta conocer si hubo emisión y a qué intento pertenece.

## Homologación y evidencia

La documentación operativa registra una autorización y una anulación reales en homologación de una Factura C por ARS 1.000,00. Las pruebas automáticas habituales usan dobles del proveedor y bases aisladas; no son nuevas emisiones externas. Durante una presentación se puede mostrar esa evidencia histórica sin solicitar otro comprobante.

<!-- page -->
# 12 Generación del PDF

FacturaPdfService genera la impresión en el backend con Apache PDFBox. La ruta GET /api/facturas/{id}/pdf obtiene los datos persistidos y devuelve bytes con Content-Type application/pdf y Content-Disposition para la descarga.

Generar la impresión no autoriza la factura, no recalcula el catálogo ni llama a ARCA. Una factura pendiente puede tener PDF y seguir sin autorización fiscal.

## De dónde sale cada dato

Los datos del emisor se leen desde EmisorFactura y sus propiedades de configuración. La cabecera y los importes provienen del reporte de factura. Los renglones utilizan precio, descripción y alícuota guardados en FacturaDetalle. El domicilio del cliente y la condición de venta son campos guardados por comprobante desde V6.

El nombre y documento del cliente se obtienen de su relación con Cliente. La impresión del ajuste de anulación consulta la operación fiscal aprobada, manteniendo su autorización separada de la factura original.

## Diseño de la impresión

El documento A4 muestra la identificación del comprobante, fecha, punto de venta y número cuando existe, emisor, receptor, artículos, cantidades, precios e importes. Para Factura C utiliza la letra C y el código 011. El número se presenta con cinco dígitos para el punto y ocho para el comprobante.

Los importes se formatean con separadores argentinos. La tabla se divide en páginas cuando hace falta, repite información de cabecera y mantiene la paginación y los avisos de estado. Los totales y observaciones deben seguir apareciendo aunque existan muchos renglones.

## Cómo refleja el estado

- Pendiente: muestra que no tiene validez fiscal y no inventa un código de autorización.
- Autorizada en homologación: muestra la autorización guardada y el aviso HOMOLOGACIÓN - SIN VALIDEZ FISCAL.
- Anulada: agrega el estado, motivo y fecha; si hubo ajuste fiscal, distingue sus datos de la autorización original.

FacturaQr prepara los datos del QR y ZXing genera su representación. Solo se produce cuando existe autorización y están completos los campos que admite esta implementación. Se usan los datos persistidos; un QR de homologación se identifica como prueba y no convierte el comprobante en uno de producción.

## Qué mostrar al profesor

Conviene descargar una factura pendiente y compararla con el comprobante histórico de homologación. La diferencia visible debe corresponder a sus estados guardados, especialmente número, CAE, vencimiento y anulación. El hecho de tener una impresión con aspecto de factura no reemplaza la autorización.

<!-- page -->
# 13 Mejoras implementadas y su motivo

Las mejoras se entienden mejor al relacionar el problema que resuelven con la parte del código que cambió. Los puntos siguientes describen el comportamiento de la versión actual; la tabla no pretende reconstruir un orden cronológico completo.

| Necesidad o problema | Cómo se resolvió | Dónde observarlo |
|---|---|---|
| Encontrar comprobantes sin cargar todo el listado. | Filtros combinables y paginación en el servidor, con orden estable por ID descendente. | FacturaService.listar y app.ts. |
| Crear y editar con el mismo recorrido. | Modal compartido y editor con estado propio; conserva datos ante errores. | InvoiceForm y saveInvoice. |
| Evitar precios manipulados desde el navegador. | La solicitud solo incluye artículo y cantidad; Java busca el precio y calcula. | aplicarContenido y CalculadoraImportes. |
| Conservar importes al cambiar el catálogo. | Copias históricas en cada detalle. | FacturaDetalle. |
| Dar de alta un artículo durante una venta. | Producto y primer precio en una transacción; actualización de catálogos sin perder borrador. | ProductoService, ProductForm y Explorers. |
| Distinguir borrado y anulación. | DELETE restringido a borrador; anulación explícita con motivo y auditoría. | FacturaService y CircuitoFiscalService. |
| Evitar envíos repetidos ante demoras. | Intento durable, reserva por serie, bloqueo y conciliación. | CircuitoFiscalService y ReservaFiscalService. |
| Recuperar la pantalla tras errores de carga. | Reintento de catálogos sin duplicar listeners ni descartar el editor. | bootstrap en app.ts. |
| Respetar cantidades ya guardadas. | Unidades enteras nuevas y soporte fraccionario en renglones históricos. | renderLines en InvoiceForm. |
| Imprimir información comercial completa. | Domicilio y condición de venta persistidos; PDF paginado con estados y autorizaciones separadas. | Migración V6 y FacturaPdfService. |

## Dos ajustes de contrato que conviene poder explicar

obtenerPaginaFacturas devuelve el objeto paginado que usa la pantalla. obtenerFacturas mantiene una función que devuelve un arreglo: cuando no se pide una página explícita, recorre las páginas disponibles. Son contratos diferentes y no deben confundirse.

actualizarEstado en el servicio TypeScript deriva la anulación hacia anularFactura y exige motivo. Para observaciones de una factura emitida utiliza el PUT permitido. Así el cliente respeta el circuito auditado definido por el backend.

<!-- page -->
# 14 Ejecución y compilación

La ejecución habitual requiere Java 26 y PostgreSQL. Maven permite construir desde las fuentes. Para desarrollar o reconstruir el frontend se necesita Node.js 22.12 o posterior y npm. Los comandos siguientes se ejecutan desde la raíz del proyecto salvo que se indique cd frontend.

## Ejecutar la aplicación local preparada

```powershell
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1
```

El primer script inicia el clúster exclusivo del proyecto. El segundo inicia el programa. La dirección habitual es http://127.0.0.1:8080. Si se usa otro PostgreSQL, se configura la conexión correspondiente mediante los scripts y variables del README.

application.properties usa DB_URL, DB_USER y DB_PASSWORD. El archivo privado .local/conexion.ps1 conserva la configuración local y no se comparte. El envío fiscal está deshabilitado por defecto.

## Desarrollar la interfaz

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Vite sirve en http://127.0.0.1:5173 y reenvía /api al backend en 8080. Spring debe estar iniciado. Durante el desarrollo hay dos servidores, pero el navegador utiliza rutas /api de su mismo origen gracias al proxy.

## Construir la entrega

```powershell
cd frontend
npm.cmd run build
cd ..
.\scripts\maven.ps1 package
```

El build verifica TypeScript, genera frontend/dist y publicar.mjs copia los recursos a src/main/resources/static. Maven los incluye en target/facturacion-1.0.0.jar. La fuente editable del frontend sigue estando en frontend/src y frontend/index.html. No conviene editar los archivos generados para introducir cambios permanentes.

Los lanzadores prefieren el JAR de target. En la entrega extraída utilizan dist/facturacion-1.0.0.jar. Cambiar una fuente sin reconstruir puede dejar ejecutándose la versión anterior del JAR.

## Laboratorio fiscal configurado

scripts/ejecutar-homologacion.ps1 inicia normalmente en 8081 con una base H2 persistente exclusiva y credenciales privadas locales. En ese laboratorio Flyway está deshabilitado y Hibernate actualiza el esquema. Es una configuración distinta de PostgreSQL, donde Flyway migra y Hibernate valida. No se deben iniciar dos procesos sobre el mismo archivo H2.

<!-- page -->
# 14 Continuación Migraciones y diagnóstico

Flyway conserva un historial de cambios de esquema. En PostgreSQL aplica cada migración pendiente en su orden y registra su ejecución. Esto permite que dos instalaciones alcancen una estructura conocida sin depender de cambios manuales en las tablas.

| Migración | Qué incorpora |
|---|---|
| V1 | Tablas del dominio, relaciones, restricciones e índices iniciales. |
| V2 | Datos de demostración. |
| V3 | Actualización de nombres y etiquetas del catálogo y datos de demostración. |
| V4 | Campos de autorización, estado fiscal y auditoría de operaciones. |
| V5 | Datos fiscales del receptor, ambiente, series y reservas. |
| V6 | Domicilio del cliente y condición de venta por factura. |

spring.jpa.hibernate.ddl-auto=validate indica que Hibernate verifica la correspondencia del modelo con la base habitual. La creación y evolución de esa estructura corresponden a Flyway. No se deben modificar migraciones que ya se aplicaron como forma habitual de introducir un nuevo cambio: se agrega una migración posterior.

## Cómo ubicar un problema

Si el navegador muestra falta de conexión, primero hay que comprobar que Spring inició y que se está usando el puerto correcto. En desarrollo también debe funcionar el proxy de Vite. Si el backend no inicia, el registro puede indicar un problema de conexión o validación de esquema.

Si una factura da un error de precio, se revisa la combinación de artículo y moneda; el sistema no convierte monedas. Si el listado queda vacío, hay que observar los filtros antes de suponer que se perdieron datos. Si una factura no permite edición, se consultan sus estados administrativo y fiscal.

Un conflicto 409 no siempre significa un fallo técnico: también informa que una regla impide la acción. Por ejemplo, querer borrar una factura numerada o modificar una autorizada.

Si se cambió la interfaz pero el JAR muestra la anterior, se reconstruye frontend y luego se empaqueta de nuevo. La cadena es fuente TypeScript, build de Vite, copia a static y empaquetado Java.

## Preparación antes de la exposición

Probar el arranque en el equipo que se usará, comprobar los catálogos y abrir una factura. Mantener disponible el documento y la evidencia histórica de homologación. Una demostración local de CRUD no necesita crear nuevas autorizaciones externas.

Para mostrar la estructura puede utilizarse sql/consultas.sql. La base habitual y la de pruebas deben estar diferenciadas. Las pruebas PostgreSQL que limpian datos requieren una base exclusiva terminada en _test.

<!-- page -->
# 15 Pruebas y evidencias

Probar no consiste solo en comprobar que se abre una pantalla. Cada nivel responde una pregunta diferente: si el cálculo es correcto, si se respetan las reglas, si la base revierte una operación incompleta y si una persona puede completar el recorrido en el navegador.

| Prueba o archivo | Qué comprueba |
|---|---|
| CalculadoraImportesTest | Porcentajes, redondeo por renglón y alícuota cero. |
| FacturaFlujosTest | Filtros, estados, autorización, anulación, errores y concurrencia con un doble fiscal. |
| FacturaIntegracionTest | Persistencia real en PostgreSQL, Flyway, restricciones, precios históricos y rollback. |
| ProductoFlujosTest | Alta conjunta, normalización del código, duplicados, concurrencia y reversión. |
| FacturaListadoTest | Que el listado con detalles no consulte una vez adicional por cada factura. |
| WsfeHomologacionTest | Firma y ticket, reservas, consultas, rechazo y ajuste con transporte controlado. |
| FacturaPdfTest | Datos de impresión, QR decodificable, estados y documentos largos. |
| frontend/tests | Contratos HTTP, validaciones, filtros, productos y vista previa. |
| frontend/tests/browser/crud.spec.ts | Recorridos de la interfaz con Playwright. |

## Cómo se ejecutan

```powershell
cd frontend
npm.cmd test
npm.cmd run build
cd ..
.\scripts\maven.ps1 test
```

La suite habitual utiliza H2 y dobles para aislar la prueba del proveedor externo. Las pruebas específicas de PostgreSQL requieren TEST_DB_URL y la configuración de una base exclusiva terminada en _test; se omiten cuando no está configurada. scripts/probar.ps1 prepara la ejecución con esa conexión. Los recorridos de navegador se habilitan con RUN_BROWSER_TESTS=true y los requisitos indicados en el README.

Un doble es una implementación controlada que responde como el servicio externo para ensayar casos reproducibles. Por ejemplo, puede simular que hubo autorización pero se perdió la respuesta. Así se verifica que conciliar no genere otro envío.

## Qué evidencia existe

AUDITORIA_TP.md registra una validación de cierre con 54 casos JUnit, 19 pruebas frontend y seis recorridos Playwright, además de la verificación del frontend servido por el JAR. Son resultados documentados de esa auditoría, no una nueva ejecución realizada al redactar esta guía.

docs/HOMOLOGACION_OPERATIVA.md registra la autorización y el ajuste reales en homologación. Conviene distinguir la evidencia externa histórica de las pruebas automáticas con dobles. Ambas aportan información diferente.

<!-- page -->
# 16 Guion para presentar al profesor

Una exposición de unos diez minutos puede combinar una explicación breve, una operación visible y la lectura de los archivos que la hacen posible. Conviene practicar el recorrido con los datos disponibles del equipo.

| Tiempo aproximado | Qué mostrar | Qué explicar |
|---|---|---|
| 0 a 1 minuto | Pantalla inicial. | Problema, alcance y diferencia entre gestión local y homologación. |
| 1 a 2 minutos | Arquitectura y carpetas. | Camino de la solicitud y separación de responsabilidades. |
| 2 a 4 minutos | Crear factura con dos renglones. | Cabecera, detalles, vista previa y total confirmado. |
| 4 a 5 minutos | Controller, Service y calculadora. | HTTP, validación, precios del servidor y transacción. |
| 5 a 6 minutos | Consulta, filtro y edición permitida. | Paginación, precio histórico y recálculo al editar. |
| 6 a 7 minutos | PDF y anulación local con motivo. | Diferencia entre imprimir, eliminar y anular. |
| 7 a 8 minutos | Comprobante histórico de homologación. | WSAA, WSFE, CAE y nota de crédito asociada. |
| 8 a 10 minutos | Pruebas y preguntas. | Rollback, restricciones y conciliación sin reenvío. |

## Apertura sugerida

“El trabajo es un sistema web de facturación de ventas. Permite cargar una cabecera y varios artículos, obtener importes calculados por el servidor, consultar comprobantes e imprimirlos. La persistencia está en una base relacional y la aplicación está organizada en capas. Además incorpora un laboratorio de Factura C con ARCA”.

## Cómo narrar la creación

“Cuando presiono Crear, TypeScript construye un JSON con los identificadores y las cantidades. El controlador recibe la solicitud y delega al servicio. El servicio verifica las referencias, busca los precios de esa moneda, calcula cada renglón y guarda todo en una transacción. El reporte vuelve como DTO y la pantalla muestra el total confirmado”.

## Cómo narrar la integración fiscal

“Guardar no significa informar a ARCA. La autorización registra primero un intento y una reserva. Si se confirma, guarda el CAE y bloquea la edición. Si se pierde la respuesta, consulta el resultado del mismo intento. Una factura autorizada se anula mediante una nota de crédito asociada, conservando la autorización original”.

## Una demostración de error útil

Intentar una cantidad inválida permite mostrar la validación. Un artículo sin precio para la moneda permite explicar un conflicto de negocio. Para demostrar un fallo de base después de haber empezado a guardar, conviene mostrar la prueba de rollback en lugar de alterar la base utilizada en la exposición.

<!-- page -->
# 17 Preguntas de defensa

## Por qué se usan DTO y no se devuelven las entidades

El DTO define qué datos viajan por la API. Evita acoplar el JSON al modelo JPA y permite resolver relaciones dentro de la transacción. FacturaMapper prepara el reporte con los importes y las acciones permitidas.

## Por qué calcular en el backend si la pantalla ya muestra el total

Porque la pantalla es una ayuda de carga y sus datos pueden modificarse o quedar desactualizados. El servidor obtiene el precio del catálogo y aplica la regla definitiva. El JSON de entrada no decide sus propios importes.

## Qué garantiza Transactional

Que la operación local se confirma o se revierte como una unidad bajo sus reglas transaccionales. En este caso evita conservar media factura o un artículo sin su primer precio. No revierte una autorización ya procesada por un sistema externo.

## Qué diferencia hay entre JPA e Hibernate

JPA define las APIs y anotaciones de persistencia. Hibernate es la implementación que usa el proyecto para administrar entidades y ejecutar las operaciones contra la base. Spring Data JPA agrega repositorios y consultas sobre ese mecanismo.

## Por qué BigDecimal y qué significa HALF_UP

BigDecimal representa decimales sin depender de aproximaciones binarias de double para el cálculo monetario. HALF_UP define cómo resolver la mitad al redondear. Aquí se redondean neto e IVA de cada renglón a dos decimales y luego se suman.

## Qué pasa si cambia un precio después de vender

Consultar o imprimir usa la copia guardada en el detalle. Una edición completa permitida vuelve a calcular con precios actuales. Las autorizadas están bloqueadas para impedir esa modificación.

## Por qué una factura emitida puede no tener número

Porque EMITIDA es su estado administrativo interno. El ID existe al guardarla; el número fiscal se obtiene por el circuito fiscal. Sin autorización confirmada no se debe presentarla como autorizada.

## Qué diferencia hay entre borrar y anular

Borrar elimina un borrador permitido y sus detalles. Anular conserva el comprobante, agrega fecha y motivo y registra la operación. Si había autorización, se exige un ajuste asociado aprobado.

## Qué pasa si no llega la respuesta de ARCA

El sistema no presupone que hubo rechazo. Marca incertidumbre, bloquea nuevos cambios y consulta el mismo comprobante reservado. Reenviar sin conocer el resultado anterior puede duplicar una emisión.

<!-- page -->
# 17 Continuación Preguntas y mapa de lectura

## La aplicación funciona sin conexión a ARCA

Sí, el circuito local funciona con el backend y su base. La integración externa se habilita expresamente. La impresión de una factura guardada tampoco realiza una llamada fiscal.

## Por qué hay PostgreSQL y H2

PostgreSQL es la base habitual y tiene su esquema versionado por Flyway. H2 facilita pruebas aisladas y el laboratorio usa un archivo persistente propio. Probar con H2 no reemplaza comprobar restricciones y migraciones reales en PostgreSQL.

## Qué evita las duplicaciones concurrentes

El formulario bloquea envíos repetidos como ayuda de uso. En el backend existen transacciones, bloqueos sobre la factura y restricciones UNIQUE. Para la emisión externa también se conserva un UUID, una reserva durable y una sola operación activa por serie. Son protecciones complementarias.

## Qué limitaciones tiene la entrega

Es una demostración local sin autenticación. El adaptador fiscal cubre el escenario de Factura C de homologación descrito y no implementa todos los tipos de comprobantes ni un entorno de producción. Una interrupción entre intento y reserva puede necesitar revisión. El PUT no exige una versión de cliente para detectar un formulario abierto antes de otra edición.

## Orden recomendado para abrir el código

1. frontend/src/main.ts y app.ts: ubicar iniciar, bootstrap y saveInvoice.
2. frontend/src/invoice-form.ts: seguir payload, preview y renderLines.
3. frontend/src/services/FacturaService.ts y http.ts: ver el paso de objeto a HTTP.
4. controller/FacturaRestController.java: relacionar rutas y métodos.
5. dto/FacturaRequestDTO.java y FacturaDetalleRequestDTO.java: revisar lo que acepta la API.
6. service/FacturaService.java y CalculadoraImportes.java: seguir crear y aplicarContenido.
7. entity/FacturaVenta.java y FacturaDetalle.java: observar relaciones, sumas e historia.
8. repository/FacturaRepository.java y service/FacturaMapper.java: ver acceso, bloqueo y reporte.
9. service/CircuitoFiscalService.java y ReservaFiscalService.java: seguir envío y conciliación.
10. fiscal/WsaaHomologacion.java, WsfeHomologacionGateway.java y SoapArca.java: ver autenticación, mensajes y transporte.
11. service/FacturaPdfService.java y FacturaQr.java: revisar la impresión.
12. src/test/java y frontend/tests: elegir una prueba de cálculo, una de rollback y una de conciliación.

Las rutas Java de esta lista, cuando no incluyen src/main, parten de src/main/java/ar/edu/utn/facturacion. No hace falta memorizar todos los métodos: el objetivo es poder ubicar una responsabilidad y seguir el dato que entra y sale.

<!-- page -->
# Referencias del proyecto y vocabulario

## Documentos y archivos de referencia

- README.md: ejecución, contrato de API y funcionamiento vigente.
- AUDITORIA_TP.md: matriz de requisitos, correcciones y resultados registrados de la auditoría.
- docs/HOMOLOGACION_OPERATIVA.md: escenario fiscal, configuración y evidencia de homologación.
- pom.xml y frontend/package.json: versiones, dependencias y comandos de construcción.
- src/main/resources/application.properties: configuración habitual de Spring.
- src/main/resources/db/migration/V1__estructura.sql a V6__datos_comerciales_factura.sql: esquema y evolución de datos.
- examples/crear-factura.json, actualizar-factura.json, anular-factura.json y crear-producto.json: ejemplos de solicitudes.
- Código Java en src/main/java/ar/edu/utn/facturacion y TypeScript en frontend/src: implementación de los recorridos explicados.
- Pruebas en src/test/java y frontend/tests: escenarios de verificación.

## Vocabulario de consulta

| Término | Significado aplicado al trabajo |
|---|---|
| Frontend | Interfaz que se ejecuta en el navegador. |
| Backend | Servidor que valida, calcula, consulta y persiste. |
| Endpoint | Combinación de método HTTP y ruta que ofrece una operación. |
| JSON | Formato de datos usado entre la interfaz y la API. |
| DTO | Objeto que define los datos de entrada o salida. |
| Entidad | Clase asociada a una tabla mediante JPA. |
| Repositorio | Componente que consulta y persiste entidades. |
| Transacción | Unidad local de cambios que se confirma o revierte. |
| Rollback | Reversión de los cambios de una transacción. |
| Migración | Cambio versionado de la estructura o los datos de la base. |
| Asíncrono | Trabajo cuyo resultado se espera sin completar inmediatamente el flujo. |
| Conciliación | Consulta para confirmar el resultado de una operación fiscal anterior. |
| Idempotencia | Repetir una operación conserva su efecto sin duplicarlo, donde está implementada. |
| UUID | Identificador que permite reconocer un intento fiscal. |
| JAR | Archivo ejecutable que reúne la aplicación Java y sus recursos. |
| Homologación | Entorno externo de pruebas usado por este laboratorio. |

Para comprobar que entendiste el programa, intentá explicar sin leer el recorrido de crear una factura, qué ocurre si falla un detalle y por qué una respuesta fiscal incierta requiere consulta. Después ubicá en el código las clases que implementan cada respuesta.
