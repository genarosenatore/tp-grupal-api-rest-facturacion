package ar.edu.utn.facturacion.controller;

import ar.edu.utn.facturacion.dto.CatalogosDTO;
import ar.edu.utn.facturacion.service.CatalogoService;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogoRestController {
    private final CatalogoService servicio;
    public CatalogoRestController(CatalogoService servicio) { this.servicio = servicio; }
    @GetMapping("/api/catalogos")
    public CatalogosDTO obtener(@RequestParam(defaultValue="true") boolean incluirArticulos) { return servicio.obtener(incluirArticulos); }
    @GetMapping("/api/catalogos/precios")
    public java.util.List<ar.edu.utn.facturacion.dto.ProductoReporteDTO> precios(@RequestParam(defaultValue="") String q,
            @RequestParam(required=false) Long listaPrecioId, @RequestParam(required=false) Long tipoMonedaId,
            @RequestParam(required=false) java.math.BigDecimal iva, @RequestParam(required=false) java.util.List<Long> articuloIds) {
        return articuloIds == null ? servicio.buscarPrecios(q, listaPrecioId, tipoMonedaId, iva) : servicio.consultarPrecios(listaPrecioId, tipoMonedaId, articuloIds);
    }
    @GetMapping("/api/catalogos/precios/{articuloId}")
    public ar.edu.utn.facturacion.dto.ProductoReporteDTO precio(@PathVariable Long articuloId,
            @RequestParam Long tipoMonedaId, @RequestParam(defaultValue="1") Long listaPrecioId) { return servicio.precio(articuloId, tipoMonedaId, listaPrecioId); }
}
