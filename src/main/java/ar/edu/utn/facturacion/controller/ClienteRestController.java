package ar.edu.utn.facturacion.controller;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteRestController {
    private final ClienteService servicio;
    public ClienteRestController(ClienteService servicio) { this.servicio = servicio; }
    @GetMapping public List<CatalogosDTO.Cliente> listar(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="") String documento) { return servicio.listar(q, documento); }
    @GetMapping("/{id}") public CatalogosDTO.Cliente obtener(@PathVariable Long id) { return servicio.obtener(id); }
    @PostMapping public ResponseEntity<CatalogosDTO.Cliente> crear(@Valid @RequestBody ClienteRequestDTO r) {
        var c = servicio.guardar(null, r); return ResponseEntity.created(URI.create("/api/clientes/" + c.id())).body(c);
    }
    @PutMapping("/{id}") public CatalogosDTO.Cliente actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequestDTO r) { return servicio.guardar(id, r); }
}
