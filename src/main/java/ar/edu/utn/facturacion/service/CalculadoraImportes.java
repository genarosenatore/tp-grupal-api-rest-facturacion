package ar.edu.utn.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
/** Redondeo por renglón; se suman los importes ya redondeados. */
@Component
public class CalculadoraImportes {
    public record Importes(BigDecimal neto, BigDecimal iva, BigDecimal total) {}
    public Importes calcular(BigDecimal cantidad, BigDecimal precio, BigDecimal alicuota) {
        BigDecimal neto = cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva = neto.multiply(alicuota).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new Importes(neto, iva, neto.add(iva));
    }
}
