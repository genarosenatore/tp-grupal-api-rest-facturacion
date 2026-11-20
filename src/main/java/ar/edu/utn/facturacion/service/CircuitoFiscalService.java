package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.exception.*;
import ar.edu.utn.facturacion.fiscal.ArcaGateway;
import ar.edu.utn.facturacion.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;

@Service
public class CircuitoFiscalService {
    private final FacturaRepository facturas;
    private final OperacionFiscalRepository operaciones;
    private final FacturaMapper mapper;
    private final ArcaGateway arca;
    private final TransactionTemplate tx;
    private final Clock reloj;
    public CircuitoFiscalService(FacturaRepository facturas, OperacionFiscalRepository operaciones,
            FacturaMapper mapper, ArcaGateway arca, PlatformTransactionManager manager, Clock reloj) {
        this.facturas = facturas; this.operaciones = operaciones; this.mapper = mapper;
        this.arca = arca; tx = new TransactionTemplate(manager); this.reloj = reloj;
    }
    private record Envio(UUID clave, String operacion, FacturaReporteDTO factura) {}

    public FacturaReporteDTO autorizar(Long id) { return operar(id, "AUTORIZAR", null); }
    public FacturaReporteDTO anular(Long id, String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 1000)
            throw new SolicitudInvalidaException("Indicá un motivo de anulación de hasta 1000 caracteres.");
        return operar(id, "ANULAR", motivo.trim());
    }
    private FacturaReporteDTO operar(Long id, String operacion, String motivo) {
        Envio envio = tx.execute(status -> {
            var f = buscar(id);
            if (operacion.equals("AUTORIZAR") && f.isInformadaArca()) return new Envio(null, operacion, mapper.aReporte(f));
            if (f.getEstado() == EstadoFactura.ANULADA) {
                if (operacion.equals("ANULAR")) return new Envio(null, operacion, mapper.aReporte(f));
                throw new ConflictoNegocioException("Una factura anulada no puede autorizarse.");
            }
            if (f.getEstadoArca() == EstadoArca.EN_PROCESO || f.getEstadoArca() == EstadoArca.INCIERTA)
                throw new ConflictoNegocioException("Existe un envío fiscal en proceso o incierto. Conciliá antes de realizar otra operación; no se reenviará.");
            if (operacion.equals("ANULAR") && !f.isInformadaArca()) {
                f.anular(Instant.now(reloj), motivo);
                var auditoria = new OperacionFiscal(f, "ANULAR_LOCAL", motivo, Instant.now(reloj));
                auditoria.resultado("LOCAL", "Sin autorización fiscal: anulación administrativa", null, Instant.now(reloj));
                operaciones.save(auditoria); facturas.flush();
                return new Envio(null, operacion, mapper.aReporte(f));
            }
            arca.verificarConfiguracion(); // Fail closed ANTES de registrar cualquier envío.
            arca.verificarFactura(mapper.aReporte(f), operacion);
            var op = operaciones.save(new OperacionFiscal(f, operacion, motivo, Instant.now(reloj)));
            f.cambiarEstadoArca(EstadoArca.EN_PROCESO); facturas.flush();
            return new Envio(op.getId(), operacion, mapper.aReporte(f));
        });
        if (envio.clave() == null) return envio.factura();
        return llamar(envio, false);
    }
    public FacturaReporteDTO conciliar(Long id) {
        arca.verificarConfiguracion();
        Envio envio = tx.execute(status -> {
            var f = buscar(id);
            var op = operaciones.findFirstByFacturaIdOrderByCreadaDesc(id)
                .orElseThrow(() -> new ConflictoNegocioException("No existe un envío para conciliar."));
            if (!op.getEstado().equals("EN_PROCESO") && !op.getEstado().equals("INCIERTA"))
                throw new ConflictoNegocioException("La última operación ya tiene un resultado definitivo.");
            return new Envio(op.getId(), op.getOperacion(), mapper.aReporte(f));
        });
        return llamar(envio, true); // Solo consulta: nunca emite de nuevo.
    }
    private FacturaReporteDTO llamar(Envio envio, boolean consulta) {
        ArcaGateway.Resultado resultado;
        try { resultado = consulta ? arca.consultar(envio.clave(), envio.operacion(), envio.factura())
            : arca.ejecutar(envio.clave(), envio.operacion(), envio.factura()); }
        catch (RuntimeException fallo) {
            marcarIncierta(envio, null);
            throw new ConflictoNegocioException("No se pudo confirmar la respuesta de ARCA. La factura quedó bloqueada para conciliación; no reenvíes el comprobante.");
        }
        if (resultado == null || (resultado.aprobada() && (!resultado.autorizacionCompleta()
            || (envio.operacion().equals("ANULAR") && !envio.factura().id().equals(resultado.facturaAsociadaId())))) || !resultado.definitiva()) {
            marcarIncierta(envio, resultado);
            throw new ConflictoNegocioException("La respuesta fiscal es incompleta o incierta. Se requiere conciliación; no se marcó como informada.");
        }
        FacturaReporteDTO factura;
        try {
            factura = tx.execute(status -> {
                var f = buscar(envio.factura().id());
                var op = operaciones.findById(envio.clave()).orElseThrow();
                if (op.getEstado().equals("APROBADA") || op.getEstado().equals("RECHAZADA")) return mapper.aReporte(f);
                op.resultado(resultado.aprobada() ? "APROBADA" : "RECHAZADA", resultado.mensaje(), resultado.respuestaAuditoria(), Instant.now(reloj));
                if (resultado.aprobada()) {
                    f.definirAmbienteArca(arca.ambiente());
                    op.autorizacion(resultado.tipoComprobante(), resultado.numero(), resultado.tipoAutorizacion(), resultado.codigo(), resultado.vencimiento());
                    if (envio.operacion().equals("ANULAR")) {
                        f.cambiarEstadoArca(EstadoArca.AUTORIZADA);
                        f.anular(Instant.now(reloj), op.getMotivo());
                    } else f.autorizar(resultado.numero(), resultado.tipoComprobante(), resultado.tipoAutorizacion(),
                        resultado.codigo(), resultado.vencimiento(), Instant.now(reloj));
                } else f.cambiarEstadoArca(f.isInformadaArca() ? EstadoArca.AUTORIZADA : EstadoArca.RECHAZADA);
                facturas.flush(); return mapper.aReporte(f);
            });
        } catch (RuntimeException falloPersistencia) {
            marcarIncierta(envio, resultado);
            throw new ConflictoNegocioException("ARCA respondió pero no se pudo consolidar el resultado local. Conciliá la operación; no reenvíes.");
        }
        if (!resultado.aprobada()) throw new ConflictoNegocioException("ARCA rechazó la operación: " + resultado.mensaje());
        return factura;
    }
    private void marcarIncierta(Envio envio, ArcaGateway.Resultado respuesta) {
        tx.executeWithoutResult(status -> {
            var f = buscar(envio.factura().id()); var op = operaciones.findById(envio.clave()).orElseThrow();
            if (op.getEstado().equals("APROBADA") || op.getEstado().equals("RECHAZADA")) return;
            op.resultado("INCIERTA", "Requiere consulta del resultado en ARCA",
                respuesta == null ? null : respuesta.respuestaAuditoria(), Instant.now(reloj));
            f.cambiarEstadoArca(EstadoArca.INCIERTA);
        });
    }
    private FacturaVenta buscar(Long id) {
        return facturas.buscarParaActualizar(id).orElseThrow(() -> new RecursoNoEncontradoException("Factura no encontrada: " + id));
    }
}
