# Análisis y decisiones del TP

> Documento histórico de una etapa anterior. Para la versión vigente y la verificación final, consultar [README](../README.md) y [ENTREGA_FINAL](ENTREGA_FINAL.md).


Fuente de requisitos: TP "Operaciones Escritura en API REST (POST, PUT, DELETE)", tres páginas.
Alcance autorizado: backend Java/Spring Boot y PostgreSQL desde cero. Frontend postergado.

| Consigna del PDF | Implementación |
|---|---|
| Request DTOs con IDs | FacturaRequestDTO y FacturaDetalleRequestDTO |
| Entidades asociadas por ID | Catálogos y repositorios; ausencia => 404 |
| Factura y detalles en cascada | FacturaVenta OneToMany y FacturaDetalle ManyToOne |
| Calcular neto, IVA y total | CalculadoraImportes y sumas en FacturaVenta |
| @Transactional | Crear, actualizar, eliminar; consultas readOnly |
| Actualizar cabecera e ítems | PUT completo con reemplazo de la colección |
| Eliminar o anular | Se elige anulación; conserva estado y fecha |
| POST 201 y reporte | Controller con Location y FacturaReporteDTO |
| PUT 200 | DTO actualizado |
| DELETE 204 | Sin cuerpo; anulación idempotente |
| Recurso inexistente 404 | RecursoNoEncontradoException y advice |
| GET para futuro frontend | Listado filtrable y consulta individual |

## Supuestos explícitos

1. Las ocho entidades mínimas son Cliente, CondicionIva, PuntoVenta, TipoMoneda, Articulo,
   ListaPrecioArticulo, FacturaVenta y FacturaDetalle. Los atributos no enumerados en el PDF
   se limitan a identificación, descripción, cantidades, precios e información necesaria.
2. Se usa una única lista vigente por artículo y moneda. No se inventan múltiples listas,
   prioridades comerciales ni descuentos. Si no hay precio, el servicio devuelve 409.
3. La alícuota está en Articulo; los datos de V2 son ejemplos ficticios. CondicionIva es
   clasificación de cabecera y no determina una regla tributaria automática.
4. No se calcula número fiscal: el ID es una clave técnica.
5. Se permiten fechas explícitas sin restricción fiscal de antigüedad/futuro.
6. El detalle conserva precio, descripción e IVA usados; la cabecera referencia catálogos.
   No se implementa auditoría histórica de cambios en el nombre del cliente.
7. PUT completo reemplaza ítems con nuevos IDs y recalcula a precios actuales.
8. Se conserva EMITIDA/ANULADA; no hay borrado físico expuesto ni reactivación.
9. DELETE repetido se trata explícitamente: mismo resultado 204 y fecha original.
10. El modo de estado de PUT sirve al futuro frontend y no se mezcla con edición completa.
11. Los filtros son simples, inclusivos, por fecha/cliente/estado; no se pagina esta maqueta.
12. Montos de hasta numeric(19,2), precio numeric(15,2), cantidad numeric(9,3).
    Valores que exceden la capacidad total se rechazan por integridad sin persistencia parcial.
13. Se agregan Jakarta Validation y errores 400/409 para completar el contrato.
14. Un reloj Clock UTC produce Instant para fechaAnulacion, equivalente al instante que
    pretende new Date() del enunciado; LocalDate representa la fecha sin hora de emisión.
15. Flyway administra SQL y Hibernate usa validate. No se deja ddl-auto=create/update.

## Relaciones

Cliente/CondicionIva/PuntoVenta/TipoMoneda 1:N FacturaVenta.
FacturaVenta 1:N FacturaDetalle. Articulo y TipoMoneda 1:N ListaPrecioArticulo.
ListaPrecioArticulo 1:N FacturaDetalle. Sus FK mantienen integridad referencial.
Solo la relación factura-detalles propaga cascadas; nunca se propaga REMOVE a catálogos.

## Operaciones SQL y atomicidad

POST lee catálogos y precios, inserta cabecera e ítems en una transacción.
PUT bloquea cabecera, lee catálogos, sustituye detalles y actualiza importes.
DELETE bloquea cabecera y actualiza estado/fechaAnulacion.
Un fallo real al insertar el segundo ítem revierte cabecera y primer ítem.
La secuencia de IDs puede avanzar aunque una transacción se revierta: no es una pérdida
de integridad y no se promete una numeración correlativa sin huecos.
