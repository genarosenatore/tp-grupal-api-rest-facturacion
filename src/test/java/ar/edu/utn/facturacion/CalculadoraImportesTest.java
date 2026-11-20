package ar.edu.utn.facturacion;
import ar.edu.utn.facturacion.service.CalculadoraImportes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class CalculadoraImportesTest {
    private final CalculadoraImportes calculadora = new CalculadoraImportes();
    @Test void calculaPrimerRenglon() {
        var r = calculadora.calcular(new BigDecimal("2"), new BigDecimal("1000"), new BigDecimal("21"));
        assertThat(r.neto()).isEqualByComparingTo("2000.00");
        assertThat(r.iva()).isEqualByComparingTo("420.00");
        assertThat(r.total()).isEqualByComparingTo("2420.00");
    }
    @Test void calculaAlicuotaDecimal() {
        var r = calculadora.calcular(new BigDecimal("3"), new BigDecimal("200"), new BigDecimal("10.50"));
        assertThat(r.total()).isEqualByComparingTo("663.00");
    }
    @Test void redondeaMitadHaciaArribaPorRenglon() {
        var r = calculadora.calcular(new BigDecimal("0.125"), new BigDecimal("1"), new BigDecimal("21"));
        assertThat(r.neto()).isEqualByComparingTo("0.13");
        assertThat(r.iva()).isEqualByComparingTo("0.03");
        assertThat(r.total()).isEqualByComparingTo("0.16");
    }
    @Test void permiteIvaCero() {
        var r = calculadora.calcular(new BigDecimal("1"), new BigDecimal("5"), BigDecimal.ZERO);
        assertThat(r.iva()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("5.00");
    }
}
