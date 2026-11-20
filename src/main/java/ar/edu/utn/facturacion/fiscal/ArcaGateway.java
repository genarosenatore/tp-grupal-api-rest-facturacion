package ar.edu.utn.facturacion.fiscal;

import ar.edu.utn.facturacion.dto.FacturaReporteDTO;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto para el adaptador del régimen fiscal que se confirme con el emisor.
 * La clave identifica el mismo envío en autorización y conciliación. El adaptador debe
 * reservar correlativos por CUIT/punto/tipo y consultar ARCA antes de reenviar.
 * respuestaAuditoria contiene la respuesta real SIN Token, Sign, certificado ni secretos.
 */
public interface ArcaGateway {
    default String ambiente() { return "PRUEBA_CONTROLADA"; }
    default void verificarFactura(FacturaReporteDTO factura, String operacion) {}
    void verificarConfiguracion();
    Resultado ejecutar(UUID clave, String operacion, FacturaReporteDTO factura);
    Resultado consultar(UUID clave, String operacion, FacturaReporteDTO factura);
    record Resultado(boolean aprobada, boolean definitiva, String mensaje, String respuestaAuditoria,
        String tipoComprobante, Long numero, String tipoAutorizacion, String codigo, LocalDate vencimiento,
        Long facturaAsociadaId) {
        public boolean autorizacionCompleta() {
            return aprobada && definitiva && numero != null && numero > 0
                && tipoComprobante != null && !tipoComprobante.isBlank() && tipoComprobante.length() <= 40
                && java.util.Set.of("CAE", "CAEA", "CAI").contains(tipoAutorizacion == null ? "" : tipoAutorizacion)
                && codigo != null && codigo.matches("[0-9]{1,30}") && vencimiento != null
                && respuestaAuditoria != null && !respuestaAuditoria.isBlank();
        }
    }
}
