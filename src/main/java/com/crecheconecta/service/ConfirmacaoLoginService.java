package com.crecheconecta.service;

import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import com.crecheconecta.repository.AcaoVerificacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ConfirmacaoLoginService {

    private static final Pattern FORMATO_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int MAXIMO_ERROS_POR_CODIGO = 5;
    private static final int MAXIMO_ERROS_RECENTES = 10;
    private static final Duration JANELA_ERROS_RECENTES = Duration.ofMinutes(15);
    private static final Duration BLOQUEIO_POR_CODIGO = Duration.ofMinutes(5);

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
        return confirmar(request, null, null);
    }

    public SessaoResponse confirmar(ConfirmarLoginRequest request, String refreshToken) {
        return confirmar(request, refreshToken, null);
    }

    public SessaoResponse confirmar(
            ConfirmarLoginRequest request, String refreshToken, String refreshAnterior
    ) {
        var resultado = Objects.requireNonNull(
                transacao.execute(status -> validarECriarSessao(request, refreshToken, refreshAnterior))
        );

        if (resultado.erro() != null) {
            throw new AutenticacaoException(resultado.erro());
        }

        return resultado.sessao();
    }

    private ResultadoConfirmacao validarECriarSessao(
            ConfirmarLoginRequest request, String refreshToken, String refreshAnterior
    ) {
        var usuarioId = acoes.buscarUsuarioId(request.acaoId()).orElse(null);
        if (usuarioId == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var usuario = usuarios.buscarPorIdComBloqueio(usuarioId).orElse(null);
        if (usuario == null) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        var acao = acoes.buscarPorIdComBloqueio(request.acaoId()).orElse(null);
        Instant agora = Instant.now();

        if (acao == null || !acao.getUsuario().getId().equals(usuarioId)
                || acao.getFinalidade() != FinalidadeAcao.LOGIN
                || acao.getConsumidoEm() != null || acao.getInvalidadoEm() != null
                || !acao.getExpiraEm().isAfter(agora)) {
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        if (!usuario.isAtivo()
                || (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(agora))) {
            acao.setInvalidadoEm(agora);
            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        long errosRecentes = acoes.somarTentativasDesde(
                usuarioId, FinalidadeAcao.LOGIN, agora.minus(JANELA_ERROS_RECENTES)
        );

        if (acao.getTentativas() >= MAXIMO_ERROS_POR_CODIGO || errosRecentes >= MAXIMO_ERROS_RECENTES) {
            return bloquearConfirmacao(usuario, acao, agora);
        }

        boolean correto = segredos.conferir(
                usuarioId, acao.getId(), FinalidadeAcao.LOGIN, request.codigo(), acao.getSegredoHash()
        );

        if (!correto) {
            acao.setTentativas(acao.getTentativas() + 1);

            if (acao.getTentativas() >= MAXIMO_ERROS_POR_CODIGO
                    || errosRecentes + 1 >= MAXIMO_ERROS_RECENTES) {
                return bloquearConfirmacao(usuario, acao, agora);
            }

            return falha(ErroAutenticacao.ACAO_INVALIDA);
        }

        String hashAnterior = refreshAnterior != null && FORMATO_TOKEN.matcher(refreshAnterior).matches()
                ? tokens.calcularHash(refreshAnterior)
                : null;
        sessoes.revogarSubstituidas(usuarioId, hashAnterior, agora);

        String token = tokens.gerar();
        Instant acessoExpiraEm = agora.plus(SessaoService.DURACAO_ACESSO);

        var sessao = new Sessao();
        sessao.setUsuario(usuario);
        sessao.setTokenHash(tokens.calcularHash(token));
        sessao.setCriadoEm(agora);
        sessao.setExpiraEm(acessoExpiraEm);

        if (refreshToken != null) {
            sessao.setRenovacaoHash(tokens.calcularHash(refreshToken));
            sessao.setAcessoExpiraEm(acessoExpiraEm);
            sessao.setExpiraEm(agora.plus(SessaoService.DURACAO_SESSAO));
        }

        sessoes.save(sessao);
        acao.setConsumidoEm(agora);

        return new ResultadoConfirmacao(null, new SessaoResponse(
                token, "Bearer", acessoExpiraEm, usuario.getId(), usuario.getNome(), usuario.getPerfil()
        ));
    }

    private ResultadoConfirmacao bloquearConfirmacao(
            Usuario usuario, AcaoVerificacao acao, Instant agora
    ) {
        acao.setInvalidadoEm(agora);
        usuario.setBloqueadoAte(agora.plus(BLOQUEIO_POR_CODIGO));
        return falha(ErroAutenticacao.LIMITE_TENTATIVAS);
    }

    private ResultadoConfirmacao falha(ErroAutenticacao erro) {
        return new ResultadoConfirmacao(erro, null);
    }

    private record ResultadoConfirmacao(ErroAutenticacao erro, SessaoResponse sessao) {}
}