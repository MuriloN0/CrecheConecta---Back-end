package com.crecheconecta.configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetencaoAutenticacaoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-11-20T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void permaneceDesativadaSemConfiguracaoExplicita() {
        contexto().run(contexto ->
                assertEquals(
                        0,
                        contexto.getBeansOfType(RetencaoAutenticacao.class).size()
                )
        );
    }

    @Test
    void permaneceDesativadaQuandoConfiguradaComoFalse() {
        contexto().withPropertyValues("app.auth.retencao.enabled=false")
                .run(contexto -> {
                    assertNull(contexto.getStartupFailure());
                    assertTrue(
                            contexto.getBeansOfType(RetencaoAutenticacao.class).isEmpty()
                    );
                });
    }

    @Test
    void ativaComPrazoELotesConfigurados() {
        contexto().withPropertyValues(
                "app.auth.retencao.enabled=true",
                "app.auth.retencao.prazo=P7D",
                "app.auth.retencao.lote=100",
                "app.auth.retencao.lotes=2"
        ).run(contexto -> {
            assertNull(contexto.getStartupFailure());
            assertNotNull(contexto.getBean(RetencaoAutenticacao.class));
        });
    }

    @Test
    void rejeitaPrazoQueApagariaContadoresRecentes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetencaoAutenticacao(jdbc, clock, "PT59M", 500, 10)
        );
    }

    @Test
    void rejeitaLotesInvalidos() {
        for (int lote : new int[]{0, 5001}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RetencaoAutenticacao(jdbc, clock, "P7D", lote, 10)
            );
        }

        for (int lotes : new int[]{0, 101}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RetencaoAutenticacao(jdbc, clock, "P7D", 500, lotes)
            );
        }
    }

    @Test
    void falhaEmSessoesNaoImpedeLimpezaDasAcoes() {
        when(jdbc.update(
                contains("DELETE FROM sessoes"),
                any(Object[].class)
        )).thenThrow(new DataAccessResourceFailureException("Falha simulada"));

        assertDoesNotThrow(() ->
                new RetencaoAutenticacao(jdbc, clock, "P7D", 500, 10).limpar()
        );

        verify(jdbc).update(
                contains("DELETE FROM acao_verificacao"),
                any(Object[].class)
        );
    }

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withInitializer(contexto -> {
                    var fontes = contexto.getEnvironment().getPropertySources();
                    fontes.remove(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
                    );
                    fontes.remove(
                            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME
                    );
                })
                .withUserConfiguration(RetencaoAutenticacao.class)
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(Clock.class, () -> clock);
    }
}