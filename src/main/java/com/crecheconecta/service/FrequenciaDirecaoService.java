package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.TurmaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class FrequenciaDirecaoService {

    private static final ZoneId FUSO_CRECHE = ZoneId.of("America/Sao_Paulo");
    private static final int MAXIMO_DIAS = 366;

    private final NamedParameterJdbcTemplate jdbc;
    private final TurmaRepository turmas;
    private final Clock clock;

    public FrequenciaDirecaoService(
            NamedParameterJdbcTemplate jdbc,
            TurmaRepository turmas,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.turmas = turmas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Resumo consultar(LocalDate inicio, LocalDate fim, UUID turmaId) {
        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial deve ser anterior ou igual à final.");
        }

        if (fim.isAfter(LocalDate.now(clock.withZone(FUSO_CRECHE)))) {
            throw new RegraNegocioException("A data final não pode estar no futuro.");
        }

        if (ChronoUnit.DAYS.between(inicio, fim) >= MAXIMO_DIAS) {
            throw new RegraNegocioException("Selecione um período de até 366 dias.");
        }

        String nomeTurma = turmaId == null
                ? "Todas as turmas"
                : turmas.findById(turmaId)
                .orElseThrow(TurmaNaoEncontradaException::new)
                .getNome();

        var parametros = new MapSqlParameterSource("inicio", inicio)
                .addValue("fim", fim);

        String filtroTurma = "";
        if (turmaId != null) {
            parametros.addValue("turmaId", turmaId);
            filtroTurma = " AND p.turma_id = :turmaId";
        }

        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT (p.turma_id, p.data)) AS chamadas,
                       COUNT(*) FILTER (WHERE p.presente IS TRUE) AS presencas,
                       COUNT(*) FILTER (WHERE p.presente IS FALSE) AS faltas,
                       COUNT(*) FILTER (WHERE p.presente IS NULL) AS pendencias
                FROM presencas p
                WHERE p.data BETWEEN :inicio AND :fim
                """ + filtroTurma, parametros, (rs, i) -> new Resumo(
                inicio,
                fim,
                turmaId,
                nomeTurma,
                rs.getLong("chamadas"),
                rs.getLong("presencas"),
                rs.getLong("faltas"),
                rs.getLong("pendencias")
        ));
    }

    public record Resumo(
            LocalDate inicio,
            LocalDate fim,
            UUID turmaId,
            String nomeTurma,
            long chamadas,
            long presencas,
            long faltas,
            long pendencias
    ) {}
}