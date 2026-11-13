package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificacaoService {

    private static final int TAMANHO = 20;
    private final NamedParameterJdbcTemplate jdbc;
    private final TermoService termos;

    public NotificacaoService(NamedParameterJdbcTemplate jdbc, TermoService termos) {

        this.jdbc = jdbc;
        this.termos = termos;
    }

    @Transactional(readOnly = true)
    public Pendencias pendencias(UsuarioAtual eu) {

        validarAceite(eu);
        var total = jdbc.queryForObject("""
            SELECT count(*) FROM notificacoes_comunicado n
            JOIN comunicados c ON c.id = n.comunicado_id AND c.ativo
            WHERE n.usuario_id = :usuario AND n.lida_em IS NULL
            """, Map.of("usuario", eu.id()), Long.class);

        return new Pendencias(total);
    }

    @Transactional(readOnly = true)
    public Pagina listar(UsuarioAtual eu, int pagina) {

        validarAceite(eu);
        if (pagina < 0) {
            throw new RegraNegocioException("Informe uma página a partir de zero.");
        }

        var registros = jdbc.query("""
            SELECT n.comunicado_id, c.titulo, n.criada_em, n.lida_em IS NOT NULL AS lida
            FROM notificacoes_comunicado n
            JOIN comunicados c ON c.id = n.comunicado_id AND c.ativo
            WHERE n.usuario_id = :usuario
            ORDER BY n.criada_em DESC, n.comunicado_id DESC
            LIMIT :limite OFFSET :offset
            """, Map.of("usuario", eu.id(), "limite", TAMANHO + 1, "offset", (long) pagina * TAMANHO),
                (rs, i) -> new Notificacao(
                        rs.getObject("comunicado_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getObject("criada_em", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("lida")));

        return new Pagina(registros.subList(0, Math.min(registros.size(), TAMANHO)), registros.size() > TAMANHO);
    }

    @Transactional
    public void marcarLida(UsuarioAtual eu, UUID comunicado) {
        validarAceite(eu);
        jdbc.update("""
            UPDATE notificacoes_comunicado SET lida_em = CURRENT_TIMESTAMP
            WHERE usuario_id = :usuario AND comunicado_id = :comunicado AND lida_em IS NULL
            """, Map.of("usuario", eu.id(), "comunicado", comunicado));
    }

    private void validarAceite(UsuarioAtual eu) {
        if (eu.perfil() == Perfil.RESPONSAVEL && termos.precisaAceitar(eu.id())) {
            throw new AcessoNegadoException("Aceite os termos vigentes para acessar as notificações.");
        }
    }

    public record Pendencias(long total) {}
    public record Notificacao(UUID comunicadoId, String titulo, Instant criadaEm, boolean lida) {}
    public record Pagina(List<Notificacao> itens, boolean temProxima) {}
}