package com.crecheconecta.service;


import com.crecheconecta.dto.SolicitarRecuperacaoRequest;
import com.crecheconecta.dto.SolicitarRecuperacaoResponse;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class RecuperacaoSenhaService {

    private static final Logger log =
            LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final UsuarioRepository usuarios;
    private final AcaoVerificacaoRepository acoes;
    private final SegredoVerificacaoService segredos;
    private final EmailService emailService;
    private final TaskExecutor executor;
    private final TransactionTemplate transacao;

    public RecuperacaoSenhaService(
            UsuarioRepository usuarios,
            AcaoVerificacaoRepository acoes,
            SegredoVerificacaoService segredos,
            EmailService emailService,
            @Qualifier("recuperacaoExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarios = usuarios;
        this.acoes = acoes;
        this.segredos = segredos;
        this.emailService = emailService;
        this.executor = executor;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public SolicitarRecuperacaoResponse solicitar(
            SolicitarRecuperacaoRequest request
    ) {
        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        UUID acaoId = UUID.randomUUID();
        Instant expiraEm = Instant.now().plusSeconds(300);

        try {
            executor.execute(
                    () -> processar(email, acaoId, expiraEm)
            );
        } catch (TaskRejectedException exception) {
            throw new AutenticacaoException(
                    ErroAutenticacao.ENVIO_EMAIL_INDISPONIVEL
            );
        }

        return new SolicitarRecuperacaoResponse(
                acaoId,
                expiraEm,
                "Se houver uma conta apta para este e-mail, "
                        + "você receberá um código de recuperação."
        );
    }

    private void processar(
            String email,
            UUID acaoId,
            Instant expiraEm
    ) {
        try {
            var envio = transacao.execute(
                    status -> preparar(email, acaoId, expiraEm)
            );

            if (envio == null) {
                return;
            }

            try {
                emailService.enviarCodigo(
                        envio.acaoId(),
                        envio.email(),
                        envio.codigo(),
                        FinalidadeAcao.RECUPERACAO_SENHA
                );
            } catch (RuntimeException exception) {
                invalidar(
                        envio.usuarioId(),
                        envio.acaoId()
                );

                throw exception;
            }
        } catch (RuntimeException exception) {
            // Não registra e-mail, código ou conteúdo da requisição.
            log.error(
                    "Falha na recuperação de senha. acaoId={}, tipo={}",
                    acaoId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private EnvioPendente preparar(
            String email,
            UUID acaoId,
            Instant expiraEm
    ) {
        var usuario = usuarios
                .buscarPorEmailComBloqueio(email)
                .orElse(null);

        Instant agora = Instant.now();

        if (usuario == null
                || !usuario.isAtivo()
                || !expiraEm.isAfter(agora)) {
            return null;
        }

        var finalidade = FinalidadeAcao.RECUPERACAO_SENHA;

        var ultimaAcao = acoes
                .findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(
                        usuario.getId(),
                        finalidade
                );

        if (ultimaAcao.isPresent()
                && ultimaAcao.get()
                .getCriadoEm()
                .plusSeconds(60)
                .isAfter(agora)) {
            return null;
        }

        long quantidade = acoes
                .countByUsuario_IdAndFinalidadeAndCriadoEmGreaterThanEqual(
                        usuario.getId(),
                        finalidade,
                        agora.minusSeconds(3600)
                );

        if (quantidade >= 5) {
            return null;
        }

        for (var anterior : acoes.buscarPendentes(
                usuario.getId(),
                finalidade
        )) {
            anterior.setInvalidadoEm(agora);
        }

        String codigo = segredos.gerarCodigo();

        var acao = new AcaoVerificacao();
        acao.setId(acaoId);
        acao.setUsuario(usuario);
        acao.setFinalidade(finalidade);
        acao.setCriadoEm(agora);
        acao.setExpiraEm(expiraEm);
        acao.setTentativas(0);

        acao.setSegredoHash(
                segredos.proteger(
                        usuario.getId(),
                        acaoId,
                        finalidade,
                        codigo
                )
        );

        acoes.save(acao);

        return new EnvioPendente(
                usuario.getId(),
                acaoId,
                usuario.getEmail(),
                codigo
        );
    }

    private void invalidar(
            UUID usuarioId,
            UUID acaoId
    ) {
        transacao.executeWithoutResult(status -> {
            usuarios.buscarPorIdComBloqueio(usuarioId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Usuário da ação não encontrado."
                    ));

            acoes.buscarPorIdComBloqueio(acaoId)
                    .ifPresent(acao -> {
                        if (acao.getConsumidoEm() == null
                                && acao.getInvalidadoEm() == null) {
                            acao.setInvalidadoEm(Instant.now());
                        }
                    });
        });
    }

    private record EnvioPendente(
            UUID usuarioId,
            UUID acaoId,
            String email,
            String codigo
    ) {
        @Override
        public String toString() {
            return "EnvioPendente[dados omitidos]";
        }
    }

}
