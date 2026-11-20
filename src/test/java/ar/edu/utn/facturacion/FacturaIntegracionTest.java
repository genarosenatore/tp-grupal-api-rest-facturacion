package ar.edu.utn.facturacion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** HTTP y PostgreSQL reales. Sin transacción del test que oculte fallos de commit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class FacturaIntegracionTest {
    @DynamicPropertySource
    static void exigirBaseExclusiva(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/[^?]+_test(?:\\?.*)?")) {
            throw new IllegalStateException("TEST_DB_URL debe apuntar a una base exclusiva terminada en _test.");
        }
    }
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String CREAR = """
        {"fecha":"2026-10-23","clienteId":1,"condicionIvaId":1,
         "puntoVentaId":1,"tipoMonedaId":1,"observaciones":"Prueba",
         "detalles":[{"articuloId":1,"cantidad":2},{"articuloId":2,"cantidad":3}]}
        """;
    @BeforeEach void limpiarSoloEsquemaDePruebas() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
        jdbc.execute("DROP TRIGGER IF EXISTS falla_detalle ON tp_pruebas.factura_detalle");
        jdbc.execute("DELETE FROM tp_pruebas.operacion_fiscal");
        jdbc.execute("DELETE FROM tp_pruebas.factura_detalle");
        jdbc.execute("DELETE FROM tp_pruebas.factura_venta");
    }
    @AfterEach void quitarFallo() {
        jdbc.execute("DROP TRIGGER IF EXISTS falla_detalle ON tp_pruebas.factura_detalle");
        jdbc.execute("DROP FUNCTION IF EXISTS tp_pruebas.rechazar_detalle()");
    }
    private HttpResponse<String> enviar(String metodo, String ruta, String json) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/api/facturas" + ruta));
        b.header("Content-Type","application/json");
        b.method(metodo,json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private long crear() throws Exception {
        var r = enviar("POST","",CREAR);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        var match = Pattern.compile("\"id\":(\\d+)").matcher(r.body());
        assertThat(match.find()).as(r.body()).isTrue();
        return Long.parseLong(match.group(1));
    }
    private int filas(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM tp_pruebas." + tabla,Integer.class);
    }
    @Test void sirveFrontendYCatalogosSinSerializarEntidades() throws Exception {
        var inicio = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(inicio.statusCode()).isEqualTo(200);
        assertThat(inicio.body()).contains("Facturas de venta", "invoice-form");
        var catalogos = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/api/catalogos"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(catalogos.statusCode()).as(catalogos.body()).isEqualTo(200);
        assertThat(catalogos.body()).contains("\"clientes\"", "Ana Martínez", "\"condicionesIva\"",
            "\"puntosVenta\"", "\"monedas\"", "\"articulos\"", "Cuaderno", "\"precios\"", "1000.00")
            .doesNotContain("hibernateLazyInitializer", "DB_PASSWORD");
        for (String recurso : new String[]{"styles.css", "app.js", "importes.mjs"}) {
            var respuesta = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/" + recurso))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(respuesta.statusCode()).as(recurso).isEqualTo(200);
        }
    }
    @Test void creaEnCascadaYCalculaImportes() throws Exception {
        var r = enviar("POST","",CREAR);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        assertThat(r.headers().firstValue("Location")).isPresent();
        assertThat(filas("factura_venta")).isEqualTo(1);
        assertThat(filas("factura_detalle")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT total FROM tp_pruebas.factura_venta",java.math.BigDecimal.class)).isEqualByComparingTo("3083.00");
        assertThat(jdbc.queryForObject("SELECT iva FROM tp_pruebas.factura_venta",java.math.BigDecimal.class)).isEqualByComparingTo("483.00");
    }
    @Test void consultaPorIdYFiltra() throws Exception {
        long id=crear();
        assertThat(enviar("GET","/"+id,null).statusCode()).isEqualTo(200);
        assertThat(enviar("GET","?clienteId=1&estado=EMITIDA&desde=2026-10-23&hasta=2026-10-23",null).body()).contains("3083.00");
        assertThat(enviar("GET","?clienteId=2",null).body()).contains("\"content\":[]", "\"totalElements\":0");
    }
    @Test void reemplazaDetallesYRecalcula() throws Exception {
        long id=crear();
        var ids=jdbc.queryForList("SELECT id FROM tp_pruebas.factura_detalle",Long.class);
        String actualizado=CREAR.replace("\"clienteId\":1","\"clienteId\":2")
            .replace("[{\"articuloId\":1,\"cantidad\":2},{\"articuloId\":2,\"cantidad\":3}]","[{\"articuloId\":2,\"cantidad\":4}]");
        var r=enviar("PUT","/"+id,actualizado);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(filas("factura_detalle")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM tp_pruebas.factura_detalle",Long.class)).doesNotContainAnyElementsOf(ids);
        assertThat(jdbc.queryForObject("SELECT total FROM tp_pruebas.factura_venta",java.math.BigDecimal.class)).isEqualByComparingTo("884.00");
        assertThat(jdbc.queryForObject("SELECT cliente_id FROM tp_pruebas.factura_venta",Long.class)).isEqualTo(2L);
    }
    @Test void anulaConservaHistoriaYEsIdempotente() throws Exception {
        long id=crear();
        assertThat(enviar("POST","/"+id+"/anular","{\"motivo\":\"Error de carga\"}").statusCode()).isEqualTo(200);
        var fecha=jdbc.queryForObject("SELECT fecha_anulacion::text FROM tp_pruebas.factura_venta",String.class);
        var repetida=enviar("POST","/"+id+"/anular","{\"motivo\":\"Error de carga\"}");
        assertThat(repetida.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT fecha_anulacion::text FROM tp_pruebas.factura_venta",String.class)).isEqualTo(fecha);
        assertThat(filas("factura_detalle")).isEqualTo(2);
        assertThat(enviar("GET","/"+id,null).body()).contains("ANULADA");
        assertThat(enviar("PUT","/"+id,CREAR).statusCode()).isEqualTo(409);
        assertThat(enviar("PUT","/"+id,"{\"estado\":\"EMITIDA\"}").statusCode()).isEqualTo(409);
    }
    @Test void anulaPorCircuitoExplicitoYBloqueaPutDeEstado() throws Exception {
        long id=crear();
        assertThat(enviar("PUT","/"+id,"{\"estado\":\"ANULADA\",\"observaciones\":\"Prueba de estado\"}").statusCode()).isEqualTo(409);
        assertThat(enviar("POST","/"+id+"/anular","{\"motivo\":\"Prueba de estado\"}").statusCode()).isEqualTo(200);
        assertThat(enviar("GET","/"+id,null).body()).contains("Prueba de estado","ANULADA");
    }
    @Test void rechazaMezclarEstadoYContenido() throws Exception {
        long id=crear();
        assertThat(enviar("PUT","/"+id,CREAR.replace("\"fecha\"","\"estado\":\"ANULADA\",\"fecha\"")).statusCode()).isEqualTo(400);
    }
    @Test void recursosInexistentesSon404() throws Exception {
        assertThat(enviar("GET","/999999999",null).statusCode()).isEqualTo(404);
        assertThat(enviar("DELETE","/999999999",null).statusCode()).isEqualTo(404);
        assertThat(enviar("PUT","/999999999",CREAR).statusCode()).isEqualTo(404);
        assertThat(enviar("POST","",CREAR.replace("\"clienteId\":1","\"clienteId\":999999999")).statusCode()).isEqualTo(404);
        assertThat(enviar("POST","",CREAR.replace("\"articuloId\":1","\"articuloId\":999999999")).statusCode()).isEqualTo(404);
        assertThat(filas("factura_venta")).isZero();
    }
    @Test void validaCabeceraDetallesYTipos() throws Exception {
        for(String invalido : new String[]{"{}",CREAR.replace("\"cantidad\":2","\"cantidad\":0"),
            CREAR.replace("\"cantidad\":2","\"cantidad\":0.0001"),
            CREAR.replace("\"clienteId\":1","\"clienteId\":-1"),
            CREAR.replace("\"fecha\":\"2026-10-23\"","\"fecha\":\"no-es-fecha\""),
            CREAR.replace("[{\"articuloId\":1,\"cantidad\":2},{\"articuloId\":2,\"cantidad\":3}]","[]"),
            CREAR.replace("[{\"articuloId\":1,\"cantidad\":2},{\"articuloId\":2,\"cantidad\":3}]","[null]")}) {
            var r=enviar("POST","",invalido);
            assertThat(r.statusCode()).as(r.body()).isEqualTo(400);
        }
        assertThat(filas("factura_venta")).isZero();
    }
    @Test void sinPrecioEsConflictoYNoGuardaNada() throws Exception {
        assertThat(enviar("POST","",CREAR.replace("\"articuloId\":2","\"articuloId\":3")).statusCode()).isEqualTo(409);
        assertThat(filas("factura_venta")).isZero();
        assertThat(filas("factura_detalle")).isZero();
    }
    @Test void filtrosYJsonInvalidosSon400() throws Exception {
        assertThat(enviar("GET","?desde=2026-10-24&hasta=2026-10-23",null).statusCode()).isEqualTo(400);
        assertThat(enviar("GET","?estado=OTRO",null).statusCode()).isEqualTo(400);
        assertThat(enviar("GET","/-1",null).statusCode()).isEqualTo(400);
        assertThat(enviar("POST","","{").statusCode()).isEqualTo(400);
        assertThat(enviar("POST","",CREAR.replace("\"fecha\"","\"precioInventado\":10,\"fecha\"")).statusCode()).isEqualTo(400);
    }
    private void instalarFalloSegundoDetalle() {
        jdbc.execute("""
            CREATE FUNCTION tp_pruebas.rechazar_detalle() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN IF NEW.cantidad=13 THEN
                RAISE EXCEPTION 'Fallo de detalle simulado por la prueba' USING ERRCODE='23514';
            END IF; RETURN NEW; END $$
            """);
        jdbc.execute("""
            CREATE TRIGGER falla_detalle BEFORE INSERT ON tp_pruebas.factura_detalle
            FOR EACH ROW EXECUTE FUNCTION tp_pruebas.rechazar_detalle()
            """);
    }
    @Test void rollbackRealSiPostgresRechazaSegundoDetalle() throws Exception {
        instalarFalloSegundoDetalle();
        var r=enviar("POST","",CREAR.replace("\"cantidad\":3","\"cantidad\":13"));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(409);
        assertThat(filas("factura_venta")).isZero();
        assertThat(filas("factura_detalle")).isZero();
    }
    @Test void rollbackDeActualizacionConservaContenidoAnterior() throws Exception {
        long id=crear();
        instalarFalloSegundoDetalle();
        var r=enviar("PUT","/"+id,CREAR.replace("\"cantidad\":3","\"cantidad\":13"));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(409);
        assertThat(filas("factura_detalle")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT total FROM tp_pruebas.factura_venta",java.math.BigDecimal.class)).isEqualByComparingTo("3083.00");
    }
    @Test void conservaPreciosHistoricosAlConsultar() throws Exception {
        long id=crear();
        jdbc.update("UPDATE tp_pruebas.lista_precio_articulo SET precio=1500 WHERE id=1");
        try {
            assertThat(enviar("GET","/"+id,null).body()).contains("3083.00","1000.00");
        } finally {
            jdbc.update("UPDATE tp_pruebas.lista_precio_articulo SET precio=1000 WHERE id=1");
        }
    }
    @Test void integridadReferencialImpideBorrarClienteUsado() throws Exception {
        crear();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tp_pruebas.cliente WHERE id=1"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(filas("cliente")).isEqualTo(2);
    }
    @Test void altaDeProductoYPrecioEsAtomicaEnPostgreSQL() throws Exception {
        String producto="""
            {"codigo":"TX-PRODUCTO-PG","descripcion":"Producto de prueba PostgreSQL","alicuotaIva":21,"tipoMonedaId":1,"precio":350.50}
            """;
        java.util.function.Function<String,HttpRequest> peticion=datos->HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/api/articulos"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(datos)).build();
        try {
            var creada=http.send(peticion.apply(producto),HttpResponse.BodyHandlers.ofString());
            assertThat(creada.statusCode()).as(creada.body()).isEqualTo(201);
            assertThat(jdbc.queryForObject("select p.precio from tp_pruebas.lista_precio_articulo p join tp_pruebas.articulo a on a.id=p.articulo_id where a.codigo='TX-PRODUCTO-PG'",java.math.BigDecimal.class)).isEqualByComparingTo("350.50");
            jdbc.execute("alter table tp_pruebas.lista_precio_articulo add constraint fallo_producto_pg check(precio<>777.77)");
            var fallida=http.send(peticion.apply(producto.replace("TX-PRODUCTO-PG","TX-PRODUCTO-PG-FALLO").replace("350.50","777.77")),HttpResponse.BodyHandlers.ofString());
            assertThat(fallida.statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from tp_pruebas.articulo where codigo='TX-PRODUCTO-PG-FALLO'",Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table tp_pruebas.lista_precio_articulo drop constraint if exists fallo_producto_pg");
            jdbc.update("delete from tp_pruebas.lista_precio_articulo where articulo_id in (select id from tp_pruebas.articulo where codigo in ('TX-PRODUCTO-PG','TX-PRODUCTO-PG-FALLO'))");
            jdbc.update("delete from tp_pruebas.articulo where codigo in ('TX-PRODUCTO-PG','TX-PRODUCTO-PG-FALLO')");
        }
    }
}
