package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.exception.SolicitudInvalidaException;
import ar.edu.utn.facturacion.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Catálogo y escritura reales, sin solicitar tickets ni enviar comprobantes a ARCA. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:h2:mem:puntos_homologacion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "arca.homologacion.enabled=true", "arca.homologacion.punto-venta=3"
})
@ActiveProfiles("flujos")
@Transactional
class HomologacionPuntosTest {
    @Autowired CatalogoService catalogos;
    @Autowired FacturaService facturas;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void preparar() {
        jdbc.update("insert into punto_venta(id,codigo,descripcion) values (2,'0002','Otro punto de prueba'),(3,'00003','Entorno de prueba')");
    }

    FacturaRequestDTO solicitud(long punto) {
        return new FacturaRequestDTO(LocalDate.now(), 1L, 1L, punto, 1L,
            "Prueba local", List.of(new FacturaDetalleRequestDTO(1L, BigDecimal.ONE)), null);
    }

    @Test void ofreceSoloElPuntoConfiguradoYRechazaOtrosInclusoSinEnvioFiscal() {
        assertThat(catalogos.obtener().puntosVenta()).extracting(CatalogosDTO.Punto::id).containsExactly(3L);
        assertThatThrownBy(() -> facturas.crear(solicitud(1L)))
            .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("entorno de prueba");
        assertThatThrownBy(() -> facturas.crear(solicitud(2L)))
            .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(facturas.crear(solicitud(3L)).puntoVentaId()).isEqualTo(3L);
    }

    @Test void conservaLaConsultaDeComprobantesDelPuntoAnterior() {
        var factura = facturas.crear(solicitud(3L));
        jdbc.update("update factura_venta set punto_venta_id=1 where id=?", factura.id());
        // Limpiar la caché JPA para leer la referencia histórica guardada.
        entityManager.clear();
        assertThat(facturas.obtener(factura.id()).puntoVentaId()).isEqualTo(1L);
    }

    @Autowired jakarta.persistence.EntityManager entityManager;
}
