package ar.edu.utn.facturacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Playwright contra HTTP/Spring/H2 reales, en un puerto aleatorio y sin adaptador ARCA. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:navegador;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "RUN_BROWSER_TESTS", matches = "true")
class FacturaNavegadorTest {
    @LocalServerPort int puerto;
    @Test void navegadorContraBackendAislado() throws Exception {
        String node = System.getenv("NODE_EXE");
        assertThat(node).as("Configure NODE_EXE con node.exe").isNotBlank();
        var log = Path.of("tmp", "playwright.log"); java.nio.file.Files.createDirectories(log.getParent());
        var builder = new ProcessBuilder(node, "node_modules/@playwright/test/cli.js", "test")
            .directory(Path.of(System.getProperty("user.dir"), "frontend").toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("API_PROXY_TARGET", "http://127.0.0.1:" + puerto);
        builder.environment().put("CI", "true");
        var proceso = builder.start();
        boolean termino = proceso.waitFor(150, TimeUnit.SECONDS);
        if (!termino) proceso.destroyForcibly();
        assertThat(termino).as("Playwright debe terminar").isTrue();
        assertThat(proceso.exitValue()).as("Resultado de Playwright: %s", java.nio.file.Files.readString(log)).isZero();
    }
}
