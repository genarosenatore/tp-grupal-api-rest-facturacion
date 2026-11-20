package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.exception.*;
import ar.edu.utn.facturacion.repository.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListaPrecioService {
    private final ListaPrecioRepository listas;
    private final ListaPrecioArticuloRepository precios;
    private final ArticuloRepository articulos;
    private final TipoMonedaRepository monedas;
    private final Validator validator;
    public ListaPrecioService(ListaPrecioRepository listas, ListaPrecioArticuloRepository precios,
            ArticuloRepository articulos, TipoMonedaRepository monedas, Validator validator) {
        this.listas = listas; this.precios = precios; this.articulos = articulos;
        this.monedas = monedas; this.validator = validator;
    }
    public ListaPrecio buscar(Long id) {
        return listas.findById(id == null ? 1L : id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Lista de precios no encontrada: " + id));
    }
    @Transactional
    public CatalogosDTO.Lista crear(ListaPrecioRequestDTO r) {
        validar(r);
        String nombre = r.nombre().strip();
        if (listas.existsByNombreIgnoreCase(nombre))
            throw new ConflictoNegocioException("Ya existe una lista de precios con ese nombre.");
        var lista = listas.saveAndFlush(new ListaPrecio(nombre));
        return new CatalogosDTO.Lista(lista.getId(), lista.getNombre());
    }
    @Transactional
    public CatalogosDTO.Precio guardarPrecio(Long listaId, PrecioRequestDTO r) {
        validar(r);
        var lista = buscar(listaId);
        var articulo = articulos.findById(r.articuloId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Artículo no encontrado: " + r.articuloId()));
        var moneda = monedas.findById(r.tipoMonedaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Moneda no encontrada: " + r.tipoMonedaId()));
        var precio = precios.findByArticuloIdAndTipoMonedaIdAndListaPrecioId(articulo.getId(), moneda.getId(), lista.getId())
            .orElseGet(() -> new ListaPrecioArticulo(articulo, moneda, r.precio(), lista.getId()));
        precio.cambiarPrecio(r.precio().setScale(2));
        precios.saveAndFlush(precio);
        return new CatalogosDTO.Precio(articulo.getId(), moneda.getId(), precio.getPrecio(), lista.getId());
    }
    private void validar(Object r) {
        var errores = validator.validate(r);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);
    }
}
