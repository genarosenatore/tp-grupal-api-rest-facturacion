package ar.edu.utn.facturacion.dto;

import java.math.BigDecimal;
public record FacturaDetalleReporteDTO(
    Long id, Long articuloId, Long listaPrecioArticuloId, String descripcion,
    BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal alicuotaIva,
    BigDecimal neto, BigDecimal iva, BigDecimal total, BigDecimal descuento
) {
    public FacturaDetalleReporteDTO(Long id, Long articuloId, Long listaPrecioArticuloId, String descripcion,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal alicuotaIva, BigDecimal neto, BigDecimal iva, BigDecimal total) {
        this(id, articuloId, listaPrecioArticuloId, descripcion, cantidad, precioUnitario, alicuotaIva, neto, iva, total, new BigDecimal("0.00"));
    }
}
