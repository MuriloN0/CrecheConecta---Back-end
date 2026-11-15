package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ConsultaAuditoriaService {

    private static final int TAMANHO = 25;
    private static final ZoneId FUSO_CRECHE = ZoneId.of("America/Sao_Paulo");

    private static final String ASSUNTO_SQL = """
            CASE
                WHEN a.acao = 'ALUNO_MATRICULADO' THEN 'TURMAS'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'CHAT' THEN 'CHAT'
                WHEN SPLIT_PART(a.acao, '_', 1) IN
                     ('SESSAO', 'DIRECAO', 'PROFESSOR', 'RESPONSAVEL', 'CONVITE') THEN 'USUARIOS'
                WHEN SPLIT_PART(a.acao, '_', 1) IN ('TURMA', 'MATRICULA') THEN 'TURMAS'
                WHEN SPLIT_PART(a.acao, '_', 1) IN ('ALUNO', 'FILHO', 'FILHOS') THEN 'ALUNOS'
                WHEN SPLIT_PART(a.acao, '_', 1) IN ('PRESENCA', 'CHAMADA') THEN 'FREQUENCIA'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'COMUNICADO' THEN 'COMUNICADOS'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'TERMO' THEN 'TERMOS'
                WHEN SPLIT_PART(a.acao, '_', 1) IN ('ATIVIDADE', 'ATIVIDADES') THEN 'ATIVIDADES'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'DIARIO' THEN 'ROTINA'
                WHEN SPLIT_PART(a.acao, '_', 1) IN ('MALA', 'MALAS', 'ITEM') THEN 'MALA_VIRTUAL'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'SOLICITACAO' THEN 'MATERIAIS'
                WHEN SPLIT_PART(a.acao, '_', 1) IN
                     ('FICHA', 'FICHAS', 'OBSERVACAO', 'OBSERVACOES') THEN 'INFORMACOES'
                WHEN SPLIT_PART(a.acao, '_', 1) = 'ERRO' THEN 'ERROS'
                ELSE 'OUTROS'
            END
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultaAuditoriaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Pagina listar(
            Assunto assunto,
            String acao,
            LocalDate inicio,
            LocalDate fim,
            int pagina
    ) {
        if (pagina < 0 || (inicio != null && fim != null && inicio.isAfter(fim))) {
            throw new RegraNegocioException("Confira o período e a página solicitada.");
        }

        String busca = acao == null ? "" : acao.strip();
        if (busca.length() > 60) {
            throw new RegraNegocioException("Busque por uma ação com até 60 caracteres.");
        }

        var sql = new StringBuilder("""
                SELECT a.id, a.instante, a.acao, a.usuario_id, u.nome AS usuario_nome,
                       a.recurso_id,
                """);

        sql.append(ASSUNTO_SQL).append("""
                 AS assunto
                FROM auditoria a
                LEFT JOIN usuarios u ON u.id = a.usuario_id
                WHERE 1 = 1
                """);

        var params = new MapSqlParameterSource()
                .addValue("limite", TAMANHO + 1)
                .addValue("offset", (long) pagina * TAMANHO);

        if (inicio != null) {
            sql.append(" AND a.instante >= :inicio");
            params.addValue("inicio", inicio.atStartOfDay(FUSO_CRECHE).toOffsetDateTime());
        }

        if (fim != null) {
            sql.append(" AND a.instante < :fim");
            params.addValue("fim", fim.plusDays(1).atStartOfDay(FUSO_CRECHE).toOffsetDateTime());
        }

        if (assunto != null) {
            sql.append(" AND (").append(ASSUNTO_SQL).append(") = :assunto");
            params.addValue("assunto", assunto.name());
        }

        if (!busca.isEmpty()) {
            sql.append(" AND STRPOS(LOWER(a.acao), LOWER(:acao)) > 0");
            params.addValue("acao", busca);
        }

        sql.append(" ORDER BY a.instante DESC, a.id DESC LIMIT :limite OFFSET :offset");

        var registros = jdbc.query(sql.toString(), params, (rs, i) -> new Evento(
                rs.getObject("id", UUID.class),
                rs.getObject("instante", OffsetDateTime.class).toInstant(),
                Assunto.valueOf(rs.getString("assunto")),
                rs.getString("acao"),
                rs.getObject("usuario_id", UUID.class),
                rs.getString("usuario_nome"),
                rs.getObject("recurso_id", UUID.class)
        ));

        return new Pagina(
                registros.subList(0, Math.min(TAMANHO, registros.size())),
                registros.size() > TAMANHO
        );
    }

    public enum Assunto {
        CHAT, USUARIOS, TURMAS, ALUNOS, FREQUENCIA, COMUNICADOS, TERMOS,
        ATIVIDADES, ROTINA, MALA_VIRTUAL, MATERIAIS, INFORMACOES, ERROS, OUTROS
    }

    public record Evento(
            UUID id,
            Instant instante,
            Assunto assunto,
            String acao,
            UUID usuarioId,
            String usuarioNome,
            UUID recursoId
    ) {}

    public record Pagina(List<Evento> itens, boolean temProxima) {}
}