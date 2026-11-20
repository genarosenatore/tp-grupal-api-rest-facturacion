package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.config.DemoH2ComprobantesConfig;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LetraComprobanteMigracionTest {
    @Test void migracionV9CompletaLasLetrasSinCambiarComprobantesHistoricos() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:letras_v9;MODE=PostgreSQL"); var s = c.createStatement()) {
            preparar(s);
            try (var recurso = getClass().getResourceAsStream("/db/migration/V9__letra_comprobante.sql")) {
                assertThat(recurso).isNotNull();
                String sql = new String(recurso.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
                for (String sentencia : sql.split(";")) if (!sentencia.isBlank()) s.execute(sentencia);
            }
            verificar(s);
            assertThatThrownBy(() -> s.executeUpdate("UPDATE factura_venta SET letra_comprobante='B' WHERE id=1"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("UPDATE factura_venta SET letra_comprobante=NULL WHERE id=1"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("UPDATE factura_venta SET letra_comprobante='Z' WHERE id=1"))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test void demoH2RecuperaLasLetrasTrasHibernateUpdateYAdmiteReinicio() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:letras_demo;MODE=PostgreSQL"); var s = c.createStatement()) {
            preparar(s);
            s.execute("ALTER TABLE factura_venta ADD COLUMN letra_comprobante varchar(1) DEFAULT 'X' NOT NULL");
            DemoH2ComprobantesConfig.actualizarLetras(c);
            DemoH2ComprobantesConfig.actualizarLetras(c);
            verificar(s);
            assertThatThrownBy(() -> s.executeUpdate("UPDATE factura_venta SET letra_comprobante='X' WHERE id=1"))
                .isInstanceOf(SQLException.class);
        }
    }

    private void preparar(Statement s) throws SQLException {
        s.execute("CREATE TABLE factura_venta(id bigint primary key,tipo_comprobante varchar(40),numero bigint,codigo_autorizacion varchar(30),total numeric(19,2))");
        s.execute("INSERT INTO factura_venta VALUES (1,'1',101,'CAE-A',1210),(2,'6',102,'CAE-B',2420),"
            + "(3,'11',103,'CAE-C',1000),(4,NULL,NULL,NULL,500),(5,'3',104,'CAE-NCA',1210),"
            + "(6,'8',105,'CAE-NCB',2420),(7,'13',106,'CAE-NCC',1000),(8,'FACTURA_PRUEBA',107,'CAE-TEST',100)");
    }

    private void verificar(Statement s) throws SQLException {
        var letras = new ArrayList<String>();
        try (var r = s.executeQuery("SELECT letra_comprobante FROM factura_venta ORDER BY id")) {
            while (r.next()) letras.add(r.getString(1));
        }
        assertThat(letras).containsExactly("A", "B", "C", "X", "A", "B", "C", "X");
        try (var r = s.executeQuery("SELECT tipo_comprobante,numero,codigo_autorizacion,total FROM factura_venta WHERE id=1")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).isEqualTo("1"); assertThat(r.getLong(2)).isEqualTo(101);
            assertThat(r.getString(3)).isEqualTo("CAE-A"); assertThat(r.getBigDecimal(4)).isEqualByComparingTo("1210");
        }
        try (var r = s.executeQuery("SELECT numero,codigo_autorizacion FROM factura_venta WHERE id=4")) {
            assertThat(r.next()).isTrue(); assertThat(r.getObject(1)).isNull(); assertThat(r.getObject(2)).isNull();
        }
    }
}
