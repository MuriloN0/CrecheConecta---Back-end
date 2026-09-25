package com.crecheconecta.service;


import com.crecheconecta.dto.ConfirmarRecuperacaoRequest;
import com.crecheconecta.dto.ConfirmarRecuperacaoResponse;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
public class ConfirmacaoRecuperacaoService {

    private final UsuarioRepository usuarios;
    private final AcaoVerificacaoRepository acoes;
    private final SegredoVerificacaoService segredos;
    private final TransactionTemplate transacao;

    public ConfirmacaoRecuperacaoService(
            UsuarioRepository usuarios,
            AcaoVerificacaoRepository acoes,
            SegredoVerificacaoService segredos,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarios = usuarios;
        this.acoes = acoes;
        this.segredos = segredos;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public ConfirmarRecuperacaoResponse confirmar(
            ConfirmarRecuperacaoRequest request
    ) {
        var resultado = Objects.requireNonNull(
                transacao.execute(
                        status -> validarECriarAutorizacao(request)
                )
        );

        if (resultado.erro() != null) {
            throw new AutenticacaoException(resultado.erro());
        }

        return resultado.resposta();
    }

    private Resultado validarECriarAutorizacao(
            ConfirmarRecuperacaoRequest request
    ) {
        var usuarioId = acoes.buscarUsuarioId(request.acaoId())
                .orElse(null);

        if (usuarioId == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var usuario = usuarios.buscarPorIdComBloqueio(usuarioId)
                .orElse(null);

        if (usuario == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var acao = acoes.buscarPorIdComBloqueio(request.acaoId())
                .orElse(null);

        Instant agora = Instant.now();

        if (acao == null
                || !acao.getUsuario().getId().equals(usuarioId)
                || acao.getFinalidade()
                != FinalidadeAcao.RECUPERACAO_SENHA
                || acao.getConsumidoEm() != null
                || acao.getInvalidadoEm() != null
                || !acao.getExpiraEm().isAfter(agora)) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        if (!usuario.isAtivo()) {
            acao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        long errosRecentes = acoes.somarTentativasDesde(
                usuarioId,
                FinalidadeAcao.RECUPERACAO_SENHA,
                agora.minusSeconds(900)
        );

        if (acao.getTentativas() >= 5 || errosRecentes >= 10) {
            acao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
        }

        boolean correto = segredos.conferir(
                usuarioId,
                acao.getId(),
                FinalidadeAcao.RECUPERACAO_SENHA,
                request.codigo(),
                acao.getSegredoHash()
        );

        if (!correto) {
            acao.setTentativas(acao.getTentativas() + 1);

            if (acao.getTentativas() >= 5
                    || errosRecentes + 1 >= 10) {
                acao.setInvalidadoEm(agora);
                return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
            }

            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        // Uma nova confirmação substitui autorizações anteriores
        // que ainda não foram utilizadas.
        for (var anterior : acoes.buscarPendentes(
                usuarioId,
                FinalidadeAcao.REDEFINICAO_SENHA
        )) {
            anterior.setInvalidadoEm(agora);
        }

        String token = segredos.gerarToken();

        var autorizacao = new AcaoVerificacao();
        autorizacao.setUsuario(usuario);
        autorizacao.setFinalidade(FinalidadeAcao.REDEFINICAO_SENHA);
        autorizacao.setCriadoEm(agora);
        autorizacao.setExpiraEm(agora.plusSeconds(300));
        autorizacao.setTentativas(0);

        autorizacao.setSegredoHash(
                segredos.proteger(
                        usuarioId,
                        autorizacao.getId(),
                        FinalidadeAcao.REDEFINICAO_SENHA,
                        token
                )
        );

        acoes.save(autorizacao);
        acao.setConsumidoEm(agora);

        return new Resultado(
                null,
                new ConfirmarRecuperacaoResponse(
                        autorizacao.getId(),
                        token,
                        autorizacao.getExpiraEm()
                )
        );
    }

    private Resultado falha(ErroAutenticacao erro) {
        return new Resultado(erro, null);
    }

    private record Resultado(
            ErroAutenticacao erro,
            ConfirmarRecuperacaoResponse resposta
    ) {
    }

}
