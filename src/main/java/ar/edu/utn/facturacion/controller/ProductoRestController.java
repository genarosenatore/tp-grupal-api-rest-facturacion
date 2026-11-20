package ar.edu.utn.facturacion.controller;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articulos")
public class ProductoRestController {
    private final ProductoService servicio;
    public ProductoRestController(ProductoService servicio) { this.servicio = servicio; }
    @PostMapping
    public ResponseEntity<ProductoReporteDTO> crear(@Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(request));
    }
}
