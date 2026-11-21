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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.crecheconecta.service.RateLimitService.Politica.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "RATE_LIMIT_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ratelimit_test"
)
class RateLimitServicePostgresTest {

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
        limites = new RateLimitService(jdbc, gerenciador, SEGREDO);
    }

    @Test
    void permiteDezChamadasEBloqueiaADecimaPrimeira() {
        for (int i = 0; i < 10; i++) {
            limites.verificar(LOGIN_EMAIL, "pessoa@example.invalid");
        }

        var erro = assertThrows(
                RateLimitException.class,
                () -> limites.verificar(
                        LOGIN_EMAIL,
                        "pessoa@example.invalid"
                )
        );

        assertTrue(erro.getSegundosRestantes() >= 1);
        assertTrue(erro.getSegundosRestantes() <= 60);
    }

    @Test
    void preservaContadoresEntreInstancias() {
        limites.verificar(RECUPERACAO_EMAIL, "pessoa");

        var outraInstancia = new RateLimitService(
                jdbc,
                gerenciador,
                SEGREDO
        );

        outraInstancia.verificar(RECUPERACAO_EMAIL, "pessoa");
        limites.verificar(RECUPERACAO_EMAIL, "pessoa");

        assertThrows(
                RateLimitException.class,
                () -> outraInstancia.verificar(
                        RECUPERACAO_EMAIL,
                        "pessoa"
                )
        );
    }

    @Test
    void separaIdentidadesEPoliticasSemSalvarEmailAberto() {
        for (int i = 0; i < 10; i++) {
            limites.verificar(LOGIN_EMAIL, "pessoa@example.invalid");
        }

        assertDoesNotThrow(() ->
                limites.verificar(LOGIN_EMAIL, "outra@example.invalid")
        );

        assertDoesNotThrow(() ->
                limites.verificar(
                        RECUPERACAO_EMAIL,
                        "pessoa@example.invalid"
                )
        );

        var chaves = jdbc.queryForList(
                "SELECT chave_hash FROM limites_requisicoes",
                String.class
        );

        assertTrue(chaves.stream().allMatch(
                chave -> chave.matches("[0-9a-f]{64}")
        ));
    }

    @Test
    void liberaAposExpiracaoSemPrecisarDaLimpeza() {
        for (int i = 0; i < 10; i++) {
            limites.verificar(LOGIN_EMAIL, "pessoa");
        }

        jdbc.update("""
                UPDATE limites_requisicoes
                SET expira_em = statement_timestamp() - interval '1 second'
                """);

        assertDoesNotThrow(() ->
                limites.verificar(LOGIN_EMAIL, "pessoa")
        );

        assertEquals(1, jdbc.queryForObject(
                "SELECT quantidade FROM limites_requisicoes",
                Integer.class
        ));
    }

    @Test
    void requisicoesNegadasNaoProrrogamJanela() {
        for (int i = 0; i < 10; i++) {
            limites.verificar(LOGIN_EMAIL, "pessoa");
        }

        var prazo = jdbc.queryForObject(
                "SELECT expira_em FROM limites_requisicoes",
                java.sql.Timestamp.class
        );

        for (int i = 0; i < 20; i++) {
            assertThrows(
                    RateLimitException.class,
                    () -> limites.verificar(LOGIN_EMAIL, "pessoa")
            );
        }

        assertEquals(prazo, jdbc.queryForObject(
                "SELECT expira_em FROM limites_requisicoes",
                java.sql.Timestamp.class
        ));

        assertEquals(11, jdbc.queryForObject(
                "SELECT quantidade FROM limites_requisicoes",
                Integer.class
        ));
    }

    @Test
    void naoPerdeContadorQuandoOperacaoDeNegocioReverte() {
        assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(gerenciador)
                        .executeWithoutResult(status -> {
                            limites.verificar(LOGIN_EMAIL, "pessoa");
                            throw new IllegalStateException(
                                    "Falha de negócio."
                            );
                        })
        );

        assertEquals(1, jdbc.queryForObject(
                "SELECT quantidade FROM limites_requisicoes",
                Integer.class
        ));
    }

    @Test
    void limitaChamadasConcorrentesAtomicamente() throws Exception {
        var tarefas = new ArrayList<Callable<Boolean>>();

        for (int i = 0; i < 30; i++) {
            tarefas.add(() -> {
                try {
                    limites.verificar(LOGIN_EMAIL, "mesma-pessoa");
                    return true;
                } catch (RateLimitException exception) {
                    return false;
                }
            });
        }

        int permitidas = 0;

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var resultado : executor.invokeAll(tarefas)) {
                if (resultado.get()) {
                    permitidas++;
                }
            }
        }

        assertEquals(10, permitidas);
    }

    @Test
    void limpezaRemoveSomenteRegistrosAntigos() {
        limites.verificar(LOGIN_EMAIL, "antiga");

        jdbc.update("""
                UPDATE limites_requisicoes
                SET expira_em = statement_timestamp() - interval '2 hours'
                """);

        limites.verificar(LOGIN_EMAIL, "ativa");
        limites.limparExpirados();

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM limites_requisicoes",
                Integer.class
        ));
    }
}