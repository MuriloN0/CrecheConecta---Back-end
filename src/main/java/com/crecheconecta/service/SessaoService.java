package com.crecheconecta.service;


import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class SessaoService {

    private final SessaoRepository sessoes;
    private final TokenSessaoService tokens;

    public SessaoService(
            SessaoRepository sessoes,
            TokenSessaoService tokens
    ) {
        this.sessoes = sessoes;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioAutenticado> autenticar(String token) {
        // Token de 32 bytes codificado em Base64 URL sem padding.
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }

        String hash = tokens.calcularHash(token);

        var sessao = sessoes.findByTokenHash(hash).orElse(null);

        if (sessao == null
                || sessao.getRevogadoEm() != null
                || !sessao.getExpiraEm().isAfter(Instant.now())) {
            return Optional.empty();
        }

        var usuario = sessao.getUsuario();

        if (!usuario.isAtivo()) {
            return Optional.empty();
        }

        return Optional.of(
                new UsuarioAutenticado(
                        usuario.getId(),
                        sessao.getId(),
                        usuario.getNome(),
                        usuario.getEmail(),
                        usuario.getPerfil()
                )
        );
    }

    @Transactional
    public void logout(UsuarioAutenticado usuario) {
        sessoes.revogar(
                usuario.sessaoId(),
                usuario.usuarioId(),
                Instant.now()
        );
    }

}
