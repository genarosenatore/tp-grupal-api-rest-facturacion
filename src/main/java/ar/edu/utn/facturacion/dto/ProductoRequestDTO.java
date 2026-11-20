package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Artículo y primer precio de lista. La moneda se recibe por ID. */
public record ProductoRequestDTO(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 150) String descripcion,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal alicuotaIva,
    @NotNull @Positive Long tipoMonedaId,
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal precio,
    @Positive Long listaPrecioId
) {}
