package com.crecheconecta.security;

import com.crecheconecta.service.RateLimitService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Base64;

import static com.crecheconecta.service.RateLimitService.Politica.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "RATE_LIMIT_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ratelimit_test"
)
class RateLimitIpPostgresTest {

    private static final String SEGREDO =
            Base64.getEncoder().encodeToString(new byte[32]);

    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager gerenciador;

    private RateLimitService limites;

    @BeforeAll
    static void prepararBancoIsolado() {
        var fonte = new DriverManagerDataSource(
                System.getenv("RATE_LIMIT_TEST_URL"),
                "rate_test",
                ""
        );

        jdbc = new JdbcTemplate(fonte);
        gerenciador = new DataSourceTransactionManager(fonte);

        jdbc.execute("DROP TABLE IF EXISTS limites_requisicoes");

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V47__criar_limites_requisicoes.sql"
        )).execute(fonte);
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM limites_requisicoes");

        limites = new RateLimitService(
                jdbc,
                gerenciador,
                SEGREDO
        );
    }

    @Test
    void limitaTrintaPorMinutoSemBloquearOutroIp() {
        for (int i = 0; i < 30; i++) {
            limites.verificar(
                    AUTENTICACAO_IP,
                    "ipv4:203.0.113.10"
            );
        }

        var erro = assertThrows(
                RateLimitException.class,
                () -> limites.verificar(
                        AUTENTICACAO_IP,
                        "ipv4:203.0.113.10"
                )
        );

        assertTrue(
                erro.getSegundosRestantes() >= 1
                        && erro.getSegundosRestantes() <= 60
        );

        assertDoesNotThrow(() ->
                limites.verificar(
                        AUTENTICACAO_IP,
                        "ipv4:198.51.100.20"
                )
        );
    }

    @Test
    void limitaTrezentasPorHora() {
        for (int i = 0; i < 300; i++) {
            limites.verificar(
                    AUTENTICACAO_IP_HORA,
                    "ipv4:203.0.113.10"
            );
        }

        var erro = assertThrows(
                RateLimitException.class,
                () -> limites.verificar(
                        AUTENTICACAO_IP_HORA,
                        "ipv4:203.0.113.10"
                )
        );

        assertTrue(
                erro.getSegundosRestantes() >= 1
                        && erro.getSegundosRestantes() <= 3600
        );
    }

    @Test
    void compartilhaLimitePorIpEntreInstancias() {
        var outra = new RateLimitService(
                jdbc,
                gerenciador,
                SEGREDO
        );

        for (int i = 0; i < 15; i++) {
            limites.verificar(
                    AUTENTICACAO_IP,
                    "ipv4:203.0.113.10"
            );

            outra.verificar(
                    AUTENTICACAO_IP,
                    "ipv4:203.0.113.10"
            );
        }

        assertThrows(
                RateLimitException.class,
                () -> outra.verificar(
                        AUTENTICACAO_IP,
                        "ipv4:203.0.113.10"
                )
        );
    }
}