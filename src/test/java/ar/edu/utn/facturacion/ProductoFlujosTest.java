package ar.edu.utn.facturacion;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:productos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
class ProductoFlujosTest {
    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http=HttpClient.newHttpClient();
    final JsonMapper json=JsonMapper.builder().build();
    final String cuerpo="""
        {"codigo":" prod-nuevo ","descripcion":" Marcador de prueba ","alicuotaIva":21,"tipoMonedaId":1,"precio":350.50}
        """;
    @BeforeEach void limpiar() {
        jdbc.update("delete from factura_detalle"); jdbc.update("delete from factura_venta");
        jdbc.update("delete from lista_precio_articulo where articulo_id not in (1,2,3)");
        jdbc.update("delete from articulo where id not in (1,2,3)");
    }
    HttpResponse<String> enviar(String metodo,String ruta,String datos) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api"+ruta))
            .header("Content-Type","application/json").method(metodo,datos==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(datos)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    @Test void creaArticuloYPrecioYPermiteUsarloInmediatamenteEnFactura() throws Exception {
        var respuesta=enviar("POST","/articulos",cuerpo);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
        var producto=json.readTree(respuesta.body()); long id=producto.path("id").asLong();
        assertThat(id).isGreaterThan(3);
        assertThat(producto.path("codigo").asString()).isEqualTo("PROD-NUEVO");
        assertThat(producto.path("descripcion").asString()).isEqualTo("Marcador de prueba");
        assertThat(jdbc.queryForObject("select precio from lista_precio_articulo where articulo_id=? and tipo_moneda_id=1",BigDecimal.class,id)).isEqualByComparingTo("350.50");
        assertThat(enviar("GET","/catalogos",null).body()).contains("PROD-NUEVO","Marcador de prueba");
        var factura=enviar("POST","/facturas","""
            {"fecha":"2026-11-12","clienteId":1,"condicionIvaId":1,"puntoVentaId":1,"tipoMonedaId":1,
             "tipoComprobante":"11","tipoDocumentoReceptor":96,"condicionIvaReceptorId":5,
             "detalles":[{"articuloId":%d,"cantidad":2}]}
            """.formatted(id));
        assertThat(factura.statusCode()).as(factura.body()).isEqualTo(201);
        assertThat(json.readTree(factura.body()).path("total").decimalValue()).isEqualByComparingTo("701.00");
        assertThat(json.readTree(factura.body()).path("iva").decimalValue()).isZero();
    }
    @Test void codigoRepetidoIgnoraMayusculasYEspaciosYNoDuplicaElPrecio() throws Exception {
        assertThat(enviar("POST","/articulos",cuerpo).statusCode()).isEqualTo(201);
        var repetida=enviar("POST","/articulos",cuerpo.replace("prod-nuevo","PROD-NUEVO"));
        assertThat(repetida.statusCode()).isEqualTo(409); assertThat(repetida.body()).contains("Ya existe un producto");
        assertThat(jdbc.queryForObject("select count(*) from articulo",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from lista_precio_articulo",Integer.class)).isEqualTo(5);
    }
    @Test void datosInvalidosYMonedaInexistenteNoDejanUnArticuloSinPrecio() throws Exception {
        for(String invalida:List.of(cuerpo.replace("350.50","-1"),cuerpo.replace("350.50","1.001"),
                cuerpo.replace("\"alicuotaIva\":21","\"alicuotaIva\":101"),cuerpo.replace("prod-nuevo"," "),
                cuerpo.replace(" Marcador de prueba "," "),cuerpo.replace("prod-nuevo","X".repeat(31))))
            assertThat(enviar("POST","/articulos",invalida).statusCode()).as(invalida).isEqualTo(400);
        assertThat(enviar("POST","/articulos",cuerpo.replace("\"tipoMonedaId\":1","\"tipoMonedaId\":999999")).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from articulo",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from lista_precio_articulo",Integer.class)).isEqualTo(4);
    }
    @Test void falloAlGuardarPrecioRevierteTambienElArticulo() throws Exception {
        jdbc.execute("alter table lista_precio_articulo add constraint fallo_precio_producto check(precio<>350.50)");
        try {
            assertThat(enviar("POST","/articulos",cuerpo).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from articulo",Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from lista_precio_articulo",Integer.class)).isEqualTo(4);
        } finally { jdbc.execute("alter table lista_precio_articulo drop constraint fallo_precio_producto"); }
    }
    @Test void dosAltasConcurrentesDelMismoCodigoConservanUnSoloProductoYPrecio() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<Integer> alta=()->{ inicio.await(); return enviar("POST","/articulos",cuerpo).statusCode(); };
            var primera=pool.submit(alta); var segunda=pool.submit(alta); inicio.countDown();
            assertThat(List.of(primera.get(10,TimeUnit.SECONDS),segunda.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from articulo",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from lista_precio_articulo",Integer.class)).isEqualTo(5);
    }
}
