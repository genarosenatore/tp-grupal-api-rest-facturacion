package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.TipoDescuento;
import ar.edu.utn.facturacion.service.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:cliente_descuentos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
class ClienteDescuentoFlujosTest {
    @Autowired FacturaService facturas;
    @Autowired ClienteService clientes;
    @Autowired CatalogoService catalogos;
    @Autowired FacturaPdfService pdf;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int puerto;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void preparar() {
        jdbc.update("delete from factura_detalle"); jdbc.update("delete from factura_venta");
        jdbc.update("update cliente set nombre='Ana Martínez',documento='12345678',tipo_documento=96,condicion_iva_receptor_id=5,domicilio='Calle original 123' where id=1");
        jdbc.update("update lista_precio_articulo set precio=1000 where id=1");
    }
    FacturaRequestDTO request(TipoDescuento tipo, String valor, String comprobante) {
        return new FacturaRequestDTO(LocalDate.of(2026,11,17),1L,1L,1L,1L,"Prueba de descuento",
            List.of(new FacturaDetalleRequestDTO(1L,new BigDecimal("2")),new FacturaDetalleRequestDTO(2L,new BigDecimal("3"))),
            null,comprobante,null,null,null,"Contado",1L,tipo,new BigDecimal(valor));
    }
    HttpResponse<String> enviar(String metodo,String ruta,String cuerpo) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api"+ruta)).header("Content-Type","application/json")
            .method(metodo,cuerpo==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(cuerpo)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void clienteEsLaFuenteYLasFacturasConservanTodosSusDatosHistoricos() throws Exception {
        var f=facturas.crear(request(TipoDescuento.SIN_DESCUENTO,"0",null));
        clientes.guardar(1L,new ClienteRequestDTO("Ana Nueva","30-12345678-9",80,1,"Domicilio nuevo 987"));
        var historica=facturas.obtener(f.id());
        assertThat(historica.clienteNombre()).isEqualTo("Ana Martínez"); assertThat(historica.clienteDocumento()).isEqualTo("12345678");
        assertThat(historica.tipoDocumentoReceptor()).isEqualTo(96); assertThat(historica.condicionIvaReceptorId()).isEqualTo(5);
        assertThat(historica.clienteDomicilio()).isEqualTo("Calle original 123");
        var nueva=facturas.crear(request(TipoDescuento.SIN_DESCUENTO,"0","1"));
        assertThat(nueva.clienteDocumento()).isEqualTo("30123456789"); assertThat(nueva.tipoDocumentoReceptor()).isEqualTo(80);
        try(var doc=Loader.loadPDF(pdf.generar(f.id()))) { assertThat(new PDFTextStripper().getText(doc)).contains("Ana Martínez","Calle original 123").doesNotContain("Ana Nueva","Domicilio nuevo"); }
        String consulta=enviar("GET","/clientes/1",null).body(); assertThat(consulta).contains("Ana Nueva","30123456789","\"tipoDocumento\":80");
        assertThat(enviar("GET","/clientes?q=ana&documento=301234",null).body()).contains("Ana Nueva");
        assertThat(enviar("POST","/facturas","{\"fecha\":\"2026-11-17\",\"clienteId\":1,\"condicionIvaId\":1,\"puntoVentaId\":1,\"tipoMonedaId\":1,\"tipoDocumentoReceptor\":96,\"detalles\":[{\"articuloId\":1,\"cantidad\":1}]} ").statusCode()).isEqualTo(400);
    }
    @Test void descuentoReduceCadaBaseAntesDelIvaYPersisteAlEditar() throws Exception {
        var f=facturas.crear(request(TipoDescuento.PORCENTAJE,"10",null));
        assertThat(f.subtotal()).isEqualByComparingTo("2600"); assertThat(f.importeDescuento()).isEqualByComparingTo("260");
        assertThat(f.neto()).isEqualByComparingTo("2340"); assertThat(f.iva()).isEqualByComparingTo("434.70"); assertThat(f.total()).isEqualByComparingTo("2774.70");
        assertThat(f.detalles().getFirst().precioUnitario()).isEqualByComparingTo("1000");
        assertThat(f.detalles().stream().map(FacturaDetalleReporteDTO::descuento).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("260");
        var fijo=facturas.actualizar(f.id(),request(TipoDescuento.MONTO_FIJO,"260",null));
        assertThat(fijo.total()).isEqualByComparingTo(f.total());
        assertThat(facturas.obtener(f.id()).tipoDescuento()).isEqualTo(TipoDescuento.MONTO_FIJO);
        assertThat(enviar("GET","/facturas/"+f.id(),null).body()).contains("\"importeDescuento\":260.00","\"tipoDescuento\":\"MONTO_FIJO\"");
        var c=facturas.crear(request(TipoDescuento.PORCENTAJE,"10","11"));
        assertThat(c.iva()).isEqualByComparingTo("0"); assertThat(c.total()).isEqualByComparingTo("2340");
        var cero=facturas.crear(request(TipoDescuento.PORCENTAJE,"100",null)); assertThat(cero.total()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> facturas.crear(request(TipoDescuento.MONTO_FIJO,"2600.01",null))).hasMessageContaining("subtotal");
        assertThat(enviar("PUT","/facturas/"+f.id(),"{\"estado\":\"EMITIDA\",\"valorDescuento\":1}").statusCode()).isEqualTo(400);
        byte[] bytes=pdf.generar(f.id()); java.nio.file.Files.write(java.nio.file.Path.of("tmp/pdfs/factura-descuento-general.pdf"),bytes);
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Descuento general","260,00","2.774,70","434,70");
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,120),"png",java.nio.file.Path.of("tmp/pdfs/factura-descuento-general.png").toFile());
        }
    }
    @Test void precioSeleccionadoSeConsultaDeNuevoYClientesSeValidanDesdeSuFicha() throws Exception {
        jdbc.update("update lista_precio_articulo set precio=1234.56 where id=1");
        assertThat(enviar("GET","/catalogos/precios/1?tipoMonedaId=1&listaPrecioId=1",null).body()).contains("1234.56");
        assertThat(enviar("GET","/catalogos/precios?q=cuaderno&listaPrecioId=1&tipoMonedaId=1&iva=21",null).body()).contains("Cuaderno A4","1234.56");
        assertThat(enviar("GET","/catalogos/precios/3?tipoMonedaId=1&listaPrecioId=1",null).statusCode()).isEqualTo(404);
        assertThat(enviar("GET","/clientes/999999",null).statusCode()).isEqualTo(404);
        assertThat(enviar("POST","/clientes","{\"nombre\":\"Cliente inválido\",\"documento\":\"12345678\",\"tipoDocumento\":80,\"condicionIvaReceptorId\":1}").statusCode()).isEqualTo(400);
        assertThat(enviar("POST","/clientes","{\"nombre\":\"Duplicado\",\"documento\":\"12345678\",\"tipoDocumento\":96,\"condicionIvaReceptorId\":5}").statusCode()).isEqualTo(409);
    }
    @Test void consultaEnBloqueDeOtraListaEsDeLecturaYDevuelveSoloSusArticulos() throws Exception {
        jdbc.update("merge into lista_precio_articulo(id,articulo_id,tipo_moneda_id,precio,lista_precio_id) key(id) values (50,1,1,800,2)");
        var factura = facturas.crear(request(TipoDescuento.PORCENTAJE,"10",null));
        var respuesta = enviar("GET","/catalogos/precios?listaPrecioId=2&tipoMonedaId=1&articuloIds=1,2",null);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(respuesta.body());
        assertThat(json.size()).isEqualTo(1); assertThat(json.get(0).path("id").asLong()).isEqualTo(1);
        assertThat(json.get(0).path("listaPrecioId").asLong()).isEqualTo(2); assertThat(json.get(0).path("precio").decimalValue()).isEqualByComparingTo("800");
        assertThat(facturas.obtener(factura.id()).listaPrecioId()).isEqualTo(1);
        assertThat(facturas.obtener(factura.id()).total()).isEqualByComparingTo(factura.total());
        assertThat(enviar("GET","/catalogos/precios?listaPrecioId=2&tipoMonedaId=1&articuloIds=0",null).statusCode()).isEqualTo(400);
        assertThat(enviar("GET","/catalogos/precios?listaPrecioId=2&articuloIds=1",null).statusCode()).isEqualTo(400);
        String demasiados = String.join(",",java.util.Collections.nCopies(101,"1"));
        assertThat(enviar("GET","/catalogos/precios?listaPrecioId=2&tipoMonedaId=1&articuloIds="+demasiados,null).statusCode()).isEqualTo(400);
    }
    @Test void catalogoSinArticulosYBusquedaDePreciosPorLista() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var liviano = enviar("GET","/catalogos?incluirArticulos=false",null);
        assertThat(liviano.statusCode()).isEqualTo(200);
        var catalogo = mapper.readTree(liviano.body());
        assertThat(catalogo.path("articulos").isEmpty()).isTrue();
        assertThat(catalogo.path("precios").isEmpty()).isTrue();
        assertThat(catalogo.path("listasPrecio").isEmpty()).isFalse();
        jdbc.update("merge into lista_precio_articulo(id,articulo_id,tipo_moneda_id,precio,lista_precio_id) key(id) values (50,1,1,800,2)");
        var respuesta = enviar("GET","/catalogos/precios?listaPrecioId=2&tipoMonedaId=1",null);
        var precios = mapper.readTree(respuesta.body());
        assertThat(respuesta.statusCode()).isEqualTo(200); assertThat(precios.size()).isEqualTo(1);
        assertThat(precios.get(0).path("listaPrecioId").asLong()).isEqualTo(2);
        assertThat(precios.get(0).path("precio").decimalValue()).isEqualByComparingTo("800");
        assertThat(mapper.readTree(enviar("GET","/catalogos",null).body()).path("articulos").isEmpty()).isFalse();
    }
}
