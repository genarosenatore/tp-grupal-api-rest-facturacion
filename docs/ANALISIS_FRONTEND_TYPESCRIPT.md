# Análisis del prompt nuevo y del TP original

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Fecha: 23/10/2026. Se leyó el PDF original de tres páginas
`TRABAJO PRÁCTICO CRUD FACTURA VENTA.pdf`, el prompt nuevo,
controladores, DTOs, servicio de facturas, calculadora, SQL y pruebas existentes antes
de escribir el frontend. Se conservan ANALISIS_REQUISITOS.md y todos los documentos anteriores.

## Diferencias detectadas y resolución mínima

| Situación | Decisión aplicada |
|---|---|
| Frontend previo en JavaScript dentro de Spring | Agregar frontend/ vanilla TS; documentarlo como interfaz actual y preservar la anterior |
| TP exige tres áreas | index.html contiene búsqueda, alta multirrenglón y listado |
| Java FacturaRequestDTO admite dos variantes | Tipar request completo y request de estado por separado |
| PUT ya permite estado/observaciones | Integrar ese modo sin modificar Java ni reenviar cabecera |
| PUT completo reemplaza ítems | Mantener edición completa, explicar recálculo a precios vigentes |
| DELETE es lógico | Único botón Anular factura con confirmación; conservar comprobante y detalles |
| No hay numeración fiscal ni filtro por número | Mostrar ID técnico y sólo cuatro filtros GET reales |
| Catálogos ya existen | Consumir GET /api/catalogos sin IDs fijos ni endpoints nuevos |
| Distintos orígenes de desarrollo | Proxy Vite /api; no modificar CORS ni seguridad |
| TP exige TypeScript pero JAR previo contiene JS | Abrir 5173 para la entrega TS; 8080 conserva interfaz anterior |
| PDF anterior es del backend | Generar manual frontend adicional con fuente y script independientes |

## Contrato inspeccionado

FacturaRestController: GET con clienteId Long positivo opcional, estado enum opcional,
desde/hasta LocalDate opcionales. No hay filtro remoto por ID ni paginación de API.
POST valida ValidacionCompleta, retorna 201 y Location. PUT tiene modo completo o de
estado según presencia de estado; retorna reporte 200. DELETE responde 204 sin cuerpo.

FacturaRequestDTO Java: fecha, clienteId, condicionIvaId, puntoVentaId, tipoMonedaId,
observaciones, detalles y estado. En modo completo la fecha, relaciones y detalles son
obligatorios; observaciones tiene límite 1000, máximo 100 detalles. Cada detalle contiene
articuloId y cantidad positiva, de 0.001 a 999999.999 con hasta tres decimales.

FacturaReporteDTO: id, version, fecha, clienteId, clienteNombre, condicionIvaId,
puntoVentaId, tipoMonedaId, moneda, estado, fechaAnulacion, observaciones, neto, iva,
total y detalles. Cada detalle expone id, articuloId, listaPrecioArticuloId, descripcion,
cantidad, precioUnitario, alicuotaIva, neto, iva y total. Importes BigDecimal son números JSON.

CatalogosDTO devuelve clientes, condicionesIva, puntosVenta, monedas, articulos y precios.
El precio se identifica por artículo y moneda. No se inventan entidades ni relaciones.
ErrorDTO expone timestamp, status, mensaje y errores.

## Reglas preservadas

EMITIDA permite edición; ANULADA no se modifica ni reactiva. DELETE idempotente conserva
primera fecha. PUT de estado no admite cabecera ni detalles. POST no admite estado inicial.
Los precios e IVA los calcula Java; la vista previa usa el mismo HALF_UP por renglón.
Se mantienen BigDecimal, restricciones PostgreSQL, transacciones y migraciones existentes.

## Inventario de entrega

Archivos nuevos de producto: frontend/index.html, package.json, package-lock.json,
tsconfig.json, vite.config.ts, playwright.config.ts, .env.example, public/favicon.svg,
src/main.ts, src/app.ts, src/invoice-form.ts, src/models/factura.dto.ts,
src/models/catalogos.dto.ts, src/services/http.ts, src/services/FacturaService.ts,
src/services/CatalogoService.ts, src/utils/dom.ts, src/utils/validators.ts,
src/utils/importes.ts, src/utils/formatters.ts, src/styles/style.css,
tests/contrato.test.ts y tests/browser/crud.spec.ts.

Documentación nueva: este análisis, GUIA_FRONTEND.md, DEMO_FRONTEND.md,
GUIA_TEORICO_PRACTICA_FRONTEND.md, GUIA_TEÓRICO_PRACTICA_FRONTEND.pdf,
generar_manual_frontend.py, VERIFICACION_TYPESCRIPT.md, EVIDENCIA_SQL_TYPESCRIPT.txt
y evidencias-frontend/escritorio.png y movil.png.
Nuevo script de consulta: scripts/verificar-frontend-sql.ps1.

Archivos previos modificados: README.md y .gitignore. El frontend anterior, código Java,
SQL, migraciones, ejemplos, pruebas Java, PDF previo y JAR de dist se conservan.
frontend/dist es un build generado; node_modules es instalación local, no fuente.
