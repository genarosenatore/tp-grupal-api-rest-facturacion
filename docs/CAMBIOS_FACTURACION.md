# Cambios de facturación - 10/11/2026

Este documento registra la etapa del 10/11/2026. La integración fiscal avanzó el
11/11/2026: consultar [HOMOLOGACION_OPERATIVA.md](HOMOLOGACION_OPERATIVA.md) para
el estado actual, la emisión con CAE y la anulación asociada ya verificadas.

## Implementación

Se mantuvo la arquitectura HTML/TypeScript (Vite) y controller/service/repository de Spring.
No se encontró AGENTS.md aplicable en este proyecto ni una integración ARCA/AFIP previa.
El README original documentaba expresamente una demostración sin numeración fiscal.

- La pantalla inicial contiene filtros y grilla. Alta y edición comparten un dialog modal.
  Guardar cierra el formulario únicamente si POST/PUT fue exitoso, conserva filtros y página
  y vuelve a consultar el servidor. Los errores conservan el formulario y los renglones.
- Filtros combinables: número fiscal exacto, cliente, estado administrativo, fechas inclusivas
  e información ARCA (todas/informadas/no informadas). Cambiarlos reinicia la página.
  El número fiscal nunca se deduce del ID interno ni equivale por sí solo a autorización.
- Paginación SQL mediante Spring Data Page/Specification, orden único id DESC, total exacto,
  página basada en cero y tamaño limitado a 100. Carga, errores y resultados vacíos explícitos.
- Exploradores modales independientes de clientes (nombre/documento) y ListaPrecioArticulo
  (código/descripción, moneda e IVA). Utilizan los catálogos reales de solo lectura existentes.
  Los filtros internos no modifican la búsqueda de facturas. La selección completa cliente
  o agrega artículo/precio en la moneda de la factura; cancelar conserva el borrador.
  Se mantiene una lista vigente por artículo/moneda, sin inventar listas comerciales.
- El backend resuelve siempre el precio por artículo/moneda, mantiene los importes BigDecimal
  y las copias históricas del detalle. La UI admite hasta 100 renglones y tres decimales.
- DELETE elimina físicamente cabecera/detalles de borradores sin número ni autorización.
  Rechazos definitivos permiten eliminar el borrador manteniendo la respuesta auditada y
  su factura_original_id. Las anuladas conservan su historial y no se eliminan.
- POST /{id}/anular exige motivo, conserva observaciones y detalle, registra fecha y auditoría.
  Es idempotente y conserva el primer motivo/fecha. PUT estado ANULADA ya no inicia anulación.
  Las facturas autorizadas solo se anulan después de aprobar un ajuste fiscal asociado.
- Autorizadas y envíos EN_PROCESO/INCIERTA bloquean modificación y DELETE en el servicio.
  Consulta y PDF están disponibles en todos los estados.
- PDF generado en Java con PDFBox: emisor configurable, cliente/documento, condición IVA,
  punto de venta, tipo/número fiscal separados del ID, fecha/moneda, detalle histórico,
  cantidades, precio, neto, IVA y total; autorización y vencimiento cuando existen.
  Las pendientes llevan SIN VALIDEZ FISCAL en todas las páginas; las anuladas, ANULADA.
  No se generan códigos/vencimientos ficticios. Si el emisor falta, dice No configurado.
- npm run build verifica TypeScript, genera frontend/dist y publica la misma UI en los
  recursos estáticos de Spring. Recompilar el JAR después de construir el frontend.

## Contrato HTTP vigente

| Método | Ruta | Resultado |
|---|---|---|
| GET | /api/facturas?numero=&clienteId=&estado=&desde=&hasta=&informadaArca=&page=0&size=8 | Página, no array |
| GET | /api/facturas/{id} | Reporte con estado fiscal, autorización y permisos |
| POST | /api/facturas | Alta sin número ni autorización inventados, 201 |
| PUT | /api/facturas/{id} | Edición completa o actualización de observaciones permitida |
| DELETE | /api/facturas/{id} | Eliminación física de borrador permitido, 204 |
| POST | /api/facturas/{id}/anular | {"motivo":"..."}; anulación administrativa o circuito de ajuste |
| POST | /api/facturas/{id}/autorizar | Solicitar autorización al adaptador fiscal configurado |
| POST | /api/facturas/{id}/conciliar | Consultar un intento incierto, nunca reenviarlo |
| GET | /api/facturas/{id}/pdf | application/pdf, descarga y Cache-Control: no-store |

Página: `{content: [...], page: 0, size: 8, totalElements: 25, totalPages: 4}`.
informadaArca acepta true/false; omitir significa todas. Número, tamaño, página y fechas
se validan en el servidor. HTTP 400 para parámetros inválidos, 404 para inexistentes y
409 para restricciones de estado, rechazos o configuración fiscal incompleta.
Este cambio de contrato exige actualizar clientes antiguos que consumían un array o
usaban DELETE/PUT para anular. El frontend incluido ya utiliza el contrato nuevo.

## ARCA: qué está preparado y qué está pendiente

No había cliente SOAP, WSAA, certificados, CUIT emisor, tipos de comprobante ni datos de
autorización en el modelo original. No se eligió arbitrariamente un régimen fiscal.
El transporte SOAP real **no está implementado ni habilitado**: se definió el puerto
ArcaGateway y un adaptador predeterminado que falla antes de crear un envío.
Configurar solamente variables no convierte este puerto en un cliente fiscal funcional.

El servicio de orquestación ya implementa:

1. Bloqueo de escritura y registro durable de intento UUID ANTES de llamar al adaptador.
2. Un solo envío concurrente por factura; autorizar una ya autorizada es idempotente.
3. Persistencia de la respuesta saneada y los datos de autorización solo si la respuesta
   está aprobada y completa. Los rechazos no marcan la factura como informada.
4. Error de transporte, respuesta incompleta o fallo al consolidar: factura bloqueada;
   recuperación mediante consulta del mismo intento. No se reintenta automáticamente.
5. Anulación autorizada únicamente con respuesta de ajuste aprobada asociada al original.
   El CAE original se conserva; autorización/tipo/número del ajuste quedan en operacion_fiscal.
6. Auditoría de rechazo/incertidumbre, motivo y fechas. Las respuestas no se exponen en
   el reporte público y el adaptador debe excluir Token, Sign y cualquier secreto.

Para conectar el servicio real falta confirmar/proveer:

- Régimen y servicio: WSFEv1, WSMTXCA u otro; tipos de factura y de ajuste que correspondan.
- CUIT, razón social, domicilio y condición fiscal del emisor; un único emisor por instalación.
- Certificado y clave privada WSAA de **homologación**, relación del servicio y permisos.
  Deben guardarse fuera del repositorio. No ingresar secretos en variables VITE_.
- Punto de venta fiscal habilitado y su correspondencia con el catálogo local.
- Tipos de documento y condición fiscal del receptor: el catálogo actual tiene solo texto
  documento y no permite inferir con seguridad el tipo de documento/condición del cliente.
- Concepto fiscal, códigos de IVA, moneda/cotización y, si correspondiera, períodos/servicios.
  El modelo actual incluye USD pero no cotización. No asumir cotización 1 salvo reglas validadas.
- Implementar un bean ArcaGateway del servicio confirmado: autenticación WSAA; consulta del
  último comprobante y consulta de autorización; reserva durable de correlativo compartida
  entre todas las facturas por CUIT/punto/tipo; FECAESolicitar u operación correspondiente;
  conciliación por la misma reserva; ajuste con comprobante asociado y mapeo validado de tipos.
  El bloqueo por factura no sustituye la reserva global de correlativos que requiere el adaptador.
- Pruebas de contrato contra homologación con sus respuestas reales, antes de habilitar emisión.

No existe un endpoint público para cargar una supuesta autorización exitosa a mano.
El único adaptador exitoso del proyecto es el doble aislado en src/test; no se empaqueta
en el JAR ni llama servicios externos. Tampoco se usaron credenciales reales en las pruebas.

El pedido decía CAI. Para WSFEv1 con FECAESolicitar la denominación es **CAE**; CAEA y CAI
son modalidades diferentes y no se intercambian. El reporte/PDF muestra tipoAutorizacion
tal como lo devuelva el adaptador verificado. Al no existir un circuito original, no se
afirma que esta instalación esté operando WSFEv1 o tenga autorización fiscal.

Fuentes oficiales revisadas:

- https://www.arca.gob.ar/ws/documentacion/ws-factura-electronica.asp
- https://servicios1.afip.gov.ar/wsfev1/service.asmx
- https://www.afip.gob.ar/genericos/guiavirtual/consultas_detalle.aspx?id=6490326
  (la reversión de un comprobante con CAE requiere nota de crédito/débito asociada).
- https://pdfbox.apache.org/3.0/getting-started.html (dependencia Java PDFBox 3.0.8).

## Configuración del PDF

Antes de iniciar Spring:

```powershell
$env:EMISOR_RAZON_SOCIAL = 'Razón social real'
$env:EMISOR_CUIT = 'CUIT real'
$env:EMISOR_DOMICILIO = 'Domicilio real'
```

Estos campos identifican al emisor al imprimir. No habilitan emisión fiscal.
La migración V4 agrega columnas fiscales y operacion_fiscal sin asignar números ni códigos
a las facturas existentes. Conserva EMITIDA/ANULADA como estado administrativo y agrega
estadoArca independiente. No se modificaron V1-V3 ya aplicadas.

## Verificación reproducible

```powershell
cd frontend
npm.cmd test
npm.cmd run build
cd ..
.\scripts\maven.ps1 test
```

La suite nueva FacturaFlujosTest usa HTTP, Spring/JPA y H2 en memoria; no requiere PostgreSQL
ni credenciales. Usa exclusivamente un doble fiscal explícito. Comprueba filtros combinados,
paginación/orden/total, número sin autorización, DELETE y cascada, motivos/idempotencia,
bloqueo de autorizadas, ajustes asociados, rechazo, incertidumbre, conciliación sin reenvío,
concurrencia, configuración ausente y PDF pendiente/autorizada/anulada con 100 detalles.

Para ejecutar Playwright contra un Spring/H2 aislado iniciado automáticamente por JUnit:

```powershell
$env:RUN_BROWSER_TESTS = 'true'
$env:NODE_EXE = (Get-Command node.exe).Source
.\scripts\maven.ps1 test
```

Usa Edge instalado y Vite en 5173 (debe estar libre). La API usa un puerto aleatorio local.
El reporte está en tmp/playwright.log; capturas en tmp/browser-results. No toca una base de
trabajo. npm run test:e2e por sí solo sigue utilizando el backend configurado para Vite;
se recomienda la opción aislada anterior para evitar escribir en datos de trabajo.

Para PostgreSQL real, conservar la suite original con base exclusiva terminada en _test:
`.\scripts\probar.ps1`. Se ajustaron sus expectativas al nuevo contrato.

En este entorno Windows bloqueó pg_ctl.exe por Application Control incluso al ejecutar
fuera del sandbox. Por eso la migración y la suite específica de PostgreSQL quedaron
pendientes de ejecutar; las pruebas H2 no sustituyen esa verificación de Flyway/PostgreSQL.
No se ejecutaron emisiones ni anulaciones en producción, ni llamadas de homologación.

Los manuales y evidencias anteriores quedan como material histórico. Para el comportamiento
vigente, usar este documento y el README actual.

### Resultado ejecutado en esta entrega

- TypeScript estricto (tsc --noEmit): correcto.
- Pruebas frontend: 13/13 correctas (contratos HTTP, errores, importes, filtros exploradores).
- Pruebas de importes de la interfaz anterior: 4/4 correctas.
- Maven package: BUILD SUCCESS. 17 casos Java ejecutados sin fallos (4 cálculos,
  12 integración Spring/H2 y 1 lanzador de navegador); 15 casos PostgreSQL omitidos
  por falta de TEST_DB_URL operativo. La política de Windows bloqueó pg_ctl.exe.
- El lanzador ejecutó 3/3 escenarios Playwright correctos en Edge contra Spring/H2
  aislado: modales/selección/preservación de datos/CRUD/PDF; paginación/filtros/errores;
  controles visibles de factura autorizada. La última usa una fixture de UI explícita;
  los bloqueos efectivos del servidor se verificaron por separado en FacturaFlujosTest.
- PDF leído con PDFBox y renderizado a PNG: verificados visualmente el comprobante
  pendiente y la última de 9 páginas con 100 renglones y observaciones extensas.
- JAR actualizado en target y dist con frontend compilado, V4 y PDFBox; SHA256SUMS
  actualizado. Comprobado que no incluye el doble fiscal ni clases de prueba.

No se validó el transporte fiscal real ni la migración contra PostgreSQL en ejecución.
