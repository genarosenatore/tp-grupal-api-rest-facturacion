# Entrega final del TP de facturación

Versión pública preparada el 20/11/2026 (Argentina).
La validación de esta versión está en
[VERIFICACION_PUBLICACION.md](VERIFICACION_PUBLICACION.md).

## Contenido

Sistema de facturación de ventas con Spring Boot, frontend HTML/TypeScript,
PostgreSQL, migraciones Flyway V1-V11, pruebas automatizadas y ejemplos ficticios.
Incluye consultas paginadas, alta/edición de facturas, clientes, productos,
listas de precios, descuentos, anulación auditada y descarga de PDF.

Los fuentes se entregan en `entrega/github-publico` y en
`entrega/TP-Facturacion-publico.zip`. La guía de publicación está en
[PUBLICACION_GITHUB.md](PUBLICACION_GITHUB.md).
La versión pública excluye contraseñas, certificados, claves privadas, tickets,
identidad fiscal personal, bases, ejecutables anteriores y evidencia binaria local.
Los comprobantes locales no tienen validez fiscal. ARCA comienza deshabilitada.

## Ejecutar desde una copia nueva

Requisitos: JDK 26, Maven, PostgreSQL 18, Node.js 22.12 o posterior y npm.
Los scripts de Windows detectan JDK/Maven como explica el [README](../README.md).

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
cd ..
.\scripts\maven.ps1 package
.\scripts\postgres-local.ps1 iniciar
.\scripts\ejecutar.ps1
```

Abrir http://127.0.0.1:8080. El script de PostgreSQL inicializa un clúster exclusivo
con contraseña aleatoria en `.local`, escucha en 127.0.0.1:55432 y crea las bases
del proyecto. No compartir `.local`. Para un servidor existente, usar
`sql/crear-bases.sql` y `scripts/configurar-conexion.ps1` según el README.
No es necesario configurar ARCA para evaluar la aplicación local.

## Recorrido para el profesor

1. Consultar la grilla y combinar filtros, incluida la búsqueda de clientes.
2. Crear una factura: seleccionar cliente y comprobante local X, moneda y punto.
3. Agregar artículos desde una lista de precios y cambiar cantidades.
4. Aplicar un descuento; guardar y consultar los importes calculados por el servidor.
5. Editar el comprobante permitido y descargar su PDF sin validez fiscal.
6. Crear un producto con su primer precio y usarlo en otra factura.
7. Crear/editar un cliente y comprobar la conservación de su copia histórica.
8. Anular con motivo o eliminar un borrador permitido y comprobar las confirmaciones.

El diseño y la correspondencia con las consignas están en
[AUDITORIA_TP.md](../AUDITORIA_TP.md). Ese informe conserva resultados históricos;
no representa una nueva ejecución de todas las pruebas en esta fecha.

## Pruebas reproducibles

```powershell
cd frontend
npm.cmd test
npm.cmd run build
cd ..
.\scripts\maven.ps1 test
```

La suite habitual utiliza H2 y dobles fiscales, sin conectarse con ARCA.
Los casos PostgreSQL se habilitan mediante `scripts/probar.ps1` con una base
exclusiva terminada en `_test`; pueden modificar su esquema de pruebas.
Los recorridos de navegador son opcionales y se habilitan con
`RUN_BROWSER_TESTS=true` y `NODE_EXE`; requieren Edge. Ver el README.

## Alcance fiscal

El adaptador WSAA/WSFE admite A/B/C de homologación y ajustes asociados.
Los ensayos reales históricos de C se conservan únicamente en el equipo privado;
la entrega pública no incluye su base, identidad, autorizaciones ni PDF.
A/B se verifican con dobles SOAP. Para ensayos externos cada instalación necesita
sus propios certificados y configuración. Ver [HOMOLOGACION_OPERATIVA.md](HOMOLOGACION_OPERATIVA.md).
La aplicación es una demostración local sin autenticación, configurada para loopback.
