package ar.edu.utn.facturacion;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:listas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
class ListasPrecioFlujosTest {
    @LocalServerPort int puerto;
    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();
    HttpResponse<String> enviar(String metodo, String ruta, String datos) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api" + ruta))
            .header("Content-Type", "application/json")
            .method(metodo, datos == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(datos)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    JsonNode ok(String metodo, String ruta, String datos, int estado) throws Exception {
        var respuesta = enviar(metodo, ruta, datos);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(estado);
        return json.readTree(respuesta.body());
    }
    long nuevaLista(String nombre) throws Exception {
        return ok("POST", "/listas-precio", "{\"nombre\":\"" + nombre + "\"}", 201).path("id").asLong();
    }
    String factura(Long lista, long articulo) {
        return """
            {"fecha":"2026-11-13","clienteId":1,"condicionIvaId":1,"puntoVentaId":1,"tipoMonedaId":1,
            "tipoComprobante":"11","tipoDocumentoReceptor":96,"condicionIvaReceptorId":5,
            %s "detalles":[{"articuloId":%d,"cantidad":2}]}
            """.formatted(lista == null ? "" : "\"listaPrecioId\":" + lista + ",", articulo);
    }
    @Test void cadaListaTieneSuPrecioYLosCambiosConservanFacturasHistoricas() throws Exception {
        long lista = nuevaLista("Distribuidores");
        ok("PUT", "/listas-precio/" + lista + "/precios", "{\"articuloId\":1,\"tipoMonedaId\":1,\"precio\":800}", 200);
        var minorista = ok("POST", "/facturas", factura(null, 1), 201);
        var distribuidor = ok("POST", "/facturas", factura(lista, 1), 201);
        assertThat(minorista.path("total").decimalValue()).isEqualByComparingTo("2000");
        assertThat(distribuidor.path("total").decimalValue()).isEqualByComparingTo("1600");
        assertThat(distribuidor.path("listaPrecioId").asLong()).isEqualTo(lista);
        var filtradas = ok("GET", "/facturas?listaPrecioId=" + lista, null, 200);
        assertThat(filtradas.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtradas.path("content").get(0).path("listaPrecioId").asLong()).isEqualTo(lista);
        ok("PUT", "/listas-precio/" + lista + "/precios", "{\"articuloId\":1,\"tipoMonedaId\":1,\"precio\":700}", 200);
        var historica = ok("GET", "/facturas/" + distribuidor.path("id").asLong(), null, 200);
        assertThat(historica.path("total").decimalValue()).isEqualByComparingTo("1600");
        assertThat(ok("POST", "/facturas", factura(lista, 1), 201).path("total").decimalValue()).isEqualByComparingTo("1400");
        var catalogo = ok("GET", "/catalogos", null, 200);
        assertThat(catalogo.path("listasPrecio").toString()).contains("Distribuidores", "Minorista", "Mayorista");
    }
    @Test void listaVaciaNoUtilizaElPrecioDeOtraLista() throws Exception {
        long lista = nuevaLista("Sin productos");
        assertThat(enviar("POST", "/facturas", factura(lista, 1)).statusCode()).isEqualTo(409);
        assertThat(enviar("POST", "/facturas", factura(999999L, 1)).statusCode()).isEqualTo(404);
        assertThat(enviar("POST", "/facturas", factura(-1L, 1)).statusCode()).isEqualTo(400);
    }
    @Test void validaNombresPreciosYReferencias() throws Exception {
        nuevaLista("Lista especial");
        assertThat(enviar("POST", "/listas-precio", "{\"nombre\":\" lista ESPECIAL \"}").statusCode()).isEqualTo(409);
        assertThat(enviar("POST", "/listas-precio", "{\"nombre\":\" \"}").statusCode()).isEqualTo(400);
        assertThat(enviar("PUT", "/listas-precio/2/precios", "{\"articuloId\":1,\"tipoMonedaId\":1,\"precio\":-1}").statusCode()).isEqualTo(400);
        assertThat(enviar("PUT", "/listas-precio/2/precios", "{\"articuloId\":1,\"tipoMonedaId\":1,\"precio\":1.001}").statusCode()).isEqualTo(400);
        assertThat(enviar("PUT", "/listas-precio/2/precios", "{\"articuloId\":999999,\"tipoMonedaId\":1,\"precio\":1}").statusCode()).isEqualTo(404);
        assertThat(enviar("PUT", "/listas-precio/2/precios", "{\"articuloId\":1,\"tipoMonedaId\":999999,\"precio\":1}").statusCode()).isEqualTo(404);
    }
    @Test void nuevoProductoSeCreaEnLaListaIndicada() throws Exception {
        var p = ok("POST", "/articulos", """
            {"codigo":"SOLO-MAYORISTA","descripcion":"Producto mayorista","alicuotaIva":21,"tipoMonedaId":1,"precio":500,"listaPrecioId":2}
            """, 201);
        assertThat(p.path("listaPrecioId").asLong()).isEqualTo(2);
        assertThat(ok("POST", "/facturas", factura(2L, p.path("id").asLong()), 201).path("total").decimalValue()).isEqualByComparingTo("1000");
        assertThat(enviar("POST", "/facturas", factura(null, p.path("id").asLong())).statusCode()).isEqualTo(409);
    }
}
