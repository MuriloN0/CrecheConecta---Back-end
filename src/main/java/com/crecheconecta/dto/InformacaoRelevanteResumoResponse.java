package com.crecheconecta.dto;

import com.crecheconecta.entity.FichaSaude;
import java.util.UUID;

public record FichaSaudeResumoResponse(UUID id, String nome) {

    public static FichaSaudeResumoResponse de(FichaSaude ficha) {
        return new FichaSaudeResumoResponse(ficha.getId(), ficha.getNome());
    }
}
