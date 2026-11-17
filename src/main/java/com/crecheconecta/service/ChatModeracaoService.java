package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ChatModeracaoService {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ChatModeracaoService(JdbcTemplate jdbc, AuditoriaService auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DIRECAO')")
    public Situacao consultar(UUID responsavelId) {
        var ids = jdbc.queryForList("""
                SELECT id FROM usuarios
                WHERE id = ? AND perfil = 'RESPONSAVEL'
                  AND ativo AND situacao_cadastro = 'APROVADO'
                """, UUID.class, responsavelId);

        if (ids.isEmpty()) {
            throw responsavelIndisponivel();
        }

        return buscarSituacao(responsavelId, clock.instant());
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public Situacao bloquear(UsuarioAtual direcao, UUID responsavelId, boolean permanente, Integer minutos) {
        if (permanente && minutos != null) {
            throw new RegraNegocioException("Retire a duração para suspender por tempo indeterminado.");
        }

        if (!permanente && (minutos == null || minutos < 1 || minutos > 43_200)) {
            throw new RegraNegocioException("Informe uma duração entre 1 e 43.200 minutos.");
        }

        bloquearLinhaResponsavel(responsavelId);

        Instant agora = clock.instant();
        Instant expiraEm = permanente ? null : agora.plus(minutos, ChronoUnit.MINUTES);

        jdbc.update("""
                INSERT INTO bloqueios_chat_responsaveis (responsavel_id, bloqueado_em, expira_em)
                VALUES (?, ?, ?)
                ON CONFLICT (responsavel_id) DO UPDATE
                  SET bloqueado_em = EXCLUDED.bloqueado_em, expira_em = EXCLUDED.expira_em
                """,
                responsavelId,
                OffsetDateTime.ofInstant(agora, ZoneOffset.UTC),
                expiraEm == null ? null : OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC));

        auditoria.aposCommit("CHAT_RESPONSAVEL_BLOQUEADO", direcao.id(), responsavelId);
        return new Situacao(true, expiraEm);
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public Situacao desbloquear(UsuarioAtual direcao, UUID responsavelId) {
        bloquearLinhaResponsavel(responsavelId);

        int removidos = jdbc.update(
                "DELETE FROM bloqueios_chat_responsaveis WHERE responsavel_id = ?",
                responsavelId
        );

        if (removidos > 0) {
            auditoria.aposCommit("CHAT_RESPONSAVEL_DESBLOQUEADO", direcao.id(), responsavelId);
        }

        return new Situacao(false, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validarEnvio(UsuarioAtual usuario) {
        if (usuario.perfil() != Perfil.RESPONSAVEL) {
            return;
        }

        bloquearLinhaResponsavel(usuario.id());

        if (buscarSituacao(usuario.id(), clock.instant()).bloqueado()) {
            throw new RegraNegocioException(
                    "O envio de mensagens está suspenso para sua conta. Procure a direção para mais informações."
            );
        }
    }

    private void bloquearLinhaResponsavel(UUID responsavelId) {
        var ids = jdbc.queryForList("""
                SELECT id FROM usuarios
                WHERE id = ? AND perfil = 'RESPONSAVEL'
                  AND ativo AND situacao_cadastro = 'APROVADO'
                FOR UPDATE
                """, UUID.class, responsavelId);

        if (ids.isEmpty()) {
            throw responsavelIndisponivel();
        }
    }

    private Situacao buscarSituacao(UUID responsavelId, Instant agora) {
        return jdbc.query("""
                SELECT expira_em FROM bloqueios_chat_responsaveis
                WHERE responsavel_id = ? AND (expira_em IS NULL OR expira_em > ?)
                """, (rs, linha) -> {
                    var expiraEm = rs.getObject("expira_em", OffsetDateTime.class);
                    return new Situacao(true, expiraEm == null ? null : expiraEm.toInstant());
                }, responsavelId, OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .stream()
                .findFirst()
                .orElseGet(() -> new Situacao(false, null));
    }

    private RegraNegocioException responsavelIndisponivel() {
        return new RegraNegocioException("Selecione um responsável ativo para moderar o chat.");
    }

    public record Situacao(boolean bloqueado, Instant expiraEm) {}
}