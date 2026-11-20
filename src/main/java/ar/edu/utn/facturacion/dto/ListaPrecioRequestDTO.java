package ar.edu.utn.facturacion.dto;

import jakarta.validation.constraints.*;

public record ListaPrecioRequestDTO(@NotBlank @Size(max = 80) String nombre) {}
