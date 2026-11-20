package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.CatalogosDTO;
import ar.edu.utn.facturacion.dto.ProductoReporteDTO;
import ar.edu.utn.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.facturacion.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogoService {
    private final ClienteRepository clientes;
    private final CondicionIvaRepository condiciones;
    private final PuntoVentaRepository puntos;
    private final TipoMonedaRepository monedas;
    private final ArticuloRepository articulos;
    private final ListaPrecioArticuloRepository precios;
    private final ListaPrecioRepository listas;
    private final boolean homologacionHabilitada;
    private final int puntoHomologacion;

    public CatalogoService(ClienteRepository clientes, CondicionIvaRepository condiciones,
                          PuntoVentaRepository puntos, TipoMonedaRepository monedas,
                          ArticuloRepository articulos, ListaPrecioArticuloRepository precios, ListaPrecioRepository listas,
                          @Value("${arca.homologacion.enabled:false}") boolean homologacionHabilitada,
                          @Value("${arca.homologacion.punto-venta:2}") int puntoHomologacion) {
        this.clientes = clientes; this.condiciones = condiciones; this.puntos = puntos;
        this.monedas = monedas; this.articulos = articulos; this.precios = precios;
        this.listas = listas;
        this.homologacionHabilitada = homologacionHabilitada;
        this.puntoHomologacion = puntoHomologacion;
    }

    public CatalogosDTO obtener() {
        return obtener(true);
    }
    public CatalogosDTO obtener(boolean incluirArticulos) {
        return new CatalogosDTO(
            clientes.findAll(Sort.by("nombre")).stream()
                .map(ClienteService::reporte).toList(),
            condiciones.findAll(Sort.by("id")).stream()
                .map(c -> new CatalogosDTO.Condicion(c.getId(), c.getDescripcion())).toList(),
            puntos.findAll(Sort.by("codigo")).stream()
                .filter(p -> !homologacionHabilitada || String.valueOf(puntoHomologacion).equals(p.getCodigo().replaceFirst("^0+", "")))
                .map(p -> new CatalogosDTO.Punto(p.getId(), p.getCodigo(), p.getDescripcion())).toList(),
            monedas.findAll(Sort.by("codigo")).stream()
                .map(m -> new CatalogosDTO.Moneda(m.getId(), m.getCodigo(), m.getDescripcion())).toList(),
            incluirArticulos ? articulos.findAll(Sort.by("descripcion")).stream()
                .map(a -> new CatalogosDTO.Articulo(a.getId(), a.getCodigo(), a.getDescripcion(), a.getAlicuotaIva())).toList() : java.util.List.of(),
            incluirArticulos ? precios.findAll().stream().map(p -> new CatalogosDTO.Precio(
                p.getArticulo().getId(), p.getTipoMoneda().getId(), p.getPrecio(), p.getListaPrecioId())).toList() : java.util.List.of(),
            homologacionHabilitada, listas.findAll(Sort.by("nombre")).stream()
                .map(l -> new CatalogosDTO.Lista(l.getId(), l.getNombre())).toList());
    }
    public ProductoReporteDTO precio(Long articuloId, Long tipoMonedaId, Long listaPrecioId) {
        var p = precios.findByArticuloIdAndTipoMonedaIdAndListaPrecioId(articuloId, tipoMonedaId, listaPrecioId)
            .orElseThrow(() -> new RecursoNoEncontradoException("El artículo no tiene precio en la lista y moneda seleccionadas."));
        return new ProductoReporteDTO(p.getArticulo().getId(), p.getArticulo().getCodigo(), p.getArticulo().getDescripcion(),
            p.getArticulo().getAlicuotaIva(), p.getTipoMoneda().getId(), p.getPrecio(), p.getListaPrecioId());
    }
    public java.util.List<ProductoReporteDTO> buscarPrecios(String q, Long listaPrecioId, Long tipoMonedaId, java.math.BigDecimal iva) {
        String texto = normal(q);
        return (listaPrecioId == null ? precios.findAll() : precios.findByListaPrecioId(listaPrecioId)).stream()
            .filter(p -> (listaPrecioId == null || listaPrecioId.equals(p.getListaPrecioId()))
                && (tipoMonedaId == null || tipoMonedaId.equals(p.getTipoMoneda().getId()))
                && (iva == null || iva.compareTo(p.getArticulo().getAlicuotaIva()) == 0)
                && normal(p.getArticulo().getCodigo() + " " + p.getArticulo().getDescripcion()).contains(texto))
            .sorted(java.util.Comparator.comparing(p -> p.getArticulo().getDescripcion()))
            .limit(100).map(p -> new ProductoReporteDTO(p.getArticulo().getId(), p.getArticulo().getCodigo(), p.getArticulo().getDescripcion(),
                p.getArticulo().getAlicuotaIva(), p.getTipoMoneda().getId(), p.getPrecio(), p.getListaPrecioId())).toList();
    }
    private static String normal(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).strip();
    }
    public java.util.List<ProductoReporteDTO> consultarPrecios(Long listaPrecioId, Long tipoMonedaId, java.util.List<Long> articuloIds) {
        if (listaPrecioId == null || listaPrecioId <= 0 || tipoMonedaId == null || tipoMonedaId <= 0
                || articuloIds.isEmpty() || articuloIds.size() > 100 || articuloIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new ar.edu.utn.facturacion.exception.SolicitudInvalidaException("Indicá una lista, una moneda y entre 1 y 100 artículos válidos.");
        return precios.findByListaPrecioIdAndTipoMonedaIdAndArticuloIdIn(listaPrecioId, tipoMonedaId, articuloIds).stream()
            .map(p -> new ProductoReporteDTO(p.getArticulo().getId(), p.getArticulo().getCodigo(), p.getArticulo().getDescripcion(),
                p.getArticulo().getAlicuotaIva(), p.getTipoMoneda().getId(), p.getPrecio(), p.getListaPrecioId())).toList();
    }
}
