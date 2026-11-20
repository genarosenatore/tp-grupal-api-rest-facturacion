package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnulacionRequestDTO(@NotBlank @Size(max = 1000) String motivo) {}
