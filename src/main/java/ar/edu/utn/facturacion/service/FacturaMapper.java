package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import org.springframework.stereotype.Component;
/** Invocado dentro de la transacción para resolver asociaciones LAZY. */
@Component
public class FacturaMapper {
    public FacturaReporteDTO aReporte(FacturaVenta f) {
        var detalles = f.getDetalles().stream().map(d -> new FacturaDetalleReporteDTO(
            d.getId(), d.getListaPrecioArticulo().getArticulo().getId(),
            d.getListaPrecioArticulo().getId(), d.getDescripcion(), d.getCantidad(),
            d.getPrecioUnitario(), d.getAlicuotaIva(), d.getNeto(), d.getIva(), d.getTotal(), d.getDescuento()
        )).toList();
        return new FacturaReporteDTO(f.getId(), f.getVersion(), f.getFecha(),
            f.getCliente().getId(), f.getClienteNombre(), f.getCondicionIva().getId(),
            f.getPuntoVenta().getId(), f.getTipoMoneda().getId(), f.getTipoMoneda().getCodigo(),
            f.getEstado(), f.getFechaAnulacion(), f.getObservaciones(),
            f.getNeto(), f.getIva(), f.getTotal(), detalles,
            f.getNumero(), f.getTipoComprobante(), f.getEstadoArca(), f.isInformadaArca(),
            f.getTipoAutorizacion(), f.getCodigoAutorizacion(), f.getVencimientoAutorizacion(),
            f.getFechaAutorizacion(), f.getMotivoAnulacion(), f.isEditable(),
            f.isEditable() && f.getNumero() == null,
            f.getClienteDocumento(), f.getPuntoVenta().getCodigo(), f.getCondicionIva().getDescripcion(),
            f.getAmbienteArca(), f.getTipoDocumentoReceptor(), f.getCondicionIvaReceptorId(),
            f.getClienteDomicilio(), f.getCondicionVenta(), f.getListaPrecioId(), f.getLetraComprobante(),
            f.getSubtotal(), f.getTipoDescuento(), f.getValorDescuento(), f.getImporteDescuento());
    }
}
