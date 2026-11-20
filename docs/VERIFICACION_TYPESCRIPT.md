# Verificación de la entrega frontend TypeScript

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Fecha: 23 de octubre de 2026, zona America/Buenos_Aires.

## Entorno y alcance realmente ejecutado

Frontend Vite vanilla 7.3.7 con TypeScript estricto. Spring Boot 4.1.2 desde fuentes,
Java 26 y PostgreSQL 18.6 en 127.0.0.1:55432. Navegador Microsoft Edge automatizado
con Playwright 1.64.1, locale es-AR y zona America/Argentina/Buenos_Aires.
La pantalla TypeScript se verificó por 127.0.0.1:5173, con proxy real a 8080.
La interfaz anterior del backend se preservó en 8080.

## Resultados

| Verificación | Ejecución / resultado |
|---|---|
| Instalación | npm install; lockfile generado; auditoría npm: 0 vulnerabilidades en ese momento |
| TypeScript y producción | npm.cmd run build: tsc --noEmit + vite build, correcto |
| Contratos, errores y cálculos | npm.cmd test: 11 aprobadas, 0 fallos |
| Backend existente | scripts/probar.ps1: 19 aprobadas, 0 fallos, 0 omitidas |
| Navegador real | npm.cmd run test:e2e: 3 aprobadas, 0 fallos en ejecución final |
| Consola del flujo CRUD | 0 pageerror y 0 mensajes console.error en el flujo real final |
| Responsive | Escritorio 1280 px y móvil 390 x 844; sin desbordamiento global |
| PostgreSQL | SELECT real de cabecera y dos detalles; salida guardada |
| PDF adicional | 21 páginas, 18 capítulos/marcadores, índice generado y páginas renderizadas a PNG para revisión visual |

No se modificó código Java, esquema, migraciones ni lógica de negocio para integrar el frontend.

## Qué verifican las 11 pruebas de contrato

GET con parámetros reales; POST con IDs/detalles sin importes; PUT de estado sin cabecera;
PUT completo con múltiples detalles; DELETE 204 sin JSON; errores HTTP 400/404/409/500;
fallo de red, HTML inesperado y JSON incompatible; validaciones de fechas/cantidades/IDs;
redondeo HALF_UP equivalente al backend. Aquí fetch se simula expresamente.

## Qué verifican las 3 pruebas de Edge

1. Carga catálogos reales, completa cabecera, agrega dos detalles y un tercero que quita,
   detecta artículo sin precio, crea por POST 201, comprueba JSON enviado y reporte,
   abre el detalle con dos renglones, cambia cantidad por PUT completo 200, cambia
   observaciones por PUT de estado, cancela una anulación y después confirma DELETE
   204. Comprueba ANULADA, ausencia de edición, filtros inclusivos por cliente/estado/fechas,
   búsqueda sin resultados, limpieza, consola y ancho móvil.
2. Conserva un borrador durante recarga de catálogos y ante errores. Intercepta sólo
   las solicitudes de ese caso para devolver 409 y luego un fallo de red.
3. Crea una factura de prueba real por API y la anula desde el diálogo de estado de la
   interfaz con PUT y un motivo. Comprueba por GET estado, fecha y que los detalles
   no cambiaron respecto del reporte de creación.

El flujo CRUD no usa fixtures ni respuestas simuladas. La última ejecución completa dejó
la factura 10 anulada por DELETE y otra factura para la prueba PUT. Ejecuciones anteriores
dejaron otros comprobantes de prueba anulados; se preservaron como historial y no se
eliminaron datos preexistentes. Las pruebas Java usan facturacion_test y su esquema propio.

## Evidencia directa en PostgreSQL

`EVIDENCIA_SQL_TYPESCRIPT.txt` registra la factura 7 de una ejecución real anterior del
mismo flujo. Cabecera: ANULADA, fecha 2026-10-23, neto 4600.00, IVA 903.00, total 5503.00,
observación de verificación y fecha de anulación. Detalles conservados:

- Cuaderno: cantidad 4, precio 1000, neto 4000, IVA 840, total 4840.
- Lápiz: cantidad 3, precio 200, neto 600, IVA 63, total 663.

Se ejecutó `scripts/verificar-frontend-sql.ps1 -FacturaId 7`. El script hace sólo SELECT.
Puede repetirlo con el ID obtenido de una ejecución nueva.

## Problemas detectados y resueltos

- El frontend anterior no cumplía TypeScript ni la separación de la nueva consigna:
  se incorporó frontend/ y se actualizó README sin borrar la interfaz anterior.
- Limpiar filtros consultaba antes del reset nativo: la prueba falló; se corrigió la
  lectura diferida y después pasó el flujo completo.
- Faltaba favicon y generaba un 404 en consola: se agregó SVG local y se verificó consola limpia.
- Al actualizar catálogos se debían preservar los borradores y refrescar opciones/precios:
  InvoiceForm.actualizarCatalogos conserva la cabecera y los renglones sin reinstalar listeners.
- Conexiones y descargas fueron bloqueadas por el sandbox; se usó ejecución autorizada
  fuera de ese entorno. No se cambió la configuración funcional del backend para sortearlo.

## Archivos y límites de la verificación

Capturas actuales: `evidencias-frontend/escritorio.png` y `movil.png`.
Manual: `GUIA_TEÓRICO_PRACTICA_FRONTEND.pdf`, fuente Markdown y script de generación.
Inventario completo y decisiones: `ANALISIS_FRONTEND_TYPESCRIPT.md`.
Pasos Windows, SQL y defensa: `DEMO_FRONTEND.md` y `GUIA_FRONTEND.md`.

No se probaron Firefox, Safari, un despliegue remoto ni una API externa con CORS.
La paginación es local, la API no pagina. number refleja números JSON y no tiene precisión
decimal ilimitada; el servidor sigue siendo autoridad. El JAR anterior no incorpora
automáticamente el build separado de Vite. No se generó una nueva distribución del JAR.
El build está disponible en frontend/dist y necesita publicación con proxy /api.
