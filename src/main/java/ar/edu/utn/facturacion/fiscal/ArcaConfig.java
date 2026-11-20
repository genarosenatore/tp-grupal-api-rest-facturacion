package ar.edu.utn.facturacion.fiscal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import ar.edu.utn.facturacion.exception.ConflictoNegocioException;
import ar.edu.utn.facturacion.dto.FacturaReporteDTO;
import java.util.UUID;

@Configuration
public class ArcaConfig {
    @Bean @ConditionalOnMissingBean(ArcaGateway.class)
    @ConditionalOnProperty(name="arca.homologacion.enabled",havingValue="false",matchIfMissing=true)
    ArcaGateway arcaNoConfigurada() {
        return new ArcaGateway() {
            public void verificarConfiguracion() {
                throw new ConflictoNegocioException("ARCA no está configurada. Falta definir régimen/servicio fiscal, CUIT y datos del emisor, certificado y clave WSAA de homologación, punto de venta, tipos de comprobante, documento/condición fiscal del receptor y cotización. Ver docs/CAMBIOS_FACTURACION.md. No se realizó ningún envío.");
            }
            public Resultado ejecutar(UUID clave, String operacion, FacturaReporteDTO factura) { verificarConfiguracion(); throw new IllegalStateException(); }
            public Resultado consultar(UUID clave, String operacion, FacturaReporteDTO factura) { verificarConfiguracion(); throw new IllegalStateException(); }
        };
    }
}
