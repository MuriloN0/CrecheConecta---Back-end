package com.crecheconecta.service;

import com.crecheconecta.dto.RedefinirSenhaRequest;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

@Service
public class RedefinicaoSenhaService {

    private static final Logger log =
            LoggerFactory.getLogger(RedefinicaoSenhaService.class);

    private final UsuarioRepository usuarios;
    private final AcaoVerificacaoRepository acoes;
    private final SessaoRepository sessoes;
    private final SegredoVerificacaoService segredos;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final TransactionTemplate transacao;

    public RedefinicaoSenhaService(
            UsuarioRepository usuarios,
            AcaoVerificacaoRepository acoes,
            SessaoRepository sessoes,
            SegredoVerificacaoService segredos,
            PasswordEncoder encoder,
            EmailService emailService,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarios = usuarios;
        this.acoes = acoes;
        this.sessoes = sessoes;
        this.segredos = segredos;
        this.encoder = encoder;
        this.emailService = emailService;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public void redefinir(RedefinirSenhaRequest request) {
        var resultado = Objects.requireNonNull(
                transacao.execute(status -> executar(request))
        );

        if (resultado.erro() != null) {
            throw new AutenticacaoException(resultado.erro());
        }

        try {
            emailService.enviarAvisoSenhaAlterada(
                    request.redefinicaoId(),
                    resultado.email()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Senha alterada, mas o aviso por e-mail falhou. "
                            + "redefinicaoId={}, tipo={}",
                    request.redefinicaoId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private Resultado executar(RedefinirSenhaRequest request) {
        var usuarioId = acoes
                .buscarUsuarioId(request.redefinicaoId())
                .orElse(null);

        if (usuarioId == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var usuario = usuarios
                .buscarPorIdComBloqueio(usuarioId)
                .orElse(null);

        if (usuario == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var autorizacao = acoes
                .buscarPorIdComBloqueio(request.redefinicaoId())
                .orElse(null);

        Instant agora = Instant.now();

        if (autorizacao == null
                || !autorizacao.getUsuario().getId().equals(usuarioId)
                || autorizacao.getFinalidade()
                != FinalidadeAcao.REDEFINICAO_SENHA
                || autorizacao.getConsumidoEm() != null
                || autorizacao.getInvalidadoEm() != null
                || !autorizacao.getExpiraEm().isAfter(agora)) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        if (!usuario.isAtivo()) {
            autorizacao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        if (autorizacao.getTentativas() >= 5) {
            autorizacao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
        }

        boolean tokenCorreto = segredos.conferir(
                usuarioId,
                autorizacao.getId(),
                FinalidadeAcao.REDEFINICAO_SENHA,
                request.tokenRedefinicao(),
                autorizacao.getSegredoHash()
        );

        if (!tokenCorreto) {
            autorizacao.setTentativas(
                    autorizacao.getTentativas() + 1
            );

            if (autorizacao.getTentativas() >= 5) {
                autorizacao.setInvalidadoEm(agora);
                return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
            }

            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        String novaSenha = request.novaSenha();

        if (novaSenha.isBlank()
                || novaSenha.codePointCount(0, novaSenha.length()) < 12
                || novaSenha.getBytes(StandardCharsets.UTF_8).length > 72) {
            return falha(ErroAutenticacao.SENHA_INVALIDA);
        }

        if (!novaSenha.equals(request.confirmacaoSenha())) {
            return falha(ErroAutenticacao.SENHAS_DIFERENTES);
        }

        String senhaHash = encoder.encode(novaSenha);
        Instant concluidoEm = Instant.now();

        if (!autorizacao.getExpiraEm().isAfter(concluidoEm)) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        usuario.setSenhaHash(senhaHash);
        usuario.setTentativas(0);
        usuario.setBloqueadoAte(null);

        autorizacao.setConsumidoEm(concluidoEm);

        for (var finalidade : FinalidadeAcao.values()) {
            for (var pendente : acoes.buscarPendentes(
                    usuarioId,
                    finalidade
            )) {
                if (!pendente.getId().equals(autorizacao.getId())) {
                    pendente.setInvalidadoEm(concluidoEm);
                }
            }
        }

        sessoes.revogarTodasDoUsuario(
                usuarioId,
                concluidoEm
        );

        return new Resultado(null, usuario.getEmail());
    }

    private Resultado falha(ErroAutenticacao erro) {
        return new Resultado(erro, null);
    }

    private record Resultado(
            ErroAutenticacao erro,
            String email
    ) {
        @Override
        public String toString() {
            return "Resultado[dados omitidos]";
        }
    }
}