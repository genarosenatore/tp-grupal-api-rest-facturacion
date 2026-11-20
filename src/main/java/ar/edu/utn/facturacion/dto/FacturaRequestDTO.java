package ar.edu.utn.facturacion.dto;

import ar.edu.utn.facturacion.entity.EstadoFactura;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
/** POST/PUT completo: cabecera y detalles. PUT de estado: estado y observaciones. */
public record FacturaRequestDTO(
    @NotNull(groups = ValidacionCompleta.class) LocalDate fecha,
    @NotNull(groups = ValidacionCompleta.class) @Positive Long clienteId,
    @NotNull(groups = ValidacionCompleta.class) @Positive Long condicionIvaId,
    @NotNull(groups = ValidacionCompleta.class) @Positive Long puntoVentaId,
    @NotNull(groups = ValidacionCompleta.class) @Positive Long tipoMonedaId,
    @Size(max = 1000) String observaciones,
    @NotEmpty(groups = ValidacionCompleta.class) @Size(max = 100)
    List<@NotNull @Valid FacturaDetalleRequestDTO> detalles,
    EstadoFactura estado,
    @Pattern(regexp = "1|6|11") String tipoComprobante,
    Integer tipoDocumentoReceptor, Integer condicionIvaReceptorId,
    @Size(max = 250) String clienteDomicilio,
    @Size(max = 80) String condicionVenta,
    @Positive Long listaPrecioId,
    ar.edu.utn.facturacion.entity.TipoDescuento tipoDescuento,
    @DecimalMin("0") @Digits(integer = 17, fraction = 2) java.math.BigDecimal valorDescuento
) {
    public FacturaRequestDTO(LocalDate fecha, Long clienteId, Long condicionIvaId, Long puntoVentaId,
            Long tipoMonedaId, String observaciones, List<FacturaDetalleRequestDTO> detalles, EstadoFactura estado,
            String tipoComprobante, Integer tipoDocumentoReceptor, Integer condicionIvaReceptorId,
            String clienteDomicilio, String condicionVenta, Long listaPrecioId) {
        this(fecha, clienteId, condicionIvaId, puntoVentaId, tipoMonedaId, observaciones, detalles, estado,
            tipoComprobante, tipoDocumentoReceptor, condicionIvaReceptorId, clienteDomicilio, condicionVenta, listaPrecioId, null, null);
    }
    public FacturaRequestDTO(LocalDate fecha, Long clienteId, Long condicionIvaId, Long puntoVentaId,
            Long tipoMonedaId, String observaciones, List<FacturaDetalleRequestDTO> detalles, EstadoFactura estado,
            String tipoComprobante, Integer tipoDocumentoReceptor, Integer condicionIvaReceptorId,
            String clienteDomicilio, String condicionVenta) {
        this(fecha, clienteId, condicionIvaId, puntoVentaId, tipoMonedaId, observaciones, detalles, estado,
            tipoComprobante, tipoDocumentoReceptor, condicionIvaReceptorId, clienteDomicilio, condicionVenta, null);
    }
    public FacturaRequestDTO(LocalDate fecha, Long clienteId, Long condicionIvaId, Long puntoVentaId,
            Long tipoMonedaId, String observaciones, List<FacturaDetalleRequestDTO> detalles, EstadoFactura estado,
            String tipoComprobante, Integer tipoDocumentoReceptor, Integer condicionIvaReceptorId) {
        this(fecha, clienteId, condicionIvaId, puntoVentaId, tipoMonedaId, observaciones, detalles, estado,
            tipoComprobante, tipoDocumentoReceptor, condicionIvaReceptorId, null, null);
    }
    public FacturaRequestDTO(LocalDate fecha, Long clienteId, Long condicionIvaId, Long puntoVentaId,
            Long tipoMonedaId, String observaciones, List<FacturaDetalleRequestDTO> detalles, EstadoFactura estado) {
        this(fecha, clienteId, condicionIvaId, puntoVentaId, tipoMonedaId, observaciones, detalles, estado, null, null, null);
    }
}
