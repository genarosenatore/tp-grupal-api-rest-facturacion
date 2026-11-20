package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.config.DemoH2ListasConfig;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DemoH2ListasTest {
    @Test void actualizaLaUnicidadAnteriorSinPerderPreciosYAdmiteReinicio() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:migrar_listas"); var s = c.createStatement()) {
            s.execute("CREATE TABLE lista_precio_articulo(id bigint primary key, articulo_id bigint, tipo_moneda_id bigint, precio numeric(15,2), UNIQUE(articulo_id,tipo_moneda_id))");
            s.execute("INSERT INTO lista_precio_articulo VALUES(1,1,1,1000)");
            s.execute("ALTER TABLE lista_precio_articulo ADD lista_precio_id bigint default 1 not null");
            DemoH2ListasConfig.actualizarRestriccion(c);
            DemoH2ListasConfig.actualizarRestriccion(c);
            s.execute("INSERT INTO lista_precio_articulo VALUES(2,1,1,800,2)");
            assertThatThrownBy(() -> s.execute("INSERT INTO lista_precio_articulo VALUES(3,1,1,700,2)")).isInstanceOf(SQLException.class);
            try (var r = s.executeQuery("SELECT precio,lista_precio_id FROM lista_precio_articulo WHERE id=1")) {
                assertThat(r.next()).isTrue(); assertThat(r.getBigDecimal(1)).isEqualByComparingTo("1000"); assertThat(r.getLong(2)).isEqualTo(1);
            }
        }
    }
}
