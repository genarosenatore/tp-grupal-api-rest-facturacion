package ar.edu.utn.facturacion.controller;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.EstadoFactura;
import ar.edu.utn.facturacion.service.FacturaService;
import ar.edu.utn.facturacion.service.CircuitoFiscalService;
import ar.edu.utn.facturacion.service.FacturaPdfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {
    private final FacturaService servicio;
    private final CircuitoFiscalService fiscal;
    private final FacturaPdfService pdf;
    public FacturaRestController(FacturaService servicio, CircuitoFiscalService fiscal, FacturaPdfService pdf) {
        this.servicio = servicio; this.fiscal = fiscal; this.pdf = pdf;
    }
    @PostMapping("/{id}/anular")
    public FacturaReporteDTO anular(@PathVariable @Positive Long id, @Valid @RequestBody AnulacionRequestDTO request) {
        return fiscal.anular(id, request.motivo());
    }
    @PostMapping("/{id}/autorizar")
    public FacturaReporteDTO autorizar(@PathVariable @Positive Long id) { return fiscal.autorizar(id); }
    @PostMapping("/{id}/conciliar")
    public FacturaReporteDTO conciliar(@PathVariable @Positive Long id) { return fiscal.conciliar(id); }
    @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> imprimir(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"factura-" + id + ".pdf\"")
            .header("Cache-Control", "no-store").body(pdf.generar(id));
    }
    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(
            @Validated(ValidacionCompleta.class) @RequestBody FacturaRequestDTO request) {
        FacturaReporteDTO resultado = servicio.crear(request);
        return ResponseEntity.created(URI.create("/api/facturas/" + resultado.id())).body(resultado);
    }
    @PutMapping("/{id}")
    public FacturaReporteDTO actualizar(@PathVariable @Positive Long id, @Valid @RequestBody FacturaRequestDTO request) {
        return servicio.actualizar(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}")
    public FacturaReporteDTO obtener(@PathVariable @Positive Long id) { return servicio.obtener(id); }
    @GetMapping
    public PaginaDTO<FacturaReporteDTO> listar(
            @RequestParam(required = false) @Positive Long clienteId,
            @RequestParam(required = false) EstadoFactura estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive Long numero,
            @RequestParam(required = false) Boolean informadaArca,
            @RequestParam(required = false) @Positive Long listaPrecioId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "8") @Min(1) @Max(100) int size) {
        return servicio.listar(clienteId, estado, desde, hasta, numero, informadaArca, page, size, listaPrecioId);
    }
}
