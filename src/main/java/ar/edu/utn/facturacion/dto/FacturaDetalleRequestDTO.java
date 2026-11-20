package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record FacturaDetalleRequestDTO(
    @NotNull @Positive Long articuloId,
    @NotNull @DecimalMin("0.001") @DecimalMax("999999.999")
    @Digits(integer = 6, fraction = 3) BigDecimal cantidad
) {}
