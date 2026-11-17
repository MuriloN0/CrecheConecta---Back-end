package com.crecheconecta.service;

import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class SessaoService {

    static final Duration DURACAO_ACESSO = Duration.ofMinutes(15);
    static final Duration DURACAO_SESSAO = Duration.ofMinutes(50);

    private static final Pattern FORMATO_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SessaoRepository sessoes;
    private final TokenSessaoService tokens;

    public SessaoService(SessaoRepository sessoes, TokenSessaoService tokens) {
        this.sessoes = sessoes;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioAutenticado> autenticar(String token) {
        if (!tokenValido(token)) {
            return Optional.empty();
        }

        var sessao = sessoes.findByTokenHash(tokens.calcularHash(token)).orElse(null);
        Instant agora = Instant.now();

        if (!sessaoAtiva(sessao, agora)) {
            return Optional.empty();
        }

        Instant acessoExpiraEm = sessao.getAcessoExpiraEm();
        if (acessoExpiraEm != null && !acessoExpiraEm.isAfter(agora)) {
            return Optional.empty();
        }

        var usuario = sessao.getUsuario();

        return Optional.of(new UsuarioAutenticado(
                usuario.getId(), sessao.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getPerfil()
        ));
    }

    @Transactional
    public Optional<SessaoResponse> renovar(String refreshToken) {
        if (!tokenValido(refreshToken)) {
            return Optional.empty();
        }

        var sessao = sessoes.buscarParaRenovacao(tokens.calcularHash(refreshToken)).orElse(null);
        Instant agora = Instant.now();

        if (!sessaoAtiva(sessao, agora)) {
            return Optional.empty();
        }

        String novoToken = tokens.gerar();
        Instant acessoExpiraEm = agora.plus(DURACAO_ACESSO);

        sessao.setTokenHash(tokens.calcularHash(novoToken));
        sessao.setAcessoExpiraEm(acessoExpiraEm);
        sessao.setExpiraEm(agora.plus(DURACAO_SESSAO));

        var usuario = sessao.getUsuario();
        return Optional.of(new SessaoResponse(
                novoToken, "Bearer", acessoExpiraEm, usuario.getId(),
                usuario.getNome(), usuario.getPerfil()
        ));
    }

    @Transactional
    public void logout(UsuarioAutenticado usuario) {
        sessoes.revogar(usuario.sessaoId(), usuario.usuarioId(), Instant.now());
    }

    private boolean tokenValido(String token) {
        return token != null && FORMATO_TOKEN.matcher(token).matches();
    }

    private boolean sessaoAtiva(Sessao sessao, Instant agora) {
        return sessao != null
                && sessao.getRevogadoEm() == null
                && sessao.getExpiraEm().isAfter(agora)
                && sessao.getUsuario().isAtivo();
    }
}