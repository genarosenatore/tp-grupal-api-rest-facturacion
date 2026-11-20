# TP CRUD de facturas de venta

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Proyecto académico UTN: API REST de facturas, detalles, cálculo de importes y PostgreSQL.
Incluye un frontend HTML5, CSS3 y TypeScript separado en `frontend/`, conectado a la API real.
Incluye gestión en modales, filtros paginados, anulación auditada y descarga de PDF.
El transporte fiscal está pendiente de definir/configurar: por defecto ARCA rechaza cualquier
intento de envío sin alterar la factura. No se emiten comprobantes reales con esta instalación.
El contrato vigente y los bloqueos se detallan en [CAMBIOS_FACTURACION.md](CAMBIOS_FACTURACION.md).
El PDF del TP exige completar componentes existentes; aquí también se construyen esos componentes desde cero.

## Tecnologías verificadas

- JDK y compilador Java 26.
- Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA, Hibernate y Jakarta Validation.
- PostgreSQL 18.6; Flyway para migraciones SQL versionadas.
- Maven 3.9.11, disponible en el IntelliJ instalado; JUnit y AssertJ para pruebas.
- Frontend HTML5, CSS3, TypeScript estricto, Fetch API y Vite vanilla, sin React/Angular/Vue.
- Node.js 22.12 o posterior y npm para compilar el frontend; no requiere Lombok ni Docker.
- ReportLab y Python se utilizan solamente para generar el manual; no para ejecutar Java.

Compatibilidad: https://docs.spring.io/spring-boot/system-requirements.html
El proyecto fija sus versiones en pom.xml. El JAR requiere Java 26.
En esta computadora JAVA_HOME no estaba definido y Maven no estaba en PATH; los scripts resuelven
las rutas de Java 26 y del Maven incluido en IntelliJ sin modificar variables globales.

## Estructura

~~~
src/main/java/ar/edu/utn/facturacion/
  FacturacionApplication.java
  config/        Reloj UTC inyectable
  controller/    HTTP y códigos de respuesta
  service/       Reglas, cálculos y transformación a DTO
  repository/    Consultas JPA
  entity/        Mapeo de las ocho tablas
  dto/           Contrato JSON y validaciones
  exception/     Excepciones y manejo centralizado
src/main/resources/
  application.properties
  db/migration/  V1 estructura; V2 datos iniciales; V3 nombres de catálogos
  static/        Interfaz JavaScript de la etapa anterior, conservada
frontend/
  index.html     Búsqueda, alta y listado
  src/main.ts    Inicio y estilos
  src/app.ts     Eventos, CRUD, renderizado, filtros y diálogos
  src/invoice-form.ts  Detalles en memoria, validaciones y vista previa
  src/models/    Espejos de los DTOs Java
  src/services/  FacturaService, catálogos y fetch centralizado
  src/utils/     DOM, validadores, importes y formatos argentinos
  src/styles/    CSS responsive
  tests/         Contratos HTTP y pruebas de navegador
src/test/        Pruebas unitarias e integración PostgreSQL
scripts/        Ejecución y configuración para PowerShell
sql/            Creación de bases y consultas de inspección
examples/       JSON listos para usar
docs/           Análisis, manual, demostración y resultados
~~~

Arquitectura: frontend web -> controller -> service -> repository -> Hibernate/JPA -> PostgreSQL.
El mapper produce DTOs dentro de la transacción. No se serializan entidades JPA.

## Inicio rápido con PostgreSQL ya instalado

Abra PowerShell en la carpeta que contiene este README. Si bloquea scripts descargados,
revise los archivos y ejecute Unblock-File sobre los scripts o use PowerShell con
ExecutionPolicy Bypass solo para esa ejecución. No cambie la política global.

La opción más sencilla reutiliza los binarios PostgreSQL instalados y crea un clúster local
exclusivo en .local/pgdata, escuchando solo en 127.0.0.1:55432. No reinstala PostgreSQL
ni modifica el servicio existente en 5432.

~~~
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1
~~~

El primer comando crea facturacion y facturacion_test y genera una contraseña aleatoria
local. El segundo inicia Spring Boot en http://127.0.0.1:8080.
Si existe dist/facturacion-1.0.0.jar, ejecutar.ps1 usa ese JAR ya compilado y no necesita
descargar dependencias para iniciar. Después de editar Java, ejecute
ejecutar.ps1 -DesdeFuentes para compilar y ejecutar los fuentes actuales con Maven.
Flyway crea las tablas y los datos al iniciar Java; Hibernate valida que el esquema coincida.
Mantenga esa terminal abierta. En otra terminal ejecute los ejemplos de docs/DEMO_PRESENTACION.md.

## Interfaz web

### Frontend actual: TypeScript (consigna nueva)

Instale Node.js LTS 22.12 o posterior desde https://nodejs.org/ (incluye npm).
Compruebe `node --version` y `npm.cmd --version` en una terminal nueva.
Use tres terminales PowerShell desde la raíz del proyecto:

~~~powershell
# Terminal 1: PostgreSQL local (el comando termina y deja la BD iniciada)
.\scripts\postgres-local.ps1 iniciar
# Terminal 2: Spring Boot, mantener abierto
.\scripts\ejecutar.ps1 -DesdeFuentes
# Terminal 3: frontend, mantener abierto
cd frontend
npm.cmd ci
npm.cmd run dev
~~~

Abra **http://127.0.0.1:5173**. `npm.cmd` evita depender de la política de ejecución de npm.ps1.
El comando `npm.cmd ci` instala las versiones del lockfile. Puede usar `npm.cmd install`
si está modificando dependencias. Para verificar y compilar:

~~~powershell
cd frontend
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
~~~

Las pruebas de navegador usan Microsoft Edge instalado, requieren Spring Boot y PostgreSQL
iniciados y generan una factura real de prueba que queda anulada. Playwright inicia Vite si falta.
No ejecute el flujo de demostración contra una base productiva.
`npm.cmd run build` verifica TypeScript y genera `frontend/dist/`. `npm.cmd run preview`
sirve ese build en http://127.0.0.1:4173 con el mismo proxy de API para revisarlo localmente.
Para producción, configure el servidor que publica `frontend/dist` para reenviar `/api` a Spring Boot.

La configuración se centraliza en `frontend/vite.config.ts` y `src/services/http.ts`.
Opcionalmente copie `frontend/.env.example` a `frontend/.env.local` y ajuste
`API_PROXY_TARGET` si Java escucha en otro puerto. El navegador consume `/api` en el mismo
origen y Vite reenvía a http://127.0.0.1:8080: no se necesita modificar CORS.
`VITE_API_BASE_URL` permite una API externa, que requeriría configurar CORS de forma restringida.
Nunca ponga contraseñas de PostgreSQL en variables `VITE_`.

`models/factura.dto.ts` refleja los records Java: fechas LocalDate como strings ISO,
IDs y BigDecimal serializados como números, campos anulables y detalles históricos.
`FacturaFiltrosDTO` representa exclusivamente los cuatro parámetros GET reales.
El request completo y el de estado se tipan por separado para evitar mezclar ambos modos de PUT.
`FacturaService` ofrece obtenerFacturas, obtenerFactura, crearFactura, actualizarFactura,
actualizarEstado y eliminarFactura. El helper HTTP controla errores, timeout, JSON y 204.
`app.ts` conecta formularios y servicio con addEventListener; `invoice-form.ts` mantiene
los renglones en memoria. El backend resuelve precios y calcula los importes definitivos.

Flujo: HTML -> evento -> app.ts -> FacturaService -> fetch -> controller Java -> service
transaccional -> repository/JPA -> PostgreSQL -> DTO JSON -> renderizado del DOM.
PUT de estado usa `{estado, observaciones}`; PUT completo manda cabecera y todos los detalles.
DELETE anula lógicamente y se ofrece como **Anular factura**, con confirmación.
No hay reactivación de anuladas ni dos botones para la misma operación.

Demostración completa y comprobación SQL: `docs/DEMO_FRONTEND.md`.
Guía didáctica: `docs/GUIA_FRONTEND.md`. Manual adicional:
`docs/GUIA_TEÓRICO_PRACTICA_FRONTEND.pdf`. Resultados nuevos: `docs/VERIFICACION_TYPESCRIPT.md`.

### Interfaz anterior conservada en Spring Boot

Abra http://127.0.0.1:8080 en el navegador después de iniciar la aplicación.
Para trabajar con los últimos fuentes, use `.\scripts\ejecutar.ps1 -DesdeFuentes`.
La interfaz y la API comparten servidor y origen; no requieren CORS ni un servidor Node.

- Facturas: búsqueda por nombre o identificador, filtros de cliente, estado y fechas,
  paginación visual y resumen de los resultados. El importe emitido suma exclusivamente
  facturas vigentes en la moneda elegida, sin convertir ni mezclar monedas.
- Alta y edición: selección de catálogos reales, fecha, moneda, observaciones y entre
  1 y 100 artículos con cantidades de hasta tres decimales. La vista previa usa aritmética
  decimal entera y redondeo HALF_UP; el servidor confirma los totales al guardar.
- Consulta: detalles e importes históricos. Anulación con confirmación, conservando
  el historial e impidiendo editar comprobantes anulados.
- Clientes y artículos: vistas de consulta de los catálogos y precios vigentes.
  Los catálogos no ofrecen alta, modificación ni eliminación.
- Diseño adaptable a móvil, navegación por teclado, diálogos y mensajes de carga,
  errores y resultados vacíos. No guarda facturas ficticias en el navegador.

`GET /api/catalogos` devuelve clientes, condicionesIva, puntosVenta, monedas,
articulos y precios como DTOs de solo lectura. El frontend usa las rutas de facturas
documentadas más abajo. El proyecto es una demostración local sin autenticación.

Verificación adicional de la vista previa de importes (requiere Node solo para esta prueba):

~~~powershell
node --test src/test/frontend/importes.test.mjs
~~~

Estado y apagado del clúster exclusivo:

~~~
.\scripts\postgres-local.ps1 estado
.\scripts\postgres-local.ps1 detener
~~~

Detenga Java con Ctrl+C antes de detener la BD. El usuario facturacion_app del clúster local
es el administrador técnico creado por initdb: se usa solo para esta demostración local.
En el servidor existente, la siguiente opción crea una cuenta LOGIN que no es superusuario.

## Usar el servidor PostgreSQL existente en 5432

Esta alternativa requiere su contraseña de administrador, ingresada localmente.
No es necesario crear otro clúster si elige esta opción.

~~~
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -W -v ON_ERROR_STOP=1 -f .\sql\crear-bases.sql
.\scripts\configurar-conexion.ps1 -Puerto 5432 -Usuario facturacion_app
.\scripts\ejecutar.ps1
~~~

El SQL crea el rol y las dos bases; pide una contraseña NUEVA para facturacion_app.
No repita ese SQL si ya existen. No contiene DROP DATABASE ni borra bases previas.
configurar-conexion.ps1 pide esa nueva contraseña y guarda las variables en .local/conexion.ps1.
No es la contraseña de postgres. Si ya usa la opción local, conserve su configuración
antes de cambiarla: ambas alternativas escriben el mismo archivo de conexión.

También puede configurar variables manualmente:

~~~
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/facturacion'
$env:DB_USER = 'facturacion_app'
$credencial = Get-Credential -UserName facturacion_app -Message 'Usuario de la aplicación'
$env:DB_PASSWORD = $credencial.GetNetworkCredential().Password
.\scripts\maven.ps1 spring-boot:run
~~~

DB_PASSWORD no tiene valor por defecto ni está guardada en el código. .local está excluida
de Git y del ZIP. Es configuración local sensible: no la envíe al profesor.
Para otro puerto HTTP defina PORT antes de iniciar.

## Datos iniciales de los catálogos

V2 carga los registros iniciales y V3 actualiza sus nombres. La actualización también se
aplica a bases existentes al iniciar la aplicación y conserva los identificadores e importes.
Los clientes son ejemplos ficticios con nombres y documentos de formato habitual.

| Catálogo | Registros |
|---|---|
| Clientes | 1 Ana Martínez; 2 Bruno Fernández |
| Punto de venta | 1, código 0001, Casa central |
| Condiciones IVA | 1 Consumidor final; 2 IVA responsable inscripto |
| Monedas | 1 ARS, Peso argentino; 2 USD, Dólar estadounidense |
| Artículos | 1 Cuaderno A4 rayado de 80 hojas; 2 Lápiz negro HB; 3 Carpeta A4 con elástico (sin precio cargado) |
| Precios ARS | artículo 1: 1000.00; artículo 2: 200.00 |
| Precios USD | artículo 1: 2.00; artículo 2: 0.50 |
| IVA por artículo | artículo 1: 21.00%; artículo 2: 10.50%; artículo 3: 0.00% |

Los porcentajes son datos didácticos configurables, no reglas fiscales deducidas del TP.
CondicionIva clasifica la factura, pero no altera la alícuota en esta implementación.
ListaPrecioArticulo tiene una fila vigente por par (artículo, moneda); la API recibe articuloId
y resuelve esa fila con tipoMonedaId. No necesita recibir un precio ni un objeto JPA.

## Contrato HTTP

| Método | Ruta | Resultado |
|---|---|---|
| GET | /api/facturas | 200 y array; incluye anuladas |
| GET | /api/facturas/{id} | 200 y FacturaReporteDTO |
| POST | /api/facturas | 201, Location y FacturaReporteDTO |
| PUT | /api/facturas/{id} | 200 y FacturaReporteDTO actualizado |
| DELETE | /api/facturas/{id} | Anulación lógica; 204 sin cuerpo |

GET admite clienteId, estado (EMITIDA/ANULADA), desde y hasta (YYYY-MM-DD, inclusivos).
Sin filtros devuelve todos los registros por id descendente. Se mantiene sencillo y sin paginación
por el tamaño académico del TP; para grandes volúmenes habría que paginar.

POST y PUT completo reciben:

~~~json
{
  "fecha": "2026-10-23",
  "clienteId": 1,
  "condicionIvaId": 1,
  "puntoVentaId": 1,
  "tipoMonedaId": 1,
  "observaciones": "Ejemplo",
  "detalles": [
    {"articuloId": 1, "cantidad": 2},
    {"articuloId": 2, "cantidad": 3}
  ]
}
~~~

Respuesta: id y version asignados por el servidor, fecha, IDs de cabecera, clienteNombre,
moneda, estado EMITIDA, fechaAnulacion null, observaciones, neto 2600.00, iva 483.00,
total 3083.00 y los dos detalles con sus IDs, articuloId, listaPrecioArticuloId, descripción,
cantidad, precioUnitario, alicuotaIva y sus tres importes.
Vea docs/RESPUESTA_REAL.json para una respuesta capturada al verificar el proyecto.

PUT completo reemplaza TODOS los detalles: los omitidos se eliminan con orphanRemoval,
los enviados se crean de nuevo con nuevos IDs y se usan precios/IVA vigentes para recalcular.
No es un PATCH y no conserva los IDs de los renglones. Permite editar solo EMITIDAS.
El id del comprobante es técnico, no una numeración fiscal.

PUT también admite exclusivamente estado y observaciones:

~~~json
{"estado":"ANULADA","observaciones":"Anulada por un error en la carga"}
~~~

No se permite mezclar este modo con campos de cabecera o detalles.
El estado inicial de POST siempre lo asigna el servidor; no envíe estado.
Enviar EMITIDA sobre una EMITIDA permite cambiar observaciones; no reactiva anuladas.
Una ANULADA puede consultarse, pero no editarse ni reactivarse. DELETE repetido retorna 204
y conserva la fecha original. PUT ANULADA repetido sin observaciones es idempotente;
si intenta cambiar sus observaciones devuelve 409.

## Cálculos y restricciones

BigDecimal en Java, numeric en PostgreSQL; nunca double.
Por detalle: neto = cantidad * precio, redondeado a 2 decimales HALF_UP;
IVA = neto redondeado * alícuota / 100, redondeado a 2 decimales HALF_UP;
total = neto + IVA. La cabecera suma los netos e IVA ya redondeados de todos los detalles.
No hay descuentos, conversión monetaria, reglas fiscales automáticas ni movimientos de stock.
Se guardan copias históricas de descripción, precio y alícuota en cada detalle.

IDs positivos; fecha y relaciones obligatorias; entre 1 y 100 detalles; cada detalle no nulo;
cantidad entre 0.001 y 999999.999, máximo 3 decimales; observaciones hasta 1000 caracteres.
JSON desconocido, tipos inválidos y enum inválido se rechazan. El cliente no fija los totales.

## Errores

| HTTP | Situación |
|---|---|
| 400 | Validación, JSON/tipo inválido, filtros incoherentes o mezcla de modos PUT |
| 404 | Factura, cliente, moneda, condición, punto o artículo inexistente |
| 409 | Artículo sin precio en moneda elegida; modificación de anulada; integridad/concurrencia |

Los errores controlados usan ErrorDTO: timestamp, status, mensaje y errores (lista).
No se exponen SQL, credenciales ni stacktraces.
Errores inesperados conservan la respuesta de error de Spring; se investigan en los logs.

## Transacciones e integridad

Crear, actualizar y anular ejecutan @Transactional en el servicio.
CascadeType.ALL guarda cabecera y detalles; mappedBy indica que la FK la posee el detalle.
orphanRemoval elimina los renglones retirados al reemplazarlos. Las consultas se mapean
dentro de una transacción de solo lectura con open-in-view desactivado.
PUT y DELETE bloquean la factura con PESSIMISTIC_WRITE para serializar escrituras;
@Version agrega control de versión de la entidad. No se exige version en el JSON del cliente.
Las FK de catálogos impiden borrar referencias usadas. DELETE HTTP no hace DELETE SQL.

## Pruebas

Pruebas unitarias (sin BD); las de integración se omiten explícitamente si no hay TEST_DB_URL:

~~~
.\scripts\maven.ps1 test
~~~

Suite completa con PostgreSQL iniciado y las bases creadas:

~~~
.\scripts\probar.ps1
~~~

La integración usa HTTP real en 127.0.0.1:18080, base facturacion_test y esquema tp_pruebas.
No la ejecute contra datos de trabajo: borra facturas de ESE esquema antes de cada caso.
Comprueba que el nombre de base termine en _test antes de limpiar registros.
Un trigger exclusivo de pruebas rechaza el segundo detalle con cantidad 13 para verificar
rollback real tanto en POST como PUT. Ese trigger NO forma parte de las migraciones.
JUnit verifica además cascada, totales, filtros, estados, errores, precios históricos y FK.
Los resultados efectivamente ejecutados se registran en docs/VERIFICACION.md.

## Compilar y ejecutar el JAR

~~~
.\scripts\maven.ps1 package
. .\.local\conexion.ps1
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\facturacion-1.0.0.jar
~~~

Configure JAVA_HOME antes del último comando si abrió otra terminal. El script maven.ps1
lo configura solo dentro de su ejecución. El ZIP incluye un JAR probado en dist.
Puede ejecutar .\dist\facturacion-1.0.0.jar con java -jar, después de importar la conexión.

## Problemas frecuentes

- mvn no se reconoce: use scripts/maven.ps1; encuentra el Maven incluido en IntelliJ.
- release 26 not supported: seleccione JDK 26 en JAVA_HOME y en el SDK del IDE.
- Connection refused: compruebe puerto, pg_isready y si usa 5432 o 55432.
- Password authentication failed: la contraseña es la de facturacion_app, no la de Windows.
- Puerto ocupado: cambie PORT para Java o Puerto al crear el clúster local.
- Variables no configuradas: importe .local/conexion.ps1 en esa terminal o ejecute ejecutar.ps1.
- Flyway validation failed: no edite migraciones ya aplicadas; agregue una nueva versión SQL.
- Permiso denegado: use una carpeta propia; los scripts no requieren instalar servicios Windows.
- Las pruebas aparecen skipped: falta TEST_DB_URL; ejecute probar.ps1 para la suite completa.
- El script local se interrumpió en su primera creación: revise .local/postgres.log y las bases;
  no borre una carpeta con datos útiles. Vea la alternativa de servidor existente.

## Documentación y alcance

docs/ANALISIS_REQUISITOS.md relaciona consigna, decisiones y archivos.
docs/GUIA_TEÓRICO_PRACTICA_FACTURACION.pdf es el manual de estudio de 12 capítulos.
docs/DEMO_PRESENTACION.md contiene la demostración lista para copiar en PowerShell.
La interfaz actual de `frontend/` implementa el TP en TypeScript. La interfaz anterior en
`src/main/resources/static` se conserva para no romper el JAR y sus pruebas previas.
No se incluyeron autenticación, reportes PDF de facturas, integración fiscal ni catálogo CRUD:
no forman parte del alcance actual. El manual PDF conserva la documentación de la etapa original del backend;
esta sección documenta la interfaz agregada.
