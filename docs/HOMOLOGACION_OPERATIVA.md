# Facturas A, B y C con ARCA en homologación

> Edición pública: las referencias a una instancia ya configurada y a sus
> comprobantes describen el laboratorio privado del desarrollo. La entrega de
> fuentes no incluye sus certificados, tickets, bases, JAR ni PDF fiscales.
> Los CAE reales se omiten. Para evaluar sin ARCA, seguir
> [ENTREGA_FINAL.md](ENTREGA_FINAL.md).

## Ampliación A/B - 13/11/2026

El laboratorio admite A (1), B (6) y C (11), productos en ARS y el punto de prueba
configurado. A acepta receptores responsables inscriptos (1) o monotributistas (6),
con CUIT (80). B acepta consumidor final (5) o exento (4); este último requiere CUIT.
C conserva el escenario de consumidor final. Es un alcance de prueba, no todos los
regímenes o combinaciones de WSFEv1.

En A/B los precios de lista son netos y se suma el IVA de cada artículo. Se admiten
0%, 2,5%, 5%, 10,5%, 21% y 27%; 0% representa gravado a tasa cero, no exento ni
no gravado. Los importes se redondean por renglón y se agrupan por alícuota para WSFE.
En C los precios siguen siendo finales sin discriminar IVA. Los detalles históricos
no cambian. La anulación genera NC A (3), B (8) o C (13) asociada a la factura original.
La conciliación A/B verifica los importes y también el desglose de IVA.

El lanzador agrega un cliente identificado como ficticio con CUIT de prueba para
cargar A. Su presencia no implica inscripción ni autorización de ARCA: el servicio
de homologación puede rechazar datos de receptores que no satisfagan sus validaciones.
Para los PDF de A/B con identidad comercial de demostración se imprime el escenario
de responsable inscripto, sin cambiar el perfil ni atribuir una inscripción real al CUIT.

La ampliación se valida con pruebas de servicio/JPA, doble SOAP, navegador y PDF.
No se hicieron emisiones externas nuevas de A/B. Se usó como referencia el
[manual oficial de WSFEv1](https://www.afip.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG.pdf).

La instancia unificada de prueba incluye A/B/C, ambas listas de precios y los
comprobantes históricos. Se inicia con el JAR principal y la base `demo-h2`:

```powershell
.\scripts\ejecutar-homologacion.ps1
```

La dirección única es http://127.0.0.1:8081. La instancia utiliza las credenciales
existentes de homologación. El inicio no autoriza comprobantes; ese envío se solicita
desde el detalle. La instancia separada de A/B en 8082 quedó detenida.

## Escenario elegido con el usuario

Se sigue la página 7 de la guía docente: Factura C (11), concepto productos (1),
pesos argentinos y consumidor final. Para anular se emite Nota de Crédito C (13)
asociada a la factura original. Esto no pretende implementar todos los regímenes fiscales.

El punto 2 proviene del ejemplo de la guía. FEParamGetPtosVenta devolvió 602 Sin resultados,
pero FECompUltimoAutorizado aceptó el punto 2/tipo 11 y devolvió último número 0.
Luego se verificó una autorización efectiva con ese escenario en homologación.
No se dio de alta ni se utilizó un punto de venta de producción.

## Implementación Java/Spring

- SoapArca usa HttpClient con HTTPS, endpoints fijos de homologación, timeouts,
  redirecciones deshabilitadas y parser XML sin DTD/entidades externas.
- WsaaHomologacion carga la clave PKCS8 y el certificado local, identifica el CUIT,
  reutiliza el ticket vinculado a la huella del certificado y lo renueva con un TRA
  firmado CMS mediante Bouncy Castle 1.86. No expone Token, Sign ni clave privada.
- WsfeHomologacionGateway implementa el puerto existente: FECompUltimoAutorizado,
  FECAESolicitar, FECompConsultar y ajustes con CbtesAsoc. No ejecuta el Main del ZIP.
- ReservaFiscalService conserva una reserva por intento UUID y bloquea la serie
  ambiente/CUIT/punto/tipo. Solo una operación puede estar activa por serie.
  Los rechazos definitivos liberan la serie; una respuesta incierta conserva la reserva.
  La conciliación compara documento, fecha, número, importes, moneda y asociación del ajuste.
  Una ausencia en FECompConsultar no se interpreta como permiso para reenviar.
  Si la reserva todavía no es visible al conciliar, el intento sigue bloqueado mientras
  se espera el resultado original. Si el proceso terminó abruptamente en ese punto y
  la reserva nunca aparece, corresponde revisar la operación por su UUID y contrastar
  el estado del emisor y de la serie antes de intervenir. El laboratorio no desbloquea
  automáticamente ese caso. Cuando el propio emisor confirma que falló la consulta
  de numeración anterior a solicitar el CAE, el sistema permite reintentar.
- La factura conserva número y CAE originales al anular. El ajuste y su autorización
  quedan en operacion_fiscal y reserva_fiscal, con sus respuestas reales saneadas.
- V5 incorpora las tablas de series/reservas y los datos fiscales de receptor/ambiente.
  V1-V4 se mantienen intactas.

## Formulario, precios y PDF

El escenario original permite elegir Factura C de homologación o comprobante local sin envío.
Para C solicita tipo de documento del receptor (DNI 96, CUIT 80 o sin identificar 99)
y consumidor final (5). DNI/CUIT deben corresponder al documento del cliente.
El escenario de envío admite exclusivamente ARS y el punto de venta configurado.
El catálogo del laboratorio ofrece únicamente el punto 0002, identificado como
Entorno de prueba - Homologación y seleccionado al crear una factura.
La API también rechaza nuevas cargas o ediciones con otro punto en este entorno.
Los puntos anteriores se conservan para consultar sus comprobantes históricos;
no se cargan como opciones del laboratorio. Toda la interfaz indica que es de prueba.

En Factura C el precio de lista se toma como precio final y no se discrimina IVA:
se guarda alícuota/IVA cero, neto igual al total, conservando cantidades y precio histórico.
El modo local conserva las reglas anteriores de alícuotas y cálculo BigDecimal.
Esto está indicado en la pantalla; no se transforma una factura local ya calculada
al enviarla. Para enviarla como C hay que elegir ese tipo antes de guardar sus importes.

El PDF imprime FACTURA C, precio/cantidad/importes, CAE y vencimiento reales.
Todo comprobante autorizado en este entorno lleva HOMOLOGACIÓN - SIN VALIDEZ FISCAL.
Las anuladas también llevan ANULADA, fecha y motivo. La consulta muestra el ambiente.

## Configuración y ejecución local

Directorio privado, excluido de Git y del JAR:

```
.local/arca-homologacion/
  privada-homo.pem          clave privada; no compartir
  solicitud-homo.csr        solicitud usada en WSASS
  certificado-homo.pem     certificado emitido por Computadores Test
  ticket-wsfe.json          credenciales temporales; no compartir
  estado-homologacion.json estado de las comprobaciones
  demo-h2.mv.db             base persistente de demostración
```

El certificado de homologación creado durante el trabajo fue comprobado contra su clave,
su CUIT, su vigencia y la cadena de homologación. El P12 de producción entregado con el ZIP
no se usa en esta instalación.

Para el laboratorio ya configurado:

```powershell
.\scripts\ejecutar-homologacion.ps1
```

Abre el backend con la UI compilada en http://127.0.0.1:8081. Si el puerto está ocupado,
la instancia anterior puede seguir activa; no iniciar dos procesos sobre la misma base H2.
La base se conserva al detener/reiniciar; los catálogos de ejemplo se cargan con MERGE.
El script prefiere el JAR compilado en `target/facturacion-1.0.0.jar`. Al extraer la entrega usa `dist/facturacion-1.0.0.jar`.
El proceso y sus logs quedan registrados en tmp/homologacion-backend.*.
Detener esa instancia antes de reemplazar el JAR que está utilizando.

Para habilitar el cliente sobre PostgreSQL, configurar estas variables al iniciar:

```powershell
$env:ARCA_HOMOLOGACION_ENABLED = 'true'
$env:ARCA_HOMOLOGACION_DIRECTORIO = 'C:\ruta\privada\arca-homologacion'
$env:ARCA_HOMOLOGACION_PUNTO_VENTA = '2'
```

También configurar DB_URL/DB_USER/DB_PASSWORD, aplicar V1-V6 con Flyway y cargar el punto
0002 en el catálogo local si no existe. El número de catálogo no equivale a la validación
de un punto fiscal en producción. El adaptador no dispone de un modo de producción.

El encabezado utiliza EMISOR_RAZON_SOCIAL, EMISOR_CUIT, EMISOR_DOMICILIO,
EMISOR_CONDICION_IVA, EMISOR_INGRESOS_BRUTOS y EMISOR_INICIO_ACTIVIDADES (AAAA-MM-DD).
EMISOR_DATOS_DEMOSTRACION identifica expresamente un perfil comercial ficticio.
El script de homologación crea .local/arca-homologacion/emisor.properties con el
comercio ficticio Librería Aula TP. Son datos académicos, no una inscripción real.
El CUIT continúa siendo el del certificado personal de homologación. El perfil privado
puede reemplazarse por los datos correspondientes al emisor sin modificar el código.
Los acentos de los archivos .properties se escriben mediante escapes Unicode.

## Diseño e información de la impresión

El PDF A4 incluye letra C/código 011, numeración de 5+8 dígitos, fecha argentina,
identidad y condición de IVA del emisor, IIBB e inicio de actividades, cliente y tipo
de documento, domicilio, condición de venta, artículos, cantidades en unidades,
precios, subtotales, total en moneda y CAE/vencimiento reales. En C no se discrimina IVA.
Tiene tabla paginada, encabezado de continuación, número de página y aviso de estado
en todas las hojas. La anulación muestra motivo, fecha argentina y número/CAE/vencimiento
del ajuste aprobado, conservando por separado la autorización original.

El QR vectorial implementa la especificación oficial ARCA v1 con fecha, CUIT,
punto/tipo/número, importe, moneda PES, cotización 1, documento y CAE/CAEA.
Se genera únicamente con autorización persistida y datos completos en un ambiente
identificado. Los pendientes, dobles de prueba y monedas sin cotización almacenada
no reciben un QR inventado. El de homologación se rotula como prueba y no acredita
validez fiscal. La impresión no realiza llamadas a ARCA.

Fuente del QR: https://www.afip.gob.ar/fe/qr/documentos/QRespecificaciones.pdf
Datos del comprobante: RG 1415, Anexo II (texto actualizado de la biblioteca ARCA).
Estos campos no convierten el laboratorio en un sistema habilitado para producción.

clienteDomicilio (250 caracteres) y condicionVenta (80) se cargan al crear/editar y se
guardan por comprobante mediante V6. Rigen los mismos bloqueos después de autorizar.
Las facturas anteriores conservan esos campos sin informar; no se modifican registros
autorizados para completar una demostración. En el escenario consumidor final el
domicilio ausente se representa como NR (No requerido), sin inventar direcciones.

## Verificación registrada el 11 y el 12 de noviembre de 2026

Los resultados de la revisión posterior están en [la auditoría del TP](../AUDITORIA_TP.md).

- Autenticación WSAA real de homologación para wsfe, con el nuevo certificado.
- Consulta real de numeración y autorización desde Spring: Factura C 00002-00000001,
  importe ARS 1000.00, CAE [CAE omitido en la versión pública], vencimiento 21/11/2026.
- Repetir POST autorizar conservó el número y CAE. PUT y DELETE devolvieron 409.
- Anulación confirmada mediante un ajuste C asociado; estado final ANULADA con CAE
  original conservado. Las respuestas de la autorización y del ajuste se persistieron.
- PDF antes y después de anular: datos/CAE/vencimiento leídos y renderizados a PNG
  para revisión visual. Identificación de homologación y anulación correctas.
- TypeScript y build frontend correctos; 15 pruebas frontend correctas.
- Suite de cierre: 51 casos Java ejecutados sin fallos ni omitidos, incluidos 9 casos del cliente
  fiscal con SOAP controlado (firma CMS, renovación/caché, rechazo, concurrencia entre
  facturas, conciliación, diferencias de importes, ajuste y protección XML).
- El lanzador Java ejecutó 4 escenarios Playwright correctos. Los tests habituales
  usan H2 y dobles SOAP; no acceden a ARCA ni requieren el certificado personal.
- Los 16 casos específicos de PostgreSQL pasaron el 12/11/2026 sobre facturacion_test,
  esquema tp_pruebas, clúster exclusivo 127.0.0.1:55432. V1-V6 aplicadas y validadas.
- JAR de homologación inspeccionado: contiene V6 y el cliente Java; excluye claves,
  tickets, certificados locales y clases de prueba. SHA256SUMS actualizado.

Prueba real controlada y reanudable del laboratorio (usar solamente en esta instancia):

```powershell
.\scripts\probar-factura-c-homologacion.ps1
.\scripts\probar-factura-c-homologacion.ps1 -Anular
```

Guarda el ID en .local y lo reutiliza: no crea otra factura cada vez. Si la operación
está incierta, consulta el resultado; no reenvía. Un rechazo previo requiere revisión.
La evidencia está en .local/arca-homologacion/prueba-factura-c.json y en los PDF de tmp/pdfs.
Ese directorio privado no debe incluirse en una entrega del código.

## Revisión manual y alcance de la entrega

1. Revisión del recorrido por el usuario con la UI disponible en 8081.
2. El perfil comercial ficticio sirve para el TP. Solo si se desea usar una identidad
   comercial real se reemplazan esos datos; el laboratorio sigue siendo homologación.
3. PostgreSQL ya está validado. La demostración fiscal de 8081 conserva su base H2
   persistente y sus autorizaciones anteriores, sin trasladarlas ni volver a emitirlas.

Fuentes técnicas: guía docente página 7; manuales oficiales WSAA/WSASS y WSFEv1;
contratos SOAP publicados en wswhomo.afip.gov.ar/wsfev1/service.asmx.

### Verificación del rediseño del PDF (11/11/2026)

51 casos Java ejecutados correctamente, ninguno omitido, 15 pruebas frontend
y 4 recorridos Playwright correctos. Se verificó la decodificación del QR renderizado
y sus datos, PDF pendiente sin autorización inventada, anulación con autorizaciones
separadas, 100 renglones, observaciones largas y persistencia de datos comerciales.
Se renderizaron los PDF y se revisaron visualmente. Se descargó nuevamente la factura
existente desde 8081, sin solicitar otra autorización ni anulación a ARCA.

El 12/11/2026 se agregó el alta de productos desde el explorador: artículo y precio
se guardan atómicamente en las tablas existentes. Pruebas de código repetido,
concurrencia, rollback real en PostgreSQL y conservación de la factura correctas.
La demo conserva los nuevos productos en su base persistente entre reinicios.
