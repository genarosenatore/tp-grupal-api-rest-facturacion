package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.facturacion.service.FacturaService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.datasource.url=jdbc:h2:mem:listado;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
class FacturaListadoTest {
    @Autowired FacturaService facturas;
    @Autowired EntityManagerFactory persistencia;

    @Test void unaPaginaConDetallesNoConsultaPorCadaFactura() {
        var request = new FacturaRequestDTO(LocalDate.of(2026, 10, 23), 1L, 1L, 1L, 1L, null,
            List.of(new FacturaDetalleRequestDTO(1L, BigDecimal.ONE)), null);
        for (int i = 0; i < 16; i++) facturas.crear(request);

        var estadisticas = persistencia.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        try {
            estadisticas.clear();
            var pagina = facturas.listar(null, null, null, null, null, null, 0, 16);
            assertThat(pagina.totalElements()).isEqualTo(16);
            assertThat(pagina.content()).allSatisfy(f -> assertThat(f.detalles()).hasSize(1));
            long consultas = estadisticas.getPrepareStatementCount();
            org.slf4j.LoggerFactory.getLogger(FacturaListadoTest.class).info("SQL para 16 facturas: {} consultas", consultas);
            assertThat(consultas).as("SQL para página de 16 facturas").isLessThanOrEqualTo(10);
        } finally {
            estadisticas.setStatisticsEnabled(false);
        }
    }
}
