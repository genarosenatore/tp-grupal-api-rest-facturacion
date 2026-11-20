package ar.edu.utn.facturacion.entity;

/** Letra persistida del comprobante; el tipo fiscal determina su valor. */
public enum LetraComprobante {
    A, B, C, X;

    public static LetraComprobante desdeTipo(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "1", "3" -> A;
            case "6", "8" -> B;
            case "11", "13" -> C;
            // El circuito local y los tipos históricos de prueba no son facturas A/B/C.
            default -> X;
        };
    }
}
