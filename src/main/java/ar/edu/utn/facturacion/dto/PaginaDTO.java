package ar.edu.utn.facturacion.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PaginaDTO<T> de(Page<T> pagina) {
        return new PaginaDTO<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
            pagina.getTotalElements(), pagina.getTotalPages());
    }
}
