package com.crecheconecta.dto;

import java.time.Instant;
import java.util.UUID;

public record ConfirmarRecuperacaoResponse(UUID redefinicaoId, String tokenRedefinicao, Instant expiraEm) {

    @Override
    public String toString() {
        return "ConfirmarRecuperacaoResponse[dados omitidos]";
    }

}
