package com.crecheconecta.saude.adapter.in.web;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.time.Instant;
import java.util.UUID;

public record FichaSaudeResumoResponse (
        UUID id,
        String nome,
        Instant dataCriacao
){
    public static FichaSaudeResumoResponse de(FichaSaude ficha) {
        return new FichaSaudeResumoResponse(ficha.id(), ficha.nome(), ficha.dataCriacao());
    }
}
