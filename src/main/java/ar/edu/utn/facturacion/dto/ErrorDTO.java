package ar.edu.utn.facturacion.dto;

import java.time.Instant;
import java.util.List;
public record ErrorDTO(Instant timestamp, int status, String mensaje, List<String> errores) {}
