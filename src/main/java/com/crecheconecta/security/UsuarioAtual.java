package com.crecheconecta.security;

import java.util.UUID;

import com.crecheconecta.entity.Perfil;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

public record UsuarioAtual(UUID id, Perfil perfil) {

    public static UsuarioAtual de(UsuarioAutenticado autenticado) {
        if (autenticado == null || autenticado.perfil() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticação necessária."
            );
        }

        Perfil perfil = switch (autenticado.perfil()) {
            case DIRECAO -> Perfil.DIRECAO;
            case PROFESSOR -> Perfil.PROFESSOR;
            case RESPONSAVEL -> Perfil.RESPONSAVEL;
        };

        return new UsuarioAtual(autenticado.usuarioId(), perfil);
    }
}