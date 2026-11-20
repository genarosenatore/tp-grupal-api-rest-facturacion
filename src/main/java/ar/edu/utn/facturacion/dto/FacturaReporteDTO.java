package ar.edu.utn.facturacion.dto;

import ar.edu.utn.facturacion.entity.EstadoFactura;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record FacturaReporteDTO(
    Long id, Long version, LocalDate fecha,
    Long clienteId, String clienteNombre, Long condicionIvaId,
    Long puntoVentaId, Long tipoMonedaId, String moneda,
    EstadoFactura estado, Instant fechaAnulacion, String observaciones,
    BigDecimal neto, BigDecimal iva, BigDecimal total,
    List<FacturaDetalleReporteDTO> detalles,
    Long numero, String tipoComprobante, ar.edu.utn.facturacion.entity.EstadoArca estadoArca,
    boolean informadaArca, String tipoAutorizacion, String codigoAutorizacion,
    LocalDate vencimientoAutorizacion, Instant fechaAutorizacion, String motivoAnulacion,
    boolean editable, boolean eliminable,
    String clienteDocumento, String puntoVentaCodigo, String condicionIvaDescripcion,
    String ambienteArca, Integer tipoDocumentoReceptor, Integer condicionIvaReceptorId,
    String clienteDomicilio, String condicionVenta, Long listaPrecioId,
    ar.edu.utn.facturacion.entity.LetraComprobante letraComprobante,
    BigDecimal subtotal, ar.edu.utn.facturacion.entity.TipoDescuento tipoDescuento,
    BigDecimal valorDescuento, BigDecimal importeDescuento
) {}
