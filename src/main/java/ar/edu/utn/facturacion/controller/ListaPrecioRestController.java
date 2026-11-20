package ar.edu.utn.facturacion.controller;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.service.ListaPrecioService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/listas-precio")
public class ListaPrecioRestController {
    private final ListaPrecioService servicio;
    public ListaPrecioRestController(ListaPrecioService servicio) { this.servicio = servicio; }
    @PostMapping
    public ResponseEntity<CatalogosDTO.Lista> crear(@Valid @RequestBody ListaPrecioRequestDTO r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(r));
    }
    @PutMapping("/{id}/precios")
    public CatalogosDTO.Precio guardarPrecio(@PathVariable Long id, @Valid @RequestBody PrecioRequestDTO r) {
        return servicio.guardarPrecio(id, r);
    }
}
