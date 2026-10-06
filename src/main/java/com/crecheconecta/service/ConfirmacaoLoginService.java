package com.crecheconecta.service;


import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
public class ConfirmacaoLoginService {

    private final UsuarioRepository usuarios;
    private final AcaoVerificacaoRepository acoes;
    private final SessaoRepository sessoes;
    private final SegredoVerificacaoService segredos;
    private final TokenSessaoService tokens;
    private final TransactionTemplate transacao;

    public ConfirmacaoLoginService(
            UsuarioRepository usuarios,
            AcaoVerificacaoRepository acoes,
            SessaoRepository sessoes,
            SegredoVerificacaoService segredos,
            TokenSessaoService tokens,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarios = usuarios;
        this.acoes = acoes;
        this.sessoes = sessoes;
        this.segredos = segredos;
        this.tokens = tokens;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public SessaoResponse confirmar(ConfirmarLoginRequest request) {
        var resultado = Objects.requireNonNull(
                transacao.execute(status -> validarECriarSessao(request))
        );

        if (resultado.erro() != null) {
            throw new AutenticacaoException(resultado.erro());
        }

        return resultado.sessao();
    }

    private ResultadoConfirmacao validarECriarSessao(
            ConfirmarLoginRequest request
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
                || acao.getFinalidade() != FinalidadeAcao.LOGIN
                || acao.getConsumidoEm() != null
                || acao.getInvalidadoEm() != null
                || !acao.getExpiraEm().isAfter(agora)) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        if (!usuario.isAtivo()
                || (usuario.getBloqueadoAte() != null
                && usuario.getBloqueadoAte().isAfter(agora))) {
            acao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        long errosRecentes = acoes.somarTentativasDesde(
                usuarioId,
                FinalidadeAcao.LOGIN,
                agora.minusSeconds(900)
        );

        if (acao.getTentativas() >= 5 || errosRecentes >= 10) {
            acao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
        }

        boolean correto = segredos.conferir(
                usuarioId,
                acao.getId(),
                FinalidadeAcao.LOGIN,
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

        String token = tokens.gerar();

        var sessao = new Sessao();
        sessao.setUsuario(usuario);
        sessao.setTokenHash(tokens.calcularHash(token));
        sessao.setCriadoEm(agora);
        sessao.setExpiraEm(agora.plusSeconds(900));

        sessoes.save(sessao);
        acao.setConsumidoEm(agora);

        return new ResultadoConfirmacao(
                null,
                new SessaoResponse(
                        token,
                        "Bearer",
                        sessao.getExpiraEm(),
                        usuario.getId(),
                        usuario.getNome(),
                        usuario.getPerfil()
                )
        );
    }

    private ResultadoConfirmacao falha(ErroAutenticacao erro) {
        return new ResultadoConfirmacao(erro, null);
    }

    private record ResultadoConfirmacao(
            ErroAutenticacao erro,
            SessaoResponse sessao
    ) {
    }

}
