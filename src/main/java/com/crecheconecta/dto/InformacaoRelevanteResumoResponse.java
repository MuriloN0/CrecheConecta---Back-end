package com.crecheconecta.dto;

import com.crecheconecta.entity.InformacaoRelevante;
import java.util.UUID;

public record InformacaoRelevanteResumoResponse(UUID id, String nome) {

    public static InformacaoRelevanteResumoResponse de(InformacaoRelevante informacao) {
        return new InformacaoRelevanteResumoResponse(informacao.getId(), informacao.getNome());
    }
}
