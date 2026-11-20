package ar.edu.utn.facturacion.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class TiempoConfig {
    @Bean
    Clock reloj() { return Clock.systemUTC(); }
}
