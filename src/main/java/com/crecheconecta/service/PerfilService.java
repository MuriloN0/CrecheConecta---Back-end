package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PerfilService {

    private static final Duration VALIDADE_CONFIRMACAO = Duration.ofMinutes(10);
    private static final Duration INTERVALO_ENVIO = Duration.ofMinutes(1);

    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder senhas;
    private final TokenSessaoService tokens;
    private final AnonimizacaoService anonimizacao;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final RestClient resend;
    private final String remetente;
    private final TransactionTemplate transacao;

    public PerfilService(
            UsuarioRepository usuarios,
            SessaoRepository sessoes,
            JdbcTemplate jdbc,
            PasswordEncoder senhas,
            TokenSessaoService tokens,
            AnonimizacaoService anonimizacao,
            AuditoriaService auditoria,
            Clock clock,
            @Qualifier("resendRestClient") RestClient resend,
            @Value("${app.email.resend.remetente}") String remetente,
            PlatformTransactionManager gerenciadorTransacoes
    ) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.jdbc = jdbc;
        this.senhas = senhas;
        this.tokens = tokens;
        this.anonimizacao = anonimizacao;
        this.auditoria = auditoria;
        this.clock = clock;
        this.resend = resend;
        this.remetente = remetente;
        this.transacao = new TransactionTemplate(gerenciadorTransacoes);
    }

    @Transactional(readOnly = true)
    public DadosPerfil consultar(UsuarioAtual atual) {
        return dados(exigirPerfilAtivo(usuarios.findById(atual.id()).orElse(null), atual.perfil()));
    }

    @Transactional
    public DadosPerfil atualizarTelefone(UsuarioAtual atual, String telefone) {
        var usuario = buscarComBloqueio(atual);
        usuario.setTelefone(telefone);
        auditoria.aposCommit("PERFIL_TELEFONE_ATUALIZADO", atual.id(), atual.id());
        return dados(usuario);
    }

    public void solicitarTrocaEmail(UsuarioAtual atual, String endereco, String senhaAtual) {
        exigirResponsavel(atual);
        String novoEmail = endereco.strip().toLowerCase(Locale.ROOT);
        String codigo = tokens.gerar();
        String hash = tokens.calcularHash(codigo);

        transacao.executeWithoutResult(status -> {
            var usuario = buscarComBloqueio(atual);
            conferirSenha(usuario, senhaAtual);

            if (usuario.getEmail().equals(novoEmail)) {
                throw new RegraNegocioException("Informe um e-mail diferente do atual.");
            }
            if (usuarios.existsByEmail(novoEmail)) {
                throw new RegraNegocioException("Este e-mail já está cadastrado.");
            }

            Instant agora = clock.instant();
            var anterior = buscarConfirmacao(atual.id());

            if (anterior != null && anterior.criadoEm().plus(INTERVALO_ENVIO).isAfter(agora)) {
                throw new RegraNegocioException("Aguarde um minuto para solicitar outro código.");
            }

            jdbc.update("DELETE FROM confirmacoes_email_perfil WHERE usuario_id = ?", atual.id());
            jdbc.update("""
                    INSERT INTO confirmacoes_email_perfil
                        (usuario_id, novo_email, codigo_hash, criado_em, expira_em)
                    VALUES (?, ?, ?, ?, ?)
                    """, atual.id(), novoEmail, hash,
                    emUtc(agora), emUtc(agora.plus(VALIDADE_CONFIRMACAO)));
        });

        try {
            enviarConfirmacao(novoEmail, codigo);
        } catch (RestClientException falha) {
            transacao.executeWithoutResult(status -> jdbc.update("""
                    DELETE FROM confirmacoes_email_perfil
                    WHERE usuario_id = ? AND codigo_hash = ?
                    """, atual.id(), hash));
            throw new AutenticacaoException(ErroAutenticacao.ENVIO_EMAIL_INDISPONIVEL);
        }
    }

    @Transactional
    public void confirmarTrocaEmail(UsuarioAtual atual, String codigo) {
        exigirResponsavel(atual);
        var usuario = buscarComBloqueio(atual);
        var pendente = buscarConfirmacao(atual.id());
        Instant agora = clock.instant();

        if (pendente == null || !pendente.expiraEm().isAfter(agora)
                || !hashIgual(pendente.codigoHash(), tokens.calcularHash(codigo))) {
            throw new RegraNegocioException("Código inválido ou expirado. Confira o e-mail ou solicite outro.");
        }

        if (usuarios.existsByEmail(pendente.novoEmail())) {
            throw new RegraNegocioException("Este e-mail já está cadastrado.");
        }

        usuario.setEmail(pendente.novoEmail());
        usuarios.flush();
        jdbc.update("DELETE FROM confirmacoes_email_perfil WHERE usuario_id = ?", atual.id());
        sessoes.revogarTodasDoUsuario(atual.id(), agora);
        auditoria.aposCommit("PERFIL_EMAIL_ATUALIZADO", atual.id(), atual.id());
    }

    @Transactional
    public void inativarConta(UsuarioAtual atual, String senhaAtual) {
        exigirResponsavel(atual);
        conferirSenha(buscarComBloqueio(atual), senhaAtual);
        jdbc.update("DELETE FROM confirmacoes_email_perfil WHERE usuario_id = ?", atual.id());
        anonimizacao.anonimizarPropriaConta(atual);
    }

    private Usuario buscarComBloqueio(UsuarioAtual atual) {
        return exigirPerfilAtivo(usuarios.buscarPorIdComBloqueio(atual.id()).orElse(null), atual.perfil());
    }

    private Usuario exigirPerfilAtivo(Usuario usuario, Perfil perfil) {
        if (usuario == null || !usuario.isAtivo() || usuario.getPerfil() != perfil
                || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO
                || (perfil != Perfil.PROFESSOR && perfil != Perfil.RESPONSAVEL)) {
            throw new AccessDeniedException("O perfil não está disponível para esta conta.");
        }
        return usuario;
    }

    private void exigirResponsavel(UsuarioAtual atual) {
        if (atual.perfil() != Perfil.RESPONSAVEL) {
            throw new AccessDeniedException("Esta operação é restrita ao responsável.");
        }
    }

    private void conferirSenha(Usuario usuario, String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72
                || !senhas.matches(senha, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual está incorreta.");
        }
    }

    private ConfirmacaoEmail buscarConfirmacao(UUID usuarioId) {
        return jdbc.query("""
                SELECT novo_email, codigo_hash, criado_em, expira_em
                FROM confirmacoes_email_perfil
                WHERE usuario_id = ?
                """, (rs, linha) -> new ConfirmacaoEmail(
                rs.getString("novo_email"),
                rs.getString("codigo_hash"),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
                rs.getObject("expira_em", OffsetDateTime.class).toInstant()
        ), usuarioId).stream().findFirst().orElse(null);
    }

    private void enviarConfirmacao(String email, String codigo) {
        var pedido = new EmailService.EnviarEmailRequest(
                remetente,
                List.of(email),
                "Confirme o novo e-mail do CrecheConecta",
                """
                Você solicitou a troca do e-mail da sua conta no CrecheConecta.

                Cole o código abaixo na tela Meu perfil. Ele é válido por 10 minutos.

                %s

                Se você não solicitou a alteração, ignore esta mensagem.
                """.formatted(codigo)
        );

        var resposta = resend.post()
                .uri("/emails")
                .body(pedido)
                .retrieve()
                .body(EmailService.EnviarEmailResponse.class);

        if (resposta == null || Objects.toString(resposta.id(), "").isBlank()) {
            throw new RestClientException("O provedor não confirmou o envio.");
        }
    }

    private boolean hashIgual(String esperado, String recebido) {
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.US_ASCII),
                recebido.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private DadosPerfil dados(Usuario usuario) {
        return new DadosPerfil(usuario.getNome(), usuario.getEmail(), usuario.getTelefone(), usuario.getPerfil());
    }

    private record ConfirmacaoEmail(String novoEmail, String codigoHash, Instant criadoEm, Instant expiraEm) {}

    public record DadosPerfil(String nome, String email, String telefone, Perfil perfil) {}
}