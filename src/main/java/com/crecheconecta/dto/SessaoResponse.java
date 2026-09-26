package com.crecheconecta.dto;

import com.crecheconecta.entity.Perfil;

import java.time.Instant;
import java.util.UUID;

public record SessaoResponse(String accessToken, String tokenType, Instant expiraEm, UUID usuarioId, String nome, Perfil perfil) {

    @Override
    public String toString() {
        return "SessaoResponse[credenciais omitidas]";
    }

}
