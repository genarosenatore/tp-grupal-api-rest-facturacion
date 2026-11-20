package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.fiscal.ArcaGateway;
import ar.edu.utn.facturacion.service.*;
import ar.edu.utn.facturacion.exception.ConflictoNegocioException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.*;

/** HTTP y transacciones reales con H2; ARCA es exclusivamente un doble de prueba. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("flujos")
@Import(FacturaFlujosTest.Config.class)
class FacturaFlujosTest {
    @TestConfiguration static class Config {
        @Bean @Primary ControlArca controlArca() { return new ControlArca(); }
    }
    static class ControlArca implements ArcaGateway {
        AtomicInteger envios = new AtomicInteger();
        boolean configurada = true, rechazo, fallo, incompleta, asociacionIncorrecta;
        CountDownLatch entro, continuar;
        public void verificarConfiguracion() {
            if (!configurada) throw new ConflictoNegocioException("Falta certificado de homologación (prueba controlada)");
        }
        public Resultado ejecutar(UUID clave, String operacion, FacturaReporteDTO factura) {
            envios.incrementAndGet();
            if (entro != null) {
                entro.countDown();
                try { if (!continuar.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
            if (fallo) throw new IllegalStateException("Timeout controlado, sin llamada externa");
            return respuesta(operacion, factura);
        }
        public Resultado consultar(UUID clave, String operacion, FacturaReporteDTO factura) { return respuesta(operacion, factura); }
        Resultado respuesta(String operacion, FacturaReporteDTO factura) {
            return new Resultado(!rechazo, true, rechazo ? "Rechazo controlado" : "Prueba local",
                "<RespuestaDePruebaSinValorFiscal/>", operacion.equals("ANULAR") ? "NOTA_CREDITO_PRUEBA" : "FACTURA_PRUEBA",
                factura.id(), "CAE", incompleta ? null : "12345678901234", LocalDate.of(2026, 11, 20),
                operacion.equals("ANULAR") ? (asociacionIncorrecta ? -1L : factura.id()) : null);
        }
        void reset() { envios.set(0); configurada = true; rechazo = fallo = incompleta = asociacionIncorrecta = false; entro = continuar = null; }
    }
    @Autowired FacturaService facturas;
    @Autowired CircuitoFiscalService fiscal;
    @Autowired FacturaPdfService pdf;
    @Autowired JdbcTemplate jdbc;
    @Autowired ControlArca arca;
    final HttpClient http = HttpClient.newHttpClient();
    @BeforeEach void limpiar() {
        arca.reset(); jdbc.update("delete from operacion_fiscal");
        jdbc.update("delete from factura_detalle"); jdbc.update("delete from factura_venta");
    }
    FacturaReporteDTO crear() {
        return facturas.crear(new FacturaRequestDTO(LocalDate.of(2026, 10, 23), 1L, 1L, 1L, 1L,
            "Observación original", List.of(new FacturaDetalleRequestDTO(1L, new java.math.BigDecimal("2")),
                new FacturaDetalleRequestDTO(2L, new java.math.BigDecimal("3"))), null));
    }
    HttpResponse<String> enviar(String metodo, String ruta, String cuerpo) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/api/facturas" + ruta))
            .header("Content-Type", "application/json").method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
    @Test void filtrosCombinadosNoConfundenNumeroConAutorizacionYPaginan() throws Exception {
        for (int i = 0; i < 13; i++) crear();
        var p0 = facturas.listar(1L, null, LocalDate.of(2026,10,23), LocalDate.of(2026,10,23), null, false, 0, 8);
        var p1 = facturas.listar(1L, null, LocalDate.of(2026,10,23), LocalDate.of(2026,10,23), null, false, 1, 8);
        assertThat(p0.totalElements()).isEqualTo(13); assertThat(p0.totalPages()).isEqualTo(2);
        assertThat(p0.content()).hasSize(8); assertThat(p1.content()).hasSize(5);
        assertThat(p0.content().stream().map(FacturaReporteDTO::id).toList()).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(p0.content().stream().map(FacturaReporteDTO::id).toList()).doesNotContainAnyElementsOf(p1.content().stream().map(FacturaReporteDTO::id).toList());
        Long id = p0.content().getFirst().id(); jdbc.update("update factura_venta set numero=7 where id=?", id);
        assertThat(facturas.listar(1L, null, LocalDate.of(2026,10,23), LocalDate.of(2026,10,23), 7L, false, 0, 8).content()).hasSize(1);
        assertThat(facturas.listar(1L, null, null, null, 7L, true, 0, 8).totalElements()).isZero();
        assertThat(facturas.listar(2L, null, null, null, null, null, 0, 8).content()).isEmpty();
        assertThat(enviar("GET", "?page=1&size=8&clienteId=1&informadaArca=false", null).body()).contains("\"totalElements\":13", "\"page\":1");
        for (String q : List.of("?desde=2026-11-01&hasta=2026-10-01", "?page=-1", "?size=101", "?size=0", "?numero=0", "?informadaArca=otro"))
            assertThat(enviar("GET", q, null).statusCode()).as(q).isEqualTo(400);
    }
    @Test void deleteFisicoSoloSinNumeroYAnulacionConservaHistoria() throws Exception {
        var borrador = crear(); assertThat(enviar("DELETE", "/" + borrador.id(), null).statusCode()).isEqualTo(204);
        assertThat(enviar("GET", "/" + borrador.id(), null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from factura_detalle where factura_id=?", Integer.class, borrador.id())).isZero();
        var f = crear(); jdbc.update("update factura_venta set numero=99 where id=?", f.id());
        assertThat(enviar("DELETE", "/" + f.id(), null).statusCode()).isEqualTo(409);
        assertThat(enviar("POST", "/" + f.id() + "/anular", "{\"motivo\":\"\"}").statusCode()).isEqualTo(400);
        var anulada = fiscal.anular(f.id(), "Error de carga");
        assertThat(anulada.estado().name()).isEqualTo("ANULADA");
        assertThat(anulada.observaciones()).isEqualTo("Observación original");
        assertThat(anulada.detalles()).usingRecursiveComparison()
            .withComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class).isEqualTo(f.detalles());
        assertThat(anulada.motivoAnulacion()).isEqualTo("Error de carga");
        assertThat(fiscal.anular(f.id(), "Otro motivo").fechaAnulacion()).isEqualTo(anulada.fechaAnulacion());
        assertThat(jdbc.queryForObject("select count(*) from operacion_fiscal where factura_id=?", Integer.class, f.id())).isEqualTo(1);
        assertThat(enviar("DELETE", "/" + f.id(), null).statusCode()).isEqualTo(409);
    }
    @Test void autorizacionPersisteRespuestaRealDelDobleYBloqueaTodaEdicion() throws Exception {
        var f = crear(); var autorizada = fiscal.autorizar(f.id());
        assertThat(autorizada.informadaArca()).isTrue(); assertThat(autorizada.editable()).isFalse(); assertThat(autorizada.eliminable()).isFalse();
        assertThat(autorizada.codigoAutorizacion()).isEqualTo("12345678901234");
        assertThat(jdbc.queryForObject("select respuesta from operacion_fiscal where factura_id=?", String.class, f.id())).isEqualTo("<RespuestaDePruebaSinValorFiscal/>");
        assertThat(fiscal.autorizar(f.id())).isEqualTo(autorizada); assertThat(arca.envios.get()).isEqualTo(1);
        assertThat(enviar("PUT", "/" + f.id(), "{\"estado\":\"EMITIDA\",\"observaciones\":\"Cambio\"}").statusCode()).isEqualTo(409);
        assertThat(enviar("PUT", "/" + f.id(), "{\"estado\":\"ANULADA\"}").statusCode()).isEqualTo(409);
        assertThat(enviar("DELETE", "/" + f.id(), null).statusCode()).isEqualTo(409);
        assertThat(enviar("GET", "/" + f.id(), null).statusCode()).isEqualTo(200);
        arca.configurada = false;
        assertThatThrownBy(() -> fiscal.anular(f.id(), "Devolución")).hasMessageContaining("homologación");
        assertThat(facturas.obtener(f.id()).estado().name()).isEqualTo("EMITIDA");
    }
    @Test void anulacionFiscalGuardaAjusteSinReemplazarCaeOriginal() {
        var f = fiscal.autorizar(crear().id());
        var anulada = fiscal.anular(f.id(), "Devolución total");
        assertThat(anulada.estado().name()).isEqualTo("ANULADA");
        assertThat(anulada.codigoAutorizacion()).isEqualTo(f.codigoAutorizacion());
        assertThat(anulada.fechaAutorizacion()).isEqualTo(f.fechaAutorizacion());
        assertThat(jdbc.queryForObject("select tipo_comprobante from operacion_fiscal where factura_id=? and operacion='ANULAR'", String.class, f.id())).isEqualTo("NOTA_CREDITO_PRUEBA");
        assertThat(arca.envios.get()).isEqualTo(2);
    }
    @Test void noAnulaSiElAjusteNoEstaAsociadoAlOriginal() {
        var f = fiscal.autorizar(crear().id()); arca.asociacionIncorrecta = true;
        assertThatThrownBy(() -> fiscal.anular(f.id(), "Devolución")).hasMessageContaining("incierta");
        assertThat(facturas.obtener(f.id()).estado().name()).isEqualTo("EMITIDA");
    }
    @Test void rechazoNoInformaYErrorInciertoExigeConciliacionSinReenvio() {
        var f = crear(); arca.rechazo = true;
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("rechazó");
        assertThat(facturas.obtener(f.id()).informadaArca()).isFalse();
        assertThat(facturas.obtener(f.id()).estadoArca().name()).isEqualTo("RECHAZADA");
        assertThat(facturas.obtener(f.id()).codigoAutorizacion()).isNull();
        arca.rechazo = false; arca.fallo = true;
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("conciliación");
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("no se reenviará");
        assertThat(facturas.obtener(f.id()).editable()).isFalse(); assertThat(arca.envios.get()).isEqualTo(2);
        arca.fallo = false; var conciliada = fiscal.conciliar(f.id());
        assertThat(conciliada.informadaArca()).isTrue(); assertThat(arca.envios.get()).isEqualTo(2);
    }
    @Test void configuracionAusenteNoCreaEnvioNiModificaEstado() {
        var f = crear(); arca.configurada = false;
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("homologación");
        assertThat(facturas.obtener(f.id()).estadoArca().name()).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("select count(*) from operacion_fiscal", Integer.class)).isZero();
    }
    @Test void borradorRechazadoPuedeEliminarseSinPerderAuditoria() {
        var f = crear(); arca.rechazo = true;
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("rechazó");
        assertThat(facturas.obtener(f.id()).eliminable()).isTrue(); facturas.eliminar(f.id());
        assertThat(jdbc.queryForObject("select count(*) from factura_venta where id=?", Integer.class, f.id())).isZero();
        assertThat(jdbc.queryForObject("select respuesta from operacion_fiscal where factura_original_id=?", String.class, f.id())).isEqualTo("<RespuestaDePruebaSinValorFiscal/>");
        assertThat(jdbc.queryForObject("select factura_id from operacion_fiscal where factura_original_id=?", Long.class, f.id())).isNull();
    }
    @Test void respuestaIncompletaNoInformaYConservaRespuestaParaAuditoria() {
        var f = crear(); arca.incompleta = true;
        assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("incompleta");
        var guardada = facturas.obtener(f.id());
        assertThat(guardada.informadaArca()).isFalse(); assertThat(guardada.codigoAutorizacion()).isNull();
        assertThat(guardada.estadoArca().name()).isEqualTo("INCIERTA");
        assertThat(jdbc.queryForObject("select respuesta from operacion_fiscal where factura_id=?", String.class, f.id())).isEqualTo("<RespuestaDePruebaSinValorFiscal/>");
    }
    @Test void dosSolicitudesConcurrentesEmitenUnaSolaVez() throws Exception {
        var f = crear(); arca.entro = new CountDownLatch(1); arca.continuar = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var primera = executor.submit(() -> fiscal.autorizar(f.id()));
            assertThat(arca.entro.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> fiscal.autorizar(f.id())).hasMessageContaining("no se reenviará");
            arca.continuar.countDown(); assertThat(primera.get(5, TimeUnit.SECONDS).informadaArca()).isTrue();
        } finally { arca.continuar.countDown(); }
        assertThat(arca.envios.get()).isEqualTo(1);
    }
    @Test void datosComercialesSeConservanYNoSePuedenCambiarConUnPutDeEstado() throws Exception {
        var f=facturas.crear(new FacturaRequestDTO(LocalDate.of(2026,11,11),1L,1L,1L,1L,null,
            List.of(new FacturaDetalleRequestDTO(1L,new java.math.BigDecimal("1"))),null,
            "11",96,5,"Calle de Prueba 456","Contado"));
        assertThat(facturas.obtener(f.id()).clienteDomicilio()).isEqualTo("Calle de Prueba 456");
        assertThat(facturas.obtener(f.id()).condicionVenta()).isEqualTo("Contado");
        assertThat(texto(pdf.generar(f.id()))).contains("Calle de Prueba 456","Condición de venta: Contado");
        assertThat(enviar("PUT","/"+f.id(),"{\"estado\":\"EMITIDA\",\"clienteDomicilio\":\"Otro domicilio\"}").statusCode()).isEqualTo(400);
        assertThat(facturas.obtener(f.id()).clienteDomicilio()).isEqualTo("Calle de Prueba 456");
    }
    @Test void pdfIncluyeDatosHistoricosAutorizacionYAnulacion() throws Exception {
        var f = crear(); String pendiente = texto(pdf.generar(f.id()));
        var directorio = java.nio.file.Path.of("tmp", "pdfs"); java.nio.file.Files.createDirectories(directorio);
        java.nio.file.Files.write(directorio.resolve("factura-pendiente.pdf"), pdf.generar(f.id()));
        try (var doc = Loader.loadPDF(pdf.generar(f.id()))) {
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, 110),
                "png", directorio.resolve("factura-pendiente.png").toFile());
        }
        assertThat(pendiente).contains("COMPROBANTE X", "DOCUMENTO NO VÁLIDO COMO FACTURA", "Cuaderno", "3.083,00", "12345678", "SIN NÚMERO").doesNotContain("CAE:", "PENDIENTE DE AUTORIZACIÓN");
        fiscal.autorizar(f.id()); String autorizada = texto(pdf.generar(f.id()));
        assertThat(autorizada).contains("CAE: 12345678901234", "20/11/2026", "FACTURA_PRUEBA", "PRUEBA CONTROLADA - SIN VALIDEZ FISCAL");
        fiscal.anular(f.id(), "Devolución total"); assertThat(texto(pdf.generar(f.id()))).contains("ANULADA", "Devolución total", "NOTA_CREDITO_PRUEBA", "CAE: 12345678901234");
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/api/facturas/" + f.id() + "/pdf")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200); assertThat(response.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(texto(response.body())).contains("ANULADA");
    }
    @Test void pdfPaginaCienRenglonesYObservacionesLargasSinOmitirTotales() throws Exception {
        var renglones = java.util.stream.IntStream.range(0,100)
            .mapToObj(i -> new FacturaDetalleRequestDTO(1L, new java.math.BigDecimal("0.001"))).toList();
        var f = facturas.crear(new FacturaRequestDTO(LocalDate.of(2026,10,23), 1L, 1L, 1L, 1L,
            "Observación de prueba\n" + "Texto extenso ".repeat(60), renglones, null));
        byte[] bytes = pdf.generar(f.id());
        var directorio = java.nio.file.Path.of("tmp", "pdfs"); java.nio.file.Files.createDirectories(directorio);
        java.nio.file.Files.write(directorio.resolve("factura-cien-renglones.pdf"), bytes);
        try (var doc = Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            var extractor = new PDFTextStripper(); String contenido = extractor.getText(doc);
            assertThat(contenido.split("Cuaderno A4", -1).length - 1).isEqualTo(100);
            assertThat(contenido).contains("TOTAL ARS", "121,00", "Observación de prueba", "COMPROBANTE X", "DOCUMENTO NO VÁLIDO COMO FACTURA");
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(doc.getNumberOfPages()-1,110), "png",
                directorio.resolve("factura-ultima-pagina.png").toFile());
        }
    }
    @Test void letrasSeAsignanEnServidorSePersistenYSeDevuelvenPorHttp() throws Exception {
        jdbc.update("merge into cliente(id,nombre,documento,tipo_documento,condicion_iva_receptor_id) key(id) values (99,'Cliente CUIT de prueba','20123456786',80,1)");
        for (String tipo : List.of("1", "6", "11", "")) {
            String letra = switch (tipo) { case "1" -> "A"; case "6" -> "B"; case "11" -> "C"; default -> "X"; };
            String camposFiscales = tipo.isEmpty() ? "" : ",\"tipoComprobante\":\"" + tipo
                + "\",\"tipoDocumentoReceptor\":" + (tipo.equals("1") ? 80 : 96)
                + ",\"condicionIvaReceptorId\":" + (tipo.equals("1") ? 1 : 5);
            String cuerpo = "{\"fecha\":\"2026-11-17\",\"clienteId\":" + (tipo.equals("1") ? 99 : 1)
                + ",\"condicionIvaId\":1,\"puntoVentaId\":1,\"tipoMonedaId\":1,"
                + "\"detalles\":[{\"articuloId\":1,\"cantidad\":1}]" + camposFiscales + "}";
            var respuesta = enviar("POST", "", cuerpo);
            assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
            assertThat(respuesta.body()).contains("\"letraComprobante\":\"" + letra + "\"");
            var match = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(respuesta.body());
            assertThat(match.find()).isTrue(); Long id = Long.valueOf(match.group(1));
            assertThat(jdbc.queryForObject("select letra_comprobante from factura_venta where id=?", String.class, id)).isEqualTo(letra);
            assertThat(facturas.obtener(id).letraComprobante().name()).isEqualTo(letra);
            assertThat(enviar("GET", "/" + id, null).body()).contains("\"letraComprobante\":\"" + letra + "\"");
            // La letra no es un dato editable del request; el servidor la determina por tipo.
            assertThat(enviar("POST", "", cuerpo.replace("\"fecha\":", "\"letraComprobante\":\"B\",\"fecha\":")).statusCode()).isEqualTo(400);
            if (tipo.equals("11")) {
                assertThat(enviar("PUT", "/" + id, cuerpo.replace(camposFiscales, "")).statusCode()).isEqualTo(200);
                assertThat(facturas.obtener(id).letraComprobante().name()).isEqualTo("X");
                assertThat(enviar("PUT", "/" + id, cuerpo).statusCode()).isEqualTo(200);
                assertThat(facturas.obtener(id).letraComprobante().name()).isEqualTo("C");
            }
        }
        assertThat(enviar("GET", "", null).body()).contains("\"letraComprobante\":\"A\"", "\"letraComprobante\":\"B\"", "\"letraComprobante\":\"C\"", "\"letraComprobante\":\"X\"");
    }
    static String texto(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); }
    }
}
