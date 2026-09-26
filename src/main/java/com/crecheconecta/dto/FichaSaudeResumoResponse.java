package com.crecheconecta.dto;

import com.crecheconecta.entity.FichaSaude;
import java.util.UUID;

public record FichaSaudeResumoResponse(UUID id, String nome, int quantidadeAnexos) {

    public static FichaSaudeResumoResponse de(FichaSaude ficha) {
        return new FichaSaudeResumoResponse(ficha.getId(), ficha.getNome(), ficha.getAnexos().size());
    }
}