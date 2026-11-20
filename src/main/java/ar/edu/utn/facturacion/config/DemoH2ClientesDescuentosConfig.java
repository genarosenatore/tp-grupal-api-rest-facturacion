package ar.edu.utn.facturacion.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Migración de datos equivalente a V10/V11 para la demo con Hibernate update. */
@Configuration
public class DemoH2ClientesDescuentosConfig {
    @Bean
    ApplicationRunner actualizarClientesDescuentosDemo(DataSource dataSource,
            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddl) {
        return args -> {
            if (!"update".equals(ddl)) return;
            try (var c = dataSource.getConnection()) {
                if (!"H2".equals(c.getMetaData().getDatabaseProductName())) return;
                actualizarHistoricos(c);
                ScriptUtils.executeSqlScript(c, new org.springframework.core.io.support.EncodedResource(
                    new ClassPathResource("db/migration/V11__ampliar_catalogo_demostracion.sql"), "UTF-8"));
            }
        };
    }
    public static void actualizarHistoricos(Connection c) throws java.sql.SQLException {
        try (var s = c.createStatement()) {
            s.executeUpdate("UPDATE factura_venta SET cliente_nombre=(SELECT nombre FROM cliente WHERE id=cliente_id) WHERE cliente_nombre IS NULL");
            s.executeUpdate("UPDATE factura_venta SET cliente_documento=(SELECT documento FROM cliente WHERE id=cliente_id) WHERE cliente_documento IS NULL");
        }
    }
}
