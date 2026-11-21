package com.crecheconecta.configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.auth.retencao", name = "enabled", havingValue = "true")
public class RetencaoAutenticacao {

    private static final Logger LOG = LoggerFactory.getLogger(RetencaoAutenticacao.class);

    private static final Duration JANELA_SEGURANCA = Duration.ofHours(1);

    private static final String EXCLUIR_LOTE = """
            DELETE FROM %1$s
            WHERE id IN (
                SELECT id FROM %1$s
                WHERE expira_em < ?
                ORDER BY expira_em, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration prazo;
    private final int lote;
    private final int lotes;

    public RetencaoAutenticacao(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${app.auth.retencao.prazo:P7D}") String prazo,
            @Value("${app.auth.retencao.lote:500}") int lote,
            @Value("${app.auth.retencao.lotes:10}") int lotes
    ) {
        this.prazo = Duration.parse(prazo);

        if (this.prazo.compareTo(JANELA_SEGURANCA) < 0) {
            throw new IllegalArgumentException(
                    "app.auth.retencao.prazo deve ser de pelo menos uma hora."
            );
        }

        if (lote < 1 || lote > 5000 || lotes < 1 || lotes > 100) {
            throw new IllegalArgumentException(
                    "Configure app.auth.retencao.lote entre 1 e 5000 e lotes entre 1 e 100."
            );
        }

        this.jdbc = jdbc;
        this.clock = clock;
        this.lote = lote;
        this.lotes = lotes;
    }

    @Scheduled(cron = "${app.auth.retencao.cron:0 0 * * * *}")
    public void limpar() {
        var limite = clock.instant().minus(prazo).atOffset(ZoneOffset.UTC);
        limparTabela("sessoes", limite);
        limparTabela("acao_verificacao", limite);
    }

    private void limparTabela(String tabela, OffsetDateTime limite) {
        int total = 0;

        try {
            String sql = EXCLUIR_LOTE.formatted(tabela);

            for (int i = 0; i < lotes; i++) {
                int removidos = jdbc.update(sql, limite, lote);
                total += removidos;

                if (removidos < lote) {
                    break;
                }
            }

            if (total > 0) {
                LOG.info(
                        "Retencao de autenticacao: tabela={}, removidos={}",
                        tabela,
                        total
                );
            }
        } catch (DataAccessException erro) {
            LOG.error(
                    "Falha na retencao de autenticacao: tabela={}, removidos={}. Nova tentativa no proximo horario.",
                    tabela,
                    total,
                    erro
            );
        }
    }
}