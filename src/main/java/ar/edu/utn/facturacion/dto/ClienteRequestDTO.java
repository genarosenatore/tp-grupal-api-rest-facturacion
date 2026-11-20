package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;

public record ClienteRequestDTO(
    @NotBlank @Size(max = 120) String nombre,
    @NotBlank @Size(max = 30) String documento,
    @NotNull Integer tipoDocumento,
    @NotNull Integer condicionIvaReceptorId,
    @Size(max = 250) String domicilio
) {}
