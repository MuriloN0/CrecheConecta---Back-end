package com.crecheconecta.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class RetencaoAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(RetencaoAuditoria.class);
    private static final String FUSO = "America/Sao_Paulo";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RetencaoAuditoria(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void limparAoIniciar() {
        limpar();
    }

    @Scheduled(cron = "${app.auditoria.retencao-cron:0 20 3 * * *}", zone = FUSO)
    public void limpar() {
        var limite = OffsetDateTime.now(clock.withZone(ZoneId.of(FUSO))).minusMonths(6);

        try {
            int removidos = jdbc.update("DELETE FROM auditoria WHERE instante < ?", limite);
            if (removidos > 0) {
                LOG.info("Retenção da auditoria removeu {} eventos anteriores a {}.", removidos, limite);
            }
        } catch (org.springframework.dao.DataAccessException erro) {
            LOG.error("A limpeza da auditoria falhou e será tentada na próxima execução.", erro);
        }
    }
}