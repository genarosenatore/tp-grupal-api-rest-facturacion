# 1. Comprender el problema

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Este manual enseña el backend que acompaña a esta entrega. Conviene recorrer primero los
capítulos 1 a 3, ejecutar la demostración del capítulo 10 y volver al código con los capítulos
4 a 9. Los ejercicios y la defensa sirven para comprobar que puede explicar las decisiones,
no solamente repetir comandos.

El TP solicita DTOs de entrada, servicios transaccionales y endpoints de escritura para
facturas de venta. También prevé un frontend en HTML y TypeScript; por decisión de alcance
ese frontend queda para otra etapa. En esta entrega se implementan además los componentes
que el enunciado suponía existentes: entidades, catálogos, repositorios y consultas GET.

Una factura representa una operación con un cliente, una fecha, un punto de venta, una
condición de IVA y una moneda. Tiene uno o más detalles: qué artículo se vendió y en qué
cantidad. El backend consulta precios e IVA configurados, calcula los importes y guarda
cabecera y renglones como una unidad coherente.

El sistema permite crear, consultar, filtrar, actualizar y anular. La actualización completa
reemplaza la cabecera editable y todos los detalles. La anulación conserva el documento y
sus importes, registra un instante y bloquea cambios posteriores. Un cliente HTTP puede
ser PowerShell, un programa de pruebas o el futuro frontend.

No es un emisor fiscal: el identificador es técnico y los porcentajes de demostración no
constituyen asesoramiento tributario. No hay autorización externa, descuentos, conversión
entre monedas, stock ni autenticación. Estas ausencias son decisiones de alcance; no
deben confundirse con funcionalidades prometidas.

## Decisiones que debe poder defender

Elegimos Java 26 porque está instalado y lo usamos tanto para compilar como para ejecutar.
Spring Boot 4.1.2 administra versiones compatibles. PostgreSQL 18.6 es la BD real de las
pruebas; no se reemplaza por una BD en memoria. Maven resuelve las dependencias.

ListaPrecioArticulo tiene un precio vigente por par artículo/moneda. El IVA de ejemplo se
guarda en Articulo. CondicionIva clasifica la cabecera, pero no altera automáticamente ese
IVA: el TP no define cómo hacerlo. Cada detalle copia precio, descripción y alícuota para
conservar lo aplicado aunque cambie un catálogo.

Usamos BigDecimal y redondeo HALF_UP a dos decimales por renglón. Elegimos anulación
lógica y DELETE idempotente: repetir la baja no crea otra fecha. Las facturas anuladas
siguen disponibles en GET. Todos estos supuestos también están en ANALISIS_REQUISITOS.md.

## Qué contará como evidencia

Compilar prueba que el código es válido para el compilador; no prueba que PostgreSQL
acepte una operación. Por eso verificamos HTTP real, filas persistidas, FK y rollback.
Los resultados ejecutados se registran en VERIFICACION.md. Si una prueba aparece omitida,
no debe presentarse como aprobada.

# 2. Conceptos fundamentales

Cada concepto sigue la secuencia qué es, para qué sirve, cómo funciona y ejemplo del TP.

## Backend

**Qué es.** La parte del programa que atiende solicitudes y aplica reglas en el servidor.

**Para qué sirve.** Centraliza cálculos, validaciones y acceso a datos.

**Cómo funciona.** Recibe HTTP, ejecuta Java y responde JSON; el proceso permanece activo entre solicitudes.

**Ejemplo aplicado.** FacturaService calcula el importe de una factura y lo guarda en PostgreSQL.

## Frontend

**Qué es.** La parte con la que interactúa una persona, normalmente en un navegador.

**Para qué sirve.** Permite ingresar datos y leer resultados.

**Cómo funciona.** Envía solicitudes al backend y representa la respuesta. Una pantalla no sustituye la persistencia del servidor.

**Ejemplo aplicado.** Se postergó: PowerShell hace el papel de cliente para demostrar el TP.

## Cliente y servidor

**Qué es.** Dos roles de comunicación: el cliente inicia la solicitud y el servidor la atiende.

**Para qué sirve.** Separan quién pide una operación de quién la ejecuta.

**Cómo funciona.** El cliente abre una conexión HTTP y recibe un código y un cuerpo. Ambos pueden estar en la misma PC.

**Ejemplo aplicado.** Invoke-RestMethod es cliente; Tomcat con Spring Boot es servidor en 127.0.0.1:8080.

## API

**Qué es.** Un contrato de operaciones y datos con el que otro programa puede interactuar.

**Para qué sirve.** Permite usar una función sin conocer sus detalles internos.

**Cómo funciona.** Define entradas, salidas y errores. Cambiar un nombre JSON puede romper clientes aunque el Java compile.

**Ejemplo aplicado.** POST /api/facturas recibe FacturaRequestDTO y devuelve FacturaReporteDTO.

## API REST

**Qué es.** Un estilo de API que organiza recursos y usa semántica HTTP.

**Para qué sirve.** Hace predecible cómo consultar y modificar recursos.

**Cómo funciona.** La URI identifica facturas; el método expresa la operación. Una solicitud debe contener lo necesario para procesarla.

**Ejemplo aplicado.** GET consulta; POST crea; PUT actualiza; DELETE anula según el contrato documentado.

## HTTP

**Qué es.** El protocolo de solicitudes y respuestas que transporta nuestra API.

**Para qué sirve.** Comunica cliente y servidor de forma estandarizada.

**Cómo funciona.** Una petición tiene método, URI, cabeceras y cuerpo opcional. La respuesta tiene estado, cabeceras y cuerpo opcional.

**Ejemplo aplicado.** POST devuelve 201 y Location; DELETE devuelve 204 sin JSON.

## JSON

**Qué es.** Un formato textual de objetos, listas, números, booleanos y null.

**Para qué sirve.** Intercambia datos entre lenguajes diferentes.

**Cómo funciona.** El convertidor HTTP transforma el texto JSON en un DTO Java y el DTO de salida en texto JSON.

**Ejemplo aplicado.** clienteId es un número y detalles es una lista de objetos con articuloId y cantidad.

## Base de datos relacional

**Qué es.** Un sistema de datos organizado en tablas relacionadas mediante claves.

**Para qué sirve.** Conserva información y protege relaciones válidas.

**Cómo funciona.** Cada fila representa un registro; las FK enlazan filas de distintas tablas y rechazan referencias inexistentes.

**Ejemplo aplicado.** factura_detalle.factura_id referencia factura_venta.id.

## SQL

**Qué es.** El lenguaje para definir, consultar y modificar datos relacionales.

**Para qué sirve.** Permite crear tablas e inspeccionar lo que realmente se guardó.

**Cómo funciona.** CREATE define estructura; SELECT lee; INSERT crea; UPDATE modifica; DELETE elimina filas.

**Ejemplo aplicado.** SELECT id,total FROM factura_venta muestra los importes persistidos.

## PostgreSQL

**Qué es.** El motor de base de datos relacional usado por el proyecto.

**Para qué sirve.** Ejecuta SQL, conserva datos y administra transacciones e integridad.

**Cómo funciona.** Corre como proceso separado; el driver JDBC se conecta por host, puerto, base y credenciales.

**Ejemplo aplicado.** facturacion es la BD de demostración y facturacion_test la de integración.

## Framework

**Qué es.** Una estructura de software que proporciona mecanismos y llama a nuestro código en puntos definidos.

**Para qué sirve.** Evita reescribir infraestructura habitual.

**Cómo funciona.** Nuestro programa se integra mediante interfaces y anotaciones; el framework administra parte del ciclo de vida.

**Ejemplo aplicado.** Spring llama al método del controlador cuando llega una ruta compatible.

## Spring

**Qué es.** Un conjunto de herramientas Java cuyo núcleo administra objetos y dependencias.

**Para qué sirve.** Organiza componentes, transacciones y aplicaciones web.

**Cómo funciona.** El contenedor crea beans, encuentra sus dependencias y los conecta. Un proxy puede envolver servicios para añadir transacciones.

**Ejemplo aplicado.** Spring inyecta FacturaService en el constructor de FacturaRestController.

## Spring Boot

**Qué es.** Una forma de configurar y arrancar aplicaciones Spring con convenciones y autoconfiguración.

**Para qué sirve.** Reduce configuración repetitiva y empaqueta una aplicación ejecutable.

**Cómo funciona.** La clase principal inicia el contexto. Los starters y propiedades activan configuraciones según las clases disponibles.

**Ejemplo aplicado.** El starter web configura Tomcat y MVC; data-jpa configura el acceso JPA a partir del DataSource.

## Spring Web MVC

**Qué es.** El módulo que recibe y enruta solicitudes HTTP del proyecto.

**Para qué sirve.** Conecta métodos Java con endpoints REST.

**Cómo funciona.** Tomcat entrega la petición al DispatcherServlet; MVC selecciona el método, convierte JSON, valida argumentos y escribe la respuesta.

**Ejemplo aplicado.** @PostMapping dirige POST /api/facturas a crear del controlador.

## JPA

**Qué es.** La especificación Jakarta Persistence para describir y administrar entidades persistentes.

**Para qué sirve.** Ofrece un modelo común para mapear objetos Java a datos relacionales.

**Cómo funciona.** Define anotaciones y contratos como EntityManager; necesita una implementación que efectúe el trabajo.

**Ejemplo aplicado.** @Entity y @ManyToOne describen cómo FacturaVenta se relaciona con Cliente.

## Hibernate

**Qué es.** La implementación de JPA usada por Spring Boot en este proyecto.

**Para qué sirve.** Traduce operaciones y cambios sobre entidades en SQL.

**Cómo funciona.** Mantiene un contexto de persistencia, detecta cambios y sincroniza con la BD al hacer flush.

**Ejemplo aplicado.** Al cambiar estado y fechaAnulacion de una factura administrada genera un UPDATE.

## ORM

**Qué es.** El mapeo objeto-relacional entre objetos y tablas.

**Para qué sirve.** Permite trabajar con entidades sin escribir cada INSERT a mano.

**Cómo funciona.** Campos corresponden a columnas y asociaciones a FK. No elimina la necesidad de conocer SQL ni diseñar restricciones.

**Ejemplo aplicado.** La lista Java detalles corresponde a filas con el mismo factura_id.

## Spring Data JPA

**Qué es.** La capa de repositorios que se apoya en JPA.

**Para qué sirve.** Reduce consultas y operaciones CRUD repetidas.

**Cómo funciona.** Spring genera implementaciones de interfaces y puede derivar consultas a partir de nombres de métodos.

**Ejemplo aplicado.** findByArticuloIdAndTipoMonedaId busca el precio del artículo en la moneda solicitada.

## DTO

**Qué es.** Un objeto que transporta datos entre límites del sistema.

**Para qué sirve.** Define qué acepta y qué devuelve la API sin exponer el modelo persistente.

**Cómo funciona.** El DTO se valida y el servicio resuelve IDs para construir entidades. El mapper crea el DTO de respuesta.

**Ejemplo aplicado.** FacturaDetalleRequestDTO recibe articuloId y cantidad; no permite que el cliente imponga precio o IVA.

## Inyección de dependencias

**Qué es.** Un mecanismo por el que una clase recibe los objetos que necesita en lugar de crearlos.

**Para qué sirve.** Reduce acoplamiento y facilita pruebas y configuración.

**Cómo funciona.** Spring inspecciona el constructor y entrega beans compatibles. Un único constructor no requiere @Autowired.

**Ejemplo aplicado.** FacturaService recibe repositorios, mapper, calculadora, Validator y Clock.

## Inversión de control

**Qué es.** El principio por el que el contenedor administra creación y llamadas que antes controlaría el programa manualmente.

**Para qué sirve.** Permite que componentes se concentren en su función.

**Cómo funciona.** Declaramos componentes y puntos de entrada; Spring determina cuándo construirlos y cuándo invocarlos.

**Ejemplo aplicado.** No llamamos manualmente al controlador desde main: MVC lo invoca al recibir HTTP.

# 3. Arquitectura del proyecto

[[ARCH]]

El flujo de dependencias va de la entrada HTTP hacia las reglas y la persistencia. La respuesta
vuelve como DTO. No hay una llamada del controlador al repositorio ni lógica monetaria en
el controlador. No se agregan interfaces de servicio o una capa genérica de CRUD: en este
TP no aportarían una ventaja que compense la complejidad.

## Controller

Responsabilidad: traducir HTTP a llamadas de servicio y elegir estados/cabeceras. Existe
para separar el transporte de las reglas de negocio. Contiene FacturaRestController.
Recibe FacturaRequestDTO, IDs y filtros; invoca FacturaService; devuelve FacturaReporteDTO
o ResponseEntity. Por ejemplo, crear devuelve 201 y una ubicación del recurso.

## Service

Responsabilidad: coordinar las reglas, entidades y transacciones. Contiene FacturaService,
CalculadoraImportes y FacturaMapper. El servicio busca catálogos, obtiene precios, construye
detalles, guarda y transforma la respuesta. La calculadora concentra las fórmulas y el mapper
la proyección de entidades a datos de salida. Así se puede estudiar cada responsabilidad.

## Repository

Responsabilidad: acceso a entidades persistentes. Contiene siete interfaces que extienden
JpaRepository; no se necesita un repositorio de detalles porque se guardan a través de la
factura. Spring Data genera los beans. FacturaRepository añade filtros y consulta con bloqueo.
El servicio recibe entidades y decide qué hacer con ellas.

## Entity

Responsabilidad: representar el estado persistente y sus relaciones. Contiene las ocho
entidades y el enum EstadoFactura. FacturaVenta concentra la colección y sumas del agregado;
FacturaDetalle posee la FK factura_id. Las entidades no conocen HTTP y no se retornan
directamente al cliente.

## DTO

Responsabilidad: definir el contrato de entrada/salida y sus restricciones. Contiene los dos
RequestDTO, los dos ReporteDTO, ErrorDTO y el grupo ValidacionCompleta. Un DTO no tiene
@Entity ni tabla propia. El grupo permite utilizar el mismo contrato para creación completa
y para el modo específico de estado en PUT.

## Exception

Responsabilidad: comunicar fallos de dominio y traducirlos a respuestas controladas.
Contiene tres excepciones específicas y ManejadorExcepciones. Un cliente inexistente produce
RecursoNoEncontradoException; el advice lo convierte en 404. El SQL de un error de integridad
no se expone al cliente.

## Config

Responsabilidad: aportar configuración explícita cuando es necesaria. TiempoConfig expone
Clock.systemUTC() como bean; evita distribuir la elección del reloj por el código. La conexión,
el puerto y las opciones JPA están en application.properties, no en clases con secretos.

## Arranque conceptual de Spring Boot

main llama SpringApplication.run. @SpringBootApplication combina configuración,
autoconfiguración y búsqueda de componentes desde ar.edu.utn.facturacion. El contenedor
registra controladores, servicios y componentes; Spring Data registra repositorios. El
DataSource abre conexiones usando las variables de entorno. Flyway aplica SQL pendiente;
Hibernate valida las entidades contra el esquema. Tomcat empieza a escuchar el puerto.
Una aplicación que arranca no garantiza por sí sola que sus reglas sean correctas: las
pruebas de integración comprueban los recorridos reales.

# 4. Explicación del código Java

Los fragmentos siguientes se leen de los archivos de esta entrega. No representan un diseño hipotético.

## TiempoConfig

Archivo: src/main/java/ar/edu/utn/facturacion/config/TiempoConfig.java

Declara el bean Clock. reloj no recibe parámetros y devuelve un reloj UTC que inyecta el servicio.

## FacturaRestController

Archivo: src/main/java/ar/edu/utn/facturacion/controller/FacturaRestController.java

Expone crear(request), actualizar(id,request), eliminar(id), obtener(id) y listar(filtros). Sus salidas son DTO, lista de DTO o ResponseEntity. La creación agrega Location; la baja no devuelve cuerpo.

## ErrorDTO

Archivo: src/main/java/ar/edu/utn/facturacion/dto/ErrorDTO.java

Forma de los errores controlados: timestamp, status, mensaje y lista errores. No contiene el objeto Exception ni stacktrace.

## FacturaDetalleReporteDTO

Archivo: src/main/java/ar/edu/utn/facturacion/dto/FacturaDetalleReporteDTO.java

Respuesta de un renglón con su ID y referencia al precio, cantidades y copias monetarias e impositivas utilizadas.

## FacturaDetalleRequestDTO

Archivo: src/main/java/ar/edu/utn/facturacion/dto/FacturaDetalleRequestDTO.java

Record con articuloId y cantidad; valida ID positivo, cantidad positiva, límite y precisión decimal. Un precio enviado por el cliente no pertenece a este contrato.

## FacturaReporteDTO

Archivo: src/main/java/ar/edu/utn/facturacion/dto/FacturaReporteDTO.java

Respuesta de cabecera con IDs, descripciones útiles, estado, importes, fecha de anulación y detalles. No es entidad ni mantiene sesión de Hibernate.

## FacturaRequestDTO

Archivo: src/main/java/ar/edu/utn/facturacion/dto/FacturaRequestDTO.java

Record de fecha, IDs de cabecera, observaciones, detalles y estado opcional. Jakarta Validation controla formato semántico y tamaño. El servicio distingue el modo completo del modo de estado.

## ValidacionCompleta

Archivo: src/main/java/ar/edu/utn/facturacion/dto/ValidacionCompleta.java

Grupo que extiende Default. Exige cabecera y detalles para POST o PUT completo y también hereda las restricciones generales.

## Articulo

Archivo: src/main/java/ar/edu/utn/facturacion/entity/Articulo.java

Catálogo de código único, descripción y alícuota de ejemplo. No almacena stock. Su IVA se copia en el detalle al crear o actualizar.

## Cliente

Archivo: src/main/java/ar/edu/utn/facturacion/entity/Cliente.java

Catálogo de nombre y documento ficticio único. El constructor protegido permite instanciación por JPA; solo se exponen getters porque la API de este TP no edita clientes.

## CondicionIva

Archivo: src/main/java/ar/edu/utn/facturacion/entity/CondicionIva.java

Catálogo de descripciones únicas. Clasifica la factura sin deducir exenciones ni reglas tributarias no dadas por el TP.

## EstadoFactura

Archivo: src/main/java/ar/edu/utn/facturacion/entity/EstadoFactura.java

Enum EMITIDA y ANULADA; limita los estados Java y JSON. La BD también restringe esos valores mediante CHECK.

## FacturaDetalle

Archivo: src/main/java/ar/edu/utn/facturacion/entity/FacturaDetalle.java

Representa un renglón. El constructor recibe el precio resuelto, cantidad e importes calculados y copia descripción, precio e IVA. asociarA establece la factura propietaria; tiene alcance de paquete para usarlo desde la cabecera.

## FacturaVenta

Archivo: src/main/java/ar/edu/utn/facturacion/entity/FacturaVenta.java

Es la cabecera y raíz del agregado. actualizarCabecera cambia datos permitidos; reemplazarDetalles mantiene la misma colección, asocia los nuevos detalles y recalcula sumas. anular fija estado e instante; cambiarObservaciones cambia el texto. Los getters permiten mapear la respuesta.

## ListaPrecioArticulo

Archivo: src/main/java/ar/edu/utn/facturacion/entity/ListaPrecioArticulo.java

Vincula artículo, moneda y precio. Sus getters permiten al servicio construir renglones. La unicidad del par artículo/moneda evita elegir arbitrariamente entre varios precios.

## PuntoVenta

Archivo: src/main/java/ar/edu/utn/facturacion/entity/PuntoVenta.java

Catálogo de código único y descripción. Es una relación de cabecera; no implementa numeración fiscal.

## TipoMoneda

Archivo: src/main/java/ar/edu/utn/facturacion/entity/TipoMoneda.java

Catálogo de código de tres caracteres y descripción. Selecciona el precio aplicable; no convierte monedas.

## ConflictoNegocioException

Archivo: src/main/java/ar/edu/utn/facturacion/exception/ConflictoNegocioException.java

RuntimeException para una regla que impide una operación, como modificar una factura anulada o facturar un artículo sin precio. El advice devuelve 409.

## ManejadorExcepciones

Archivo: src/main/java/ar/edu/utn/facturacion/exception/ManejadorExcepciones.java

@RestControllerAdvice centraliza 400, 404 y 409. Métodos anotados con @ExceptionHandler construyen ResponseEntity<ErrorDTO>. Las excepciones SQL se resumen para no exponer detalles internos.

## RecursoNoEncontradoException

Archivo: src/main/java/ar/edu/utn/facturacion/exception/RecursoNoEncontradoException.java

RuntimeException de recursos inexistentes. Su constructor recibe un mensaje. El advice la traduce a 404 y una transacción de escritura se revierte si se propaga.

## SolicitudInvalidaException

Archivo: src/main/java/ar/edu/utn/facturacion/exception/SolicitudInvalidaException.java

RuntimeException para un contrato incoherente, como mezclar estado y datos completos o invertir el rango de fechas. Devuelve 400.

## FacturacionApplication

Archivo: src/main/java/ar/edu/utn/facturacion/FacturacionApplication.java

Punto de entrada. main recibe argumentos de la línea de comandos y arranca el contexto Spring; no contiene reglas de facturación.

## ArticuloRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/ArticuloRepository.java

Acceso al catálogo Articulo. Hereda findById, existsById y demás operaciones CRUD; el servicio lo usa para resolver IDs y detectar ausencias. No tiene implementación manual ni requiere @Repository explícito: Spring Data registra el bean.

## ClienteRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/ClienteRepository.java

Acceso al catálogo Cliente. Hereda findById, existsById y demás operaciones CRUD; el servicio lo usa para resolver IDs y detectar ausencias. No tiene implementación manual ni requiere @Repository explícito: Spring Data registra el bean.

## CondicionIvaRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/CondicionIvaRepository.java

Acceso al catálogo CondicionIva. Hereda findById, existsById y demás operaciones CRUD; el servicio lo usa para resolver IDs y detectar ausencias. No tiene implementación manual ni requiere @Repository explícito: Spring Data registra el bean.

## FacturaRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/FacturaRepository.java

Hereda CRUD y JpaSpecificationExecutor. buscarParaActualizar(id) usa JPQL y PESSIMISTIC_WRITE, por lo que debe ejecutarse en una transacción activa.

## ListaPrecioArticuloRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/ListaPrecioArticuloRepository.java

findByArticuloIdAndTipoMonedaId recibe dos IDs y retorna Optional. @EntityGraph carga artículo y moneda con la consulta para utilizarlos en el servicio.

## PuntoVentaRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/PuntoVentaRepository.java

Acceso al catálogo PuntoVenta. Hereda findById, existsById y demás operaciones CRUD; el servicio lo usa para resolver IDs y detectar ausencias. No tiene implementación manual ni requiere @Repository explícito: Spring Data registra el bean.

## TipoMonedaRepository

Archivo: src/main/java/ar/edu/utn/facturacion/repository/TipoMonedaRepository.java

Acceso al catálogo TipoMoneda. Hereda findById, existsById y demás operaciones CRUD; el servicio lo usa para resolver IDs y detectar ausencias. No tiene implementación manual ni requiere @Repository explícito: Spring Data registra el bean.

## CalculadoraImportes

Archivo: src/main/java/ar/edu/utn/facturacion/service/CalculadoraImportes.java

calcular recibe cantidad, precio y alícuota BigDecimal. Devuelve el record Importes con neto, IVA y total redondeados. No consulta BD ni conoce HTTP.

## FacturaMapper

Archivo: src/main/java/ar/edu/utn/facturacion/service/FacturaMapper.java

aReporte recibe una FacturaVenta administrada y devuelve FacturaReporteDTO con una lista de FacturaDetalleReporteDTO. Se usa antes de cerrar la transacción para resolver asociaciones LAZY.

## FacturaService

Archivo: src/main/java/ar/edu/utn/facturacion/service/FacturaService.java

Orquesta crear y actualizar, valida con validarCompleta, construye con aplicarContenido y resuelve referencias con repositorios. obtener y listar devuelven reportes dentro de transacciones de lectura. eliminar anula; buscarBloqueada serializa escrituras. actualizarEstado impide mezclar modos y reactivar anuladas. asegurarEditable protege el contenido y noExiste construye errores 404.

## Fragmento: entrada HTTP y respuesta 201

~~~
    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(
            @Validated(ValidacionCompleta.class) @RequestBody FacturaRequestDTO request) {
        FacturaReporteDTO resultado = servicio.crear(request);
        return ResponseEntity.created(URI.create("/api/facturas/" + resultado.id())).body(resultado);
    }
~~~

@RequestBody indica conversión del cuerpo JSON. @Validated selecciona ValidacionCompleta.
La validación ocurre antes de entrar al método. El controlador llama a servicio.crear;
si el servicio retorna, ya se completó el límite transaccional de esa llamada.
ResponseEntity.created agrega 201 y Location; body agrega la respuesta de datos.
Si una excepción se propaga, el advice decide el error en lugar de este retorno.

## Fragmento: guardado en servicio

~~~
    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO request) {
        validarCompleta(request);
        if (request.estado() != null) throw new SolicitudInvalidaException("El estado inicial lo asigna el servidor: EMITIDA.");
        FacturaVenta factura = new FacturaVenta();
        aplicarContenido(factura, request);
        // Detectar errores SQL antes de construir la respuesta.
        return mapper.aReporte(facturas.saveAndFlush(factura));
    }
~~~

La creación valida, rechaza un estado impuesto por el cliente y construye una cabecera nueva.
aplicarContenido obtiene referencias y detalles. saveAndFlush registra el agregado y fuerza
sincronización SQL antes de mapear. El flush no equivale a commit: si aparece una excepción
posterior dentro de la transacción, todavía puede revertirse.

## Anotaciones que utiliza el código

| Anotación | Efecto en este proyecto |
|---|---|
| @SpringBootApplication | Configuración, búsqueda de componentes y autoconfiguración |
| @RestController | Bean MVC con respuesta serializada |
| @RequestMapping | Prefijo /api/facturas |
| @PostMapping / @PutMapping / @DeleteMapping / @GetMapping | Selección de método y ruta HTTP |
| @RequestBody | Conversión JSON a record |
| @PathVariable / @RequestParam | ID de ruta y filtros de consulta |
| @Validated / @Valid | Grupo de restricciones y validación de objetos anidados |
| @NotNull / @NotEmpty / @Positive / @Size / @Digits | Restricciones de entrada |
| @Service / @Component | Detección de lógica y colaboradores como beans |
| @Configuration / @Bean | Registro del reloj UTC |
| @Entity / @Table | Mapeo de clase y tabla |
| @Id / @GeneratedValue | PK asignada con identity de PostgreSQL |
| @Column | Nombre implícito, nulabilidad, longitud y precisión |
| @ManyToOne / @JoinColumn | Asociación con FK en esta entidad |
| @OneToMany / @OrderBy | Colección inversa y orden de detalles recuperados |
| @Enumerated | Guarda EMITIDA/ANULADA como texto |
| @Version | Versión de concurrencia de la cabecera |
| @Transactional | Límite de lectura/escritura y rollback |
| @Lock / @Query / @Param | Consulta JPQL con bloqueo pesimista |
| @EntityGraph | Carga de asociaciones de precio |
| @RestControllerAdvice / @ExceptionHandler | Traducción centralizada de fallos |

@Repository no se escribió en las interfaces porque Spring Data JPA ya las detecta y
registra como repositorios. No se debe agregar una clase de implementación solo para usar
esa anotación. @Autowired se usa en la prueba para JdbcTemplate; las clases productivas
utilizan inyección por constructor.

# 5. PostgreSQL y persistencia

Un clúster PostgreSQL es un directorio de datos administrado por un proceso servidor.
Puede contener varias bases. Un servidor escucha un puerto; una base contiene esquemas;
un esquema contiene tablas. En la opción local hay un clúster exclusivo en .local/pgdata,
puerto 55432, base facturacion y esquema public. La integración usa facturacion_test y
tp_pruebas. El servicio que ya existía en 5432 no se modifica.

## Tablas y significado

| Tabla | Qué conserva y restricciones principales |
|---|---|
| cliente | Nombre y documento ficticio único |
| punto_venta | Código único y descripción |
| condicion_iva | Descripción única |
| tipo_moneda | Código único y descripción |
| articulo | Código, descripción, alícuota entre 0 y 100 |
| lista_precio_articulo | FK artículo y moneda; precio no negativo; par único |
| factura_venta | FK de cabecera; fecha; estado; importes; versión; anulación |
| factura_detalle | FK factura y precio; descripción y valores históricos |

flyway_schema_history es una tabla técnica adicional que registra migraciones, checksum
y versiones. No representa un concepto de facturación.

[[ER]]

## Claves e integridad

La PK identifica una fila. GENERATED BY DEFAULT AS IDENTITY obtiene un valor de una
secuencia. Una FK exige que el registro relacionado exista. NOT NULL evita ausencias en
campos obligatorios. UNIQUE impide duplicar claves de catálogo o precios del mismo par.
CHECK verifica cantidades positivas, montos coherentes y correspondencia entre estado y
fecha de anulación. Si una fila está ANULADA, su fecha de anulación no puede ser null.

Se agregan índices por cliente, fecha, estado y factura de detalle. No son reglas de negocio:
facilitan consultas y relaciones. Las FK no tienen ON DELETE CASCADE hacia catálogos.
Intentar borrar un cliente usado produce un conflicto de integridad. La baja de la API es
UPDATE de estado, por lo que no rompe la historia.

## Tipos elegidos

bigint corresponde a Long para IDs. date corresponde a LocalDate y no contiene hora.
timestamptz representa el instante de anulación; PostgreSQL lo muestra según la zona de
la sesión y la API lo serializa como instante UTC. numeric evita representación binaria
aproximada en montos: precio tiene 15 dígitos totales y 2 decimales; importes 19 y 2;
cantidad 9 y 3. varchar limita textos y facilita restricciones.

## Reconstrucción de la BD

Primero se crea rol y base mediante sql/crear-bases.sql o el script de clúster local.
Al iniciar la aplicación, Flyway ejecuta V1__estructura.sql y luego V2__datos_demostracion.sql.
No copie tablas a mano si usará Flyway sobre esa misma base: la primera migración ya las crea.
Una segunda ejecución valida el historial y no vuelve a insertar los datos de V2.
Si necesita cambiar el esquema, agregue V3; editar V1 después de aplicada rompe el checksum.
Hibernate está en validate: verifica el esquema pero no lo modifica automáticamente.

## Inspección SQL

~~~
SELECT * FROM cliente;
SELECT * FROM condicion_iva;
SELECT * FROM tipo_moneda;
SELECT * FROM punto_venta;
SELECT a.id, a.descripcion, a.alicuota_iva, m.codigo AS moneda, p.precio
FROM articulo a LEFT JOIN lista_precio_articulo p ON p.articulo_id=a.id
LEFT JOIN tipo_moneda m ON m.id=p.tipo_moneda_id ORDER BY a.id,m.id;
SELECT id,fecha,cliente_id,estado,neto,iva,total,fecha_anulacion FROM factura_venta ORDER BY id;
SELECT f.id, count(d.id) AS renglones, f.total, sum(d.total) AS suma_detalles
FROM factura_venta f JOIN factura_detalle d ON d.factura_id=f.id
GROUP BY f.id ORDER BY f.id;
SELECT * FROM factura_detalle ORDER BY factura_id,id;
~~~

La consulta con JOIN compara el total de cabecera con la suma de renglones. El WHERE
permite enfocar una factura. GROUP BY agrupa las filas del mismo comprobante.
No es necesario memorizar SQL completo para la defensa: debe poder explicar qué fila
representa cada tabla y cómo una FK conecta las filas.

## Transacciones y ACID

Atomicidad: se confirma todo o nada. Consistencia: las reglas y restricciones se respetan
al terminar. Aislamiento: operaciones concurrentes no comparten cambios sin confirmar
como si fueran definitivos; este proyecto usa el nivel por defecto READ COMMITTED y
bloquea la cabecera durante escrituras. Durabilidad: un commit exitoso persiste más allá
del proceso Java. El bloqueo no es una autorización de usuario ni una protección completa
contra edición basada en un formulario antiguo: no exigimos version en el JSON.

# 6. Cómo funcionan JPA y Hibernate

Conviene separar tres piezas. JPA es la especificación; Hibernate es la implementación;
Spring Data JPA genera repositorios que usan esa infraestructura. PostgreSQL sigue siendo
quien ejecuta el SQL. Ninguna de estas herramientas elimina la necesidad de comprobar
datos, consultas y transacciones.

## El contexto de persistencia

Dentro de una transacción, Hibernate mantiene una identidad por entidad: dos búsquedas
del mismo ID pueden devolver la misma instancia administrada. Una entidad nueva todavía
no se guarda; una administrada participa en seguimiento de cambios; una separada del
contexto ya no se sincroniza automáticamente. Por eso el mapper se ejecuta antes de cerrar
la transacción y los clientes reciben DTOs sin asociaciones dependientes de una sesión.

## Qué ocurre con findById

El repositorio usa el EntityManager. Si el contexto ya conoce esa identidad, puede reutilizarla;
si no, consulta PostgreSQL. Devuelve Optional: ausencia es un resultado posible.
El servicio convierte ausencia a RecursoNoEncontradoException para que HTTP responda 404.
No se llama get sobre un Optional vacío esperando un error genérico.

## Qué ocurre con save y saveAndFlush

Spring Data distingue una entidad nueva de una existente para delegar en persist o merge.
En creación, nuestra factura es nueva. Con IDENTITY puede emitirse un INSERT temprano
para obtener la PK. La cascada propaga persistencia a los detalles. saveAndFlush agrega
una sincronización explícita: SQL pendiente se envía antes de crear el reporte.
El commit ocurre al completar el método interceptado por el proxy transaccional, no al
construir cada objeto y tampoco necesariamente con cada llamada a save.

## Actualizar sin save adicional

buscarParaActualizar obtiene una entidad administrada. Los cambios de cabecera, estado y
colección son detectados por Hibernate (dirty checking). No es necesario llamar de nuevo
a save para que se emita UPDATE; se usa flush para detectar fallos de BD dentro de la
operación. Cambiar un DTO por sí solo nunca actualiza una tabla.

## Dueño de la relación y cascada

~~~
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FacturaDetalle> detalles = new ArrayList<>();
~~~

mappedBy="factura" significa que la columna FK está gobernada por FacturaDetalle.factura.
La cabecera mantiene la vista inversa. reemplazarDetalles llama asociarA para que cada
detalle apunte a su cabecera: agregar solo a una lista sin fijar el dueño sería incorrecto.

CascadeType.ALL incluye persistencia, merge, eliminación y otras propagaciones. Aquí se
usa en la relación factura-detalles: son parte de la misma unidad. No se aplica a Cliente,
Articulo ni TipoMoneda: borrar una factura jamás debería borrar catálogos compartidos.

orphanRemoval=true elimina detalles que salieron de la colección administrada.
No significa borrar catálogos ni anular. En PUT se mantiene la colección y se reemplaza su
contenido; el resultado son detalles nuevos y eliminación de los anteriores.

## LAZY y límites de sesión

ManyToOne se configura LAZY: la asociación puede cargarse cuando se accede a ella.
Si se intenta acceder después de cerrar el contexto, puede fallar. Por eso no retornamos
entidades y open-in-view=false impide depender de una sesión abierta durante serialización.
EntityGraph adelanta carga de artículo y moneda en la consulta de precios.
El listado sencillo puede hacer varias consultas; para un volumen grande habría que
optimizar el plan y paginar, sin cambiar las reglas monetarias.

## @Transactional y rollback

Spring envuelve el servicio en un proxy. Una llamada externa al método público entra por
ese proxy y abre/participa en una transacción. Si termina bien, confirma. Si se propaga una
RuntimeException, la política por defecto revierte. Nuestras excepciones de negocio son
RuntimeException. No se atrapan fallos para devolver éxito dentro del mismo método.
Las llamadas privadas como aplicarContenido participan en la transacción ya activa; poner
@Transactional en un método privado no crearía mágicamente otro límite.

La prueba decisiva no es una excepción antes de guardar. Un trigger de prueba rechaza el
segundo detalle cuando el primero y la cabecera ya fueron enviados. La respuesta es 409
y las consultas posteriores deben encontrar cero cabeceras y cero detalles. Los IDs de
secuencia pueden presentar huecos después de un rollback: las secuencias no prometen
numeración fiscal correlativa.

# 7. Flujo completo de una solicitud

## POST: recorrido detallado

[[SEQ]]

1. PowerShell lee examples/crear-factura.json y envía POST a /api/facturas con Content-Type
application/json. La conexión TCP llega al puerto de Tomcat; no se conecta directamente
a la BD ni puede imponer importes.

2. Spring MVC identifica FacturaRestController.crear. El convertidor JSON instancia
FacturaRequestDTO y sus FacturaDetalleRequestDTO. Si el texto no es JSON válido, una fecha
no tiene formato aceptable o aparece un campo desconocido, la petición se rechaza con 400.

3. Jakarta Validation aplica ValidacionCompleta, heredando Default y recorriendo @Valid.
Comprueba fecha, IDs y detalles. Una cantidad cero no alcanza el servicio: el advice
construye ErrorDTO con el campo inválido.

4. El controlador llama servicio.crear(request). La referencia inyectada es el bean
interceptado por Spring. @Transactional inicia la unidad de trabajo sobre una conexión.
La validación de servicio repite el contrato completo para preservar la regla también
si el servicio se usa desde otro componente.

5. Se crea FacturaVenta en memoria con estado EMITIDA. aplicarContenido busca Cliente 1,
CondicionIva 1, PuntoVenta 1 y TipoMoneda 1. Si falta una referencia, se propaga 404.
Todavía no hay una factura válida que pueda confirmarse parcialmente.

6. Para el primer renglón, se verifica Articulo 1 y se busca el precio por artículo 1 y
moneda 1. Se obtiene 1000.00 y una alícuota ficticia de 21.00. Cantidad 2 produce neto
2000.00, IVA 420.00 y total 2420.00. El detalle copia esos valores y la descripción.

7. El segundo renglón resuelve artículo 2, precio 200.00 e IVA 10.50. Cantidad 3 produce
neto 600.00, IVA 63.00 y total 663.00. Si hubiera un artículo sin precio se devolvería
409 y no se guardaría el agregado.

8. actualizarCabecera asigna las referencias administradas. reemplazarDetalles establece
las dos direcciones de la relación y suma los renglones: neto 2600.00, IVA 483.00,
total 3083.00.

9. facturas.saveAndFlush registra la cabecera y los detalles por cascada. Hibernate envía
INSERT a factura_venta y a factura_detalle con las FK resueltas. PostgreSQL asigna IDs
y comprueba FK, CHECK, tipos y NOT NULL. Un fallo de cualquier INSERT revierte la unidad.

10. FacturaMapper.aReporte construye los DTOs mientras el contexto está disponible.
El método termina; el proxy confirma la transacción. Entonces el controlador construye
201, Location /api/facturas/{id} y el reporte. Los IDs concretos pueden variar entre pruebas.

11. Spring serializa la respuesta a JSON y la envía al cliente. Un GET independiente ya
puede consultar los registros confirmados. La respuesta es dato transportable; no mantiene
conexiones ni entidades abiertas en el navegador.

## Solicitud real de ejemplo

~~~
{
  "fecha": "2026-10-23", "clienteId": 1, "condicionIvaId": 1,
  "puntoVentaId": 1, "tipoMonedaId": 1,
  "observaciones": "Factura para la demostración académica",
  "detalles": [{"articuloId": 1, "cantidad": 2}, {"articuloId": 2, "cantidad": 3}]
}
~~~

## Respuesta JSON capturada en la auditoría

Esta respuesta corresponde al POST ejecutado sobre la base exclusiva de demostración. Los IDs de nuevas ejecuciones pueden variar.

~~~
{
  "id": 2, "version": 0, "fecha": "2026-10-23", "clienteId": 1, "clienteNombre": "Ana Martínez",
  "condicionIvaId": 1, "puntoVentaId": 1, "tipoMonedaId": 1, "moneda": "ARS",
  "estado": "EMITIDA", "fechaAnulacion": null,
  "observaciones": "Factura para la demostración académica", "neto": 2600.0, "iva": 483.0,
  "total": 3083.0,
  "detalles": [
    {
      "id": 4, "articuloId": 1, "listaPrecioArticuloId": 1, "descripcion": "Cuaderno A4 rayado de 80 hojas",
      "cantidad": 2, "precioUnitario": 1000.0, "alicuotaIva": 21.0, "neto": 2000.0, "iva": 420.0,
      "total": 2420.0
    },
    {
      "id": 5, "articuloId": 2, "listaPrecioArticuloId": 2, "descripcion": "Lápiz negro HB",
      "cantidad": 3, "precioUnitario": 200.0, "alicuotaIva": 10.5, "neto": 600.0, "iva": 63.0,
      "total": 663.0
    }
  ]
}
~~~

## SQL representativo del efecto

Estas sentencias ilustran el efecto, no prometen el texto exacto que Hibernate genera:

~~~sql
SELECT ... FROM cliente WHERE id = 1;
SELECT ... FROM lista_precio_articulo
  WHERE articulo_id = 1 AND tipo_moneda_id = 1;
INSERT INTO factura_venta (...,estado,neto,iva,total)
  VALUES (...,'EMITIDA',2600.00,483.00,3083.00);
INSERT INTO factura_detalle (...,cantidad,neto,iva,total)
  VALUES (...,2,2000.00,420.00,2420.00);
INSERT INTO factura_detalle (...,cantidad,neto,iva,total)
  VALUES (...,3,600.00,63.00,663.00);
~~~

Hay una fila de cabecera y dos filas de detalle. Las PK de los detalles no tienen por qué
coincidir con las del artículo o la factura. En RESPUESTA_REAL.json encontrará una captura
efectiva de este contrato, incluyendo la identificación asignada durante la auditoría.

## PUT completo: qué cambia

La entrada debe contener fecha, las cuatro relaciones y la lista completa. MVC valida los
campos presentes; el servicio valida el grupo completo cuando no hay estado.
buscarParaActualizar ejecuta una consulta con bloqueo de escritura. Si la factura no
existe se obtiene 404; si está anulada, 409.

Se resuelven nuevamente catálogos y precios, se prepara la nueva lista y se reemplaza
la colección. Los renglones nuevos tienen nuevas PK; los anteriores se retiran por
orphanRemoval. Se recalculan importes con valores vigentes. Hibernate detecta cambios,
sincroniza y confirma; el controlador devuelve 200 con el reporte actualizado.
Si la BD rechaza un nuevo detalle, el rollback conserva los anteriores y los importes
previos. La prueba de actualización lo verifica.

## PUT de estado

El cuerpo solo tiene estado y observaciones opcionales. Mezclarlo con detalles o cabecera
es 400. EMITIDA sobre EMITIDA permite actualizar observaciones. ANULADA registra el instante.
No puede reactivarse ni editarse una anulada. Repetir ANULADA sin observaciones devuelve
el mismo recurso; intentar cambiar su texto después de anulada produce 409.

## DELETE: baja lógica y respuesta sin cuerpo

El ID llega por ruta, no por JSON. El servicio bloquea y busca la factura. Ausencia es 404.
Si está emitida, cambia estado y fechaAnulacion; si ya está anulada conserva los valores.
El flush produce un UPDATE, no un DELETE SQL. La transacción confirma y HTTP retorna
204 No Content. No se debe ejecutar ConvertFrom-Json sobre una respuesta vacía.
GET incluye anuladas salvo que se filtre por estado.

## Errores en distintas etapas

Un JSON roto falla en conversión; un campo inválido falla en validación; una referencia
ausente falla en servicio; una FK o trigger puede fallar en PostgreSQL durante flush.
El lugar del fallo explica por qué no basta con probar un único error antes de guardar.
El advice traduce errores, pero la transacción la gobierna el servicio.

# 8. DTOs y validaciones

Una Entity describe persistencia; un DTO describe datos de un límite. La entidad tiene
identidad, asociaciones y ciclo de vida administrado. Un RequestDTO recibe solo lo que
el cliente puede decidir; un ReporteDTO devuelve datos calculados y útiles para consultar.
Separarlos permite cambiar una tabla sin publicar automáticamente sus campos internos.

Si se recibiera FacturaVenta directamente, el cliente podría intentar fijar total, estado o
referencias arbitrarias. Con el DTO solo llegan IDs. El servidor consulta esos IDs y
construye referencias reales. Los detalles siguen anidados porque son partes de la factura,
pero no se mandan objetos Cliente o Articulo completos.

~~~
package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record FacturaDetalleRequestDTO(
    @NotNull @Positive Long articuloId,
    @NotNull @DecimalMin("0.001") @DecimalMax("999999.999")
    @Digits(integer = 6, fraction = 3) BigDecimal cantidad
) {}
~~~

@NotNull impide ausencia; @Positive impide IDs cero/negativos; @DecimalMin fija la menor
cantidad; @DecimalMax y @Digits limitan magnitud y precisión. Una lista puede tener
elementos null aunque no esté vacía: por eso se valida también List<@NotNull ...>.
@Valid recorre los detalles y no debe confundirse con una anotación que por sí sola
imponga restricciones.

ValidacionCompleta extiende Default para exigir cabecera y lista en creación o reemplazo.
El modo de estado puede omitirlos. El servicio prohíbe mezclar contratos y valida nuevamente
cuando se exige una factura completa. El DTO de salida no requiere esas restricciones:
lo construimos a partir de entidades y cálculos ya validados.

## Transformaciones y errores posibles

JSON a DTO: nombres/tipos desconocidos, fechas mal formadas o enum inválido producen 400.
DTO a entidades: IDs ausentes producen 404; una combinación sin precio produce 409.
Entidades a DTO: se resuelven datos asociados dentro de la transacción para evitar carga
LAZY fuera de sesión. La API no devuelve proxies de Hibernate ni referencias recursivas.

## Solicitud inválida controlada

~~~json
{
  "fecha":"2026-10-23","clienteId":1,"condicionIvaId":1,
  "puntoVentaId":1,"tipoMonedaId":1,
  "detalles":[{"articuloId":1,"cantidad":0}]
}
~~~

La respuesta tiene status 400, mensaje Solicitud inválida y una lista que identifica
detalles[0].cantidad. El texto específico de la restricción puede depender del locale.
No queda una factura porque la validación ocurrió antes del guardado.
Un cuerpo vacío {}, una lista vacía y una lista con null también se rechazan.

# 9. Lógica de facturación

El precio unitario es el valor de una unidad sin IVA en la moneda elegida. La cantidad
puede incluir hasta tres decimales. Aquí llamamos neto del renglón al producto de cantidad
por precio redondeado a dos decimales. No hay descuentos; por lo tanto subtotal sin IVA
y neto del renglón coinciden.

La alícuota es un porcentaje almacenado en Articulo. No se recibe en el JSON. En esta
maqueta la condición de IVA no modifica la fórmula. El total combina neto e IVA.
No se mezclan monedas dentro de una factura: todos los precios se buscan con la misma
tipoMonedaId de cabecera.

## Fórmulas implementadas

neto_renglón = redondear(cantidad x precio_unitario, 2, HALF_UP)
IVA_renglón = redondear(neto_renglón x alícuota / 100, 2, HALF_UP)
total_renglón = neto_renglón + IVA_renglón
neto_factura = suma(neto_renglón)
IVA_factura = suma(IVA_renglón)
total_factura = neto_factura + IVA_factura

~~~
    public Importes calcular(BigDecimal cantidad, BigDecimal precio, BigDecimal alicuota) {
        BigDecimal neto = cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva = neto.multiply(alicuota).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new Importes(neto, iva, neto.add(iva));
    }
}
~~~

BigDecimal("100") representa exactamente cien. Usar new BigDecimal(0.1) tomaría como punto
de partida un double aproximado; preferimos cadenas para constantes decimales.
setScale fija dos decimales y HALF_UP redondea la mitad hacia arriba para estos valores
no negativos. No existe una única regla universal de redondeo fiscal: esta es la decisión
académica explícita del proyecto.

## Ejemplo verificable a mano

| Artículo | Cantidad | Precio | Neto | IVA % | IVA | Total |
|---|---|---|---|---|---|---|
| Cuaderno | 2 | 1000.00 | 2000.00 | 21.00 | 420.00 | 2420.00 |
| Lápiz | 3 | 200.00 | 600.00 | 10.50 | 63.00 | 663.00 |
| Suma | | | 2600.00 | | 483.00 | 3083.00 |

Un caso de borde: 0.125 unidades a precio 1.00 generan 0.125 sin redondear. El neto
guardado es 0.13; 0.13 x 21 / 100 = 0.0273, cuyo IVA guardado es 0.03; total 0.16.
La suite unitaria verifica ese criterio. Calcular IVA sobre el producto sin redondear
sería otro criterio y podría dar diferencias; no debe mezclarse con el implementado.

## Historia de precios

Si mañana el cuaderno pasa a 1500.00, un GET de la factura anterior sigue mostrando el
precioUnitario 1000.00 copiado en el detalle. Una edición completa, en cambio, se define
como recálculo con el catálogo vigente. Una factura anulada no puede entrar en ese flujo.
Una cuenta docente puede modificar datos de catálogo con SQL para estudiar el efecto;
la API de catálogo no forma parte del TP.

# 10. Pruebas prácticas

Este capítulo recorre una ejecución desde cero. Las instrucciones completas copiables están
también en DEMO_PRESENTACION.md. Abra IntelliJ con pom.xml y seleccione JDK 26.
Maven importa las dependencias. No hace falta instalar extensiones de frontend ni Docker.

## Preparar PostgreSQL

En PowerShell, dentro del proyecto:

~~~powershell
.\scripts\postgres-local.ps1 iniciar
~~~

Reutiliza los binarios instalados y crea un clúster propio y dos bases en el primer inicio.
El archivo .local/conexion.ps1 contiene configuración sensible y queda excluido del ZIP.
Para utilizar el servicio 5432 en lugar de esta opción, siga crear-bases.sql y
configurar-conexion.ps1 del README. No necesita cambiar el servicio existente.

~~~powershell
. .\.local\conexion.ps1
$env:PGPASSWORD = $env:DB_PASSWORD
pg_isready -h 127.0.0.1 -p $env:PGPORT
~~~

pg_isready comprueba disponibilidad del servidor; no sustituye una consulta autenticada.
Las tablas se crean al iniciar Spring Boot, no solo al crear la base.

## Arrancar el backend

~~~powershell
.\scripts\ejecutar.ps1
~~~

Mantenga esa terminal abierta. Flyway aplica estructura y datos si faltan; Hibernate valida;
Tomcat escucha 8080. Abra otra terminal en el proyecto para las solicitudes.

## Crear y examinar

~~~powershell
. .\.local\conexion.ps1
$env:PGPASSWORD = $env:DB_PASSWORD
$api = 'http://127.0.0.1:8080/api/facturas'
$json = Get-Content .\examples\crear-factura.json -Raw -Encoding UTF8
$r = Invoke-WebRequest -Uri $api -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -UseBasicParsing
$r.StatusCode
$f = $r.Content | ConvertFrom-Json
$idFactura = $f.id
$f | ConvertTo-Json -Depth 8
~~~

Debe obtener 201, dos renglones y total 3083.00. Guarde el ID de la respuesta; los huecos
de secuencia son normales y no se supone un ID constante. Compruebe la BD:

~~~powershell
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT id,estado,total FROM factura_venta WHERE id=$idFactura;"
psql -h 127.0.0.1 -p $env:PGPORT -U $env:DB_USER -d facturacion -c "SELECT * FROM factura_detalle WHERE factura_id=$idFactura;"
Invoke-RestMethod -Uri "$api/$idFactura" | ConvertTo-Json -Depth 8
~~~

## Actualizar y anular

~~~powershell
$cambio = Get-Content .\examplesctualizar-factura.json -Raw -Encoding UTF8
Invoke-RestMethod -Uri "$api/$idFactura" -Method Put -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cambio)) | ConvertTo-Json -Depth 8
$baja = Invoke-WebRequest -Uri "$api/$idFactura" -Method Delete -UseBasicParsing
$baja.StatusCode
Invoke-RestMethod -Uri "$api/$idFactura" | ConvertTo-Json -Depth 8
~~~

PUT deja un renglón, cliente 2 y total 884.00. DELETE devuelve 204 y GET muestra ANULADA
con fecha. Puede repetir la consulta SQL y verificar que la fila todavía existe.

## Provocar un error

~~~powershell
$objeto = $json | ConvertFrom-Json
$objeto.detalles[0].cantidad = 0
$invalido = $objeto | ConvertTo-Json -Depth 8
try {
    Invoke-RestMethod -Uri $api -Method Post -ContentType 'application/json' -Body $invalido
} catch {
    [int]$_.Exception.Response.StatusCode
    $_.ErrorDetails.Message
}
~~~

Debe obtener 400; no confunda el error HTTP esperado con una caída del servidor.
Un GET del ID 999999999 muestra 404. Un PUT completo sobre la anulada muestra 409.

## Ejecutar la suite

~~~powershell
.\scripts\probar.ps1
~~~

La suite abre HTTP 18080 y usa facturacion_test, esquema tp_pruebas. No depende de que
el servidor de demostración 8080 esté arrancado. Antes de cada prueba elimina facturas
de su esquema y verifica el sufijo _test del nombre de base. No apunte la variable de
pruebas a una base de trabajo.

CalculadoraImportesTest comprueba fórmulas y redondeo sin Spring ni BD.
FacturaIntegracionTest inicia la aplicación y envía HTTP con java.net.http.HttpClient;
consulta filas mediante JdbcTemplate. No usa @Transactional en la prueba porque una
transacción externa podría ocultar el comportamiento real de cada petición.

La prueba de rollback instala temporalmente una función y trigger solo en tp_pruebas.
El segundo detalle con cantidad 13 es rechazado por PostgreSQL con una violación simulada.
La API devuelve 409; nuevas consultas deben contar cero cabeceras y cero detalles.
Otra prueba inicia una factura correcta y provoca el mismo fallo al actualizar: debe
conservar los dos detalles y el total original. El trigger se retira al terminar.

Los informes se encuentran en target/surefire-reports. Sin TEST_DB_URL se ejecutan solo
las unitarias y la integración se informa como omitida. VERIFICACION.md registra la
auditoría real de esta entrega y no debe reemplazarse por una suposición de éxito.

# 11. Ejercicios para aprender

Realice cambios en una copia del proyecto. Primero prediga qué debería pasar; después
ejecute una petición o una prueba. Las soluciones explican el razonamiento, no solo un
fragmento para copiar.

## 1. Cambiar una validación

Ejercicio: limite observaciones a 500 caracteres e identifique todos los lugares afectados.
Solución razonada: cambie @Size del RequestDTO. Si también quiere reducir almacenamiento,
agregue una migración V3 para varchar(500) y ajuste @Column. Antes revise textos existentes.
Una prueba HTTP con 501 caracteres debe devolver 400 y no escribir datos. Cambiar solo
el HTML futuro no protegería la API de clientes directos.

## 2. Agregar un atributo

Ejercicio: agregue referenciaExterna opcional a la factura.
Solución: defina longitud, agregue columna mediante nueva migración, campo de entidad,
entrada DTO y salida DTO. Ajuste actualizarCabecera y mapper. Explique qué ocurre si el
cliente lo omite en PUT completo. Pruebe guardar y volver a consultar. No es suficiente
agregar una columna SQL porque el contrato y el objeto Java quedarían desconectados.

## 3. Crear una consulta Repository

Ejercicio: obtener clientes por fragmento de nombre.
Solución: agregue findByNombreContainingIgnoreCase(String nombre) a ClienteRepository.
Spring Data interpreta la propiedad nombre y el operador. La prueba debe incluir coincidencias
y ausencias. No hace falta escribir una implementación manual. Una propiedad mal escrita
puede producir un fallo de arranque que debe corregirse leyendo la excepción.

## 4. Crear un endpoint sencillo

Ejercicio: publicar GET /api/clientes con ID y nombre.
Solución: agregue un DTO de cliente y un servicio de lectura que consulte el repositorio y
mapée. El controlador delega al servicio; no exponga Cliente directamente. Decida si la
lista debe paginarse según el tamaño. Mantenga esta extensión fuera del alcance entregado
si solo está practicando.

## 5. Escribir una consulta SQL

Ejercicio: sumar ventas emitidas por cliente.
Solución:

~~~sql
SELECT c.id,c.nombre,SUM(f.total) AS total_emitido
FROM cliente c JOIN factura_venta f ON f.cliente_id=c.id
WHERE f.estado='EMITIDA'
GROUP BY c.id,c.nombre ORDER BY c.id;
~~~

Explique que mezclar monedas haría esa suma conceptualmente incorrecta. Una solución
más completa agrupa también tipo_moneda_id o filtra una moneda. El motor puede sumar
números válidos aunque la interpretación comercial sea inválida.

## 6. Modificar una regla de negocio

Ejercicio: impedir facturas con fecha posterior al día actual.
Solución: primero reconozca que es una regla nueva, no una exigencia original. Compare
con LocalDate.now(reloj) en servicio o use una restricción adecuada con reloj de validación
configurado. Pruebe pasado, hoy y mañana. El reloj inyectable permite fijar el tiempo de
una prueba sin depender del día en que se ejecuta.

## 7. Explicar una transacción

Ejercicio: dibuje qué ocurre si se guarda cabecera y primer detalle, pero falla el segundo.
Solución: BEGIN -> INSERT cabecera -> INSERT detalle 1 -> error detalle 2 -> ROLLBACK.
No debe quedar ninguno confirmado. Los valores de secuencia consumidos pueden no
reutilizarse. Localice la prueba real y explique por qué no solo simula una validación Java.

## 8. Detectar un error de relación

Ejercicio: imagine quitar d.asociarA(this) de reemplazarDetalles.
Solución: mappedBy declara a FacturaDetalle como dueño de la FK; la lista inversa por sí sola
no fija factura_id. El INSERT podría fallar por FK null. El arreglo consiste en mantener las
dos direcciones, no en permitir factura_id nullable. Use la prueba de cascada para detectarlo.

## 9. Comprender orphanRemoval

Ejercicio: reemplace dos renglones por uno y prediga las filas.
Solución: la misma factura sigue existiendo; los dos detalles viejos se eliminan y queda uno
nuevo con otra PK. Si desactivara orphanRemoval sin implementar otra estrategia, podrían
persistir renglones retirados o romperse la relación. No confunda este ejercicio con DELETE
de factura, que en este proyecto solo anula.

## 10. Razonar sobre dinero

Ejercicio: compruebe 0.125 x 1.00 y 21% con el criterio elegido.
Solución: neto 0.13; IVA 0.03; total 0.16. Cambiar el orden de redondeo equivale a cambiar
el contrato matemático. Añada un caso de prueba antes de modificar la calculadora.

# 12. Preparación para defender el TP

## Guion de exposición de 5 a 10 minutos

Minuto 0 a 1: "El problema es registrar facturas con varios renglones y mantener coherencia
entre datos relacionados e importes. Entrego backend y PostgreSQL; el frontend queda
para otra etapa". Muestre el alcance y dos decisiones: precios del servidor y anulación.

Minuto 1 a 2: muestre el diagrama de capas y el ER. Explique la separación Controller,
Service y Repository, y que factura-detalles forma un agregado. Abra FacturaVenta y
señale la colección, mappedBy, cascada y orphanRemoval.

Minuto 2 a 4: ejecute POST del archivo de ejemplo. Muestre 201, Location y el total 3083.00.
Abra PostgreSQL y señale una cabecera y dos detalles. Explique de dónde salió cada precio
y cómo se calculó neto 2600.00 e IVA 483.00.

Minuto 4 a 6: ejecute GET y PUT. Compruebe el nuevo total 884.00 y la sustitución de
renglones. Luego DELETE: 204 sin cuerpo y GET con ANULADA y fecha. Muestre que la fila
se conserva.

Minuto 6 a 8: envíe una cantidad cero y muestre 400. Explique el 404 de un ID ausente y
el 409 al modificar una anulada. Ejecute las pruebas o muestre el informe real y la prueba
de rollback. Si la red o la terminal fallan, describa exactamente la limitación; no invente éxito.

Minuto 8 a 10: explique JPA frente a Hibernate, DTO frente a entidad y transacción frente
a cascada. Termine enumerando lo pendiente del frontend y responda preguntas.

## Código que conviene tener abierto

FacturaRestController.crear para HTTP; FacturaService.crear/aplicarContenido para reglas;
FacturaVenta.reemplazarDetalles para relación y sumas; CalculadoraImportes.calcular para
dinero; FacturaIntegracionTest.rollbackRealSiPostgresRechazaSegundoDetalle para atomicidad.
No intente mostrar todos los getters en la exposición.

## Preguntas posibles y respuestas razonadas

### 1. ¿Por qué Spring Boot?

Reduce configuración de infraestructura y permite una aplicación ejecutable con versiones coordinadas. Seguimos escribiendo y probando las reglas: Boot no decide cómo facturar.

### 2. ¿Qué diferencia hay entre Spring y Spring Boot?

Spring aporta el contenedor, MVC y transacciones. Boot ayuda a configurarlos y arrancarlos por convenciones según dependencias y propiedades.

### 3. ¿Qué es una API REST?

Es un contrato de recursos y operaciones que usa HTTP. En el TP las URI representan facturas y los métodos indican consulta, creación, actualización o baja lógica.

### 4. ¿Qué hace Controller?

Adapta HTTP: ruta, DTO de entrada, validación y estado de respuesta. Delega reglas al servicio para que no dependan del protocolo.

### 5. ¿Por qué separar Controller y Service?

Porque seleccionar 201 no es lo mismo que resolver precios o garantizar atomicidad. Separarlos facilita razonar, cambiar el cliente y probar reglas.

### 6. ¿Para qué sirve Repository?

Da acceso a entidades mediante JPA. Spring Data genera implementación de operaciones y consultas; el repositorio no elige si una factura anulada puede editarse.

### 7. ¿Qué es JPA?

Es la especificación de persistencia Java. Define el vocabulario de entidades y relaciones que Hibernate implementa.

### 8. ¿Qué hace Hibernate?

Administra entidades y convierte operaciones/cambios en SQL. También detecta cambios y propaga cascadas; PostgreSQL ejecuta SQL y aplica restricciones.

### 9. ¿Por qué DTOs?

Para publicar un contrato controlado. El cliente envía IDs y cantidades; no puede imponer total ni necesita conocer asociaciones JPA.

### 10. ¿Qué diferencia hay entre Request y Reporte?

Request representa decisiones permitidas al cliente. Reporte muestra identidad asignada, estado y cálculos del servidor; ambos son datos, no entidades administradas.

### 11. ¿Qué significa cascada?

Que una operación de persistencia sobre la cabecera se propaga a los detalles según su mapeo. No es la propiedad que hace rollback: esa corresponde a la transacción.

### 12. ¿Para qué @Transactional?

Para delimitar una unidad de trabajo que confirme todos los cambios o los revierta si se propaga un fallo. Se coloca en servicio porque allí se coordinan múltiples operaciones.

### 13. ¿Qué ocurre si falla un detalle?

Si el fallo se propaga dentro de la transacción, se revierte cabecera y los otros detalles. Lo comprobamos con un error real de PostgreSQL en el segundo renglón.

### 14. ¿Cómo se relacionan las tablas?

La cabecera tiene FK a catálogos; cada detalle tiene FK a su factura y al precio utilizado. El precio tiene FK al artículo y a la moneda.

### 15. ¿Por qué BigDecimal?

Porque representa decimales con precisión controlada y una regla explícita de redondeo. double introduce aproximaciones binarias inadecuadas para esta política monetaria.

### 16. ¿De dónde salen precio e IVA?

Del par artículo/moneda en ListaPrecioArticulo y de Articulo.alicuotaIva. Los valores de la semilla son ficticios y no una interpretación fiscal del TP.

### 17. ¿Cómo se calcula el total?

Se calcula neto e IVA redondeados por renglón, se suman y se obtiene el total de cabecera. La fórmula y el orden de redondeo están en CalculadoraImportes.

### 18. ¿Qué sucede al anular?

Cambia estado y se registra Instant UTC. No se borra la historia ni se recalculan importes. GET sigue mostrando el recurso; editarlo produce 409.

### 19. ¿Por qué DELETE no borra físicamente?

El enunciado admite anulación. Elegimos conservar historia y documentamos esa semántica HTTP; la respuesta exitosa sigue siendo 204 sin cuerpo.

### 20. ¿Qué ocurre si repito DELETE?

Devuelve 204 y conserva la fecha original. Es una política idempotente explícita para evitar múltiples fechas de anulación.

### 21. ¿Qué hace orphanRemoval?

Borra los detalles retirados de la colección administrada durante PUT completo. No borra catálogos ni significa anulación de cabecera.

### 22. ¿Por qué no hay repositorio de detalle?

Porque se administra a través del agregado factura y se persiste por cascada. Un CRUD independiente podría permitir renglones incoherentes con los totales.

### 23. ¿Por qué no devolvemos entidades?

Pueden exponer campos internos, relaciones recursivas y carga LAZY fuera de contexto. El mapper crea respuestas estables dentro de la transacción.

### 24. ¿Qué diferencia hay entre flush y commit?

Flush sincroniza cambios con la BD dentro de la transacción. Commit confirma. Un error posterior al flush todavía puede causar rollback antes del commit.

### 25. ¿Puede faltar un número de ID después de fallar?

Sí. Las secuencias pueden avanzar aunque se reviertan las filas. El ID técnico no garantiza numeración fiscal sin huecos.

### 26. ¿Para qué Flyway si está Hibernate?

Flyway versiona la creación y evolución SQL; Hibernate valida el mapeo y opera con entidades. Evitamos que cada arranque modifique tablas de manera implícita.

### 27. ¿Cómo evitamos modificaciones simultáneas?

PUT y DELETE bloquean la cabecera en la transacción; @Version agrega detección de versión. No implementamos comparación de versión del formulario del cliente.

### 28. ¿Por qué 400, 404 y 409?

400 indica entrada inválida; 404, recurso ausente; 409, conflicto con reglas o integridad del estado actual. Diferenciarlos permite al cliente reaccionar correctamente.

### 29. ¿Qué falta para frontend?

Interfaces TypeScript espejo, cliente fetch, formularios, tabla de renglones y filtros visuales. La API y sus contratos ya permiten esa siguiente etapa.

### 30. ¿La suite prueba una BD en memoria?

No. Usa PostgreSQL instalado, HTTP real y una base exclusiva. Si no se configura, la integración queda omitida y no se afirma que haya pasado.

## Fuentes técnicas y límites

El enunciado PDF es la fuente del alcance funcional. Las decisiones no definidas se registran
en ANALISIS_REQUISITOS.md. Para profundizar, consulte documentación primaria:

- Spring Boot: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Framework, transacciones: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- Jakarta Persistence: https://jakarta.ee/specifications/persistence/
- Hibernate ORM: https://hibernate.org/orm/documentation/
- PostgreSQL: https://www.postgresql.org/docs/18/
- Maven: https://maven.apache.org/guides/

Las explicaciones se basan en el código que acompaña a este manual. Las futuras mejoras
deben reflejarse en pruebas, migraciones y documentación. No se promete cumplimiento fiscal
ni una arquitectura destinada a grandes volúmenes: se prioriza aprender un backend real
con responsabilidades claras y evidencia verificable.
