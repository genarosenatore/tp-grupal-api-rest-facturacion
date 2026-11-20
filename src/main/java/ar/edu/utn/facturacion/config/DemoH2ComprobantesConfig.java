package ar.edu.utn.facturacion.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

/** Equivalente a V9 para la demo H2 persistente, que usa Hibernate update sin Flyway. */
@Configuration
public class DemoH2ComprobantesConfig {
    @Bean
    ApplicationRunner actualizarLetrasDemo(DataSource dataSource,
            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddl) {
        return args -> {
            if (!"update".equals(ddl)) return;
            try (var connection = dataSource.getConnection()) {
                if ("H2".equals(connection.getMetaData().getDatabaseProductName())) actualizarLetras(connection);
            }
        };
    }

    public static void actualizarLetras(Connection connection) throws SQLException {
        // Hibernate agrega la columna con X por defecto: recuperar la letra de los tipos previos.
        try (var statement = connection.createStatement()) {
            String letra = "CASE WHEN tipo_comprobante IN ('1','3') THEN 'A' "
                + "WHEN tipo_comprobante IN ('6','8') THEN 'B' "
                + "WHEN tipo_comprobante IN ('11','13') THEN 'C' ELSE 'X' END";
            statement.executeUpdate("UPDATE factura_venta SET letra_comprobante=" + letra
                + " WHERE letra_comprobante IS NULL OR letra_comprobante <> (" + letra + ")");
            statement.execute("ALTER TABLE factura_venta ADD CONSTRAINT IF NOT EXISTS ck_factura_letra_tipo CHECK (letra_comprobante = (" + letra + "))");
        }
    }
}
