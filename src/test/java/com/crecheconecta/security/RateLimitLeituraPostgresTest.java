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
class RateLimitLeituraPostgresTest {

    private static JdbcTemplate jdbc;
    private static RateLimitService limites;

    @BeforeAll
    static void prepararBancoIsolado() {
        var fonte = new DriverManagerDataSource(
                System.getenv("RATE_LIMIT_TEST_URL"),
                "rate_test",
                ""
        );

        jdbc = new JdbcTemplate(fonte);

        jdbc.execute("DROP TABLE IF EXISTS limites_requisicoes");

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V47__criar_limites_requisicoes.sql"
        )).execute(fonte);

        limites = new RateLimitService(
                jdbc,
                new DataSourceTransactionManager(fonte),
                Base64.getEncoder().encodeToString(new byte[32])
        );
    }

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM limites_requisicoes");
    }

    @Test
    void permiteDozeLeiturasDoMuralEBloqueiaATreze() {
        String recurso = "usuario-a\n/api/comunicados";

        for (int i = 0; i < 12; i++) {
            limites.verificar(LEITURA_MURAL, recurso);
        }

        var erro = assertThrows(
                RateLimitException.class,
                () -> limites.verificar(
                        LEITURA_MURAL,
                        recurso
                )
        );

        assertTrue(
                erro.getSegundosRestantes() >= 1
                        && erro.getSegundosRestantes() <= 60
        );

        assertDoesNotThrow(() ->
                limites.verificar(
                        LEITURA_MURAL,
                        "usuario-b\n/api/comunicados"
                )
        );
    }

    @Test
    void limitaTrintaLeiturasPorRecursoEMantemOutrosRecursosDisponiveis() {
        String recurso = "usuario-a\n/api/alunos";

        for (int i = 0; i < 30; i++) {
            limites.verificar(LEITURA_RECURSO, recurso);
        }

        assertThrows(
                RateLimitException.class,
                () -> limites.verificar(
                        LEITURA_RECURSO,
                        recurso
                )
        );

        assertDoesNotThrow(() ->
                limites.verificar(
                        LEITURA_RECURSO,
                        "usuario-a\n/api/turmas"
                )
        );
    }
}