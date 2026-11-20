package ar.edu.utn.facturacion.dto;

import java.math.BigDecimal;
import java.util.List;

/** Datos de selección del frontend; nunca expone entidades administradas por JPA. */
public record CatalogosDTO(
    List<Cliente> clientes, List<Condicion> condicionesIva, List<Punto> puntosVenta,
    List<Moneda> monedas, List<Articulo> articulos, List<Precio> precios,
    boolean homologacionHabilitada, List<Lista> listasPrecio
) {
    public record Cliente(Long id, String nombre, String documento, Integer tipoDocumento,
            Integer condicionIvaReceptorId, String domicilio) {}
    public record Condicion(Long id, String descripcion) {}
    public record Punto(Long id, String codigo, String descripcion) {}
    public record Moneda(Long id, String codigo, String descripcion) {}
    public record Articulo(Long id, String codigo, String descripcion, BigDecimal alicuotaIva) {}
    public record Lista(Long id, String nombre) {}
    public record Precio(Long articuloId, Long tipoMonedaId, BigDecimal precio, Long listaPrecioId) {}
}
