package com.crecheconecta.service;

import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.entity.ConviteCadastro;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConviteProfessorService {

    private final ConviteCadastroRepository convites;
    private final UsuarioRepository usuarios;
    private final TokenConviteService tokens;
    private final EmailService emails;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacao;
    private final String urlCadastro;

    public ConviteProfessorService(ConviteCadastroRepository convites, UsuarioRepository usuarios, TokenConviteService tokens, EmailService emails, AuditoriaService auditoria, PlatformTransactionManager transactionManager, @Value("${app.cadastro.url}") String urlCadastro) {
        this.convites = convites;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.emails = emails;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transactionManager);
        this.urlCadastro = validarUrlCadastro(urlCadastro);
    }

    public ConviteCadastroResponse convidar(ConvidarProfessorRequest request, UsuarioAtual solicitante) {
        if (solicitante == null || solicitante.perfil() != Perfil.DIRECAO) {
            throw new AcessoNegadoException( "Você não possui permissão para esta operação.");
        }

        var envio = Objects.requireNonNull(transacao.execute(status -> prepararConvite(request, solicitante)));

        try {
            emails.enviarConviteProfessor(envio.conviteId(), envio.email(), envio.link());
        } catch (RuntimeException exception) {
            try {
                cancelarAposFalha(envio.conviteId());
            } catch (RuntimeException falhaCancelamento) {
                exception.addSuppressed(falhaCancelamento);
            }

            throw exception;
        }

        return new ConviteCadastroResponse(envio.conviteId(), envio.email(), envio.expiraEm(), "Convite enviado. O professor deve concluir o cadastro pelo e-mail.");
    }

    private EnvioConvite prepararConvite(ConvidarProfessorRequest request, UsuarioAtual solicitante) {
        var diretor = usuarios.buscarPorIdComBloqueio(solicitante.id())
                .orElseThrow(() -> new AcessoNegadoException("Você não possui permissão para esta operação."));

        if (!diretor.isAtivo() || diretor.getPerfil() != Perfil.DIRECAO) {
            throw new AcessoNegadoException("Você não possui permissão para esta operação.");
        }

        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        String nome = request.nome().strip();

        if (nome.isBlank()) {throw new RegraNegocioException("Informe o nome do professor.");
        }

        if (usuarios.existsByEmail(email)) {
            throw new RegraNegocioException("Já existe um usuário cadastrado com este e-mail.");
        }

        Instant agora = Instant.now();

        var anterior = convites.buscarPendentePorEmailComBloqueio(email)
                .orElse(null);

        if (anterior != null) {
            if (anterior.getCriadoEm()
                    .plusSeconds(60)
                    .isAfter(agora)) {

                throw new RegraNegocioException("Aguarde 60 segundos antes de enviar outro convite.");
            }

            anterior.setCanceladoEm(agora);

            convites.flush();
        }

        String token = tokens.gerarToken();

        var convite = new ConviteCadastro();
        convite.setNome(nome);
        convite.setEmail(email);
        convite.setPerfil(Perfil.PROFESSOR);
        convite.setTokenHash(tokens.calcularHash(token));
        convite.setCriadoPor(diretor);
        convite.setCriadoEm(agora);
        convite.setExpiraEm(
                agora.plus(Duration.ofHours(24))
        );

        convites.saveAndFlush(convite);

        auditoria.aposCommit("CONVITE_PROFESSOR_CRIADO", diretor.getId(), convite.getId());

        String link = urlCadastro + "#token=" + token;

        return new EnvioConvite(convite.getId(), email, convite.getExpiraEm(), link);
    }

    private void cancelarAposFalha(UUID conviteId) {
        transacao.executeWithoutResult(status -> {
            var convite = convites.buscarPorIdComBloqueio(conviteId)
                    .orElse(null);

            if (convite != null && convite.getConsumidoEm() == null && convite.getCanceladoEm() == null) {

                convite.setCanceladoEm(Instant.now());
            }
        });
    }

    private String validarUrlCadastro(String valor) {
        URI uri = URI.create(valor);

        boolean https = "https".equalsIgnoreCase(uri.getScheme());

        boolean httpLocal = "http".equalsIgnoreCase(uri.getScheme()) && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));

        if ((!https && !httpLocal) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {

            throw new IllegalArgumentException("app.cadastro.url deve ser uma URL HTTPS " + "ou HTTP local, sem credenciais, query ou fragmento."
            );
        }

        return uri.toASCIIString();
    }

    private record EnvioConvite(UUID conviteId, String email, Instant expiraEm, String link) {
        @Override
        public String toString() {
            return "EnvioConvite[dados omitidos]";
        }
    }
}