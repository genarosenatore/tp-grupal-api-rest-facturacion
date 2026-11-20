package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.FacturaReporteDTO;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Especificación ARCA QR v1. No consulta ni valida el comprobante en producción. */
final class FacturaQr {
    private FacturaQr() {}
    static Optional<String> url(FacturaReporteDTO f, String cuit) {
        String ambiente = Objects.toString(f.ambienteArca(), "");
        String documento = Objects.toString(f.clienteDocumento(), "").replace("-", "");
        String emisor = Objects.toString(cuit, "").replace("-", "");
        if (!f.informadaArca() || !(ambiente.equals("HOMOLOGACION") || ambiente.equals("PRODUCCION"))
                || !emisor.matches("[0-9]{11}") || f.numero() == null || f.numero() < 1
                || !Objects.toString(f.tipoComprobante(), "").matches("[0-9]{1,3}")
                || !Objects.toString(f.codigoAutorizacion(), "").matches("[0-9]{14}")
                || !Set.of("CAE", "CAEA").contains(Objects.toString(f.tipoAutorizacion(), ""))
                || !"ARS".equals(f.moneda()) || !Objects.toString(f.puntoVentaCodigo(), "").matches("[0-9]{1,5}"))
            return Optional.empty();
        if (f.tipoDocumentoReceptor() == null || !Set.of(80,96,99).contains(f.tipoDocumentoReceptor())) return Optional.empty();
        if (f.tipoDocumentoReceptor() != 99 && !documento.matches("[0-9]{1,20}")) return Optional.empty();
        var datos = new LinkedHashMap<String, Object>();
        datos.put("ver", 1); datos.put("fecha", f.fecha().toString()); datos.put("cuit", new BigInteger(emisor));
        datos.put("ptoVta", Integer.parseInt(f.puntoVentaCodigo())); datos.put("tipoCmp", Integer.parseInt(f.tipoComprobante()));
        datos.put("nroCmp", f.numero()); datos.put("importe", f.total()); datos.put("moneda", "PES"); datos.put("ctz", 1);
        datos.put("tipoDocRec", f.tipoDocumentoReceptor());
        datos.put("nroDocRec", f.tipoDocumentoReceptor() == 99 ? BigInteger.ZERO : new BigInteger(documento));
        datos.put("tipoCodAut", "CAEA".equals(f.tipoAutorizacion()) ? "A" : "E");
        datos.put("codAut", new BigInteger(f.codigoAutorizacion()));
        String json = JsonMapper.builder().build().writeValueAsString(datos);
        return Optional.of("https://www.arca.gob.ar/fe/qr/?p=" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }
}
