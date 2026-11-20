package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.entity.TipoDescuento;
import ar.edu.utn.facturacion.exception.SolicitudInvalidaException;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reparte centavos por restos mayores, con desempate por orden del renglón. */
@Component
public class DescuentoGeneral {
    public record Resultado(TipoDescuento tipo, BigDecimal valor, BigDecimal importe, List<BigDecimal> porRenglon) {}
    public Resultado calcular(List<BigDecimal> subtotales, TipoDescuento tipo, BigDecimal valor) {
        tipo = tipo == null ? TipoDescuento.SIN_DESCUENTO : tipo;
        valor = valor == null ? BigDecimal.ZERO : valor;
        if (valor.signum() < 0 || valor.scale() > 2 || valor.precision() - valor.scale() > 17)
            throw new SolicitudInvalidaException("El descuento debe ser positivo y admitir hasta dos decimales.");
        if (tipo == TipoDescuento.SIN_DESCUENTO && valor.signum() != 0)
            throw new SolicitudInvalidaException("Elegí porcentaje o monto fijo para aplicar un descuento.");
        if (tipo == TipoDescuento.PORCENTAJE && valor.compareTo(new BigDecimal("100")) > 0)
            throw new SolicitudInvalidaException("El porcentaje de descuento no puede superar el 100%.");
        BigDecimal subtotal = subtotales.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        BigDecimal importe = tipo == TipoDescuento.PORCENTAJE ? subtotal.multiply(valor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
            : tipo == TipoDescuento.MONTO_FIJO ? valor.setScale(2) : new BigDecimal("0.00");
        if (importe.compareTo(subtotal) > 0) throw new SolicitudInvalidaException("El descuento no puede superar el subtotal antes de IVA.");
        var centavos = new ArrayList<BigInteger>();
        var restos = new ArrayList<BigInteger>();
        BigInteger base = subtotal.movePointRight(2).toBigIntegerExact(), descuento = importe.movePointRight(2).toBigIntegerExact();
        BigInteger asignado = BigInteger.ZERO;
        for (BigDecimal renglon : subtotales) {
            BigInteger[] parte = base.signum() == 0 ? new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}
                : renglon.movePointRight(2).toBigIntegerExact().multiply(descuento).divideAndRemainder(base);
            centavos.add(parte[0]); restos.add(parte[1]); asignado = asignado.add(parte[0]);
        }
        var orden = new ArrayList<Integer>();
        for (int i = 0; i < subtotales.size(); i++) orden.add(i);
        orden.sort(Comparator.<Integer, BigInteger>comparing(restos::get).reversed().thenComparingInt(Integer::intValue));
        int faltantes = descuento.subtract(asignado).intValueExact();
        for (int i = 0; i < faltantes; i++) { int indice = orden.get(i); centavos.set(indice, centavos.get(indice).add(BigInteger.ONE)); }
        return new Resultado(tipo, valor.setScale(2), importe, centavos.stream().map(c -> new BigDecimal(c, 2)).toList());
    }
}
