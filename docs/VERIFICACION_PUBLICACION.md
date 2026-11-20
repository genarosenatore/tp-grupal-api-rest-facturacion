# Verificación de la entrega pública

Fecha: 20/11/2026 (Argentina).

## Comprobaciones realizadas

| Comprobación | Resultado |
|---|---|
| `npm.cmd test` desde `frontend` | 27 pruebas correctas |
| `npm.cmd run build` desde `frontend` | TypeScript y Vite correctos; recursos de Spring actualizados |
| `scripts/maven.ps1 package` | Compilación y JAR correctos; 83 casos contabilizados: 66 ejecutados sin fallos ni errores y 17 omitidos |
| `python -m unittest discover -s scripts -p test_preparar_publicacion.py -v` | 7 pruebas correctas del filtro y del ZIP |
| `scripts/preparar-entrega.ps1 -SoloVerificar` | Revisión de los archivos seleccionados sin coincidencias con datos privados conocidos |

Los 17 casos Java omitidos corresponden a PostgreSQL y al lanzador de navegador,
que requieren activación explícita. No se habilitaron en esta revisión.
La ejecución Java se hizo fuera del aislamiento del agente porque ese aislamiento
impedía abrir conexiones HTTP de loopback. Los casos ejecutados usan H2 y dobles
fiscales; no se solicitaron autorizaciones a ARCA.

## Protección aplicada

- Configuración privada, claves, certificados, tickets y bases excluidos.
- Sin JAR, ZIP anteriores, PDF fiscales ni capturas en el repositorio exportado.
- CAE reales y ruta personal retirados de la documentación pública.
- Comparación con contraseñas e identificadores disponibles en `.local`, sin
  mostrar ni distribuir sus valores.
- El ZIP contiene los mismos archivos y bytes que la carpeta pública; el
  generador comprueba la integridad y adjunta un SHA256 externo.
- Solo se exportan los assets de Vite referenciados por el HTML actual.
- No se copia historial Git; la carpeta original no tenía repositorio Git.

Las pruebas del exportador comprueban la exclusión de archivos privados y binarios,
el bloqueo de datos privados y claves incrustadas, la detección de rutas personales,
la conservación de la carpeta anterior y la correspondencia entre carpeta y ZIP.
Los respaldos de documentación original permanecen en `.local`, fuera de la entrega.

Esta verificación cubre el estado preparado en esa fecha. Antes de nuevas
publicaciones, repetir la revisión y examinar los archivos añadidos.
