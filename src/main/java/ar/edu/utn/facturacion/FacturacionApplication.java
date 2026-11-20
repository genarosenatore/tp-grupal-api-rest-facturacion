package ar.edu.utn.facturacion;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@org.springframework.boot.context.properties.EnableConfigurationProperties(ar.edu.utn.facturacion.service.EmisorFactura.class)
public class FacturacionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacturacionApplication.class, args);
    }
}
