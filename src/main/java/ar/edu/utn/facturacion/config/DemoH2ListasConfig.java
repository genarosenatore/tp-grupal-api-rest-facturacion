package ar.edu.utn.facturacion.config;

import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

/** La demo persistente usa Hibernate update en lugar de Flyway/PostgreSQL. */
@Configuration
public class DemoH2ListasConfig {
    @Bean
    ApplicationRunner actualizarListasDemo(DataSource dataSource,
            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddl) {
        return args -> {
            if (!"update".equals(ddl)) return;
            try (var connection = dataSource.getConnection()) {
                if ("H2".equals(connection.getMetaData().getDatabaseProductName())) actualizarRestriccion(connection);
            }
        };
    }
    public static void actualizarRestriccion(Connection connection) throws java.sql.SQLException {
        Map<String, Set<String>> restricciones = new HashMap<>();
        try (var statement = connection.prepareStatement("""
            SELECT k.constraint_name, k.column_name
            FROM information_schema.key_column_usage k
            JOIN information_schema.table_constraints c
              ON c.constraint_catalog=k.constraint_catalog AND c.constraint_schema=k.constraint_schema AND c.constraint_name=k.constraint_name
            WHERE c.table_schema=current_schema() AND c.table_name='LISTA_PRECIO_ARTICULO' AND c.constraint_type='UNIQUE'
            """); var rows = statement.executeQuery()) {
            while (rows.next()) restricciones.computeIfAbsent(rows.getString(1), key -> new HashSet<>()).add(rows.getString(2));
        }
        var columnasNuevas = Set.of("ARTICULO_ID", "TIPO_MONEDA_ID", "LISTA_PRECIO_ID");
        // Primero asegurar la nueva unicidad; luego retirar solo la restricción anterior.
        if (!restricciones.containsValue(columnasNuevas)) {
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE lista_precio_articulo ADD CONSTRAINT uq_precio_lista_articulo_moneda UNIQUE(lista_precio_id,articulo_id,tipo_moneda_id)");
            }
        }
        for (var entrada : restricciones.entrySet()) {
            if (entrada.getValue().equals(Set.of("ARTICULO_ID", "TIPO_MONEDA_ID"))) {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE lista_precio_articulo DROP CONSTRAINT \"" + entrada.getKey().replace("\"", "\"\"") + "\"");
                }
            }
        }
    }
}
