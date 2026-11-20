package ar.edu.utn.facturacion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.LocalDate;

/** Identidad comercial para impresión. Las credenciales fiscales no forman parte de esta configuración. */
@ConfigurationProperties("facturacion.emisor")
public record EmisorFactura(String razonSocial, String cuit, String domicilio, String condicionIva,
        String ingresosBrutos, LocalDate inicioActividades, boolean datosDemostracion) {
    public boolean completo() {
        return presente(razonSocial) && presente(cuit) && presente(domicilio)
            && presente(condicionIva) && presente(ingresosBrutos) && inicioActividades != null;
    }
    private static boolean presente(String s) { return s != null && !s.isBlank(); }
}
