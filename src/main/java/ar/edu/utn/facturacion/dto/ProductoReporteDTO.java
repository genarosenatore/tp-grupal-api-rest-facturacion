package ar.edu.utn.facturacion.dto;

import java.math.BigDecimal;

public record ProductoReporteDTO(Long id, String codigo, String descripcion, BigDecimal alicuotaIva,
        Long tipoMonedaId, BigDecimal precio, Long listaPrecioId) {}
