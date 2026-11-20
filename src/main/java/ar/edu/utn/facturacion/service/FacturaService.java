package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.exception.*;
import ar.edu.utn.facturacion.repository.*;
import ar.edu.utn.facturacion.fiscal.ReglasHomologacion;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Service
@Transactional(readOnly = true)
public class FacturaService {
    private final FacturaRepository facturas;
    private final OperacionFiscalRepository operaciones;
    private final ClienteRepository clientes;
    private final CondicionIvaRepository condiciones;
    private final PuntoVentaRepository puntos;
    private final TipoMonedaRepository monedas;
    private final ListaPrecioArticuloRepository precios;
    private final ArticuloRepository articulos;
    private final FacturaMapper mapper;
    private final CalculadoraImportes calculadora;
    private final DescuentoGeneral descuentos;
    private final Validator validator;
    private final ListaPrecioService listas;
    private final boolean homologacionHabilitada;
    private final int puntoHomologacion;
    public FacturaService(FacturaRepository facturas, ClienteRepository clientes,
            CondicionIvaRepository condiciones, PuntoVentaRepository puntos,
            TipoMonedaRepository monedas, ListaPrecioArticuloRepository precios,
            ArticuloRepository articulos, FacturaMapper mapper,
            CalculadoraImportes calculadora, DescuentoGeneral descuentos, Validator validator, OperacionFiscalRepository operaciones, ListaPrecioService listas,
            @Value("${arca.homologacion.enabled:false}") boolean homologacionHabilitada,
            @Value("${arca.homologacion.punto-venta:2}") int puntoHomologacion) {
        this.facturas = facturas; this.clientes = clientes; this.condiciones = condiciones;
        this.puntos = puntos; this.monedas = monedas; this.precios = precios;
        this.articulos = articulos; this.mapper = mapper; this.calculadora = calculadora;
        this.descuentos = descuentos;
        this.validator = validator;
        this.operaciones = operaciones;
        this.listas = listas;
        this.homologacionHabilitada = homologacionHabilitada;
        this.puntoHomologacion = puntoHomologacion;
    }
    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO request) {
        validarCompleta(request);
        if (request.estado() != null) throw new SolicitudInvalidaException("El estado inicial lo asigna el servidor: EMITIDA.");
        FacturaVenta factura = new FacturaVenta();
        aplicarContenido(factura, request);
        // Detectar errores SQL antes de construir la respuesta.
        return mapper.aReporte(facturas.saveAndFlush(factura));
    }
    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO request) {
        FacturaVenta factura = buscarBloqueada(id);
        if (request.estado() != null) actualizarEstado(factura, request);
        else {
            asegurarEditable(factura);
            validarCompleta(request);
            aplicarContenido(factura, request);
        }
        facturas.flush();
        return mapper.aReporte(factura);
    }
    /** Solo los borradores sin número ni autorización pueden borrarse físicamente. */
    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = buscarBloqueada(id);
        asegurarEditable(factura);
        if (factura.getNumero() != null)
            throw new ConflictoNegocioException("Una factura con número no puede eliminarse. Utilizá Anular.");
        // Los rechazos definitivos no impiden borrar un borrador; se conserva su auditoría.
        operaciones.desvincularFactura(id);
        facturas.delete(factura);
        facturas.flush();
    }
    public FacturaReporteDTO obtener(Long id) {
        return mapper.aReporte(facturas.findById(id).orElseThrow(() -> noExiste("Factura", id)));
    }
    public PaginaDTO<FacturaReporteDTO> listar(Long clienteId, EstadoFactura estado, LocalDate desde, LocalDate hasta,
            Long numero, Boolean informadaArca, int page, int size) {
        return listar(clienteId, estado, desde, hasta, numero, informadaArca, page, size, null);
    }
    public PaginaDTO<FacturaReporteDTO> listar(Long clienteId, EstadoFactura estado, LocalDate desde, LocalDate hasta,
            Long numero, Boolean informadaArca, int page, int size, Long listaPrecioId) {
        if (page < 0 || size < 1 || size > 100)
            throw new SolicitudInvalidaException("page debe ser >= 0 y size debe estar entre 1 y 100.");
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new SolicitudInvalidaException("desde no puede ser posterior a hasta");
        Specification<FacturaVenta> filtro = (root, query, cb) -> {
            var condiciones = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (listaPrecioId != null) condiciones.add(cb.equal(root.get("listaPrecioId"), listaPrecioId));
            if (clienteId != null) condiciones.add(cb.equal(root.get("cliente").get("id"), clienteId));
            if (estado != null) condiciones.add(cb.equal(root.get("estado"), estado));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            if (hasta != null) condiciones.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            if (numero != null) condiciones.add(cb.equal(root.get("numero"), numero));
            if (informadaArca != null) {
                var informada = cb.or(cb.equal(root.get("estadoArca"), EstadoArca.AUTORIZADA),
                    cb.isNotNull(root.get("codigoAutorizacion")), cb.isNotNull(root.get("fechaAutorizacion")));
                condiciones.add(informadaArca ? informada : cb.not(informada));
            }
            return cb.and(condiciones.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PaginaDTO.de(facturas.findAll(filtro, PageRequest.of(page, size, Sort.by("id").descending())).map(mapper::aReporte));
    }
    private void aplicarContenido(FacturaVenta f, FacturaRequestDTO r) {
        Cliente cliente = clientes.findById(r.clienteId()).orElseThrow(() -> noExiste("Cliente", r.clienteId()));
        CondicionIva condicion = condiciones.findById(r.condicionIvaId()).orElseThrow(() -> noExiste("Condición IVA", r.condicionIvaId()));
        PuntoVenta punto = puntos.findById(r.puntoVentaId()).orElseThrow(() -> noExiste("Punto de venta", r.puntoVentaId()));
        if (homologacionHabilitada && !String.valueOf(puntoHomologacion).equals(punto.getCodigo().replaceFirst("^0+", "")))
            throw new SolicitudInvalidaException("Usá el punto de venta " + puntoHomologacion + " del entorno de prueba.");
        TipoMoneda moneda = monedas.findById(r.tipoMonedaId()).orElseThrow(() -> noExiste("Moneda", r.tipoMonedaId()));
        var lista = listas.buscar(r.listaPrecioId());
        boolean facturaC = "11".equals(r.tipoComprobante());
        boolean fiscal = r.tipoComprobante() != null;
        if (fiscal) {
            if (!"ARS".equals(moneda.getCodigo())) throw new SolicitudInvalidaException("El entorno de homologación utiliza pesos argentinos.");
            if (cliente.getTipoDocumento() == null || cliente.getCondicionIvaReceptorId() == null)
                throw new SolicitudInvalidaException("Completá el tipo de documento y la condición fiscal en la ficha del cliente.");
            try { ReglasHomologacion.validarReceptor(r.tipoComprobante(), cliente.getTipoDocumento(), cliente.getCondicionIvaReceptorId(), cliente.getDocumento()); }
            catch (IllegalArgumentException error) { throw new SolicitudInvalidaException(error.getMessage()); }
        }
        // Compatibilidad con requests anteriores: nunca permitir que reemplacen la ficha del cliente.
        if (r.tipoDocumentoReceptor() != null && !r.tipoDocumentoReceptor().equals(cliente.getTipoDocumento()))
            throw new SolicitudInvalidaException("El tipo de documento enviado no coincide con el del cliente. Editá su ficha.");
        if (r.condicionIvaReceptorId() != null && !r.condicionIvaReceptorId().equals(cliente.getCondicionIvaReceptorId()))
            throw new SolicitudInvalidaException("La condición fiscal enviada no coincide con la del cliente. Editá su ficha.");
        if (r.clienteDomicilio() != null && !r.clienteDomicilio().isBlank() && !r.clienteDomicilio().equals(cliente.getDomicilio()))
            throw new SolicitudInvalidaException("El domicilio se modifica en la ficha del cliente.");
        List<FacturaDetalle> nuevos = new ArrayList<>();
        for (FacturaDetalleRequestDTO item : r.detalles()) {
            if (!articulos.existsById(item.articuloId())) throw noExiste("Artículo", item.articuloId());
            ListaPrecioArticulo precio = precios.findByArticuloIdAndTipoMonedaIdAndListaPrecioId(item.articuloId(), moneda.getId(), lista.getId())
                .orElseThrow(() -> new ConflictoNegocioException(
                    "No existe precio para artículo " + item.articuloId() + " en moneda " + moneda.getCodigo() + " en la lista " + lista.getNombre()));
            var alicuota = facturaC ? java.math.BigDecimal.ZERO : precio.getArticulo().getAlicuotaIva();
            if (fiscal && !facturaC) {
                try { ReglasHomologacion.idIva(alicuota); }
                catch (IllegalArgumentException error) { throw new SolicitudInvalidaException(error.getMessage()); }
            }
            var importes = calculadora.calcular(item.cantidad(), precio.getPrecio(), alicuota);
            var detalle = new FacturaDetalle(precio, item.cantidad(), importes.neto(), importes.iva(), importes.total());
            detalle.aplicarAlicuota(alicuota); nuevos.add(detalle);
        }
        var descuento = descuentos.calcular(nuevos.stream().map(FacturaDetalle::getNeto).toList(), r.tipoDescuento(), r.valorDescuento());
        for (int i = 0; i < nuevos.size(); i++) {
            var d = nuevos.get(i); var importe = descuento.porRenglon().get(i);
            var valores = calculadora.calcular(java.math.BigDecimal.ONE, d.getNeto().subtract(importe), d.getAlicuotaIva());
            d.aplicarDescuento(importe, valores.neto(), valores.iva(), valores.total());
        }
        f.actualizarCabecera(r.fecha(), cliente, condicion, punto, moneda, r.observaciones());
        f.definirListaPrecio(lista.getId());
        f.definirCircuito(r.tipoComprobante(), cliente.getTipoDocumento(), cliente.getCondicionIvaReceptorId());
        f.definirDatosComerciales(cliente.getDomicilio(), r.condicionVenta());
        f.copiarCliente(cliente);
        f.definirDescuento(descuento.tipo(), descuento.valor(), descuento.importe());
        f.reemplazarDetalles(nuevos);
    }
    private void actualizarEstado(FacturaVenta f, FacturaRequestDTO r) {
        if (r.fecha() != null || r.clienteId() != null || r.condicionIvaId() != null ||
            r.puntoVentaId() != null || r.tipoMonedaId() != null || r.detalles() != null
            || r.tipoComprobante() != null || r.tipoDocumentoReceptor() != null || r.condicionIvaReceptorId() != null
            || r.clienteDomicilio() != null || r.condicionVenta() != null || r.listaPrecioId() != null
            || r.tipoDescuento() != null || r.valorDescuento() != null)
            throw new SolicitudInvalidaException("No mezclar cambio de estado con actualización completa.");
        if (f.getEstado() == EstadoFactura.ANULADA) {
            if (r.estado() == EstadoFactura.ANULADA && r.observaciones() == null) return;
            throw new ConflictoNegocioException("Una factura anulada no puede modificarse ni reactivarse.");
        }
        asegurarEditable(f);
        if (r.estado() == EstadoFactura.ANULADA) {
            // Mantener un único circuito de anulación/auditoría: usar POST /{id}/anular.
            throw new ConflictoNegocioException("Para anular utilizá POST /api/facturas/{id}/anular con motivo. No se permite anular por actualización de estado.");
        } else if (r.observaciones() != null) f.cambiarObservaciones(r.observaciones());
    }
    private void asegurarEditable(FacturaVenta f) {
        if (f.getEstado() == EstadoFactura.ANULADA)
            throw new ConflictoNegocioException("Una factura anulada no puede modificarse.");
        if (f.isFiscalBloqueada())
            throw new ConflictoNegocioException("La factura está autorizada o tiene un envío fiscal pendiente de conciliación. Solo admite consulta, impresión o anulación por el circuito fiscal.");
    }
    private void validarCompleta(FacturaRequestDTO r) {
        var errores = validator.validate(r, ValidacionCompleta.class);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);
    }
    private FacturaVenta buscarBloqueada(Long id) {
        return facturas.buscarParaActualizar(id).orElseThrow(() -> noExiste("Factura", id));
    }
    private RecursoNoEncontradoException noExiste(String recurso, Long id) {
        return new RecursoNoEncontradoException(recurso + " no encontrado: " + id);
    }
}
