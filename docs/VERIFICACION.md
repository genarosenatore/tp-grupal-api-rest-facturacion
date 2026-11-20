# Verificación ejecutada

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Fecha: 23 de octubre de 2026, zona America/Buenos_Aires.

## Entorno comprobado

- Java y javac: Oracle JDK 26, build 26+35-2893.
- Maven: Apache Maven 3.9.11 incluido en IntelliJ IDEA 2026.1.3.
- PostgreSQL: 18.6 instalado en Windows.
- Spring Boot: 4.1.2, dependencias resueltas desde Maven Central.
- Compilación: javac con release 26.

No se reinstalaron Java, PostgreSQL ni Maven ni se modificó el PATH global.
El PostgreSQL preexistente del puerto 5432 respondió "accepting connections"; su acceso
requiere contraseña y no se modificaron sus roles ni bases.

La verificación utilizó los mismos binarios PostgreSQL instalados, con un clúster exclusivo
en .local/pgdata, puerto 55432. Se crearon facturacion y facturacion_test.
La primera conserva datos de demostración; la segunda contiene el esquema tp_pruebas.
El usuario generado en ese clúster local es administrador técnico del clúster de demostración;
no se creó un superusuario en el servidor 5432.

## Compilación y suite completa

Última ejecución completa de package: 12:12:49 (hora local).

| Suite | Ejecutadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| CalculadoraImportesTest | 4 | 0 | 0 | 0 |
| FacturaIntegracionTest | 14 | 0 | 0 | 0 |
| Total | 18 | 0 | 0 | 0 |

Resultado Maven: BUILD SUCCESS. Se generó y probó el JAR ejecutable de dist.
Se excluyó Mockito del starter de pruebas porque estas pruebas no utilizan mocks;
así no necesitan adjuntar un agente a la JVM Java 26.

Se verificaron:

- Neto, IVA, total y redondeo HALF_UP.
- Creación HTTP 201, Location y cascada con dos detalles.
- Consulta individual y filtros por cliente, estado y fechas.
- Actualización de cabecera y reemplazo con eliminación de detalles huérfanos.
- Cambio de estado mediante PUT y rechazo de cuerpos que mezclan modos.
- DELETE 204, conservación histórica e idempotencia de fecha de anulación.
- Bloqueo de edición y reactivación de facturas anuladas.
- 404 para recursos inexistentes.
- 400 para cantidades, IDs, fechas, listas, filtros y JSON inválidos.
- 409 para artículo sin precio y conflictos de integridad.
- Conservación de precios históricos en GET después de cambiar el catálogo.
- Restricción FK al intentar borrar un cliente utilizado.
- Rollback real si PostgreSQL rechaza el segundo detalle al crear.
- Rollback real al actualizar, conservando contenido anterior.

La integración usa solicitudes HTTP reales, Spring, Hibernate y PostgreSQL.
No se usó H2 ni se envolvieron las pruebas en una transacción externa.
Un trigger temporal de pruebas simula un error SQLSTATE 23514 en el segundo detalle.
Esos errores intencionales aparecen en los logs y son parte de las pruebas aprobadas.

La primera ejecución sin variables de integración informó 14 casos omitidos;
esa ejecución no se tomó como evidencia de la BD. El resultado anterior corresponde
a la suite completa con TEST_DB_URL y cero omisiones.

## Prueba adicional del JAR por PowerShell

Se inició dist/facturacion-1.0.0.jar contra facturacion y se comprobó:

1. Inicio real de Tomcat y conexión autenticada.
2. Flyway creó las ocho tablas del dominio y su historial.
3. POST del ejemplo: 201, dos detalles, neto 2600.00, IVA 483.00, total 3083.00.
4. Consulta SQL de cabecera y detalles confirmados.
5. GET individual: 200.
6. PUT del ejemplo: 200, cliente 2, un detalle y total 884.00.
7. DELETE: 204 sin contenido.
8. GET posterior: ANULADA, fecha de anulación y datos conservados.
9. POST con cantidad cero: 400.

RESPUESTA_REAL.json y RESPUESTA_ANULADA_REAL.json contienen las respuestas capturadas
(reformateadas para su lectura). EVIDENCIA_SQL.txt registra la fila creada y las tablas.
Los ejemplos usan IDs de catálogos cargados por V2; el ID de factura se toma de la respuesta.

## Documentación y límites

El manual se genera como un PDF real con ReportLab. Se verifica su apertura mediante
pdfplumber y se renderizan sus páginas con Poppler para inspección visual.
Tiene 33 páginas, portada, índice, 12 capítulos, tres diagramas, fragmentos reales, ejercicios y
30 preguntas de defensa. La fuente Markdown y el generador están incluidos.

No se implementó frontend. No se verificó una conexión autenticada al servidor 5432
porque no se dispone de su contraseña; se ofrece configuración local interactiva.
La conexión autenticada, migraciones y API sí se verificaron en el clúster exclusivo 55432.
Al finalizar la auditoría se detuvieron el proceso Java de demostración y ese clúster;
los datos permanecen guardados y se reinician con los scripts del README.

Los tests no prueban cumplimiento fiscal, autenticación, rendimiento de grandes volúmenes
ni múltiples usuarios editando formularios antiguos. Esas funcionalidades no se prometen.
La alternativa sql/crear-bases.sql para un servidor ya existente queda documentada;
no se ejecutó en el PostgreSQL principal para no requerir ni modificar su acceso.
