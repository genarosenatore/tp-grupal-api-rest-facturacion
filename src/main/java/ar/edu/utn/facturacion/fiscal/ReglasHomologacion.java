package ar.edu.utn.facturacion.fiscal;

import java.math.BigDecimal;
import java.util.Set;

/** Alcance del laboratorio: productos en pesos, A a RI/monotributo, B a CF/exento, C a las cuatro condiciones. */
public final class ReglasHomologacion {
    private ReglasHomologacion() {}
    public static boolean admite(String tipo) { return tipo != null && Set.of("1", "6", "11").contains(tipo); }
    public static String letra(String tipo) {
        return ar.edu.utn.facturacion.entity.LetraComprobante.desdeTipo(tipo).name();
    }
    public static int credito(String tipo) {
        return switch (tipo) { case "1" -> 3; case "6" -> 8; case "11" -> 13; default -> throw new IllegalArgumentException("Seleccioná Factura A, B o C de homologación."); };
    }
    public static String condicion(Integer id) {
        if (id == null) return "No informada";
        return switch (id) { case 1 -> "IVA Responsable Inscripto"; case 4 -> "IVA Sujeto Exento"; case 5 -> "Consumidor final"; case 6 -> "Responsable Monotributo"; default -> "Condición " + id; };
    }
    public static void validarReceptor(String tipo, Integer documentoTipo, Integer condicion, String documento) {
        if (!admite(tipo)) throw new IllegalArgumentException("Seleccioná Factura A, B o C de homologación.");
        boolean condicionValida = condicion != null && switch (tipo) {
            case "1" -> Set.of(1, 6).contains(condicion);
            case "6" -> Set.of(4, 5).contains(condicion);
            default -> Set.of(1, 4, 5, 6).contains(condicion);
        };
        if (!condicionValida) throw new IllegalArgumentException("La condición fiscal del receptor no corresponde al escenario de Factura " + letra(tipo) + ".");
        if (documentoTipo == null || !Set.of(80, 96, 99).contains(documentoTipo))
            throw new IllegalArgumentException("Indicá el tipo de documento del receptor.");
        if ((tipo.equals("1") || Set.of(1, 4, 6).contains(condicion)) && documentoTipo != 80)
            throw new IllegalArgumentException("Factura A y clientes inscriptos, monotributistas o exentos requieren CUIT (80).");
        String numero = documento == null ? "" : documento.replace("-", "");
        if ((documentoTipo == 80 && !numero.matches("[0-9]{11}")) || (documentoTipo == 96 && !numero.matches("[0-9]{7,8}")))
            throw new IllegalArgumentException("El documento del cliente no coincide con el tipo de documento seleccionado.");
    }
    public static int idIva(BigDecimal alicuota) {
        return switch (alicuota.stripTrailingZeros().toPlainString()) {
            case "0" -> 3; case "10.5" -> 4; case "21" -> 5; case "27" -> 6; case "5" -> 8; case "2.5" -> 9;
            default -> throw new IllegalArgumentException("Alícuota " + alicuota + "% no admitida por el laboratorio A/B. Usá 0, 2,5, 5, 10,5, 21 o 27%.");
        };
    }
}
