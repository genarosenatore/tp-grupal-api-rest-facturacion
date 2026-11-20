package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.entity.TipoDescuento;
import ar.edu.utn.facturacion.service.DescuentoGeneral;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescuentoGeneralTest {
    final DescuentoGeneral calculadora = new DescuentoGeneral();
    @Test void porcentajeYMontoFijoProducenElMismoReparto() {
        var bases = List.of(new BigDecimal("2000.00"),new BigDecimal("600.00"));
        var porcentaje = calculadora.calcular(bases,TipoDescuento.PORCENTAJE,new BigDecimal("10"));
        var fijo = calculadora.calcular(bases,TipoDescuento.MONTO_FIJO,new BigDecimal("260"));
        assertThat(porcentaje.importe()).isEqualByComparingTo("260");
        assertThat(porcentaje.porRenglon()).containsExactly(new BigDecimal("200.00"),new BigDecimal("60.00"));
        assertThat(fijo.porRenglon()).isEqualTo(porcentaje.porRenglon());
    }
    @Test void reparteElCentavoRestanteConDesempateEstableSinDescontarRenglonesEnCero() {
        var r = calculadora.calcular(List.of(new BigDecimal("0.01"),new BigDecimal("0.01"),new BigDecimal("0.01"),new BigDecimal("0.00")),TipoDescuento.MONTO_FIJO,new BigDecimal("0.02"));
        assertThat(r.porRenglon()).containsExactly(new BigDecimal("0.01"),new BigDecimal("0.01"),new BigDecimal("0.00"),new BigDecimal("0.00"));
    }
    @Test void admiteCeroYCienPorCientoYRechazaDescuentosInvalidos() {
        var bases = List.of(new BigDecimal("0.01"),new BigDecimal("0.02"));
        assertThat(calculadora.calcular(bases,TipoDescuento.PORCENTAJE,new BigDecimal("100")).porRenglon()).isEqualTo(bases);
        assertThat(calculadora.calcular(List.of(BigDecimal.ZERO),TipoDescuento.PORCENTAJE,new BigDecimal("100")).importe()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> calculadora.calcular(bases,TipoDescuento.PORCENTAJE,new BigDecimal("100.01"))).hasMessageContaining("100%");
        assertThatThrownBy(() -> calculadora.calcular(bases,TipoDescuento.MONTO_FIJO,new BigDecimal("0.04"))).hasMessageContaining("subtotal");
        assertThatThrownBy(() -> calculadora.calcular(bases,TipoDescuento.MONTO_FIJO,new BigDecimal("-1"))).hasMessageContaining("descuento");
        assertThatThrownBy(() -> calculadora.calcular(bases,TipoDescuento.MONTO_FIJO,new BigDecimal("0.001"))).hasMessageContaining("decimales");
        assertThatThrownBy(() -> calculadora.calcular(bases,null,BigDecimal.ONE)).hasMessageContaining("Elegí");
    }
}
