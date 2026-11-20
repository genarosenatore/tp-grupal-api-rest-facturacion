package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.exception.*;
import ar.edu.utn.facturacion.repository.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class ProductoService {
    private final ArticuloRepository articulos;
    private final ListaPrecioArticuloRepository precios;
    private final TipoMonedaRepository monedas;
    private final Validator validator;
    private final ListaPrecioService listas;
    public ProductoService(ArticuloRepository articulos, ListaPrecioArticuloRepository precios,
            TipoMonedaRepository monedas, Validator validator, ListaPrecioService listas) {
        this.articulos = articulos; this.precios = precios; this.monedas = monedas; this.validator = validator;
        this.listas = listas;
    }
    @Transactional
    public ProductoReporteDTO crear(ProductoRequestDTO request) {
        var errores = validator.validate(request);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);
        String codigo = request.codigo().strip().toUpperCase(Locale.ROOT);
        if (codigo.length() > 30) throw new SolicitudInvalidaException("El código admite hasta 30 caracteres al normalizarlo a mayúsculas.");
        if (articulos.existsByCodigoIgnoreCase(codigo))
            throw new ConflictoNegocioException("Ya existe un producto con el código " + codigo + ". Elegí otro código o seleccioná el existente.");
        var moneda = monedas.findById(request.tipoMonedaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Moneda no encontrada: " + request.tipoMonedaId()));
        var lista = listas.buscar(request.listaPrecioId());
        var articulo = articulos.saveAndFlush(new Articulo(codigo, request.descripcion().strip(), request.alicuotaIva().setScale(2)));
        var precio = precios.saveAndFlush(new ListaPrecioArticulo(articulo, moneda, request.precio().setScale(2), lista.getId()));
        return new ProductoReporteDTO(articulo.getId(), articulo.getCodigo(), articulo.getDescripcion(), articulo.getAlicuotaIva(),
            moneda.getId(), precio.getPrecio(), lista.getId());
    }
}
