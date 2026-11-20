package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PrecioRequestDTO(
    @NotNull @Positive Long articuloId,
    @NotNull @Positive Long tipoMonedaId,
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal precio
) {}
